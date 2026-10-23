# aideepin 改造任务书（可直接交给 AI 执行）

> 执行者读这份就够了。**不要再去读其他需求文档**，全部内容已在本文件内。  
> 状态：已定稿，无待拍板项。工期 26 周，需 3 人并行。

---

## 0. 执行须知

**规则**

1. **严格按批次顺序**。批次内可并行，批次间有硬依赖。
2. **每项任务动手前先跑验收基线**，记录 before 数据。没有 before 就无法证明 after 有效。
3. **凡涉及检索/生成行为改动，合入前后各跑一次评测并附结果**（铁律 9）。
4. **所有新增可配置字段必须能读回来**，否则弹窗会「保存一次即关闭」（铁律 3）。
5. **迁移脚本必须幂等**，且同步 `all_ddl.sql` / `all_dml_cn.sql` / `all_dml_en.sql`。
6. 任务卡里标⚠️ 的坑是已实测确认的，不要"优化"掉。

**已完成，不要重做**

- 21 种节点类型已实现
- 资源选择器已全部实现（无需手填 ID）——⚠️ 改造中不要退化回填 ID
- 数据库 Schema 浏览器 / Agent 模型能力联动 / 条件分支 and-or 保留现状
- `check-i18n-braces.mjs` 脚本已存在，只需接入 CI

---

## 1. 十条铁律

| #  | 铁律                                                                                                                                                                                                               |
| -- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1  | 前端加节点是**六处注册**：`nodes/index.ts` → `WorkflowDefine.vue` slot → `WfDefineRightPanel.vue` → `workflow-util.ts` → locales 中英文案 + `componentTitle` → **`RuntimeNodes.vue` 指标 chip**。漏第六处 = 指标入库不显示。（T3-04 完成后自动降为1 处） |
| 2  | vue-i18n 文案**禁字面花括号**，用 `braceLiteral.ts`。新增文案必须做负向验证                                                                                                                                                            |
| 3  | 任何可编辑配置字段必须在响应 DTO 能读回来，否则弹窗显示「关闭」，保存一次即关掉                                                                                                                                                                       |
| 4  | MyBatis jsonb 必须配 `resultMap` + 用 `updateById`，否则字段静默为 null                                                                                                                                                      |
| 5  | 迁移脚本幂等，同步三个 sql 文件                                                                                                                                                                                               |
| 6  | 测试断言必须真实。空值恒真 / 连接失败误判 OK / 僵尸 SQL 是七类假 OK 来源                                                                                                                                                                    |
| 7  | 检索出处抬头只改返回文本，**不改 embedding 输入**                                                                                                                                                                                 |
| 8  | `setup` 中没有 `this`                                                                                                                                                                                               |
| 9  | 任何检索/生成行为改动，合入前后各跑一次评测并附 PR                                                                                                                                                                                      |
| 10 | 所有行为改动带系统配置开关，可热回滚                                                                                                                                                                                               |

---

# 第 1 批 · 本周（D1–D5）

> 这批决定后面 40+ 项重构能否安全进行。**三件必须最先落地，其余可等**。

## T0-01 质量门禁 ⭐最先做

**做什么**  
搭 CI 流水线，让构建必跑类型检查。

**怎么做**

1. 新建 `E:/reg-project/.github/workflows/ci.yml`，跑两端 build + 后端 Maven 编译
2. 删 `user-web/Dockerfile:13` 的 type-check 跳过
3. 修 `admin-web/package.json`：build 缺 type-check；删掉无效 key `"build typecheck"`
4. 装 husky hooks（当前 `husky` 装了但未install）
5. 把已有 `check-i18n-braces.mjs` 并入 CI（**先做 S-04 的 vue-i18n 版本统一**，否则两个脚本行为不可比会误判）
6. CI 必须同时挡住：admin-web 现有 46 个历史类型错误（**要么修完要么先降级为 warn，但不能静默**）

**达到什么效果**

- 人为写一个类型错误，push 后 CI 红
- `check-i18n-braces` 能拦住含字面花括号的文案（做负向验证：故意写一个 `"{a}"` 确认被拦）
- 后续所有重构都有网

---

## T0-02 DOMPurify 覆盖全部22 处 v-html

**做什么**  
给所有 `v-html` 加输入消毒。

**怎么做**

1. `pnpm add dompurify && pnpm add -D @types/dompurify`
2. 新建 `user-web/src/utils/sanitize.ts`（admin-web 同名）：

```ts
import DOMPurify from 'dompurify'
const ALLOWED_URI = /^(?:https?:|mailto:|tel:|#|\/)/i
export function safeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['a','b','i','em','strong','code','pre','br','p','ul','ol','li',
      'h1','h2','h3','h4','blockquote','table','thead','tbody','tr','th','td',
      'span','div','sup','sub','img'],
    ALLOWED_ATTR: ['href','title','target','rel','class','src','alt','width','height'],
    ALLOWED_URI_REGEXP: ALLOWED_URI,
  })
}
```

1. 替换 **22 处**（user-web 6 + admin-web 16）：

| 优先级   | 位置                                                                                                                  | 输入来源                    |
| ----- | ------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| 🔴 最高 | `user-web/src/components/common/UserAvatar/index.vue:23`                                                            | **用户自己填写的简介**           |
| 🔴 高  | `user-web/src/views/chat/components/Message/Text.vue:69`                                                            | AI 输出 + 用户输入            |
| 🔴 高  | `admin-web/src/wf/chat-msg/Text.vue:69`                                                                             | 同上                      |
| 🟠 高  | `user-web/src/views/mcp/index.vue:189`                                                                              | 三方 MCP 描述               |
| 🟠 高  | `user-web/src/views/skill/index.vue:60`                                                                             | AI 生成的技能详情              |
| 🟡 中  | `user-web/src/components/common/ApiDocPanel.vue:104,127`<br />`admin-web/src/wf/components/ApiDocPanel.vue:104,127` | 后端接口描述                  |
| 🟡 中  | `admin-web/src/views/finetune/guide.vue` **9 处**、**`finetune/job.vue` 4 处**                                         | i18n 文案（允许 `<br>`，仍须过滤） |
| ⚪ 低   | `admin-web/src/components/CodeEditor/index.vue:4`                                                                   | highlight.js 输出，已转义     |

1. 同批修两处细节：
   - `markdown-it-link-attributes` 的 attrs 补 `rel: 'noopener noreferrer nofollow'`
   - `Text.vue` 的 `highlightBlock(str, lang)` 模板拼接中 `lang` 未转义 → 用 `md.utils.escapeHtml(lang)`

**达到什么效果**

- `safeHtml('<img src=x onerror=alert(1)>')` 被清洗
- `safeHtml('[x](javascript:alert(1))')` 中 `javascript:` 协议被剥离
- `grep -rn "v-html" user-web/src admin-web/src` 每一处都在 `safeHtml()` 包裹内

---

## T0-03 nginx 安全响应头

**做什么**  
部署层加固 + CSP。

**怎么做**  
改 `admin-web/nginx.conf`：

```nginx
server {
  listen 443 ssl;
  server_name localhost;
  charset utf-8;

  add_header X-Content-Type-Options nosniff always;
  add_header X-Frame-Options SAMEORIGIN always;
  add_header Referrer-Policy strict-origin-when-cross-origin always;
  add_header Permissions-Policy "geolocation=(), microphone=(self), camera=()" always;
  add_header Strict-Transport-Security "max-age=31536000" always;
  add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data: blob: https:; connect-src 'self'; frame-ancestors 'self'; base-uri 'self'; form-action 'self'" always;

  location / {
    root /usr/share/nginx/adi-web;
    index /index.html;
    try_files $uri $uri/ /index.html;   # ← 当前被注释了，恢复
  }
  location /admin/ {
    alias /data/adi-admin-web/;
    index /index.html;
    try_files $uri $uri/ /admin/index.html;
  }
  location /api/ {
    # ⚠️ proxy_set_header 必须写在 location 内
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_pass http://localhost:9999/;
  }
}
```

**达到什么效果**

- `curl -I` 返回 6 个安全头
- `proxy_set_header` 从 `server` 块末尾移进 `location` 后，静态资源也带上头
- ⚠️ `script-src 'self'`（不含 unsafe-inline）上线前验证一遍，highlight.js 不走 eval

---

## T0-04 仓库隔离

**做什么**  
防env 和构建日志入库。

**怎么做**

1. 新建 `E:/reg-project/.gitignore`：

```
*.log
*.log.*
logs/
*.pid
e2e-*.png
.playwright-cli/
.workbuddy/
.zcode/
.mimosa/
data/
target/
node_modules/
dist/
.idea/
.vscode/
*.zip
```

1. 两端 `.gitignore` 各追加：`.env` / `.env.*` / `!.env.example`
2. 新建 `user-web/.env.example`、`admin-web/.env.example` 作模板

**达到什么效果**

- `git init && git add -A` 后，`git ls-files` 里没有任何 `.env` 和 `.log`
- ⚠️ 根目录当前**无 .git 也无 .gitignore**，散落 30+ 日志（含 4.5MB 的 `adi-boot-fulltest.log`）+ 6 个 e2e 截图，`git init` 前先确认是否要建仓

---

## T0-05 依赖分类与版本修正

**做什么**  
修构建隐患 + 统一框架版本。

