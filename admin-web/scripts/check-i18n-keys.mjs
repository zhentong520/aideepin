#!/usr/bin/env node
/**
 * i18n 缺键守卫（admin-web）
 * =============================================
 * 检查「代码里实际用到的 i18n key」是否在 zh-CN / en-US 语言文件中都存在，
 * 缺键即 exit 1（阻断构建/发布）。
 *
 * 背景：vue-i18n 找不到 key 时会**原样回显 key**（如界面出现 kbRecall.paramsTitle），
 * 不报错、肉眼难发现。2026-10-29 召回测试页整个 kbRecall 命名空间 +
 * 11 条 route 菜单标题漏加词条，页面大面积英文回显，此脚本即为防再犯。
 *
 * 扫描范围（两类使用点）：
 *   ① 静态调用：src/**\/*.{vue,ts,tsx} 里的 t('key') / $t("key")（含模板与脚本）
 *   ② 路由标题：src/router/**\/*.ts 里的 title: 'route.xxx'（菜单/面包屑经
 *      t(routeItem.meta.title) 动态翻译，t() 调用点本身拿不到字面量，必须单独扫）
 *
 * 已知局限（接受，不做激进推断）：
 *   - 动态拼接 key（t('a.' + x)、t(`a.${x}`)）无法静态解析，不校验；
 *   - ①里拼接形式 t('a.' + x) 会跳过（检测到引号后紧跟 + 即跳过，防误报）；
 *   - 注释里的 t('key') 会被当作使用，若 key 缺失会误报——注释里别写假调用。
 *
 * 用法：node scripts/check-i18n-keys.mjs            # 门禁模式
 *       node scripts/check-i18n-keys.mjs --update-baseline  # 重新生成基线（存量债务快照）
 *
 * 基线棘轮（i18n-keys-baseline.json）：
 *   2026-10-29 首次接入时存量缺键 554 个（wf-designer 迁移 / 知识库 / 聊天等多处历史欠账），
 *   一次性补齐不现实，故采用棘轮：基线里的键 = 已知债务，不阻断；
 *   —— 新出现的缺键（不在基线）→ FAIL，防止再犯；
 *   —— 基线里已能解析的键（债务还掉了）→ FAIL，强制从基线剔除，防止债务只进不出。
 *   清理债务后跑 --update-baseline 重新固化。
 */
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const here = dirname(fileURLToPath(import.meta.url))
const srcDir = resolve(here, '../src')
const localeFiles = {
  'zh-CN': resolve(srcDir, 'locales/zh-CN.ts'),
  'en-US': resolve(srcDir, 'locales/en-US.ts'),
}

/* ---------------- 语言文件解析：递归展平为完整点路径 ---------------- */

function parseLocaleFlat(file) {
  const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)
  let root = null
  ts.forEachChild(source, (node) => {
    if (ts.isExportAssignment(node)) root = node.expression
  })
  if (!root || !ts.isObjectLiteralExpression(root)) {
    throw new Error(`无法解析 ${file}：期望 export default { ... }`)
  }
  const keys = new Set()
  const walk = (objNode, prefix) => {
    for (const prop of objNode.properties) {
      if (!ts.isPropertyAssignment(prop)) continue
      const key = prop.name.getText(source).replace(/^['"]|['"]$/g, '')
      const fullKey = prefix ? `${prefix}.${key}` : key
      if (ts.isObjectLiteralExpression(prop.initializer)) {
        walk(prop.initializer, fullKey)
      } else {
        keys.add(fullKey)
      }
    }
  }
  walk(root, '')
  return keys
}

/* ---------------- 源码收集：递归遍历目录 ---------------- */

function* walkFiles(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const st = statSync(full)
    if (st.isDirectory()) {
      yield* walkFiles(full)
    } else if (/\.(vue|ts|tsx)$/.test(entry)) {
      yield full
    }
  }
}

/* ---------------- 使用点提取 ---------------- */

/** 静态 t / $t 调用：捕获引号内 key；引号后紧跟 + 视为动态拼接，跳过。
 *  前置约束：t 前不能是标识符字符/./$（防 split( first( .t( 误匹配），
 *  $ 可选（模板裸 t( 与脚本 $t( 两种写法都要命中）。 */
