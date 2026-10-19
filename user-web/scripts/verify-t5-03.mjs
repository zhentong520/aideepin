/**
 * T5-03 机器化自查（一次性脚本，任务书铁律：多来源合并必须机器校验）
 *
 * 三方 diff 必须为空：
 *   A. 描述文件 descriptor.ts 的 type 集合
 *   B. 后端 WfNodeFactory 的 20 个 case（经 WfComponentNameEnum 映射为组件名）
 *   C. 改造前六处硬编码各自的 type 集合（取自 git HEAD 的旧版本）
 *
 * 另校验：
 *   - 六处旧硬编码在当前工作区已消除（无残留）
 *   - RuntimeNodes 旧 metadata.type 指标分支全部被描述文件 metrics 覆盖（第六处防漏）
 *
 * 用法：node user-web/scripts/verify-t5-03.mjs  （在 aideepin 仓库根目录执行）
 */
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const repoRoot = execSync('git rev-parse --show-toplevel', { encoding: 'utf8' }).trim()

const PATHS = {
  descriptor: 'user-web/src/views/workflow/components/nodes/descriptor.ts',
  nodesIndex: 'user-web/src/views/workflow/components/nodes/index.ts',
  define: 'user-web/src/views/workflow/WorkflowDefine.vue',
  panel: 'user-web/src/views/workflow/WfDefineRightPanel.vue',
  util: 'user-web/src/utils/workflow-util.ts',
  runtime: 'user-web/src/views/workflow/components/RuntimeNodes.vue',
  zhLocale: 'user-web/src/locales/zh-CN.ts',
  enLocale: 'user-web/src/locales/en-US.ts',
  factory: 'server/adi-common/src/main/java/com/moyz/adi/common/workflow/WfNodeFactory.java',
  nameEnum: 'server/adi-common/src/main/java/com/moyz/adi/common/workflow/WfComponentNameEnum.java',
}

const head = (p) => execSync(`git show HEAD:${p}`, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 })
const work = (p) => readFileSync(`${repoRoot}/${p}`, 'utf8')
const norm = (s) => s.toLowerCase()
const asSet = (arr) => new Set(arr.map(norm))

function extract(re, text, group = 1) {
  const out = []
  let m
  while ((m = re.exec(text)) !== null) out.push(m[group])
  return out
}

// ---------- A. 描述文件 type 集合（仅顶层节点条目：块首 "  {" + 4 空格 type:，类型首字母大写） ----------
const descriptorText = work(PATHS.descriptor)
const entryRe = /^  \{\r?\n    type: '([A-Za-z]+)',([\s\S]*?)^  \},/gm
const descTypes = new Set()
const descMetrics = {}
for (const m of descriptorText.matchAll(entryRe)) {
  if (!/^[A-Z]/.test(m[1]))
    continue
  descTypes.add(norm(m[1]))
  descMetrics[norm(m[1])] = (m[2].match(/'([A-Za-z]+)'/g) || []).map(s => s.slice(1, -1))
}

