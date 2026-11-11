# wf-eval — wf-route 金标集 v1（路由准确率评测）

回答一个问题：**用户一句自然话术，LLM 能不能选对工具（启动哪个工作流 / 取消 / 暂停 / 追问 / 不动手）？**

这是业务编排 Harness 的 G1 门禁数据源（方案 §268）：M2 先并进来量化出基线，M3 再定正式门禁阈值（≥95% + 参数必填追问率 100%）。

## 组成

| 文件 | 作用 |
|---|---|
| `scene.json` | 场景 fixture：1 个场景（报销助手）绑定 3 个工作流（差旅报销提交 / 周报生成 / 合同审批查询），含入参定义与必填标记 |
| `golden.jsonl` | 30 条：`{id, scene, utterance, expect, [workflow, args, runtime_uuid, context]}` |
| `eval_route.py` | 评测脚本：脚本侧**逐字复刻** Java 端工具描述与 manifest 文本（改 Java 必须同步），调 OpenAI 兼容端点做工具选择，按 golden 断言 |

30 条分布：launch 正例 14 / 缺必填追问 8 / 取消 4 / 暂停 2 / 纯问答干扰 2。

## 怎么跑

```bash
export ADI_EVAL_BASE=https://xxx/v1      # OpenAI 兼容 base url
export ADI_EVAL_KEY=sk-xxx
export ADI_EVAL_MODEL=deepseek-chat      # 被测模型
python eval_route.py                     # 全量 30 条
python eval_route.py --ids w15,w19       # 抽测追问用例
python eval_route.py --tag qwen72b       # 结果文件加模型 tag
```

退出码：0 = 路由准确率 ≥85% 且追问率 100%（v1 观察阈值）；正式门禁 ≥95% 待 M3 定。

## 断言口径

| expect | 断言 |
|---|---|
| `launch` | 调用了期望的 `wf_*` 工具，且 `args.input` 覆盖 golden.args 全部键值 |
| `ask` | **不调用任何工具**（manifest 规则 3：必填缺失先追问）——即「参数必填追问率」 |
| `cancel` / `pause` | 调用对应控制工具，且 `runtime_uuid` 与 golden 一致（context 注入对话） |
| `none` | 不调用任何工具 |

## 已知近似（与真实链路的差异）

1. 工具名：真实系统是 `wf_<uuid前8位>_<slug(title)>`，fixture 无 uuid，用 `wf_<title前8位>_<slug>` —— 内部自洽，不影响路由行为（LLM 看到的是 description 而非命名规则）。
2. 文本复刻：tool description / manifest 与 `LaunchWorkflowTool` / `WorkflowControlTool` / `WorkflowOrchestrationService.buildManifest` 逐字对齐（2026-11-11 快照）。**改 Java 文案必须同步本目录**，否则评测量的是旧文本。
3. 真实链路工具调用走 langchain4j 的 ToolSpecification；本脚本走 OpenAI tools 参数——语义等价，个别模型在两种编码下行为可能有差异，按模型分档看报告。

## 结果文件

`results-<tag>.jsonl`：每行 `{id, expect, ok, reason, got:{tool,args}}`，供逐条审 FAIL 与按模型分档汇总。