const CALL_RE = /(?<![\w$.])\$?t\(\s*(['"])((?:(?!\1).)+)\1/g

/** 形似 i18n key 的字面量：点分、段内是合法标识符（用于路由 title） */
const KEY_LIKE = /^[A-Za-z][\w-]*(\.[\w-]+)+$/

function extractUsedKeys() {
  const used = new Map() // key -> 首个出处（相对路径:行号）
  const inRouterDir = (file) => relative(resolve(srcDir, 'router'), file).startsWith('..') === false

  for (const file of walkFiles(srcDir)) {
    if (file.startsWith(resolve(srcDir, 'locales'))) continue // 语言文件自身不算使用点
    const rel = relative(srcDir, file).replace(/\\/g, '/')
    const text = readFileSync(file, 'utf8')
    const isRouter = inRouterDir(file)

    if (isRouter) {
      // 路由模块：额外扫 title: 'x.y'（含多行 / 注释里的，宁多勿漏）
      const titleRe = /title:\s*(['"])([^'"\n]+)\1/g
      for (const m of text.matchAll(titleRe)) {
        if (KEY_LIKE.test(m[2]) && !used.has(m[2])) {
          used.set(m[2], `${rel}:${text.slice(0, m.index).split('\n').length}`)
        }
      }
    }

    for (const m of text.matchAll(CALL_RE)) {
      const key = m[2]
      // 动态拼接：t('a.' + x) —— 引号后是 +，跳过防误报
      const after = text.slice(m.index + m[0].length).replace(/^\s*/, '')
      if (after.startsWith('+')) continue
      if (key.includes('${')) continue // 模板串兜底（正则本身不会捕获，防御）
      if (!KEY_LIKE.test(key)) continue // t('保存') 等直接字面量不是 key
      if (!used.has(key)) {
        used.set(key, `${rel}:${text.slice(0, m.index).split('\n').length}`)
      }
    }
  }
  return used
}

/* ---------------- 主流程 ---------------- */

const BASELINE_PATH = resolve(here, 'i18n-keys-baseline.json')
const UPDATE_BASELINE = process.argv.includes('--update-baseline')

// 基线：存量缺键快照（只存 key，不存出处——出处会随代码漂移）
let baseline = new Set()
try {
  baseline = new Set(JSON.parse(readFileSync(BASELINE_PATH, 'utf8')).missing)
} catch {
  /* 首次运行没有基线文件，视为空 */
}

let failed = 0
const locales = {}
for (const [locale, file] of Object.entries(localeFiles)) {
  locales[locale] = parseLocaleFlat(file)
  console.log(`[info] ${locale}: ${locales[locale].size} 个文案键`)
}

const used = extractUsedKeys()
console.log(`[info] 代码静态使用点：${used.size} 个 key`)

const missing = [] // 全部缺键
const newMissing = [] // 不在基线里的缺键（真正的回归）
for (const [key, where] of used) {
  const absentIn = Object.entries(locales)
    .filter(([, keys]) => !keys.has(key))
    .map(([locale]) => locale)
  if (absentIn.length > 0) {
    missing.push(key)
    if (!baseline.has(key)) {
      newMissing.push(`  [FAIL] ${key}（${absentIn.join(' / ')} 缺失）—— ${where}`)
    }
  }
}

if (UPDATE_BASELINE) {
  writeFileSync(BASELINE_PATH, JSON.stringify({ missing: missing.sort() }, null, 2) + '\n', 'utf8')
  console.log(`\n[info] 基线已更新：${BASELINE_PATH}（当前缺键 ${missing.length} 个）`)
  console.log('OK：基线模式不作为门禁')
  process.exit(0)
}

if (newMissing.length > 0) {
  failed += newMissing.length
  console.log(`\n新增缺键 ${newMissing.length} 个（vue-i18n 缺 key 会原样回显成英文，基线里没有这些键）：`)
  console.log(newMissing.sort().join('\n'))
}

// 基线腐化检测：债务还掉了但没更新基线 → 强制剔除，保证棘轮只紧不松
const stale = [...baseline].filter((k) => !missing.includes(k))
if (stale.length > 0) {
  console.log(`\n[FAIL] 基线里 ${stale.length} 个键已能解析（债务已还），请更新基线：`)
  console.log('  node scripts/check-i18n-keys.mjs --update-baseline')
  failed += stale.length
}

const knownDebt = missing.length - newMissing.length
console.log(`\n[info] 存量缺键（基线内，不阻断）：${knownDebt} 个；全部使用点 ${used.size} 个`)

// 信息项：语言文件里疑似没人用的键（动态 key 会导致误报，只提示不阻断）
const usedKeys = new Set(used.keys())
const unused = [...locales['zh-CN']].filter((k) => !usedKeys.has(k))
console.log(`[info] 语言文件中未在静态扫描中出现的键：${unused.length} 个（含动态用法，仅提示不阻断；如需清理由人工确认）`)

if (failed > 0) {
  console.log(`\n共 ${failed} 类问题：新增缺键请补齐 locales/zh-CN.ts 与 en-US.ts（并同步更新基线）；基线腐化请跑 --update-baseline。`)
  process.exit(1)
}
console.log('\nOK：无新增缺键；代码使用的 i18n key（除基线存量外）在中英文语言文件中全部存在')
