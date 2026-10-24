# OPT-15 第 1 步 · adi-common 划域实证报告（2026-10-24）

> 结论先行：**D-5 拍板的六域边界与源码现实存在 4 组结构性互环**（23 处跨域 service 引用）。
> 不解环就无法做 Maven 模块化（模块间循环依赖 = 构建失败）。本报告给出证据、
> 解环清单与分批落地顺序；域业务归属不变，仅做「共享面下沉 core」与
> 「multiagent 归属修正」两项证据驱动的实施期修正。

## 一、跨域 service 引用矩阵（扫描脚本口径：类名前缀 → 域，D-5 边界）

合法方向（高层引低层，分层 = draw < model < kb-rag < workflow < chat-core）：

| 方向 | 处数 | 代表 |
|---|---|---|
| kb-rag → model | 10 | CompositeRag → AbstractLLMService |
| workflow → model | 9 | KnowledgeRetrievalNode → 模型服务 |
| workflow → kb-rag | 5 | KnowledgeRetrievalNode → EmbeddingRag |
| chat-core → kb-rag | 11 | CharacterChatService → DocumentSegmentService |
| chat-core → model | 7 | CharacterChatService → AbstractLLMService |

**违反方向（环）**：

| 环 | 处数 | 明细 |
|---|---|---|
| draw → model | 1 | DrawService → AbstractImageModelService |
| model → kb-rag | 4 | AbstractLLMService → TokenEstimatorFactory/ThreadLocal；AiModelInitializer → GoogleSearchEngineService/SearchEngineServiceContext |
| model → chat-core | 7 | AbstractLLMService → MapDBChatMemoryStore/SkillToolService；DelegateAgentTool/DelegationAgentResolver/DelegationService → CharacterService/UserService/AgentService |
| kb-rag → chat-core | 4 | CompositeRag/KnowledgeBaseService → MapDBChatMemoryStore；DocumentQaModeService/GraphRag → UserDayCostService |
| workflow → chat-core | 5 | AgentNode/OrchestratorNode → AgentService；SkillNode → SkillService/SkillToolService；CodeNode → SkillScriptRunner |

## 二、解环方案（共享面下沉 core，零接口倒置）

对拟下沉类做了依赖纯净度检查（逐 import 核对）：

| 类 | 依赖纯净度 | 处置 |
|---|---|---|
| TokenEstimatorThreadLocal | ✓ 纯净 | 下沉 core |
| MapDBChatMemoryStore | ✓ 纯净（仅 entity） | 下沉 core（对话记忆存储基建） |
| SkillScriptRunner | ✓ 纯净 | 下沉 core |
| AgentCallChain | ✓ 纯净 | 下沉 core |
| UserDayCostService | ✓ 纯净（仅 entity/mapper） | 下沉 core（平台计费基建） |
| TokenEstimatorFactory | ✗ 引 AbstractLLMService | 拆分：Factory 与 LLM 远程估算实现分离，接口进 core |
| SearchEngineServiceContext / GoogleSearchEngineService | ✗ 引 interfaces.AbstractSearchEngineService | 连同 AbstractSearchEngineService（interfaces 包，本就是公共抽象）一起下沉 core |
| SkillToolService | ✗ 引 CharacterService/SkillService | 留 chat-core；model/workflow 消费点改为 core 接口（唯一一处接口倒置） |

**multiagent 归属修正**：D-5 决策 4 将 multiagent 归 adi-model，理由是
AbstractLLMService 依赖 AgentCallChain。实证显示 DelegateAgentTool /
DelegationAgentResolver / DelegationService 直接引用 CharacterService /
UserService / AgentService（chat-core 核心），multiagent 留在 model 会永久锁死
model→chat-core 反向依赖。AgentCallChain 下沉 core 后 AbstractLLMService 的依赖
已解，**multiagent 改归 chat-core**（依赖链闭合：multiagent → CharacterService 同域）。

## 三、分批落地顺序（每批全量测试 + 独立提交，过渡聚合层模式）

1. **批 1 · core|biz 切分**：adi-common 拆为 adi-common-core（地基 + 上述下沉类 +
   entity/mapper）与 adi-common-biz（全部域代码的过渡聚合层）。
   ⚠️ 实证：core 候选包存在 54 处对业务包的存量反向引用（base 的 TypeHandler/Jobs、
   config 的 BeanConfig/GraphStore 配置、helper 的 5 个 ModelContext、util 的
   CharacterChatHelper 等）——这些类随业务走 biz，core 集合以
   「不引用任何业务包」为唯一判据反向生成。
2. **批 2 · adi-draw**（试点，3 个 service）；draw → biz（model 尚在 biz）。
3. **批 3 · adi-model**（languagemodel/AiModel/MCP 系）；adi-draw 改依赖 adi-model；
   biz 剩余域 → adi-model 单向 ✓。
4. **批 4 · adi-kb-rag**（rag/searchengine 余量/KB·Document·Segment 全系/eval）。
5. **批 5 · adi-chat-core**（Character/memory/User/Prompt/Scene/Skill/finetune +
   multiagent）。
6. **批 6 · adi-workflow**（Workflow 全系 + Trigger 三件）；biz 清空删除；
   adi-draw 改依赖 core+model。

每批的模块依赖按真实引用单向闭合；`WfNodeInputConfig`/`NodeExecutionMetrics` 等
jsonb 数据形状类随 entity 归 workflow 域，entity 按域拆分（core 只留跨域共享实体）。

## 四、规则先行（已落地，随本报告同批提交）

`AdiDomainCycleTest`（archunit-junit5 1.3.0，FreezingArchRule）：

- **包循环冻结**：`slices().should().beFreeOfCycles()`——存量环违规冻结于
  `frozen/`，新增即红；
- **地基纯净**：core 候选包 → 业务包依赖冻结（存量 54 处），新增即红；
  批 1 落地后 refreeze 收紧为「零依赖」。

冻结基线：`frozen/8b7461c6…`（core→biz 16719 行）、`frozen/0033041b…`（包循环 213 行）。