**怎么做**

| 动作              | 详情                                                                                                                                               |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `user-web` 依赖归类 | `axios ^1.3.4` / `crypto-js ^4.1.1` / `uuid ^9.0.0` **全在 devDependencies**，移入 dependencies；axios 升 `^1.7.0`（当前低于 1.6.0，含 CVE-2023-45857 XSRF 修复） |
| 统一 `vue`        | user **3.5.13** / admin **3.4.21** → 两端统一 3.5.x（不同 reactivity 语义，`defineModel`/reactive props 解构行为不同）                                            |
| 统一 `vue-i18n`   | user **9.14.5** / admin **11.4.2** → 统一 major。⚠️ v9→v11 是破坏性大版本，**必须在 T0-01 之前完成**，否则两个 `check-i18n-braces` 行为不可比，CI 会误判                         |

**达到什么效果**

- `pnpm install --prod` 后 `vite build` 不失败
- 两端 vue / vue-i18n 版本一致
- i18n 检查脚本在两端行为一致（做负向验证）

---

## T1-01 检索日志表 ⭐与 ACL 同批

**做什么**  
建 `adi_rag_query_log`，让检索行为可观测。

**怎么做**

1. 迁移 `035_rag_query_log.sql`（幂等）：
   - `query_id` / `user_uuid` / `kb_uuids` / `filter_expression` / `hit_segments` / `hit_count` / `elapsed_ms` / `prompt_tokens` / `embedding` / `feedback`（👍👎）/ `created_at`
2. 在检索链路写入日志。写入点= 检索请求发起处（与 T1-04 同一处）
3. 管理端加查询入口（按时间/用户/库筛选）

**达到什么效果**

- 抽样 50 次检索，日志完整率 100%（每条含主体、库、过滤表达式、耗时）
- 能按 user_uuid 查到某人调了哪个库

**⚠️ 为什么必须先做**：ACL 上线后若过滤失效（尤其工作流节点绕过主体权限），没有日志无法定位。

---

## T1-02 越权评测集（门禁）⭐必须先于 ACL

**做什么**  
建能验证 ACL 是否生效的测试集。

**怎么做**

1. 30 条越权问题（用户无该库权限却能问到内容）
2. 30 条有权问题（对照组，防过度过滤）
3. 128 位哨兵令牌注入：每条越权问题的正确答案里插入唯一哨兵串，检测泄露
4. 做成可重复执行的脚本

**达到什么效果**

- 哨兵泄露率 **0.00%**（越权）/ 有权问题召回正常（防「一刀切全拒」）
- **A-01/A-03 每一步改动后都跑一次**

**⚠️ 为什么必须先于 T1-03**：否则改完 ACL 才发现有没有生效，等于没有验证手段。这是铁律 6 的直接应用。

---

# 第 2 批 · W1–W3 · 知识库线 A（安全与开放）

## T1-03 ACL 迁移脚本

**做什么**  
库级 ACL 字段落库。

**怎么做**  
迁移 `035_kb_acl.sql`（幂等，同步 `all_ddl.sql`）：

- `adi_knowledge_base` 加 `default_visibility_groups`（数组）、`default_sensitivity`
- **密级三档**：`PUBLIC` / `ORG` / `PRIVATE`，预留第四档扩展位，**字段不留死位**

**达到什么效果**

- 幂等复跑第二次影响 0 行
- `all_ddl.sql` 同步

---

## T1-04 chunk 级 ACL 固化

**做什么**  
分块入库时把 ACL 写进向量 metadata。

**怎么做**

1. `server/adi-common/.../rag/AdiConstant.java` 的 `MetadataKey` 加 2 个键：`ACL_VISIBILITY_GROUPS`、`ACL_SENSITIVITY`
2. 改入库链路（`DocumentSegmentService` 分块落库处）写这两个键
3. ⚠️ **不参与向量化**——只写 metadata，不重跑 embedding，不重建索引

**达到什么效果**

- 改 ACL 后不重建索引即可生效（查 `embedding` 调用次数为 0）
- 存量数据由 T1-06 回填

---

## T1-05 检索侧 ACL 过滤 + 双保险

**做什么**  
把 ACL 加到检索 where 条件。

**怎么做**  
⚠️ **底层检索入口只有1 处**（已实测确认）：

```
server/adi-common/src/main/java/com/moyz/adi/common/rag/AdiEmbeddingStoreContentRetriever.java:274
  embeddingStore.search(searchRequest)
```

**改这一处即覆盖全部上层调用。** 在这里往 `Filter` 追加 ACL 条件：

```
当前（CharacterChatHelper.java:131 传入）：
  new IsIn(KB_UUID, kbUuids)          ← 只有库过滤

改为：
  new IsIn(KB_UUID, kbUuids)
  + ACL 条件（主体可访问的 visibility_groups ∩ sensitivity 满足）
```

再加双保险：取回后在应用层断言一次（命中了无权内容则剔除并打日志）。

⚠️ 三条铁律：

- ACL **必须在向量检索阶段的 where 条件**——不能塞 prompt、不能取回后筛
- 另一处`SemanticMemoryService.java:75` 也用 `EmbeddingSearchRequest`，走**用户记忆**（非知识库），需确认**不共用 `kb_uuid` 命名空间**
- 改完跑 T1-02 评测集

**达到什么效果**

- 越权评测集泄露率 0%
- 有权问题召回不下降（防过度过滤）
- 应用层断言日志在正常情况下为空

---

## T1-06 存量数据回填

**做什么**  
给已有 chunk 补 ACL metadata。

**怎么做**

1. 迁移 `039_acl_backfill.sql`：按库级 `owner_uuid` / `is_public` 推导 chunk ACL
2. ⚠️ **只UPDATE metadata，不动向量**（不重跑 embedding）
3. 幂等
4. 管理端加「一键回填」入口 + 回填进度显示

**达到什么效果**

- 幂等复跑第二次影响 0 行
- 回填前后**存量用户检索行为零变化**
- 回填耗时可接受，大库有进度条

---

## T1-07 管理端 ACL 配置 UI

**做什么**  
库级默认 + 文档级覆盖 + 「待规范化」提示。

**怎么做**

1. 库级：知识库设置页加默认可见组/密级
2. 文档级：文档列表支持覆盖
3. 对只配了库级、chunk 里没 ACL 的记录，标「待规范化」并提供一键回填入口

⚠️ **新字段必须在 `KbInfoResp` 能读回来**（铁律 3），否则弹窗保存一次即关闭。MyBatis jsonb 必须配 `resultMap` + `updateById`（铁律 4）。

**达到什么效果**

- 打开弹窗能正确回显已保存值
- 保存后不误关
- MyBatis 层不出现「更新了但查出来是 null」

---

## T1-08 `POST /ext/v1/knowledge/retrieve`（只检索不生成）

**做什么**  
对外提供纯检索 API。

**怎么做**  
改 `server/adi-chat/.../externalapi/v1/ExtKnowledgeBaseController.java`（当前只有一个 `/ext/v1/knowledge`，只支持问答）：

- 新增 `POST /ext/v1/knowledge/retrieve`
- 支持多库（`kbUuids` 数组）
- **无 LLM 调用**
- 无权库静默跳过，列入响应 `skippedKbUuids`
- ⚠️ 必须复用 T1-05 的同一检索内核，不得旁路实现

**达到什么效果**

- 接口返回检索结果 + 相关度分数，**不返回任何生成内容**
- 传 3 个库、其中 1 个无权 → 返回 2 个库结果 + `skippedKbUuids` 含该库
- ⚠️ 限流配额（T1-09）**必须先于本接口开放**

---

## T1-09 限流与配额

**做什么**  
单 Key QPS + 日配额。

**怎么做**  
在 `/ext/v1/**` 加：单 Key QPS 限流 + 日调用配额 + 日 token 配额。

⚠️ **必须先有配额再对外开放接口**（项目历史事故：额度耗尽返「当月额度已经用完」而检查逻辑 `len>0` 判PASS，是假绿）。

**达到什么效果**

- 超限返回 429 + `Retry-After`
- ⚠️ 断言必须检查**实际返回码**，不能只看响应有内容

---

## T1-10 KB → MCP Server

**做什么**  
知识库能力以 MCP 形态暴露。

**怎么做**

1. `GET /mcp/kb/{kbUuid}` 起MCP Server
2. 工具：`kb_search`（检索）、`kb_get_document`（取原文）
3. 多库聚合
4. 复用 `/mcp` 现有鉴权
5. ⚠️ **必须复用同一检索内核**（同 T1-05），不得旁路

**达到什么效果**

- **真实 MCP 客户端端到端调通 + 截图**（不接受"接口返回 200"当验收）
- 传无权库的 key → 返回空结果 + 明确错误，不是静默放行
- 检索结果与 T1-08 完全一致（同内核）

---

# 第 3 批 · W1–W6 · 安全与性能

## T2-01 工作流运行时性能止血

**做什么**  
修 4 处，已定位到行号。


**怎么做**

