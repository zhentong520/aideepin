# 端到端 QA 评测（T6-01）与 overlap 对照（T6-04）

> 铁律 9：分数必须来自真实检索/生成链路。本地无 LLM key / 知识库环境时，
> 只交付并验证「可执行脚本 + 环境接入说明 + 报告结构」，绝不伪造分数。

## 目录

| 文件 | 作用 |
|---|---|
| `qa-dataset.json` | 固定题库（34 题：fact 18 / multi_hop 7 / refusal 9）。金标片段逐字照抄语料原文，派生自 kb-eval `golden.jsonl` |
| `compare_reports.py` | 对比两份报告：指标变化 + 逐题明细变化（改善/退步/拒答翻转） |
| `run_overlap_eval.sh` | T6-04：overlap ∈ {0, 0.1, 0.2}（折算 0/51/102 token）三组对照编排 |
| `sample-report-structure.json` | 报告 JSON schema 示例（占位数字，非真实分数） |
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

## 题库维护纪律

- `expected` 片段必须是语料原文**逐字串**（与 kb-eval AddNeed.py 同纪律）；
- 语料文档改名/改写后必须同步评审题库（compare 脚本对题量不一致会直接提示）；
- 拒答题的口径：题面在语料范围内无答案；新增能力后要复核拒答题是否仍成立。
