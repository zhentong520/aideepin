# 任务书执行 · Wave 1 完结报告（2026-10-17）

> 范围：任务书**第 1 批全部（T0-01~05 + T1-01/02，7 卡）** + **第 2 批 ACL 核心链（T1-03~07，5 卡）**，共 12 卡。
> 执行方式：前后端两角色代理并行 + 编排主线（nginx / CI / 越权评测集 / 验收），执行计划见 `任务书执行计划-Wave1.md`。
> 全部改动**未 git commit**（工作区 + 暂存区）。

## 一、结果总览：12/12 卡完成

| 卡 | 内容 | 状态 | 核心产出 |
|---|---|---|---|
| T0-01 | 质量门禁 CI | ✅ | `.github/workflows/ci.yml`（后端测试+集成测试+schema 门禁+双端 build）；admin-web **147→0** 个历史类型错误；husky pre-commit 双端 i18n 守卫 |
| T0-02 | DOMPurify 22 处 | ✅ | 两端 sanitize.ts + 全部 v-html 包裹（实测 22=user6+admin16）+ 行为自测 6 断言全过 + mila rel 补齐 + lang escapeHtml |
| T0-03 | nginx 安全头 | ✅ | 6 安全头 + CSP + 443/ssl + try_files 恢复 + proxy_set_header 进 location |
| T0-04 | 仓库隔离 | ✅ | 根 .gitignore + 两端 .env.example + 8 个已跟踪 .env 全部 `git rm --cached` |
| T0-05 | 依赖修正 | ✅ | axios/CryptoJS/uuid 归位 + axios^1.7 + vue 统一 3.5.x + vue-i18n 统一 11.x；**计划外修复 vite patch 存量损坏**（截断+路径前缀错） |
| T1-01 | 检索日志表 | ✅ | V4__rag_query_log + 异步写入（mainExecutor）+ 管理端查询接口（@AdminOnly）+ 5 例单测 |
| T1-02 | 越权评测集 | ✅ 脚本就绪 | `acl-eval/acl_eval.py`（L1 建库哨兵 / L2 真实 QA 链路 / L3 哨兵断言），端到端打通；实测见 §四 |
| T1-03 | ACL 迁移 | ✅ | V5__kb_acl：default_visibility_groups(jsonb) + default_sensitivity(CHECK 三档)；实体/DTO/保存链路全通 |
| T1-04 | chunk ACL 固化 | ✅ | Metadata 3 键（2 计划 + kb_owner_uuid 补充）；两个入库点固化；零向量化改动 |
| T1-05 | 检索 ACL 过滤+双保险 | ✅ | 唯一入口 ：274 追加 Filter + 应用层双保险 + `adi.kb.acl.enabled` 开关（默认 false 可热回滚）+ KbAclFilterBuilder 7 例单测；确认记忆链路不共用 kb_uuid 命名空间 |
| T1-06 | 存量回填 | ✅ | V6__acl_backfill（DO 动态遍历维度表，json::jsonb 归一）+ 管理端一键回填；实库回填 59 行、复跑 0 行 |
| T1-07 | 管理端 ACL UI | ✅ | 密级 NTag 列 + 可见组/密级表单（弹窗回显）+ 一键回填按钮；vue-tsc 0 错误 / build 绿 / 截图交付 |

## 二、测试矩阵（最终门禁，全绿）

| 门禁 | 结果 |
|---|---|
| 后端单测 | **211**（195 既有 + 16 新增）全绿 |
| Testcontainers 集成测试 | 实库执行 V1~V6 → 实体 56 表全存在，绿 |
| schema 对账门禁 | 实体 60 张 vs all_ddl 57 张，无缺口（3 动态分表白名单） |
| user-web / admin-web | build 全绿（含 vue-tsc 0 错误 + i18n AST 守卫） |
| ACL 行为单测 | KbAclFilterBuilder 7 例：owner 命中/PUBLIC 命中/他人 PRIVATE 过滤 |
| sanitize 行为自测 | onerror 清洗 / javascript: 剥离 / `<script>` 移除 / `<br>` 保留 |
| 健康检查 | actuator health/liveness/readiness 全 UP，env/heapdump 401 |

## 三、任务书偏离项（均已记录理由）

1. **CI 放 `aideepin/.github/workflows/ci.yml`**（任务书写根仓）——根仓无 remote，workflow 永不触发
2. **迁移编号 V4~V6**（任务书写 035/039）——V1~V3 已被 Flyway 占用，同步三处（Flyway 目录/编号目录/all_ddl.sql）
3. **未装 @types/dompurify**——包已废弃，dompurify 3.4 自带类型
4. **负向验证措辞**：任务书的 `"{a}"` 是合法插值不会被拦（两端一致），实际用 `{{a}}`/`{中文}` 验证拦截——建议修正任务书
5. **组管理简化**：visibility_groups 先用「owner 合成组 + 管理员全通过」，组维护 UI 留 TODO（KbAclFilterBuilder 已集中推导逻辑）

## 四、未尽事项与风险

1. **T1-02 实测中断（环境非代码）**：评测脚本全链路打通（哨兵 chunk 真实入库 + 真实 QA 调用），实测时 dev 环境 **embedding 模型服务不可用**（AiModelInitializer: volcengine/qianwen disabled）→ 检索整体失败，leak=0/1 为假绿不可采信。**部署环境（embedding 可用）重跑 `python acl_eval.py --prepare && --run` 即出真实门禁数据**；ACL Filter 行为本身已由 7 例单测覆盖
2. **T1-08~T1-10 未做**（对外检索 API / 限流配额 / MCP Server）——按任务书依赖顺序属第 2 批后半，且需 MCP 客户端端到端验收
3. **第 3~9 批未启动**（26 周工程按批次推进）
4. **git 未提交**：12 卡改动全在工作区/暂存区，建议按模块分批 commit
5. `admin-web/src/types/wf-shared.d.ts` 与 user-web 双份类型声明，后续需同步（T9-09 约束）

## 五、计划外修复（执行中发现并已修）

- `admin-web/patches/vite+3.2.10.patch` 存量损坏（hunk 截断+路径前缀错）导致 postinstall 一直失败
- admin-web 147 个历史类型错误清零（含 2 处真 bug：未定义变量引用、缺失 await）
- 缺失运行时依赖补声明：chalk/fs-extra 及其类型（TS2307 根因）
- user-web Dockerfile build-only → build（恢复全门禁）