| 位置                                                  | 问题                                                                      | 修法                                  |
| --------------------------------------------------- | ----------------------------------------------------------------------- | ----------------------------------- |
| `user-web/src/views/workflow/WorkflowDefine.vue:71` | 边循环内 `props.workflow.nodes.find(...)` = **O(n²)**                       | 建 Map 索引，循环内 O(1) 查                 |
| 同文件 `:237`                                          | `setTimeout(() => { renderGraph() }, 2000)` = **固定 2 秒硬等**              | 改为数据就绪即渲染（watch 触发），去掉硬等待           |
| 同文件 `:283`                                          | `onMounted` 里 `console.log('...', this)` —— **setup 中没有 this**（铁律 8）    | 删掉                                  |
| 工作流模块共 **54 处 console.log**                         | `console.log('Node drag stop:', nodes, node, event)` 每次拖拽结束触发，对象序列化在主线程 | 删全部；调试日志改用 `import.meta.env.DEV` 门控 |
| 同文件 `:84`                                           | 所有边硬编码 `animated: true`                                                 | 按数据流实际是否流动来定（延到 T3-03）              |

**达到什么效果**

- 100 节点场景渲染 ≤ 500ms（改造前先测before）
- 拖拽 ≥ 55fps
- 首屏不再白屏 2 秒

---

## T2-02 highlight.js 按需注册

**做什么**  
减850KB ~ 1MB。

**怎么做**  
新建 `user-web/src/utils/highlight.ts`（admin-web 同名），用 `highlight.js/lib/core` 注册 18 种：

`javascript / typescript / xml（覆盖 html/vue/svg）/ css / python / java / go / rust / bash / sql / json / yaml / markdown / diff / kotlin / swift / php / ruby`

别名注册：`js|jsx|mjs|cjs`→javascript，`ts|tsx`→typescript，`html|vue|svg|xhtml`→xml，`sh|zsh`→bash，`yml`→yaml，`py`→python

替换 3 处全量引入：

- `user-web/src/views/chat/components/Message/Text.vue:6`
- `user-web/src/views/mcp/index.vue:6`
- `admin-web/src/wf/chat-msg/Text.vue:6`

**达到什么效果**

- 包体减 850KB ~ 1MB（改造前后对比 dist 体积）
- ⚠️ 未注册的语言走 `highlightAuto` 兜底，不报错

---

## T2-03 manualChunks 拆包

**做什么**  
拆巨型chunk。

**怎么做**  
两端 `vite.config.ts` 加 `rollupOptions.output.manualChunks`，按包分：

`vendor-vue`（vue/vue-router/pinia/vue-i18n/@vueuse/@vue/shared/runtime-core/reactivity）、`vendor-flow`（@vue-flow）、`vendor-naive`（naive-ui/@vicons/vueuc/date-fns，admin）、`vendor-echarts`（admin）、`vendor-hljs`、`vendor-md`、`vendor-jsoneditor`、`vendor-drag`（admin）、`vendor-katex`、`vendor-cyto`、`vendor-http`、`vendor`

同时：`chunkSizeWarningLimit: 700`（当前 admin 是 2000，等于把告警静音）。

**达到什么效果**

- **user 入口 chunk < 400KB**（当前 1263KB）
- **admin 入口 chunk < 600KB**（当前 1889KB）
- 超过 700KB 的 chunk 触发告警

---

## T2-04 workflow 编辑器整页异步化

**做什么**  
1389KB 的 workflow chunk 不进首屏。

**怎么做**  
路由改动态：

```ts
{ path: '/workflow', component: () => import('@/views/workflow/index.vue') }
```

⚠️ **不要**把 `@vue-flow/core` 的 `Handle` / `Position` 改异步（**41 个** node/edge 组件各自 import，改了会导致节点渲染时序错乱、出现「节点延迟闪现」）。整体异步化页面即可。

**达到什么效果**

- 首屏不请求 workflow chunk
- 切到 workflow 页面节点正常显示，无闪现

---

## T2-05 katex 字体懒加载

**做什么**  
首屏减约 200KB 字体。

**怎么做**

1. `user-web/src/plugins/assets.ts:1` 移除全局 `import 'katex/dist/katex.min.css'`（该 CSS 的 `@font-face` 引用 59 个字体文件）
2. 新建 `user-web/src/utils/lazyKatex.ts`：

```ts
let loaded = false
export async function ensureKatexStyle() {
  if (loaded) return
  loaded = true
  await import('katex/dist/katex.min.css')
}
```

1. 在含公式的组件 `onMounted` 或 markdown-it 插件里按需触发（检测文本含 `$`）

**达到什么效果**

- 首屏字体请求数 = 0
- 遇到公式时字体正常加载

---

## T2-06 死依赖清理

**做什么**  
减包 + 修安全。

**怎么做**

```
# user-web
pnpm remove html2canvas

# admin-web（src 中实测 0 引用）
pnpm remove @vueup/vue-quill blueimp-md5 vfonts mitt mockjs
```

⚠️ `mitt` 之前被「35 文件使用」是`submitting` 子串误匹配，`from 'mitt'` 实测 0 处。

再清理无引用 devDeps：`jest` + `eslint-plugin-jest`（无任何测试文件）、`less-loader`（Vite 不用）、`vue-draggable-next`（已用 `vuedraggable`）、`vue-demi`、`gh-pages`、`core-js`、`cross-env`、`vite-plugin-style-import`

**达到什么效果**

- 移除 6 个运行时死依赖
- 构建无报错

---

## T2-07 凭据集中管理（唯一涉及 DB 结构变更的安全项）

**做什么**  
消除节点配置里的明文密码 / 密钥。

**为什么必须做**  
⚠️ 本批唯一需要 DB 结构变更的安全项，**迁移越晚做越麻烦**（存量明文数据越多，回填脚本越贵）。

**怎么做**

1. 迁移 `040_credential_vault.sql`（幂等，同步三个 sql 文件）：
   - 新建 `adi_credential`（`credential_uuid` / `type` / `cipher_text` / `iv` / `key_version`）
   - 节点配置表加 `credential_uuid` 字段，**旧字段保留不删**（可回滚）
2. 加 `CredentialCipher` 工具类：AES-256-GCM，主密钥从环境变量 `ADI_CRED_KEY` 读，**不入库不入日志**
3. 存量回填 `041_credential_backfill.sql`：扫描节点配置中的 `mailPassword` / `apiKey` / `secret` 类字段，逐条加密写入 `adi_credential`，原字段置空
4. 读取路径改造：节点执行时按 `credential_uuid` 解密，**日志与前端响应体一律不出现明文**

**达到什么效果**

- `grep -rn "password" 节点配置表` 查不到任何明文
- 前端接口响应、链路日志、异常栈中搜不到明文密钥
- ⚠️ 回填脚本幂等，可重复执行

---

# 第 4 批 · W1–W6 · 工作流可用性（重点）

## T3-01 ⭐补 `content_line` 样式（阻塞后续所有美化）

**做什么**  
给节点内容行补样式定义。**这是「页面太丑」的直接根因**。

**现状**  
**34 个文件**用 `class="content_line flex items-center"`（两端合计），但全项目**无任何 CSS/LESS 定义**。`user-web/src/styles/global.less` 仅 10 行，admin-web 无同名文件。结果只剩 Tailwind `flex items-center` 生效 → 节点内容行无间距/边框/层级。

**怎么做**  
在 less 里定义 `.content_line`（间距、边框、层级），⚠️ **两端都要加**（admin-web 的wf 模块有同名 class）。

**达到什么效果**

- 节点内容行有清晰的间距与视觉层级
- **不做这步，后面 T3-02/T3-03 的美化全部无效**

---

## T3-02 节点卡片统一

**做什么**  
统一视觉规格。

**怎么做**

- 统一宽度 280px
- 按节点类型加 7 类配色色条
- 显示配置摘要（当前配了什么，一眼可见）
- 改 `CommonNodeHeader.vue` + 各节点组件

**达到什么效果**

- 同类节点宽度一致
- 节点类型靠色条即可区分

---

## T3-03 画布视觉

**做什么**  
网格、连线、选中态。

**怎么做**

- 加网格背景
- 连线按数据流方向着色（⚠️ 替换 T2-01 提到的 `:84` 硬编码 `animated: true`）
- 选中态清晰
- 吸附参考线

**达到什么效果**

- 100 节点场景下画布仍清晰可读

---

## T3-04 属性面板改抽屉式 ⭐

**做什么**  
解决「编辑时看不到被编辑节点」。

**现状**  
`WfDefineRightPanel.vue` 是 `absolute right-0 top-20` + `width: 600px`，**覆盖画布**。

**怎么做**

- 改为 380px 独立栏
- 折叠时留 56px 摘要条
- ⚠️ 后续新节点需求工期从 ×2 降到 ×1（依赖 T5-03）

**达到什么效果**

- 编辑属性时能看到被编辑的节点
- 折叠状态不占画布空间

---

## T3-05 撤销/重做 ⭐

**做什么**  
`Ctrl+Z` / `Ctrl+Shift+Z`。

**现状**  
**全仓无 undo/redo 历史栈**（不是弱实现，是完全没有）。

**怎么做**

1. 挂载点放在 `workflow-util.ts` 的**入口函数**：`createNewNode` / `createNewEdge` / `deleteEdgesBySource`，而非散落在各组件
2. 覆盖：加节点、删节点、移动、改属性、连/断线
3. 跨保存生效（保存后仍可撤销到上一版）
4. 保留最近 50 步

