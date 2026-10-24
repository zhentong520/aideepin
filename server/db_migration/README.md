## Database Initialization

> **[🇨🇳 中文文档](README.zh-CN.md)** | English

> ⚠️ **FROZEN since 2026-10 (OPT-08/OPT-11)**: this directory is a historical archive of the
> manual-numbering era and is no longer maintained. The single source of truth for schema is
> **Flyway** (`adi-bootstrap/src/main/resources/db/migration/V*.sql`) — migrations run
> automatically at startup. Do not execute these files manually and do not add new ones here.
> The `all_*.sql` snapshots are kept for reference only (generated artifacts, may drift).

### 1. Install PostgreSQL

For Ubuntu users, it's recommended to add the PGDG repository first (see [postgresql.org/download/linux/ubuntu](https://www.postgresql.org/download/linux/ubuntu/)), then install PostgreSQL along with the required extensions in one command:

```bash
sudo apt install -y \
  postgresql-16 \
  postgresql-16-pgvector \
  postgresql-16-age
```

> **Note**: Install extensions as needed. If you use neo4j to replace an extension, you don't need to install that extension.
>
> * pgvector: https://github.com/pgvector/pgvector (vector database, can be replaced by neo4j)
> * Apache AGE: https://github.com/apache/age (graph database, can be replaced by neo4j)

### 2. Enable Extensions

> Only execute for extensions you have installed.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
```

### 3. Create Database

```sql
CREATE DATABASE aideepin;
```

### 4. Execute SQL Files

File naming convention:
* `all_*.sql` — Full snapshot scripts for fresh install (always up-to-date)
* `NNN_*.sql` — Incremental migration scripts for upgrading from earlier versions

**Fresh Install:**

1. Execute `all_ddl.sql` to create all tables, triggers, and comments
2. Execute `all_dml.sql` to insert required base data (admin account, system config, model platforms, AI models)
3. Execute one of the following to insert language-specific display data (conversation presets, workflow components, MCP servers):
   * `all_dml_en.sql` — English display data
   * `all_dml_cn.sql` — Chinese display data

**Upgrade (from an earlier version):**

Execute the migration scripts in sequence (do NOT run the `all_*` files):
* `001_3.21.0.sql`
* `002_update_deprecated_models.sql`
* `003_add_user_locale.sql`
* `004_preset_role_enhancement_cn.sql` or `004_preset_role_enhancement_en.sql` (pick the one matching your display language)
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
* `018_volcengine_ark.sql` — VolcEngine ARK platform + models; the API key is injected via a psql variable, see the header of the file
* `019_agent_scene_dbmcp.sql`
* `020_binding_unique_index.sql`
* `021_kb_retrieval_switch.sql` — per-knowledge-base switch for automatic retrieval in chat
* `022_finetune_params_and_metrics.sql` — finetune hyper-parameter preset origin + training report
* `023_workflow_mcp_skill_node.sql` — workflow MCP-tool and Skill nodes
* `024_kb_chunk_context_defaults.sql` — segment size 1000→512 (`ingest_max_overlap` stays 0, see the file header for the measurement)
* `025_kb_split_strategy_default.sql` — default split strategy is now `sentence_cn` (Chinese sentence splitting)
* `026_kb_neighbor_window.sql` — neighbour-window switch and radius for retrieved hits (off by default, radius 1)
* `027_kb_retrieve_max_results_default.sql` — recall size default 3→0 (auto): the recall size is the hard ceiling of every later ranking step; measured, a pool of 3 leaves 13.7% of questions with the answer outside the pool
* `028_kb_column_comment_sync.sql` — align four `adi_knowledge_base` column comments with the migrations that own them (024/025/026 updated only one side, which kept `check_init` red)
* `029_qianwen_maas_provider.sql` — Qianfan (Qianwen) MaaS platform and its models
* `030_qianwen_rerank_default.sql` — switch the default rerank model to Qianfan
* `031_kb_doc_source.sql` — knowledge-base document sources: fetch from a web URL or an HTTP API (also adds md/markdown parsing)
* `032_kb_doc_raw_text.sql` — `adi_document.raw_text` keeps the pre-clean parse output
* `033_global_scene_agent_user_conv.sql` — global scenes/agents + per-user agent conversations
* `034_kb_category.sql` — knowledge-base categories
* `035_finetune_sample_pool.sql` — finetune sample pool (session-to-finetune pipeline)
* `036_rag_query_log.sql` — RAG query log (retrieval funnel / eval question source)
* `037_kb_acl.sql` — knowledge-base ACL: default visibility groups + sensitivity
* `038_acl_backfill.sql` — ACL backfill for existing rows
* `039_credential_vault.sql` — credential vault (T2-07)
* `040_wf_test_run.sql` — workflow single-node test-run records
* `041_observability.sql` — observability additions
* `042_embedding_provider_and_memory_user.sql` — embedding provider switch + user memory
* `042_rag_quality_dashboard.sql` — RAG quality dashboard + failure loop: `adi_rag_query_log.query_text` column (eval-question source) and the thumbs-up/down feedback table `adi_rag_feedback`

> **Note**: numbering collided at `042` (two files) and `018` (two files) — historical fact,
> preserved as-is. Equivalent Flyway versions: see `adi-bootstrap/src/main/resources/db/migration/`.

> **Note**: `019` inserts the workflow component row with an English placeholder title. To display
> it in Chinese, run the language pack (`all_dml_cn.sql` / `all_dml_en.sql`) afterwards; both
> refresh that row.

> **Note**: The default SCHEMA in DDL is `public`. Modify it if needed.
