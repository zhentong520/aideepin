# T7-05 bge-m3 embedding 替换评估 · 现状核查与补齐清单

> 核查日期：2026-10-21　| 铁律 9：**本环境无法产出 bge-m3 与当前 provider 的真实分数对比，不伪造结论。**
> 本报告给出的是：① 环境缺什么（可执行清单）② 已就绪的接入点与评估脚本 ③ 换 provider 的量化代价基线。

## 一、结论摘要

| 事项 | 状态 |
|---|---|
| bge-m3 接入点（provider 切换 + 全量重建 + 回滚） | ✅ 第 8 批已交付（`EmbeddingProviderService` / `EmbeddingProviderGuard` / 重建 API） |
| 评估脚本（两 provider 同题库对照） | ✅ 已交付（`run_embedding_eval.sh` + `sample-embedding-report-structure.json`） |
| **bge-m3 可调用端点** | ❌ **本环境不存在**——详见第二节 |
| 真实分数对比结论 | ❌ 无法产出（缺端点）。**不猜、不外推、不用别家数据冒充** |

**决策建议**：在拿到 bge-m3 端点并跑出真实对比之前，**保持当前 provider 不变**（铁律 10 可回滚已就位，随时可切）。

## 二、环境缺什么（逐条实测）

| 候选端点 | 实测结果 | 结论 |
|---|---|---|
| volcengine（火山方舟，**有 key**，当前生产 provider） | `POST /embeddings` + `model=BAAI/bge-m3` → **HTTP 404 `UnsupportedModel`** | 该平台不提供 bge-m3 |
| siliconflow | `adi_model_platform.api_key` 为空 | 补 key 即可用（`BAAI/bge-m3` 是其标准模型） |
| dashscope | `adi_model_platform.api_key` 为空 | 补 key 即可用（通义有 `text-embedding-v3`，非 bge-m3） |
| ollama（本地） | `localhost:11434` 连接被拒（未部署） | 需 `ollama pull bge-m3` + 起服务 |
| langchain4j 内置 ONNX 推理 | **无 bge-m3 推理类**（只有 `bge-small-zh-v15`）；bge-m3 权重约 2GB，不适合随发行包分发 | 只能走远端 API 或独立推理服务 |

**最小补齐动作（二选一）**：

1. **给 siliconflow 补 key**（最快，5 分钟）：
   ```sql
   update adi_model_platform set api_key = '<siliconflow key>' where name = 'siliconflow';
   ```
   然后用 `model=BAAI/bge-m3`（1024 维）跑对照。
2. **本地 ollama**：`ollama pull bge-m3` + `ollama serve`，embedding 走 `http://localhost:11434/v1/embeddings`
   （OpenAI 兼容协议，`EmbeddingProviderService` 已支持 `platform:model` 形态）。

## 三、接入点与脚本已就绪（补上端点即可跑）

- **provider 解析**：`EmbeddingProviderService`（`local:xxx` / `platform:model` 两种形态），
  切换由配置 `adi.embedding-model` 决定，`EmbeddingProviderGuard` 负责阻止「切了 provider 但没重建索引」的错误组合。
- **全量重建**：`KnowledgeBaseController` / `AdminKbController` 已提供「失效该库全部文档段行与向量」的标准动作，
  换 provider 后的标准流程就是「切配置 → 重建 → 跑基线」。
- **评估脚本**：`server/dev-scripts/eval/run_embedding_eval.sh`（同题库、同分块参数，两 provider 对照），
  报告结构见 `sample-embedding-report-structure.json`。
- **基线**：`RetrievalRegressionBaselineTest`（第 7 批，025/026/027/030/017 固化）——换 provider 后**必须**跑通。

## 四、换 provider 的量化代价基线（可先看这部分）

当前生产 provider：`volcengine / doubao-embedding-vision`，**2048 维**；
本地备选 `bge-small-zh-v1.5`：**512 维**（pgvector 表 `adi_knowledge_base_embedding_bge_512`）。

| 项 | 现状（2048 维） | bge-m3（1024 维，若接入） | 备注 |
|---|---|---|---|
| 单向量存储 | 2048 × 4B ≈ 8KB | 1024 × 4B ≈ 4KB | 索引体积约减半 |
| 全量重建成本 | 语料篇数 × embedding 调用 | 同 | **换 provider 必须全量重建**（向量空间不兼容） |
| 检索延迟 | 与维度正相关 | 预计更优 | 需实测 |
| 中文效果 | 未知（无同题库对比数据） | 未知 | **必须实跑才知** |

⚠️ 维度变化不只是「重建」：pgvector 表按后缀区分向量空间
（现库中并存 `adi_knowledge_base_embedding_bge_512` 与 `..._volcengine_2048` 两套），
切换后**新旧向量不可混用**，回滚 = 切回旧 provider + 重新全量重建（该流程已在代码中固化）。

## 五、补上端点后的执行清单（约 30 分钟）

```bash
# 0. 补 key（或起 ollama）后，先验证端点能出向量
# 1. 确认 provider 配置可切：adi.embedding-model=platform:BAAI/bge-m3
# 2. 全量重建：调「失效该库全部文档段行与向量」API，然后重新入库
# 3. 跑同题库对照：
cd E:/reg-project/kb-eval
WINDOWS="0" bash run_eval.sh "BGE-M3:recursive:512:0:"     # 候选
WINDOWS="0" bash run_eval.sh "DOUBAO:recursive:512:0:"     # 基线
# 4. 跑第 7 批基线回归：RetrievalRegressionBaselineTest
# 5. 出结论：recall@5 / mrr@10 / ans@5 / ctxChars@5 四项对比 + 重建成本
```

判定口径（写死，避免事后找理由）：
- **值得换**：recall@5 或 ans@5 提升 ≥ 2 个百分点，且重建成本可接受；
- **不值得换**：两项持平或下降 → 保持现状（并把重建成本记为「省下的钱」）。

## 六、遗留风险

- 本报告的「代价基线」是**结构性的**（维度、存储、重建流程），不含质量结论；
- 质量结论必须等真实端点。若决定投入，建议先在 1 个知识库上做**单库试点**（不切生产 provider，
  用 `platform:BAAI/bge-m3` + 独立表后缀），确认提升后再全量。