**达到什么效果**

- 误删误连可恢复
- 覆盖「加删改移+ 属性修改」全部操作
- 有单测

---

## T3-06 保存前校验 + 连线合法性

**做什么**  
拦住非法配置。

**现状**  
保存不校验；「红星是装饰」。

**怎么做**

1. 空必填字段拦截 + 定位到具体节点
2. 自环连线拦截
3. 重复连线拦截

**达到什么效果**

- 保存时拦住空必填/自环/重复连
- 错误提示能定位到具体节点（不只是"保存失败"）

---

## T3-07 画布基础能力

**做什么**  
补齐业界标配。

**现状**：缩放/缩略图代码**被注释掉了**。

**怎么做**

| 能力             | 说明                                   |
| -------------- | ------------------------------------ |
| 缩放控件           | 左下                                   |
| 小地图            | 右下                                   |
| 自动布局           | 一键整理                                 |
| 智能对齐吸附         | 拖动时显示参考线                             |
| 框选 + 批量操作      | 多选、批量删/移/禁用                          |
| 复制粘贴           | 含连线，智能偏移                             |
| 双击快速添加         | 画布双击 → 居中搜索 → 回车添加                   |
| 节点 hover 快捷操作条 | 编辑/复制/删除/禁用                          |
| 快捷键集           | `Ctrl+S` `Ctrl+Enter` `Esc` `Ctrl+D` |

**达到什么效果**

- 对齐 n8n / Coze / Dify 的画布基础能力
- 全部操作有E2E 覆盖

---

## T3-08 配置面板 n8n 化

**做什么**  
配置表单改成 n8n 三段式。

**现状**  
`WfDefineRightPanel.vue` 是 **19 处 `v-else-if` 硬编码链**。

**怎么做**

1. 必填/可选分组
2. `Resource → Operation → Fields` 三段式
3. **用选择器替代 ID 填写**（⚠️ 资源选择器已实现，不要退化）
4. 可选参数收进「高级设置」默认收起

**达到什么效果**

- 用户不用填 ID，只需选
- 21 种节点类型的配置面板结构一致

---

## T3-09 失败定位 + 执行链路回放

**做什么**  
失败能定位，过程能回放。

**现状**：只有线性列表，⚠️ 不能只靠颜色标记。

**怎么做**

1. 执行失败时，点击失败条目直接跳到画布对应节点
2. 画布上回放执行过程（节点按执行顺序高亮 + 连线显示数据流）
3. 失败节点用 ⚠️ 图标 + 文案标记（⚠️ 不能只靠红色，色觉障碍用户看不出来）

**达到什么效果**

- 点失败条目 → 画布定位到该节点
- 画布上能看出走了哪条路、卡在哪一步

---

## T3-10 单节点试跑

**做什么**  
改一个参数不用重跑整条流程。

**怎么做**

- 前端：选中节点 → 试跑
- ⚠️ 后端需配合「从指定节点起执行」（跳过上游）

**达到什么效果**

- 单节点能独立试跑
- 试跑结果不影响正式运行记录

---

## T3-11 节点校验徽标

**做什么**  
未配置/配置失效的节点显式标记。

**怎么做**

- 节点上显示 ⚠️ 图标 + 文案
- ⚠️ **不能只靠红色**（色觉障碍），必须带图标 + 文案

**达到什么效果**

- 一眼看出哪些节点没配好
- 文案能说明缺什么

---

## T3-12 知识检索节点增强

**做什么**  
把库级参数暴露到节点级。

**现状**：库级已有 topK / minScore / 重排 / 扩窗，但**节点级暴露不出来** → 不同分支无法用不同策略。

**怎么做**  
在知识检索节点配置面板暴露：topK / minScore / 重排开关 / 扩窗开关。

**达到什么效果**

- 同一工作流的不同检索节点可用不同策略
- 未设置时继承库级配置

---

## T3-13 字段级变量引用 + 穿梭框

**做什么**  
能引用上游输出的**某个字段**。

**现状**：仅 `{变量名}` 文本替换。

**怎么做**

1. 输入 `{{` 弹出可用变量列表（穿梭框）
2. 能引用 HTTP 响应的某个具体字段（如 `{{resp.data.id}}`）
3. 表达式校验：写错变量立即提示

**达到什么效果**

- 能取上游 HTTP 响应的特定字段
- 引用错变量名立刻报错，不等运行失败

---

## T3-14 节点文档 / 模板 / 批量导入

**做什么**  
三件补齐「可交接」能力的项。

**怎么做**

1. **节点文档**：在 T5-03 的描述文件里为每个节点补 `doc` 字段（用途、必填项、输出结构、常见错误），节点卡片 hover 显示
2. **节点模板 / 常用流程**：内置若干可直接拖入的模板（客服问答、知识库问答、内容生成），落到 T5-03 描述文件的 `presets` 字段
3. **批量导入导出知识包到工作流**：工作流 JSON 里引用知识包时按 `kbUuid` 导出，导入时按 uuid 匹配；⚠️ **匹配不到必须报错而非静默丢节点**

**达到什么效果**

- 新人不用看代码就能配出正确工作流
- 一个含知识检索的工作流能整体导出再导入，导入后仍指向同一知识库
- ⚠️ 导入缺包时明确报「知识包 X 不存在」，不静默丢弃

---

# 第 5 批 · W3–W8 · 样式体系

## T4-01 design token 单一来源

**做什么**  
解决同一语义被拆成多个色值。

**现状（这直接导致 token 层无效）**

| 语义      | 散落色值                                                                                      | 合计     |
| ------- | ----------------------------------------------------------------------------------------- | ------ |
| primary | `#4b9e5f`(16) / `#2d8cf0`(8) / `#2c2770`(18) / `#142d6e`(16)                              | **58** |
| success | `#4b9e5f`(16) / `#19be6b`(6) / `#18a058`(3) / `#1e8c0e`(6) / `#1be3b3`(7) / `#03d5b7`(12) | **50** |
| border  | `#ebebeb`(14) / `#dbdbdb`(10) / `#bacad9`(16) / `#c9d1d9`(5) / `#eee` / `#e0e0e0`(3) 等    | **55** |
| warning | `#ffc412`(14) / `#ffc727`(5) / `#f0a020`(3) / `#ffe427`(11)                               | **33** |
| danger  | `#d03050` / `#cf222e` / `#a8071a` / `#f85149` / `#82071e`                                 | **17** |
| 暗色底     | `#24272e`(9) / `#101014`(5) / `#24292f`(4) / `#0d1117`                                    | **18** |

- hex 总次数：user-web **339** / admin-web **602**
- `:root` 与自定义 CSS 变量：**两端均为 0**
- `user-web/tailwind` extend 仅 animation；**admin-web extend 完全为空 `{}`**
- ⚠️ **admin-web 还缺 `darkMode`**（见 T4-02）

**主色 = 蓝 `#2d8cf0`（已定）**

**怎么做**

1. 新建 `packages/aideepin-tokens/src/index.ts`（根目录已有 `pnpm-workspace.yaml`）：

```ts
export const tokens = {
  light: {
    primary: '#2d8cf0', success: '#19be6b', warning: '#f0a020',
    danger: '#d03050', info: '#2563eb',
    bg: '#ffffff', 'bg-subtle': '#f6f8fa',
    border: '#e5e6eb', text: '#1f2328', 'text-muted': '#6e7781',
  },
  dark: {
    primary: '#5ecfff', success: '#3fb950', warning: '#d29922',
    danger: '#f85149', info: '#58a6ff',
    bg: '#0d1117', 'bg-subtle': '#161b22',
    border: '#30363d', text: '#e6edf3', 'text-muted': '#8b949e',
  },
} as const

// 主色阶（HSL 派生）
export const brandScale = {
  50:'#f1f7fe', 100:'#deedfd', 200:'#bcdbfa', 300:'#92c3f7', 400:'#67acf4',
  500:'#2d8cf0', 600:'#1077e4', 700:'#0e63be', 800:'#0b4f98', 900:'#083c72',
} as const

export const radius = { sm:'4px', md:'8px', lg:'12px' } as const
export const space  = [0,4,8,12,16,24,32,48] as const          // ⚠️ 禁 10/13/30px
export const shadow = {
  sm:'0 1px 2px 0 rgb(0 0 0 / 0.05)',
  md:'0 4px 6px -1px rgb(0 0 0 / 0.1)',
  lg:'0 10px 15px -3px rgb(0 0 0 / 0.1)',
} as const
export const zIndex = { base:0, dropdown:1000, sticky:1100, overlay:1200, modal:1300, toast:1400 } as const
```

1. 写 `buildTokens()` 脚本，把 light/dark 同时输出为 `:root` 与 `html.dark` 的 CSS 变量，供 Tailwind 任意值与 less 复用
2. 两端 `tailwind.config.js` 消费：

```js
const { tokens, brandScale, radius, space, shadow } = require('./packages/aideepin-tokens/src/index.js')
module.exports = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: brandScale,
        primary: tokens.light.primary, success: tokens.light.success,
        warning: tokens.light.warning, danger: tokens.light.danger,
        border: tokens.light.border, muted: tokens.light['text-muted'],
      },
      borderRadius: radius, spacing: space, boxShadow: shadow,
      screens: { sm:'640px', md:'768px', lg:'1024px', xl:'1280px', '2xl':'1536px' },
    },
  },
}
```


