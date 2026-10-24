## 数据库初始化

> 中文文档 | **[🇬🇧 English](README.md)**

> ⚠️ **自 2026-10 起本目录冻结（OPT-08/OPT-11）**：这里是手工编号时代的历史存档，不再维护。
> schema 的唯一事实源是 **Flyway**（`adi-bootstrap/src/main/resources/db/migration/V*.sql`），
> 应用启动时自动执行迁移。请勿手工执行本目录文件，也不要在此新增文件。
> `all_*.sql` 快照仅作参考（生成产物，可能与实际漂移）。

### 1. 安装 PostgreSQL

Ubuntu 用户建议先添加 PGDG 源（参考 [postgresql.org/download/linux/ubuntu](https://www.postgresql.org/download/linux/ubuntu/)），然后一键安装 PostgreSQL 及所需扩展：

```bash
sudo apt install -y \
  postgresql-16 \
  postgresql-16-pgvector \
  postgresql-16-age
```

> **注意**：扩展按需安装。如果使用 neo4j 替代某个扩展，则该扩展无需安装。
>
> * pgvector：https://github.com/pgvector/pgvector（向量数据库，可用 neo4j 替代）
> * Apache AGE：https://github.com/apache/age（图数据库，可用 neo4j 替代）

### 2. 启用扩展

> 仅安装了对应扩展的才需要执行。

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
```

### 3. 创建数据库

```sql
CREATE DATABASE aideepin;
```

### 4. 执行 SQL 文件

> ⚠️ **2026-10-16 起主线已改为 Flyway（DB-01）**：应用启动时自动执行
> `adi-bootstrap/src/main/resources/db/migration/V{n}__*.sql`（V1 为基线快照，
> 此后每个 DDL 变更加一个 V{n} 文件）。**全新库只需建库 + 装扩展，启动应用即可，
> 不再需要手工执行本目录任何脚本**。以下手工流程保留作无 Flyway 场景的兜底。
>
> 配套门禁：`bash db_migration/check_schema_sync.sh` 校验「实体表名 vs all_ddl.sql」
> 无缺口（已挂 `dev-scripts/check.sh` 第 1 步）。
>
> ⚠️ **不要修改 V1__baseline.sql** —— 它已在存量库上执行，Flyway checksum 已固化，
> 改动会导致启动失败。所有 DDL 变更一律新增 V{n} 文件，并同步 all_ddl.sql（铁律 #5）。

文件命名规范：
* `all_*.sql` — 全量参考快照（须与 V{n} 迁移合集保持等价，由门禁校验）
* `NNN_*.sql` — 增量迁移脚本，用于从旧版本升级

**全新安装：**

1. 执行 `all_ddl.sql` 创建所有表、触发器和注释
2. 执行 `all_dml.sql` 插入必需的基础数据（管理员账号、系统配置、模型平台、AI 模型）
3. 选择执行以下其中一个文件插入语言相关的展示数据（预设角色、工作流组件、MCP 服务）：
   * `all_dml_cn.sql` — 中文展示数据
   * `all_dml_en.sql` — 英文展示数据

**版本升级：**

按顺序执行增量迁移脚本（不需要执行 `all_*` 文件）：
* `001_3.21.0.sql`
* `002_update_deprecated_models.sql`
* `003_add_user_locale.sql`
* `004_preset_role_enhancement_cn.sql` 或 `004_preset_role_enhancement_en.sql`（按展示语言二选一）
* `005_openai_tts_asr.sql`
* `006_conversation_to_character.sql`
* `007_model_and_kb_split.sql`
* `008_draw_and_mcp_external_api.sql`
* `009_observability.sql`
* `010_replace_image_model.sql`
* `011_amap_mcp_streamable_http.sql`
* `012_document_hit_count_and_status.sql`
* `013_segment_refactor.sql`
* `014_parallel_search_mcp.sql`
* `015_finetune.sql`
* `016_skill.sql`
* `017_rag_hybrid_rerank.sql`
* `018_volcengine_ark.sql` — 火山方舟平台与模型，API Key 通过 psql 变量注入，见文件头部说明
* `019_agent_scene_dbmcp.sql`
* `020_binding_unique_index.sql`
* `021_kb_retrieval_switch.sql` — 知识库是否参与自动检索的开关
* `022_finetune_params_and_metrics.sql` — 微调超参预设来源标记 + 训练报告
* `023_workflow_mcp_skill_node.sql` — 工作流 MCP 工具节点与技能节点
* `024_kb_chunk_context_defaults.sql` — 分段上限 1000→512（`ingest_max_overlap` 保持 0，理由见文件头实测）
* `025_kb_split_strategy_default.sql` — 默认分段策略改为 `sentence_cn`（中文分句）
* `026_kb_neighbor_window.sql` — 命中片段左右扩窗开关与半径（默认关闭、半径 1）
* `027_kb_retrieve_max_results_default.sql` — 召回条数默认值 3→0（自动）：召回条数是排序侧一切手段的硬天花板，实测池=3 时 13.7% 的问题答案在池外
* `028_kb_column_comment_sync.sql` — 知识库 4 条列注释与迁移对齐（024/025/026 只改了一边，导致 `check_init` 结构层长期为红）
* `029_qianwen_maas_provider.sql` — 千帆 MaaS 平台与模型
* `030_qianwen_rerank_default.sql` — rerank 默认模型切换为千帆
* `031_kb_doc_source.sql` — 知识库文档来源：URL 网页抓取 / API 接口拉取（含 md、markdown 解析支持）
* `042_rag_quality_dashboard.sql` — RAG 质量看板 + 失败闭环：`adi_rag_query_log` 补 `query_text` 列（评测题题面来源），新建 👍👎 反馈表 `adi_rag_feedback`

> **注意**：`019` 插入的工作流组件记录用的是英文占位标题。如需中文展示，升级后执行语言包
> `all_dml_cn.sql`（或 `all_dml_en.sql`），两个语言包都会把该记录刷新为对应语言。

> **注意**：DDL 中默认使用的 SCHEMA 为 `public`，请根据实际情况修改。
