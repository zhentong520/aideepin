# 任务书执行计划 · Wave 1（2026-10-16）

> 范围：任务书**第 1 批全部 7 卡**（T0-01~05 + T1-01/02）+ **第 2 批 ACL 核心链 5 卡**（T1-03~07）。
> T1-08~T1-10（对外 API/MCP，需 MCP 客户端端到端验收）与第 3 批起留后续 Wave。
> 铁律 10 条全程生效；每卡动手前记录 before，完成后跑验收断言。

## 分工与并行结构

| 角色 | 承担卡 | 依赖 |
|---|---|---|
| **frontend-dev 代理** | T0-05（最先）→ T0-02 → T0-04 → T0-01 前端部分（package.json/husky/type-check）→ T1-07（管理端 ACL UI，等后端 DTO 定稿后开工） | T0-05 是 T0-01 前置（vue-i18n 统一后双端 i18n 脚本行为才可比） |
| **backend-dev 代理** | T1-01（检索日志表+写入）→ T1-03（ACL 迁移）→ T1-04（chunk ACL 固化）→ T1-05（检索侧过滤+双保险）→ T1-06（存量回填）；每卡同步 all_ddl.sql | 严格串行（同一条链）；T1-05 前需输出 KbInfoResp 字段定稿供 T1-07 |
| **编排（主线）** | T0-03（nginx 安全头）→ T0-01（CI workflow，等 T0-05）→ T1-02（越权评测集脚本，等 T1-05）→ 全量验收 → 完结报告 | — |

## 关键执行锚点（已实测）

- 检索唯一入口：`AdiEmbeddingStoreContentRetriever.java:274 embeddingStore.search(searchRequest)`——ACL 过滤只改这一处
- 主体过滤现状：`CharacterChatHelper.java:131` 的 where 仅 `IsIn(KB_UUID, kbUuids)`
- `SemanticMemoryService.java:75` 走用户记忆，需确认不共用 kb_uuid 命名空间（T1-05 检查项）
- 项目已有 Flyway V1~V3 + `check_schema_sync.sh` 门禁 + 集成测试 `SchemaMigrationTest`——新迁移必须过这三关
- 迁移编号：任务书写 035/039，**实际下一个 Flyway 编号是 V4**（V1~V3 已占用），文件命名 `V4__rag_query_log.sql` / `V5__kb_acl.sql` / `V6__acl_backfill.sql`，同时同步 `db_migration/` 编号目录与 all_ddl.sql

## 验收门（全部通过才出完结报告）

1. `bash dev-scripts/check.sh` 全绿（195+ 单测、集成测试、schema 对账、双端 i18n、双端 build）
2. CI：故意引入类型错误 → CI 红；故意写字面花括号文案 → i18n 守卫拦截（负向验证）
3. T1-02 评测集：哨兵泄露率 0%，有权召回不下降
4. T1-06 幂等复跑 0 行变化；回填前后存量用户检索行为零变化
5. T1-07 弹窗保存后回显正确（铁律 3）
6. T0-02 验收：`grep v-html` 22 处全在 safeHtml 包裹内 + safeHtml 单测（onerror/javascript: 清洗）