1. **两端 `useTheme.ts` / `App.vue` 都接上 tokens**：

```ts
import { tokens, brandScale } from 'aideepin-tokens'

const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const t = isDark.value ? tokens.dark : tokens.light
  return {
    common: {
      primaryColor:        t.primary,
      primaryColorHover:   brandScale[400],   // #67acf4 亮
      primaryColorPressed: brandScale[600],   // #1077e4 暗
      primaryColorSuppl:   t.primary,
      successColor: t.success, warningColor: t.warning,
      errorColor: t.danger,   infoColor: t.info,
      borderColor: t.border,  textColorBase: t.text,
      bodyColor: t.bg,        borderRadius: '8px',
    },
  }
})
```

⚠️ **`user-web/src/hooks/useTheme.ts:22` 当前是空的 `common: {}`**，不改这里会「蓝按钮 +绿Tag」。

1. 清残留硬编码：
   - `#4b9e5f` 9 处：`chat/components/Header/index.vue:74`、`Message/Avatar.vue:52-53`、`InputToolbar.vue:280`、`chat/layout/sider/List.vue:170`、`knowledge-base/layout/sider/SubList.vue:71`、`workflow/layout/sider/SubList.vue:69` → 换 `text-brand-500` 等 token 类
   - naive 默认绿 `#18a058` 3 处：`segment-status.ts:30,60`、`ParentChildSegmentList.vue:86` → ⚠️ 这两处语义是「**已向量化**」= 成功态，**用 `tokens.success` 不是 primary**
   - ✅ **Tailwind 语义绿 18 处不动**（`text-green-600` 等是成功/健康/进行中状态，与品牌色无关）
2. ⚠️ **修 admin-web 主题两处不一致**：
   - `App.vue:32-33` + `plugins/naiveDiscreteApi.ts:20-21`：`primaryColorHover` 与 `primaryColorPressed` **同值**（都是 `lighten(appTheme, 6)`）→ 悬停和按下无视觉差，分别改 `brandScale[400]` / `brandScale[600]`
   - `naiveDiscreteApi.ts` **漏了 `primaryColorSuppl`**（`App.vue:34` 有）→ 补齐

**达到什么效果**

- 改 `tokens.light.primary` 一个值，**两端主色同步生效**
- `grep -rn "4b9e5f" user-web/src` 返回 0；`grep -rn "18a058" user-web/src` 返回 0
- admin-web 主题色切换器（`appThemeList` 18 色）仍可用
- hex 计数：user 339 → 个位数，admin 602 → 个位数

---

## T4-02 修复 admin-web 暗色模式

**做什么**  
让暗色真正生效。

**现状（完全失效）**

| 条件                              | user-web    | admin-web                                 |
| ------------------------------- | ----------- | ----------------------------------------- |
| `tailwind.config.js` `darkMode` | `'class'` ✅ | **缺失** ❌（默认走 `prefers-color-scheme` 媒体查询） |
| `classList` 注入 `dark`           | ✅           | **grep 0 命中** ❌                           |
| `dark:` 变体使用处                   | 85          | 23                                        |
| `html.dark` 选择器                 | 1 处（正常匹配）   | 1 处（**永不匹配**）                             |

**怎么做**

1. `admin-web/tailwind.config.js` 补 `darkMode: 'class'`（T4-01 已加）
2. 加 `document.documentElement.classList.add('dark')` 注入逻辑
3. `themeOverrides` 按 isDark 切`tokens.dark` / `tokens.light`

**达到什么效果**

- admin-web 暗色全站一致，切换即时生效
- ⚠️ **不改配置只改 CSS 是无效的**——这是根因

---

## T4-03 交互一致性（四态）

**做什么**  
统一空/加载/错误/边界四态。

**怎么做**

- 加载态统一改骨架屏（顺带替掉 T2-01 处理过的白屏 2 秒）
- 空态 / 错误态 / 边界态统一组件与文案

**达到什么效果**

- 全站四态一致
- ⚠️ 不再出现「白屏 + 无提示」

---

## T4-04 暗色对比度修正

**做什么**  
修5 组WCAG 不达标色值。

**怎么做**

| 色值        | 位置                                 | 当前对比度   | 目标             |
| --------- | ---------------------------------- | ------- | -------------- |
| `#142d6e` | user-web navbar 底（16 处）            | ≈1.9:1  | ≥3:1（大字）/ 边界可见 |
| `#515a6e` | admin-web `Header/index.vue`（15 处） | ≈3.6:1  | ≥4.5:1（正文 AA）  |
| `#d03050` | user-web danger 文本（6 处）            | ≈4.1:1  | ≥4.5:1         |
| `#cfdae6` | admin-web `CodeEditor`（34 处）       | ≈12.6:1 | 暗色下与编辑器融合      |
| `#ebebeb` | user-web 边框（14 处）                  | ≈15.8:1 | 暗色下不刺眼         |

**达到什么效果**

- 全部 ≥ WCAG AA 4.5:1（正文）/ 3:1（大字）
- 暗色下不出现刺眼网格线

---

## T4-05 spacing / font-size / z-index 收口

**做什么**  
统一间距、字号、层级。

**怎么做**  
用 T4-01 的 `space`（禁 10/13/30px）、`zIndex` token 替换散落值。

**达到什么效果**

- 无 10/13/30px 之类非档位间距
- z-index 只来自 token，不出现 `99999`魔法值

---

## T4-06 inline 硬编码样式清理

**做什么**  
清理样式硬编码。

**怎么做**

- `admin-web/src/components/CodeEditor/index.vue` 20 处 Primer 色 → token
- 全站 inline `style` 改为 class + token

**达到什么效果**

- 全仓 hex 只在 tokens 定义处出现

---

## T4-07 断点体系统一 + 调试残留清理

**怎么做**

- 断点：两端各定义一套 `@media`，收敛到 T4-01 的 `breakpoints` token（`sm/md/lg/xl`）
- 清掉调试残留：`?debug`、`console.log` 留下的开关、临时注释代码

**达到什么效果**

- `@media` 只引用 breakpoints 变量，不出现裸 `768px` / `1024px`
- 无调试开关残留进主干

---

# 第 6 批 · W6–W16 · 架构与工程质量

## T5-01 合并三份 RefGraph

**做什么**  
消除 95% 克隆。

**现状**  
`chat/RefGraph.vue`(172 行) 与 `knowledge-base/RefGraph.vue`(183 行) 是 **95%+ 克隆**，`renderGraph`/`initCy`/`relayout` **逐字符相同**（含同样的 `console.log('ref graph initCy')` 残留）。差异仅 4 处且全是数据来源：store、prop、api、kb 版多 `NSpin` 与 i18n key。

`DocumentGraphCanvas.vue`(283 行) 是渐进增强版（多了分页游标 `vertexCursor`/`edgeCursor`/`loadedVertices`/`hasMore`、`destroyCy`、`appendBatch`、`addNodes`、`defineEmits`、`onActivated/onDeactivated`）。

**怎么做**  
以 `DocumentGraphCanvas.vue` 为唯一实现，另两处改薄壳，传 `fetchGraph` 回调 + `nodeKey`。顺带删 `console.log` 残留。

**达到什么效果**

- 减约 330 行
- 改渲染逻辑只需改一处

---

## T5-02 拆 `api/index.ts`

**做什么**  
1275 行 / 125 处 `<T = any>`。

**现状**：全部接口塞一个文件，形式上有泛型但**实质全部绕过**（调用方不传 T 就退化为 any）。

**怎么做**  
按业务域拆分 + 去掉 `<T = any>` 默认值，调用方必须显式传类型。

**达到什么效果**

- 无 125 处 `any`
- 类型错误在编译期暴露（⚠️ 拆完 CI 必须仍绿）

---

## T5-03 节点描述化（扩展底座）★关键路径

**做什么**  
把前端六处硬编码收敛成一份描述文件。

**现状**  
后端已有 `WfNodeFactory`（20 个 case + 单测 `WfNodeFactoryRegistrationTest`），**不要重复造**。前端是六处硬编码：

`nodes/index.ts` → `WorkflowDefine.vue` slot → `WfDefineRightPanel.vue` → `workflow-util.ts` → locales 中英文案 + `componentTitle` → **`RuntimeNodes.vue` 指标 chip**

⚠️ 漏第六处 = 指标入库不显示。

**怎么做**

1. 定义节点描述文件结构：

```ts
interface WfNodeDescriptor {
  type: string
  icon: string
  category: 'trigger' | 'llm' | 'retrieval' | 'transform' | 'action' | 'flow'
  color: string          // 7 类配色
  component: string
  componentTitle: {zh: string, en: string}   // i18n
  metrics: string[]      // RuntimeNodes 指标 chip ← 第六处
  configSchema: Field[]  // 供 T3-08 生成表单
  hasInput: boolean
  hasOutput: boolean
}
```

1. 由描述文件自动生成：注册表、slot、面板分支、`workflow-util` 分派、i18n key、指标 chip
2. 21 种现有节点全部迁入

**达到什么效果**

- **加一种节点只改一处描述文件，两端自动生效**
- 后续每个新节点需求工期从 ×2 降到 ×1
- ⚠️ 遗漏自检：新增节点后确认指标 chip 能在 `RuntimeNodes.vue` 显示

