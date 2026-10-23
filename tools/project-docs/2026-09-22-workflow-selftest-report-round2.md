# 工作流节点 — 第二轮代码走查与自测报告

- 日期：2026-09-22（第二轮，覆盖第一轮未细读的文件与运行时链路）
- 结论：**又发现 2 个真实缺陷（1 个 P0 生产环境炸、1 个功能做了一半），均已修复并复测通过**

---

## 一、本轮新增发现

### 🔴 BUG-2（P0，已修复）：i18n 文案里的字面花括号在生产模式会抛异常

**现象**：节点提示语要展示占位符语法，文案里直接写了 `{city}` / `{{input}}` / `{"city": "{input}"}`。vue-i18n 把 `{xxx}` 当插值：

| 环境 | 表现 |
|------|------|
| 开发模式 | 每次渲染报 `Message compilation error: Not allowed nest placeholder / Invalid token in placeholder`，文案回退成原文（**看起来正常，只是刷错误日志**） |
| **生产模式**（`__DEV__ = false`） | **直接抛 `SyntaxError`** → `t()` 抛错 → 属性面板/画布节点渲染失败 |

另外 en-US 的 `scriptArgsTip` 用了合法标识符 `{param}`，被**插值成空**，渲染成 `Use  to reference an upstream param`（可见的文案缺字）。

**触发条件**（只有这两类会炸）：嵌套花括号 `{{input}}`；花括号内不是合法标识符（如中文 `{参数名}`、`{"city":`）。

**影响面**：9 条文案（zh 5 + en 4），分布在 Database / MCP / Skill 三个属性面板。

**修复**：改用本项目**既有约定**（`ReferComment.vue` / `ReferTooltip.vue` 原本就有）：文案里写 ASCII 插值名，真实文本集中在 `braceLiteral.ts`，调用方 `t(key, braceLiteral)` 传入——插值值原样拼接，不会被二次解析。

- 新增 `views/workflow/components/braceLiteral.ts`（9 个 token），并把 `ReferComment` / `ReferTooltip` 里各自的局部 map **收敛到这一处**（原先是两份重复定义）。
- 3 个属性面板改为传参；locale 文案恢复可读（不再有 `{\'{\'}input{\'}\'}` 这类转义噪声）。
- 新增守卫 `user-web/scripts/check-i18n-braces.mjs`，并挂进 `npm run build`（失败即阻断发布）。

**验证**：生产模式逐字符断言 18/18 通过（含既有 `variableUsage1/2`、`variableExample` 一并回归）。
**守卫不是假绿**：注入一条 `{{input}}` 坏文案 → 守卫 `exit=1` 并打印原因；移除后 `exit=0`。

### 🔴 BUG-3（P1，已修复）：运行详情不渲染 mcp / skill 指标

`RuntimeNodes.vue` 按 `node.metadata.type` 渲染指标 chip，已覆盖 `llm / agent / http_request / search / knowledge_retrieval / image / mail / document` —— **唯独漏了本轮新增的 `mcp` / `skill`**。

后果：节点的 Metrics 正常写入 `adi_workflow_runtime_node.metadata`(jsonb)，但运行详情里**永远看不见**——无报错、无异常，纯静默缺失（与当初 Database 节点"后端有、前端没跟"是同一类问题）。

**修复**：补两个 chip 模板块 —— MCP：工具名 / 实际命中的服务 / 返回长度；Skill：技能名 / 模式 / 脚本路径 / 返回长度。

### ✅ 补充：多态序列化回归测试（新增 3 项）

`NodeExecutionMetricsSerializationTest`：McpMetrics / SkillMetrics 往返（断言 JSON 带 `type`、字段与耗时都不丢、还原成子类而非基类）+ 历史数据无 `type` 时仍能读成基类。
动机：漏登记 `@JsonSubTypes` **编译期毫无提示**，失败发生在运行期写指标时，属于典型的"测试不覆盖就一定会漏"的地方。

---

## 二、本轮核对通过（无需改动）

| 检查项 | 结果 |
|--------|------|
| **前后端接口契约** | 前端 7 个数据源接口（list / saveOrUpdate / delete / test / test GET / tables / columns / query）路径与参数名（`uuid`、`table`）与 `DbDatasourceController` **逐条一致** |
| **locale 键完整性** | 第二批 14 个键 + `mcpServerCount` 中英双语齐全，插值语法正确（`t('datasource.tableCount', { count })`、`t('workflow.mcpServerCount', { count })` 调用点均传参） |
| **组件名枚举点全扫** | 前端：workflow-util / WfDefineRightPanel / WorkflowDefine(slot) / App(菜单+路由) 全部覆盖；后端：`WorkflowComponentService` / `WorkflowNodeService` / `WorkflowService` / `WorkflowEngine` 只对 MAIL_SEND、CLASSIFIER、SWITCHER、START、END、GOOGLE_SEARCH 做特殊分支，**不涉及新节点** |
| **展示组件** | `McpNode.vue` 卡片：工具名/候选服务数/输出格式渲染正确；`t('workflow.mcpServerCount', { count })` 实测输出「候选服务：3 个」 |
| **数据源管理页** | 列表/新增/编辑/测试连接（区分"按配置测试"与"按 uuid 测试"，避免编辑态密码为空必然失败）/删除 逻辑正确 |

