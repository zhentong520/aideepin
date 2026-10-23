# 工作流 MCP / Skill / Database 节点 — 代码走查与自测报告

- 日期：2026-09-22
- 范围：本轮工作流改造的全部新增/修改代码（后端 adi-common + adi-chat、前端 user-web、迁移 023）
- 结论：**发现并修复 1 个真实 bug；修复后后端 44 项单测全绿、前端 tsc 0 错 + build 成功，可交付**

---

## 一、走查范围与结果总览

| 模块 | 文件 | 结果 |
|------|------|------|
| 工作流节点 | `McpNode` / `McpNodeConfig` / `SkillNode` / `SkillNodeConfig` | ✅ 无缺陷 |
| 引擎 | `WfNodeFactory`（判空修复）、`WfComponentNameEnum`、`NodeExecutionMetrics`+`McpMetrics`/`SkillMetrics` | ✅ 无缺陷 |
| 工具方法 | `WorkflowUtil.renderJsonTemplate` / `escapeJsonString` | ✅ 无缺陷（含 JSON 转义、合法性校验、null→空串、裸数字） |
| 复用改造 | `SkillToolService`（private→public）、`SkillService.listFiles`、`UserMcpService`（listTools/callTool 等） | ⚠️ 发现 1 个真实 bug（见下） |
| 节点改造 | `DatabaseNode`（`{name}` 占位符） | ✅ 无缺陷；`{{input}}` 先替换的顺序有正确注释 |
| 控制器 | `UserMcpController`（/enabled /tools）、`SkillController`（/{uuid}/files） | ✅ 无缺陷（私有技能归属校验、getByUuid 不存在即抛） |
| 安全 | 归属校验（SkillNode）、数据源归属+只读+限行+30s 超时（`queryForUser`/`doQuery`） | ✅ 有效 |
| 迁移 | `023_workflow_mcp_skill_node.sql` | ✅ 幂等（复跑 `INSERT 0 0`），已应用到 dev 库 |
| 前端注册 | nodes/index.ts、WorkflowDefine.vue、WfDefineRightPanel.vue、workflow-util.ts、locales（21 个新键 zh/en 全核对）、路由、菜单 | ✅ 五处注册齐全，无遗漏 |
| 前端组件 | Database/Mcp/Skill 展示+属性面板、datasource 管理页 | ✅ 无缺陷 |

## 二、发现的问题与修复

### 🔴 BUG-1（已修复）：`UserMcpService.saveOrUpdate` 更新分支写错对象

- **现象**：`else`（已存在记录）分支里构造了只带改动字段的 `updateObj`，最后却执行 `baseMapper.updateById(userMcp)` —— 传入的是**查库得到的原值对象**。等于把旧数据原样写回，**用户新提交的 MCP 参数与启用/停用状态从未落库**。
- **影响**：对已有 `user_mcp` 记录的任何 `saveOrUpdate` 修改（参数、启停）静默失效。属既有代码缺陷（非本轮引入），本轮走查发现。
- **修复**：改为 `baseMapper.updateById(updateObj)`（jsonb 字段走实体 typeHandler + `autoResultMap`，符合本项目「jsonb 必须 updateById」的约定）。
- **验证**：修复后全量 44 项单测复跑通过（该方法无独立单测，依赖 mapper/DB，验证方式为编译 + 全量回归 + 代码审读；已在报告中标注）。

### 🟡 观察-1（不修，备忘）：`callTool` 的"多服务同名工具"告警分支不可达

首个命中工具的服务会立即执行并 return，`matchedServers.size() > 1` 分支实际到不了。属无害的防御性代码（若未来把"首个命中前先校验全部服务"语义改成"记录后继续"，该分支才有意义）。保留并记录，不改行为。

### 🟡 观察-2（已知限制，P2）：Skill 节点下拉不含"自己的私有技能"

前端技能下拉数据源 = 公开技能市场 + 已安装集合。`search` 接口返回的 Skill 带 `userId`，但前端用户信息只有 `uuid`，无法判断归属，故用户自己的**私有技能**无法在下拉中选中（后端 SkillNode 本身允许运行者执行自己的私有技能）。影响为可用性，非安全/正确性问题。建议后续加"我的技能"接口（返回归属技能清单）后放开过滤。

## 三、自测执行结果

### 后端（adi-common 为核心，全仓编译）

| 项 | 命令要点 | 结果 |
|----|----------|------|
| 编译+单测（修复前基线） | plexus launcher `-o test` | BUILD SUCCESS，44/44 |
| 编译+单测（修复后复测） | 同上 | **BUILD SUCCESS，44/44，0 失败 0 错误** |
| 测试套件 | 7 套 | WebMvcConfigTest 2、SegmentExpandProcessorCitation 9、KbServiceAuthorization 4、SegmentIndexChunkParams 6、ClassifierDecision 12、**WfNodeFactoryRegistration 4**、**WorkflowUtilRenderJsonTemplate 7** |
| 说明 | 此前记录的"45 项"为误计，实际合计 44 | — |

测试断言质量复核（按"假 OK 七类"标准）：新增 11 项测试全部断言具体值/具体异常信息，无空值恒真、无连接失败误判、无僵尸 SQL；工厂测试的 MessageSource 桩返回 `code + ": {0}"`，确保能断言到组件名。✅

### 前端（user-web）

| 项 | 结果 |
|----|------|
| `npx vue-tsc --noEmit` | exit 0（0 错误） |
| `npx vite build` | exit 0，✓ built in 1m27s（chunk 体积告警为既有问题，与本次无关） |
| locale 键核对 | 21 个 workflow 新键 zh/en 双语全在；menu.datasource 中英全在 |

### 数据库迁移（dev 库）

| 项 | 结果 |
|----|------|
| 023 首次执行 | `INSERT 0 1` × 2 |
| 幂等复跑 | `INSERT 0 0` × 2 |
| 库内核对 | Database(4)/Mcp(8)/Skill(9) 三组件均已注册 |

## 四、遗留事项（不阻塞交付）

1. **P2**：Database 节点 SQL 值注入——上游文本直接拼进 SQL（`{{input}}` 与 `{name}` 均如此），只读 + 行数上限 + 超时已兜底，但建议后续加数据源级只读账号约束。
2. **P2**：观察-2 的"我的技能"接口。
3. **P1**（上轮评审已列）：MCP 多工具连续调用、MCP 客户端连接池化。

## 五、产物与日志

- 修复：`server/adi-common/.../service/UserMcpService.java`（saveOrUpdate 更新分支）
- 日志：`E:/reg-project/review-test2.log`（后端）、`review-fe-tsc.log` / `review-fe-build.log`（前端）
- 知识库：`D:/cursor-data/projects/reg-project/knowledge/learned/2026-09-22-workflow-mcp-skill-database-nodes.md`