---

## T5-04 admin-web http 层修

**做什么**  
修基建。

**怎么做**

- `src/utils/http/axios/Axios.ts` 核心基建里有 `@ts-ignore` → 消掉
- `build/utils.ts:31-35` 的 `VITE_PROXY` 吞异常 → 抛出
- `build/vite/proxy.ts` 的 `createProxy` **从未被调用** → 接通

**达到什么效果**

- 无`@ts-ignore` 在核心基建
- proxy 真的生效（当前配置被静默忽略）

---

## T5-05 admin-web 权限二选一

**做什么**  
处理权限空壳。

**现状**  
`asyncRoute.ts:94` 过滤逻辑是 `if (!permissions) return true`（无permissions 一律放行）。13 个路由模块中 `meta.permissions` 唯一命中是 `router/modules/dashboard.ts:44,54` 两处**被注释掉的**代码。

**怎么做**  
**选B（补 `meta.permissions`）**：为13 个路由模块补全权限声明，删掉 `if (!permissions) return true` 兜底。

⚠️ 若本期不做，至少要**删掉注释代码 + 加说明**，不要留「看起来有权限控制实际没有」的状态。

**达到什么效果**

- 权限声明真实生效
- 或明确记录为已知缺口（不留假象）

---

## T5-06 admin-web 死代码清理

**做什么**  
删 860 行死代码。

**怎么做**

- `src/components/Modal/`（**480 行**，使用者 0，原生 `NModal` 用了 20 处）
- `src/components/Upload/`
- `src/components/Lockscreen/`
- 死 hooks：`useAsync` / `useDomWidth` / `useTimeout` / `usePage`(63 行 vben 遗留) / `useBattery` / `useOnline` / `useTime` —— views 外引用均为 0

**达到什么效果**

- 减约 860 行
- `grep -rn "components/Modal"` 返回 0

---

## T5-07 `noImplicitAny` 收紧

**做什么**  
让 `strict: true` 名副其实。

**现状**  
`admin-web/tsconfig.json` 是 `noImplicitAny: false`。

**怎么做**  
分批收紧（每批控制在可回归范围）：

| 文件                                     | any 数  | 行数  |
| -------------------------------------- | ------ | --- |
| `views/knowledge-base/info.vue`        | **49** | 998 |
| `views/skill/index.vue`                | 27     | 872 |
| `views/finetune/job.vue`               | 20     | 743 |
| `views/knowledge-base/recall-test.vue` | 14     | 387 |
| `views/agent/index.vue`                | 13     | 315 |

**达到什么效果**

- 逐步把 `noImplicitAny` 打开
- ⚠️ 每批改完CI 必须绿，不允许「一次性全开然后长期红」

---

## T5-08 表单校验体系化

**做什么**  
user-web 表单校验统一。

**怎么做**  
统一校验方案（naive-ui `rules`），逐个表单接入。

**达到什么效果**

- 校验规则集中管理
- 前后端校验一致

---

## T5-09 抽公共 UI 组件

**做什么**  
消除重复（700–900 行）。

**现状**

| 候选                   | 重复情况                                                                                                                         |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `MessageList.vue`    | **3 份**：`chat/components/Message/index.vue`(268) / `draw/.../Message/index.vue`(210) / `workflow/.../Message/index.vue`(163) |
| `EmptyState`         | `NEmpty`散落 **9 个文件**                                                                                                         |
| `LoadingState`       | `NSpin` 散落 **11 个文件**                                                                                                        |
| `ConversationHeader` | `chat/components/Header/pc.vue`(77) vs `knowledge-base/Header/pc.vue`(68)，diff 仅 55 行 → 约 70% 相同                             |
| `SessionSider`       | `chat/layout/sider/List.vue`(191) / `workflow/layout/sider/List.vue`(117) / `knowledge-base/layout/sider/index.vue`(74)      |

**达到什么效果**

- 减 700~900 行
- 四态在T4-03 已统一，这里只做位置收敛

---

## T5-10 拆分超大文件

**做什么**  
治理超大文件。

**做法与目标**

| 文件                                                                      | 行数                             | 目标                                                                                  |
| ----------------------------------------------------------------------- | ------------------------------ | ----------------------------------------------------------------------------------- |
| `admin-web/src/wf/utils/constant.ts` + `user-web/src/utils/constant.ts` | **1636**（全仓最大，两端各一份）           | 按节点域拆分，单文件 ≤400 行                                                                   |
| `DocumentDetail.vue`                                                    | 982（script 1–740，**28 个顶层函数**） | 按职责拆 composable                                                                     |
| `admin-web/views/knowledge-base/info.vue`                               | 998                            | 同上                                                                                  |
| `chat/index.vue`                                                        | 776（含 SSE 流式控制）                | SSE 控制逻辑独立                                                                          |
| `admin-web/views/skill/index.vue`                                       | 872                            | 拆分                                                                                  |
| `KnowledgeBaseDetail.vue`                                               | 745                            | 拆分                                                                                  |
| `admin-web/views/finetune/job.vue`                                      | 743                            | 拆分                                                                                  |
| `workflow-util.ts`（两端）                                                  | 531                            | 下沉到 workflow 模块                                                                     |
| `admin-web/src/wf/stores/workflow/index.ts`                             | 519                            | Options API → setup store；8 个设计器专属 state 移入 `views/wf-designer/useDesignerStore.ts` |
| `user-web/src/utils/functions/index.ts`                                 | 429                            | 通用（debounce/throttle）与业务（`emptyCharacter`/`emptyAudioPlayState`）拆两层                 |
| `admin-web/src/utils/constants.ts`                                      | 305                            | 内含模型厂商业务枚举，移出 utils                                                                 |

**达到什么效果**

- 单文件 ≤ 400 行
- `chat/index.vue` 的 SSE 逻辑可独立测试

---

## T5-11 下沉局部状态为局部 store

**做什么**  
把无意义的全局 store 改成局部。

**现状**

| 模块                  | 行数  | 问题                                                                                                     |
| ------------------- | --- | ------------------------------------------------------------------------------------------------------ |
| `settings/index.ts` | 22  | `useSettingStore` 全项目**仅 1 处**引用，真实消费方是设置弹窗 → localStorage 语义 > 全局 store                               |
| `gallery/index.ts`  | 106 | 内含 `loading`/`loadingUuid`（纯 UI）、`appendStarDraws`/`starDraw`/`unStarDraw`（Draw 页动作）→ 拆为全局列表 + 页面内 ref |
| `prompt/index.ts`   | 17  | 提示词只在使用它的对话框内有意义                                                                                       |

**达到什么效果**

- 全局 store 只放真正跨页面共享的状态
- 离开页面即释放的状态不进全局

---

## T5-12 构建配置修正

**做什么**  
修两端 vite 配置里已失效的项。

**怎么做**

| 位置                          | 问题                        | 改法           |
| --------------------------- | ------------------------- | ------------ |
| admin `build/vite/proxy.ts` | `createProxy` 从未被调用       | 见 T5-04，接通   |
| admin vite 配置               | `esbuild: {}` 空对象         | 删掉           |
| admin proxy                 | 硬编码 target                | 走 `.env`     |
| user proxy                  | rewrite 缺陷                | 修 rewrite 规则 |
| 两端                          | `VITE_GLOB_APP_PWA=false` | 见 T9-11      |

**达到什么效果**

- `pnpm build` 无失效配置项
- proxy 配置真的生效

---

## T5-13 死 hooks 与调试残留清理

**怎么做**

- `useAsync` / `useDomWidth` / `useTimeout` / `usePage`(63 行 vben 遗留) / `useBattery` / `useOnline` / `useTime` —— views 外引用均为 0（与 T5-06 同批做）
- 两端 `console.log` 清零：workflow 模块 **54 处**、RefGraph.vue 残留 `console.log('ref graph initCy')`
- ⚠️ 清零时**保留**必要的 error/warn，不许一刀切删

**达到什么效果**

- `grep -rn "console.log" user-web/src admin-web/src` 仅剩允许清单内的行
- 死 hooks 文件全部删除

---

## T5-14 admin-web `strict: true` 名存实亡

**做什么**  
`tsconfig` 开了 strict 但被 `@ts-ignore` / `any` 绕过，等于没开。

**现状**  
四个基础引擎采纳率：Table 48%、Form 26%，其余更低 → 说明 strict 实际未生效，团队在用 `any` 绕过。

**怎么做**

1. 先统计 `@ts-ignore` / `@ts-nocheck` / 显式 `any` 数量
2. 按 T5-07 的收紧顺序逐文件去掉，**每改一个模块跑一次 build**
3. 禁止新增 `@ts-ignore`（ESLint 规则拦截）

**达到什么效果**

- `@ts-ignore` 数量降到 0
- strict 真正生效（故意写一个类型错误能被 tsc 拦住）


## T5-16 i18n 补全（`vue-i18n` 版本统一后）

**怎么做**

1. 前置：T0-01 第 5 步的 vue-i18n 版本统一必须先完成
2. 跑扫描脚本列出 zh/en 两侧 key 不对等的条目
3. 补齐缺失 key；⚠️ 全部走 `braceLiteral.ts`，禁字面花括号（铁律 2）
4. 新增文案负向验证：故意写一个 `"{a}"` 确认被 `check-i18n-braces` 拦住

