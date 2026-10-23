# tools/ — 评测与支撑工具区

本目录收录原散落在 aideepin 仓外（`E:/reg-project` 根）的评测/支撑工具，纳入版本管理（决策 D-2:A，2026-10-23）。各目录用途与入口：

| 目录 | 用途 | 入口 |
|:-----|:-----|:-----|
| [kb-eval/](kb-eval/) | RAG 分块/召回/可答性评测（recall@K、mrr@10、answered@K、上下文成本） | `bash run_eval.sh`，详见其 README.md |
| [acl-eval/](acl-eval/) | ACL（访问控制）评测脚本 | `python acl_eval.py`，产出 report/state JSON |
| [mcp-smoke/](mcp-smoke/) | MCP 服务冒烟测试 | `python mcp_smoke.py` |
| [training-worker/](training-worker/) | 模型微调训练 Worker（从后端领任务，LLaMA-Factory 执行 LoRA/QLoRA，支持 mock/real 双模式） | `python worker.py --mode mock`，配置见 config.example.json |
| [project-docs/](project-docs/) | 项目过程文档（需求、规格、任务书、自测报告、架构说明等） | 直接阅读 |

## 约定

- 本目录内容独立于主服务构建链（后端 Maven / 前端 pnpm 均不依赖 tools/），工具自身的依赖与运行方式见各子目录。
- 评测产生的运行产物（`results-*.tsv` 向量缓存、`*.log`、`workspace/`、`__pycache__/` 等）不入库，如需保留请放各自目录并加入 `.gitignore`。
- project-docs 为历史过程文档快照，仅作记录，不代表当前实现状态。
