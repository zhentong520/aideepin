# 端到端 QA 评测（T6-01）与 overlap 对照（T6-04）

> 铁律 9：分数必须来自真实检索/生成链路。本地无 LLM key / 知识库环境时，
> 只交付并验证「可执行脚本 + 环境接入说明 + 报告结构」，绝不伪造分数。

## 目录

| 文件 | 作用 |
|---|---|
| `qa-dataset.json` | 固定题库（34 题：fact 18 / multi_hop 7 / refusal 9）。金标片段逐字照抄语料原文，派生自 kb-eval `golden.jsonl` |
| `compare_reports.py` | 对比两份报告：指标变化 + 逐题明细变化（改善/退步/拒答翻转） |
| `run_overlap_eval.sh` | T6-04：overlap ∈ {0, 0.1, 0.2}（折算 0/51/102 token）三组对照编排 |
| `run_embedding_eval.sh` | T7-05：embedding provider（基线 vs bge-m3 候选）同题库对照编排 |
| `sample-report-structure.json` | 报告 JSON schema 示例（占位数字，非真实分数） |
| `sample-embedding-report-structure.json` | T7-05 报告 schema + 结论解释纪律（占位数字，非真实分数） |
| `reports/` | 真实跑分报告落盘处（`eval-<时间戳>.json`），入 git 供对比 |
| Java 侧 | `com.moyz.adi.common.eval` 包：`QaEvalScorer`（计分器，单测覆盖）/ `QaEvalRunner`（编排）/ `KbQaEvalGateway`（真实链路网关）/ `QaEvalMain`（CLI 入口） |

## 指标定义（与计分器实现一一对应）

- **accuracy（命中 gold 来源）**：非拒答题中，每条金标片段被上下文**逐字覆盖**且覆盖块的文档归属
  与题库 `sourceDocs` 一致的比例。只覆盖内容不看出处不算命中。
- **recall（召回率）**：非拒答题金标片段的逐题平均覆盖率。
- **refusalAccuracy（拒答正确率）**：应拒答的拒了 + 不应拒答的答了，占全部题的比例。
  拒答判定：网关显式给出，或按 无检索结果 / 空答案 / 命中拒答话术 推断。
- 网关异常的题按 0 分计入，**不剔除不重试**；报告里 `errored` 逐题可见。

## 环境接入（跑真实分数的步骤）

1. 准备知识库：新建（或复用）一个知识库，把 37 篇产品文档按当前入库参数灌入并完成向量化。
2. 开启评测网关：应用启动参数加 `adi.eval.enabled=true`（默认 false，防止评测意外消耗额度）。
3. 触发跑分（在 Spring 上下文内）：
   - 方式 A：把 `QaEvalMain.attach(applicationContext)` 挂到启动监听器后，
     通过应用内入口执行 `QaEvalMain --dataset <题库> --kb <kbUuid> [--with-generation]`；
   - 方式 B：运维接口注入 `KbQaEvalGateway` 后调用 `QaEvalRunner.run(dataset, gateway, config, reportDir, env)`。
4. 报告自动写入 `reports/eval-<时间戳>.json`。
5. 对比两轮：`python compare_reports.py reports/eval-A.json reports/eval-B.json`。

**无 key 环境的最低验证**：`mvn -pl adi-common test -Dtest=QaEvalScorerTest`
——计分器的全部算术（覆盖判定 / 来源匹配 / 拒答判定 / 三指标聚合）用 mock 检索结果单测覆盖，
跑分链路的正确性由测试保证，真实分数只差一个可用环境。

## T6-04 overlap 对照流程

```bash
# 1. 三个知识库分别按 overlap=0 / 51 / 102 token 重建同语料索引（管理端操作，消耗真实额度）
# 2. 演练计划（无环境也能跑）：
bash run_overlap_eval.sh --kb a b c --dry-run
# 3. 真实跑分（需 EVAL_RUN_CMD，见脚本头注释）：
EVAL_RUN_CMD='...' bash run_overlap_eval.sh --kb <kbOv0> <kbOv51> <kbOv102>
```

⚠️ 约束：默认 overlap 保持 0 不动，是否开启 overlap 由三组对照的量化结论决定；
改动前后必须先跑 T6-02 基线（`RetrievalRegressionBaselineTest`）归因。

## T7-05 embedding provider（bge-m3）评估流程

```bash
# 0. 基线体检：先过 T6-02 检索回归基线
#    mvn -pl adi-common test -Dtest=RetrievalRegressionBaselineTest
# 1. 基线轮：当前 adi.embedding-model 下的语料库跑一轮，报告归档 embedding-baseline.json
# 2. 切换候选：adi.embedding-model 改为 bge-m3（如 xinference:bge-m3，模型配置 dimension=1024）→ 重启
# 3. 全量重建：POST /admin/kb/rebuildEmbedding/{uuid}（T7-05 端点），确认响应 currentVersion 与生效 provider 一致
# 4. 候选轮：再跑一轮，报告归档 embedding-candidate.json
# 5. 对比：
EVAL_RUN_CMD='...' bash run_embedding_eval.sh --baseline-kb <kbUuid> --candidate-kb <kbUuid>
# 无环境演练（只打印计划，不产出数字）：
bash run_embedding_eval.sh --baseline-kb a --candidate-kb b --dry-run
```

⚠️ 约束与解释纪律：

- 两轮必须同题库、同 top-k、同切分/rerank 参数，唯一变量是 embedding provider；
- 候选轮全题零命中时优先怀疑「切模型后未全量重建」（新维度表为空），启动日志
  `EMBEDDING PROVIDER MISMATCH` 告警（`EmbeddingProviderGuard`）与重建端点响应可定位；
- 默认 `adi.embedding-model` 在拿到可复现量化结论前保持不变；回滚 = 切回基线 provider
  + 重启 + 对受影响库再调一次重建端点（审计动作 `KB_REINDEX` 记录完整版本链）；
- 单题权重 ≈ 1/34 ≈ 2.9%，指标变化 < 3 题（约 ±8.8%）不下结论，先核对逐题明细。

## 题库维护纪律

- `expected` 片段必须是语料原文**逐字串**（与 kb-eval AddNeed.py 同纪律）；
- 语料文档改名/改写后必须同步评审题库（compare 脚本对题量不一致会直接提示）；
- 拒答题的口径：题面在语料范围内无答案；新增能力后要复核拒答题是否仍成立。