**达到什么效果**

- zh/en key 集合完全一致（脚本输出 0 差异）
- 无字面花括号

---

# 第 7 批 · W8–W20 · AI 能力与可观测

> ⚠️ **本批所有检索/生成行为改动，合入前后必须各跑一次评测并附结果**（铁律 9）。

## T6-01 端到端 QA 评测（EVAL 门禁）★后续所有 AI 改动的前置

**做什么**  
建固定题库 + 自动跑分。

**怎么做**

1. 建固定题库（覆盖面：事实问答/多跳/拒答边界）
2. 自动跑分脚本，输出准确率/召回率/拒答正确率
3. 接入 CI 或手动触发，产出对比报告

**达到什么效果**

- 任何检索/生成改动前后能拿到**可比对的两份数据**
- ⚠️ 断言必须真实，不能用「有内容即通过」（全0.5 分也是绿）

---

## T6-02 检索回归固化

**做什么**  
把已验证的调参效果固化成基线。

**怎么做**  
把 025/026/027/030 每次调参的效果固化成回归基线：中文分句（025）+ 左右扩窗（026）+ 候选池上限 10（027）+ Rerank（030）+ 混合检索（017）。

**达到什么效果**

- 改检索参数能立即看出效果变化
- ⚠️「候选池天花板」是已知结论（topK=100 → 池=57 覆盖全库 → 纯关键词路必然为 0），**展示关键词路价值时池必须小于全库**

---

## T6-03 qa 分块推广

**做什么**  
把 025 的中文分句方案推广到其他入库路径。

**怎么做**  
`sentence_cn` / 512 / overlap=0 推广到 qa 及其他入库入口。

**达到什么效果**

- 各入库路径分块策略一致
- ⚠️ 改动前后跑 T6-02 基线

---

## T6-04 overlap 评测

**做什么**  
评估当前 `overlap=0` 是否最优。

**怎么做**  
跑 overlap ∈ {0, 0.1, 0.2} 三组对比。

**达到什么效果**

- 给出「是否该开 overlap」的量化结论
- ⚠️ 改前必须跑 T6-02 基线，否则无法归因

---

## T6-05 提示注入防御

**做什么**  
语义层防护（与 T0-02 的 XSS 是不同层）。

**怎么做**  
对检索内容与用户输入做指令注入识别与隔离。

**达到什么效果**

- 「忽略以上指令」类注入无法生效
- ⚠️ T0-02 管渲染安全，T6-05 管语义安全，两层都要做

---

## T6-06 长对话摘要压缩

**做什么**  
避免上下文爆炸。

**怎么做**  
超出 token 阈值自动摘要压缩历史消息。

**达到什么效果**

- 长对话不因上下文超限失败
- ⚠️ 压缩不能吃掉系统提示词（见 `2026-10-05-identity-guard-and-system-message-memory-trap.md`）

---

## T6-07 `create_skill` 按需注入

**做什么**  
技能描述按需注入而非全量进 prompt。

**怎么做**  
只注入当前任务相关的技能描述。

**达到什么效果**

- prompt token 下降
- 技能召回准确率不降

---

## T6-08 streaming 超时配置化

**做什么**  
超时从硬编码改为配置。

**怎么做**  
超时参数接入系统配置，支持热更新。

⚠️ 重试时必须配合 `firstTokenSent` 保护——若已输出部分内容再重试会产生重复输出。

**达到什么效果**

- 超时可配置、可热回滚（铁律 10）
- 重试不产生重复内容

---

## T6-09 模型降级路由

**做什么**  
主模型失败自动降级。

**怎么做**  
按 `LLMErrorClassifier` 的 RETRIABLE / PERMANENT 分类决定是否降级：RETRIABLE 降级重试，PERMANENT 直接报错不浪费额度。

**达到什么效果**

- 网络抖动自动切备用模型
- ⚠️ PERMANENT 类错误不降级（配置错误降级也没用）

---

## T6-10 工具循环三重预算

**做什么**  
防死循环。

**怎么做**  
同时生效三个预算：**深度** + **墙钟时间** + **token**，任一超限即中止。

**达到什么效果**

- 工具循环有硬上限
- ⚠️ 三个预算必须都有测试覆盖（只测一个等于没测）

---

## T6-11 temperature 语义修复

**做什么**  
参数语义与实际采样行为对齐。

**达到什么效果**

- 设置的 temperature 与实际行为一致
- ⚠️ 修完跑 T6-01 评测确认无回归

---

## T6-12 工具日志脱敏

**做什么**  
避免密钥/PII 进日志。

**怎么做**  
工具入参出参日志脱敏 + 截断。

**达到什么效果**

- 日志中无明文密钥
- 日志体积可控

---

## T6-13 模型成本看板

**做什么**  
看清钱花在哪。

**怎么做**  
按模型/Agent 归集 token 与费用。

⚠️ 数据源故障时显示「不可用」，**不显示 0**（0 会被误读为「没花钱」）。

**达到什么效果**

- 能按模型/Agent 归集成本
- ⚠️ 与 T7-01 RAG 质量看板是两个东西，别混

---

## T6-14 trace 全链路贯通 + AgentMetrics 补全

**做什么**  
一次调用能看到完整链路。

**达到什么效果**

- 单次对话能看到完整 trace
- AgentMetrics 指标完整（不缺失）

---

## T6-15 委派评测集

**做什么**  
评测多Agent 委派质量。

**达到什么效果**

- 委派正确率可量化

---

# 第 8 批 · W14–W26 · 多 Agent 与知识增强

## T7-01 RAG 质量看板 + 失败闭环

**做什么**  
看清答得准不准 + 失败案例转评测集。

**怎么做**

1. 五个质量指标 + 下钻 + CSV 导出
2. 👍👎 反馈
3. 一键把失败案例转评测集（对接 `kb-eval` 题库格式）

⚠️ **前置依赖 T1-01 检索日志**（数据源 `adi_rag_query_log`）。

**达到什么效果**

- 数据源故障时显示「不可用」而非 0
- 👍👎 点一下能直接生成评测题

---

## T7-02 `delegate_agent`（agent-as-tool）

**做什么**  
把 Agent 作为工具被调用。

**怎么做**

1. 实现 agent-as-tool 机制
2. 委派契约：`objective` / `output_format` / `context_summary` / `must_not_do`
3. effort-scaling（任务难度调节投入）
4. ⚠️ **嵌套深度 = 1**（再深会失控）

**达到什么效果**

- Agent 能把子任务委派给专门 Agent
- 委派契约可追溯
- ⚠️ 嵌套深度超过 1 必须拒绝

---

## T7-03 `OrchestratorNode` + 共享黑板

**做什么**  
编排型节点 + 共享状态。

**达到什么效果**

- 多 Agent 协作有统一编排入口
- 共享黑板状态可读写

---

## T7-04 A2A 双向互调

**做什么**  
Agent 间双向调用。

⚠️ 与 T7-02 的嵌套深度限制一致，**双向互调也要防环**。

**达到什么效果**

- A→B→A 的双向调用可控（不死循环）

---

## T7-05 bge-m3 embedding 替换评估

**做什么**  
评估换 embedding 模型的效果。

⚠️ 换 embedding **必须全量重建索引** + 跑 T6-02 基线，且**必须带配置开关**（铁律 10），可回滚。

**达到什么效果**

- 给出「是否值得换」的量化结论
- 能一键回滚到旧 embedding

---

## T7-06 知识包导出/导入 + 分享

**做什么**  
知识库能力可打包带走。

**怎么做**  
打包 `SKILL.md` + `config.json` + `pack.json` 为 ZIP。  
⚠️ 虚拟 `SKILL.md` 需兜底。  
⚠️ **共享的是能力不是数据**——导入方仍受自身 ACL 约束。

**达到什么效果**

- 导出再导入，往返一致
- 导入方无法通过知识包绕过自己的 ACL

---

## T7-07 用户维度记忆

**做什么**  
记忆绑定 userId。

**怎么做**  
绑定 userId，跨 Agent 跨会话。

⚠️ **写入前必须用户确认**。

**达到什么效果**

- 同一用户在不同 Agent 间记忆共享
- 敏感信息写入有确认环节

---

# 第 9 批 · P3 · 单独立项（不在主线排期内）