// ---------- B. WfNodeFactory 20 case -> 组件名 ----------
const factoryCases = extract(/case ([A-Z_]+):/g, head(PATHS.factory))
const enumPairs = [...head(PATHS.nameEnum).matchAll(/([A-Z_]+)\("([^"]+)"\)/g)]
  .map(m => [m[1], m[2]])
const enumToName = Object.fromEntries(enumPairs)
const factoryTypes = asSet(factoryCases.map(c => enumToName[c]).filter(Boolean))
const unmappedCases = factoryCases.filter(c => !enumToName[c])

// ---------- C. 六处旧硬编码集合（git HEAD） ----------
// 1. 旧 nodes/index.ts：画布节点组件导入（排除 SpecialNode 与 *NodeProperty）
const oldIndexImports = extract(/import (\w+) from '\.\/\w+\.vue'/g, head(PATHS.nodesIndex))
const oldIndexTypes = asSet(oldIndexImports
  .filter(n => n.endsWith('Node') && n !== 'SpecialNode' && !n.endsWith('NodeProperty'))
  .map(n => n.slice(0, -'Node'.length)))

// 2. 旧 WorkflowDefine.vue slot：#node-xxx（special 为 SpecialNode 边场景特殊节点，不属于 20 种工厂类型，排除）
const oldSlotTypes = asSet(extract(/#node-([a-z]+)=/g, head(PATHS.define)).filter(n => n !== 'special'))

// 3. 旧 WfDefineRightPanel.vue：v-else-if 链中的 'Xxx' 判断
const oldPanelTypes = asSet(extract(/=== '([A-Za-z]+)'/g, head(PATHS.panel)))

// 4. 旧 workflow-util.ts：三个 switch 的 case 'xxx' + createNewNode 分支 === 'Xxx'
const oldUtilTypes = asSet([
  ...extract(/case '([A-Za-z]+)':/g, head(PATHS.util)),
  ...extract(/component\.name === '([A-Za-z]+)'/g, head(PATHS.util)),
])

// 5. 旧 locales componentTitle keys（中英两侧）
function localeComponentTitleKeys(text) {
  const start = text.indexOf('componentTitle: {')
  const end = text.indexOf('},', start)
  return asSet(extract(/^\s{6}([A-Za-z]+):/gm, text.slice(start, end)))
}
const oldZhKeys = localeComponentTitleKeys(head(PATHS.zhLocale))
const oldEnKeys = localeComponentTitleKeys(head(PATHS.enLocale))

// 6. 旧 RuntimeNodes.vue metadata.type 分支（指标 chip 类型）
const oldRuntimeMetaTypes = new Set(extract(/metadata\?\.type === '([a-z_]+)'/g, head(PATHS.runtime)))
// metadata.type -> 产生该指标的节点组件名
const metaTypeToNode = {
  llm: ['Answer', 'Classifier', 'KeywordExtractor', 'FaqExtractor'],
  agent: ['Agent'],
  http_request: ['HttpRequest'],
  search: ['Google'],
  knowledge_retrieval: ['KnowledgeRetrieval'],
  image: ['OpenAiImage', 'Tongyiwanx'],
  mail: ['MailSend'],
  document: ['DocumentExtractor'],
  mcp: ['Mcp'],
  skill: ['Skill'],
}

// ---------- diff ----------
let failed = false
function diff(label, set, ref = descTypes) {
  const missing = [...ref].filter(x => !set.has(x))
  const extra = [...set].filter(x => !ref.has(x))
  const ok = missing.length === 0 && extra.length === 0
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}  (${set.size} 项${ok ? '' : ` | 缺失: [${missing}] 多余: [${extra}]`})`)
  if (!ok) failed = true
  return ok
}

console.log(`描述文件 type 数: ${descTypes.size}；WfNodeFactory case 数: ${factoryCases.length}`)
diff('① nodes/index.ts（旧导入组件）       ', oldIndexTypes)
diff('② WorkflowDefine.vue slot（旧 #node-*）', oldSlotTypes)
diff('③ WfDefineRightPanel.vue（旧分支判断）  ', oldPanelTypes)
diff('④ workflow-util.ts（旧 switch/分支）    ', oldUtilTypes)
diff('⑤ locales zh componentTitle keys      ', oldZhKeys)
diff('⑤ locales en componentTitle keys      ', oldEnKeys)

if (unmappedCases.length > 0) {
  console.log(`FAIL  WfNodeFactory case 未映射到组件名: [${unmappedCases}]`)
  failed = true
}
else {
  diff('⑥ WfNodeFactory case（经枚举映射）     ', factoryTypes)
}

// 第六处专项：旧 RuntimeNodes 每个 metadata.type 仍被描述文件 metrics 覆盖
for (const metaType of oldRuntimeMetaTypes) {
  const producers = metaTypeToNode[metaType] || []
  const covered = producers.filter(n => (descMetrics[norm(n)] || []).length > 0)
  if (covered.length === 0) {
    console.log(`FAIL  第六处：metadata.type '${metaType}' 无任何节点描述 metrics 覆盖（指标 chip 将不显示）`)
    failed = true
  }
  else {
    console.log(`PASS  第六处覆盖：metadata.type '${metaType}' ← [${covered.join(', ')}]`)
  }
}

// 旧硬编码残留检查（当前工作区不再出现旧写法）
const residuals = [
  [PATHS.define, /#node-(start|end|answer|classifier|switcher|template|google|agent|database|mcp|skill)=/, 'WorkflowDefine.vue 残留硬编码 slot'],
  [PATHS.util, /case 'answer':/, 'workflow-util.ts 残留 switch 硬编码'],
  [PATHS.runtime, /metadata\?\.type === '/, 'RuntimeNodes.vue 残留 metadata.type 硬编码分支'],
  [PATHS.panel, /getIconByComponentName\(/, 'WfDefineRightPanel.vue 残留旧工具函数调用'],
]
for (const [p, re, label] of residuals) {
  if (re.test(work(p))) {
    console.log(`FAIL  残留：${label}`)
    failed = true
  }
  else {
    console.log(`PASS  无残留：${label}`)
  }
}

// locales key 保留确认（防其他引用断）
for (const [p, label] of [[PATHS.zhLocale, 'zh-CN'], [PATHS.enLocale, 'en-US']]) {
  const kept = localeComponentTitleKeys(work(p))
  diff(`locales ${label} key 保留（当前 vs 描述）  `, kept)
}

console.log(failed ? '\n结果：FAIL ❌' : '\n结果：全部通过 ✅（三方 diff 为空，六处收敛完成）')
process.exit(failed ? 1 : 0)