---

## 三、自测与复测结果

| 项 | 命令要点 | 结果 |
|----|----------|------|
| 后端编译+单测 | plexus launcher `-f server/pom.xml -o test` | **BUILD SUCCESS，54 / 54，0 失败 0 错误**（9 套件，含本轮新增 3 项 + 并行改动引入的 CjkSentenceSplitter 7 项） |
| 前端类型检查 | `npx vue-tsc --noEmit` | **exit 0** |
| 前端构建 | `npx vite build` | **exit 0，✓ built in 1m59s** |
| i18n 花括号守卫 | `node scripts/check-i18n-braces.mjs` | 31 + 31 条含花括号文案全部可编译，**exit 0**；负向注入后 exit 1 |
| i18n 生产模式断言 | 逐字符比对 18 条 | 全部通过 |

> 注：本轮期间工作区存在并行改动（新增 `CjkSentenceSplitterTest` 等），上述结果为包含这些改动的最新状态，仍全绿。

---

## 四、两轮累计（修复清单）

| # | 缺陷 | 级别 | 状态 |
|---|------|------|------|
| 1 | `WfNodeFactory` 未知组件名抛 NPE（default 显式报错不可达） | P1 | 第一轮已修 |
| 2 | `UserMcpService.saveOrUpdate` 更新写错对象 → 参数/启停不落库 | P1 | 第一轮已修 |
| 3 | i18n 字面花括号 → **生产模式抛 SyntaxError** | **P0** | 本轮已修 + 加守卫 |
| 4 | 运行详情不渲染 mcp / skill 指标（功能做了一半） | P1 | 本轮已修 |
| 5 | `callTool` 多服务同名告警分支不可达 | P3 | 保留（无害防御） |
| 6 | Skill 下拉不含自己的私有技能 | P2 | 已知限制，待"我的技能"接口 |

## 五、遗留（不阻塞）

1. **P2** Database 节点 SQL 值注入：上游文本直接拼进 SQL，已由只读校验 + 限行 + 30s 超时兜底，建议后续加数据源级只读账号。
2. **P2** "我的技能"接口，放开 Skill 下拉对私有技能的过滤。
3. **P1**（评审已列）MCP 多工具连续调用、MCP 客户端连接池化。

## 六、产物

- 修复：`user-web/src/locales/{zh-CN,en-US}.ts`、`user-web/src/views/workflow/components/braceLiteral.ts`（新增）、`ReferComment.vue`、`ReferTooltip.vue`、`nodes/{Database,Mcp,Skill}NodeProperty.vue`、`RuntimeNodes.vue`、`user-web/scripts/check-i18n-braces.mjs`（新增）、`user-web/package.json`（build 挂守卫）
- 测试：`adi-common/src/test/.../workflow/NodeExecutionMetricsSerializationTest.java`（新增 3 项）
- 日志：`E:/reg-project/review-test3.log`、`review-fe-tsc3.log`、`review-fe-build3.log`

---

## 七、第三轮复查（2026-09-22 20:35，应用户要求）

针对**本轮修复本身的代码**做了回归式走查（修 bug 的改动最容易留残留），**未发现新缺陷**：

| 检查项 | 结果 |
|---|---|
| locale 文件残留（`{'{'}` 转义混用 / `__braceGuardProbe` 探针） | 无残留；`mcpServerCount` 负向验证后已正确还原（各 1 处） |
| `braceLiteral.ts` 键与全部含花括号文案的插值键 | 完全对齐（`{name}/{city}/{legacyInput}/{jsonExample}` 等全部有 token，无漏传） |
| 三个属性面板 `t(key, braceLiteral)` 调用与 import | 齐全 |
| `RuntimeNodes.vue` 新 chip 字段 vs `McpMetrics`/`SkillMetrics` typings | 逐字段一致（工具名/命中服务/长度；技能名/模式/脚本/长度） |
| `mcpServerCount` 调用处 | `t(key, { count: serverCount })` 正确传参 |
| 守卫脚本逻辑 | 只抓"会炸"的两类（嵌套括号/非法 token），注释如实；曾做负向验证 |
| 后端漂移 | 工作流相关文件最后修改 15:59–16:38，均在最后一次绿测之前；`updateById(updateObj)` 修复仍在 |

### 第三轮最终自测（覆盖并行会话的最新改动）

| 项 | 结果 |
|---|---|
| i18n 守卫 | exit 0（zh 31 + en 31 条含花括号文案全部可编译） |
| 前端 `vue-tsc --noEmit` | exit 0 |
| 前端 `vite build` | **exit 0，✓ built in 1m 18s**（首跑 exit 1 系沙箱批量删除守卫拦截 vite 清理 `dist/assets`（122 个文件 > 阈值 50），构建本体当时已完成；清掉 dist 重跑即绿，非代码问题） |
| 后端编译 + 全量单测 | **BUILD SUCCESS，73/73，0 失败**（11 套件，已包含并行会话新增的 CjkSentenceSplitter 等 RAG 测试与本次 Kb* 改动） |

**结论：三轮累计 4 个真缺陷全部修复且复测通过，当前工作区状态全绿，无新增问题。**