| ID    | 任务                        | 关键点                                                                                                       |
| ----- | ------------------------- | --------------------------------------------------------------------------------------------------------- |
| T9-01 | 定时/Webhook/API 触发         | ⚠️ **功能性缺口不是体验缺口**——目前只能手动点运行，本质仍是手工脚本。做完后才谈得上「自动化」                                                       |
| T9-02 | 循环节点（Loop / Each）         | 引擎改造                                                                                                      |
| T9-03 | 并行节点（Parallel）            | 引擎改造                                                                                                      |
| T9-04 | 代码节点（Code）                | ⚠️ **不要新造执行器**，复用 Skill 节点已有沙箱                                                                            |
| T9-05 | 工作流版本历史与回滚                | 3 天                                                                                                       |
| T9-06 | 工作流导入导出 JSON + 模板         | 2 天                                                                                                       |
| T9-07 | 错误处理分支 + 子工作流             | 5 天；子工作流用 try/catch 错误分支                                                                                  |
| T9-08 | 子流程分组（Container 节点）       | 解决「意大利面线」；与 T9-07 的子工作流不同，这是画布组织                                                                          |
| T9-09 | 抽包 `packages/aideepin-wf` | 全仓最大重复源 **4400 行**。⚠️ **本期不做**，先立规矩「改一处必须同步另一处」。若后续要做，**必须等 T5-03 描述化落地之后**，否则仍要维护两份描述文件                  |
| T9-10 | 响应式重做                     | 桌面内部系统，优先级低                                                                                               |
| T9-11 | PWA 启用                    | 当前被显式关闭（`VITE_GLOB_APP_PWA=false`）                                                                        |
| T9-12 | a11y 补齐                   | admin `<img>` 缺 alt 2 处；`<button>` 共 18 个带 `aria-label` 的 **0 个**；admin `:focus-visible` **0 处**（键盘焦点不可见） |

---

# 附录 A · 关键实测数据（执行时直接用，不要重新测）

## 检索链路

- **底层检索入口只有 1 处**：`adi-common/.../rag/AdiEmbeddingStoreContentRetriever.java:274` 的 `embeddingStore.search()`
- 根因位置：`CharacterChatHelper.java:131` 的 `new IsIn(KB_UUID, kbUuids)`（只有库过滤，无 ACL）
- `SemanticMemoryService.java:75` 也用 `EmbeddingSearchRequest`，走用户记忆（非知识库），需确认不共用 `kb_uuid` 命名空间
- ACL 需要的 metadata 键：现有 `AdiConstant.MetadataKey` 已有 `KB_UUID`/`KB_ITEM_UUID`/`SEGMENT_ID`/`DOC_UUID`/`POSITION`/`DOC_TITLE`/`ENGINE_NAME`/`SEARCH_UUID`/`CHARACTER_ID`，**需新增 2 个 ACL 键**

## 前端体积

| 项                   | 值                                        |
| ------------------- | ---------------------------------------- |
| user 入口 chunk       | `index-3181902c.js` **1263KB**           |
| user workflow chunk | `index-976f1059.js` **1389KB**           |
| user 另一 chunk       | `index-a5dfd60d.js` **1173KB**           |
| admin 入口 chunk      | `index.7e6bacce.js` **1889KB**           |
| admin               | `index.76b14504.js` **1417KB**           |
| admin               | `json-editor-vue.8994eab6.js` **1001KB** |
| admin echarts       | `props.47c3d578.js` **764KB**            |
| katex 字体            | **59 个文件**，首屏约 200KB                     |

## 工作流模块

- `content_line` 用于 **34 个文件**，`user-web/src/styles/global.less` 仅 10 行，admin-web 无同名文件
- `console.log` **54 处**
- `WfDefineRightPanel.vue`：`absolute right-0 top-20` + `width: 600px`，`v-else-if` **19 处**
- node/edge 组件 **41 个**（两端各41）
- 抽包重复量：**4400 行**。`user-web/src/store/modules/workflow/index.ts`(519行) vs `admin-web/src/wf/stores/workflow/index.ts`(519 行) **diff 仅 10 行、3 处差异**（`functions` 导入路径、`router` 导入形式、路由 name `WfDetail` vs `WorkflowDesigner`）；6 个组件 diff =0/0/0/0/0/8 行；`Text.vue` diff 4 行；`Message/style.less` 与 `wf/chat-msg/style.less` 的 `html.dark` **都在第 62 行**
- `WfNodeFactory` 含 **20 个 case**：START / LLM_ANSWER / CLASSIFIER / SWITCHER / TEMPLATE / KEYWORD_EXTRACTOR / DOCUMENT_EXTRACTOR / FAQ_EXTRACTOR / KNOWLEDGE_RETRIEVER / GOOGLE_SEARCH / OPENAI_IMAGE / TONGYI_WANX / HUMAN_FEEDBACK / MAIL_SEND / HTTP_REQUEST / AGENT / DATABASE / MCP / SKILL / END

## 迁移脚本

当前最新 `034_kb_category.sql`。本任务书新增：`035_rag_query_log`、`035_kb_acl`、`039_acl_backfill`。

---

# 附录 B · 已确认「不要动」

| 项                       | 原因                               |
| ----------------------- | -------------------------------- |
| 21 种节点类型已实现             | 不是缺失项，是已交付                       |
| 资源选择器已全部实现              | 相对 n8n 的优势（无需手填 ID），T3-08改造时不要退化 |
| 数据库 Schema 浏览器          | 已实现                              |
| Agent 模型能力联动            | 已实现                              |
| 条件分支 and-or             | 已实现                              |
| `check-i18n-braces.mjs` | 已有脚本，T0-01 只需接入 CI               |

---

# 附录 C · 明确不做

| 项                         | 原因                                                                                                                                                  |
| ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Multi-Query 查询改写**      | 本项目 2026-09-22 已实测 HyDE/MultiQuery 为**负收益**：HyDE 生成流畅但**编造产品行为**，作分数来源有害，只能作名次投票者。检索侧只走已验证路径：中文分句（025）+ 左右扩窗（026）+ 候选池（027）+ Rerank（030）+ 混合检索（017） |
| **admin-web 升级 Vite 3→4** | 升了可移除 `patch-package` 补丁，但 `build/vite/` 那套模板代码要一并现代化，工作量不小。本期不做                                                                                    |
| LDAP / HR 同步              | 可见组**手工维护 + 预留同步接口**。把 LDAP 绑进关键路径会让线A +5 天且交付不可控                                                                                                   |

---

# 附录 D · 溯源

| 你要读的                     | 去哪                              |
| ------------------------ | ------------------------------- |
| 需求来源、去重过程、选型论证、拍板记录、自查复盘 | `全量需求-合并去重与实施总排期.md`（667 行，给人看） |
| 可即插即用知识库的完整 PRD          | `PRD-知识库即插即用能力增强.md`            |
| 工作流体验规格（面向设计的细节）         | `工作流设计器体验增强规格.md`               |
| **执行**                   | 只用本文件，不要再去读上面三份                 |

## D.1 ID 溯源对照（任务书卡片 ← 原 98 项）

| 原 ID                                                             | 卡片                                                                                                                                                    |
| ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| S-01 / S-02 / S-03 / S-04 / S-05 / S-06                          | T0-02 / T0-03 / T0-04 / T0-05 / T2-07 / T0-01                                                                                                         |
| A-01 ~ A-10                                                      | T1-01 / T1-02 / T1-03 / T1-04 / T1-05 / T1-06 / T1-07 / T1-08 / T1-09 / T1-10                                                                         |
| P-01 ~ P-06                                                      | T2-02 / T2-03 / T2-04 / T2-05 / T2-06 / T2-01                                                                                                         |
| W-00 / W-01 / W-02 / W-02b / W-03a / W-03b / W-04 / W-04b / W-05 | T3-01 / T3-05 / T3-06 / T3-07 / T3-04 / T3-08 / T5-03 / T3-09 / T3-10                                                                                 |
| W-14 ~ W-18 / W-21                                               | T3-02 / T3-03 / T3-11 / T3-12 / T3-13 / T9-08                                                                                                         |
| W-07 / W-08 / W-19 / W-20 / W-22 / W-23 / W-24                   | T9-06 / T9-01 / T9-05 / T9-07 / T3-14 / T3-14 / T3-14                                                                                                 |
| V-01 ~ V-09                                                      | T4-01 / T4-02 / T4-01 / T4-03 / T4-04 / T4-05 / T4-06 / T4-07 / T9-10                                                                                 |
| R-01 ~ R-05                                                      | T6-08 / T6-09 / T6-10 / T6-11 / T6-12                                                                                                                 |
| AI-01 ~ AI-11                                                    | T6-01 / T6-02 / T6-03 / T6-04 / T6-05 / T6-06 / T6-07 / T7-05 / T7-02 / T7-03 / T7-04                                                                 |
| C-01 ~ C-04                                                      | T6-13 / T7-01 / T7-06 / T7-07                                                                                                                         |
| E-03 / E-04 / E-06 ~ E-24                                        | T5-01 / T5-02 / T5-04 / T5-05 / T5-06 / T5-07 / T5-08 / T5-09 / T5-10 / T5-11 / T5-12 / T5-13 / T5-14 / T5-15 / T5-16 / T9-09 / T9-11 / T9-12 / T9-10 |
| X-02 / X-03 / X-04                                               | T9-02 / T9-03 / T9-04                                                                                                                                 |
| OBS-01 / OBS-02                                                  | T6-14 / T6-15                                                                                                                                         |
| V-03                                                             | 已完成 ✅（Q1 拍板：主色 `#2d8cf0`，实际工作量并入 T4-01）                                                                                                               |

**覆盖校验**：98/98 ✅（2026-10-16 机器化逐项比对，零遗漏）。

## D.2 改动后必须复跑的自查

```python
# 任务卡 ID 唯一性
import re
t = open('任务书-可直接交给AI执行.md', encoding='utf-8').read()
ids = re.findall(r'^## (T\d-\d+)', t, re.M)
dup = [x for x in set(ids) if ids.count(x) > 1]
print(len(ids), '张卡, 重复:', dup or '无')
```
