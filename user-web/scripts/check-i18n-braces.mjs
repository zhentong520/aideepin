#!/usr/bin/env node
/**
 * i18n 文案守卫（admin-web）
 * =============================================
 * 做两件事，任一不过即 exit 1（阻断发布）：
 *   ① **致命花括号**：文案里写死字面花括号（如 {中文}、{{input}}）在生产模式渲染会抛
 *      SyntaxError，导致属性面板整体渲染失败。约定是文案只写 ASCII 插值名，
 *      真实文本放 src/views/workflow/components/braceLiteral.ts 并用 t(key, braceLiteral) 传入。
 *   ② **中英文键对齐**：某语言缺键时，fallbackLocale='en-US' 会让界面静默显示另一种语言，
 *      不报错、肉眼难发现。整体替换某段 i18n 曾静默吃掉过原有键，故必须门禁。
 *
 * 实现说明：用 TypeScript 编译器 API 解析 AST，而不是正则。
 * 正则方案已踩过三个坑（都会造成「假绿」或误报）：
 *   - prettier 折行有「值折行」和「键值分离」两种形态；
 *   - 双引号文案里常含单引号（"the client's"），按引号计数判断闭合会误判；
 *   - 不同段里存在同名键（如 mcp/model 都有 titlePlaceholder），按段内单键提取会互相覆盖。
 * AST 解析对以上情况都免疫。
 *
 * 用法：node scripts/check-i18n-braces.mjs
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createI18n } from 'vue-i18n'
import ts from 'typescript'

const here = dirname(fileURLToPath(import.meta.url))
const files = {
  'zh-CN': resolve(here, '../src/locales/zh-CN.ts'),
  'en-US': resolve(here, '../src/locales/en-US.ts'),
}

/** 用 TS AST 解析出「段 -> (key -> 文案)」，只取字符串字面量 */
function parseLocale(file) {
  const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)

  let root = null
  ts.forEachChild(source, (node) => {
    // export default { ... }
    if (ts.isExportAssignment(node)) root = node.expression
  })
  if (!root || !ts.isObjectLiteralExpression(root)) {
    throw new Error(`无法解析 ${file}：期望 export default { ... }`)
  }

  const messages = new Map()
  const sections = new Set()

  for (const sectionProp of root.properties) {
    if (!ts.isPropertyAssignment(sectionProp)) continue
    const sectionName = sectionProp.name.getText(source)
    const sectionInit = sectionProp.initializer
    if (!ts.isObjectLiteralExpression(sectionInit)) continue
    sections.add(sectionName)

    for (const itemProp of sectionInit.properties) {
      if (!ts.isPropertyAssignment(itemProp)) continue
      const key = itemProp.name.getText(source)
      const init = itemProp.initializer
      if (!ts.isStringLiteral(init) && !ts.isNoSubstitutionTemplateLiteral(init)) continue
      const value = init.text
      const fullKey = `${sectionName}.${key}`
      // 同名键在不同段很常见，用换行拼接而不是覆盖，避免后写覆盖前写
      messages.set(fullKey, messages.has(fullKey) ? `${messages.get(fullKey)}\n${value}` : value)
    }
  }
  return { messages, sections }
}

// 生产模式：让 vue-i18n 走「编译失败即抛错」的路径，而非开发模式的告警 + 回退
process.env.NODE_ENV = 'production'

let broken = 0
const parsed = {}

for (const [locale, file] of Object.entries(files)) {
  parsed[locale] = parseLocale(file)
  const { messages, sections } = parsed[locale]

  // ① 致命花括号：语法层预检，不依赖 vue-i18n 的运行时行为
  const withBrace = [...messages].filter(([, v]) => v.includes('{'))
  for (const [key, value] of withBrace) {
    const nested = value.match(/\{\{[^}]*\}\}/)
    if (nested) {
      broken++
      console.log(`[FAIL] ${locale} ${key}: 嵌套花括号 ${nested[0]}（生产模式渲染时抛 SyntaxError）`)
      console.log('       修法：文案写 ASCII 插值名，真实文本放 braceLiteral.ts，以 t(key, braceLiteral) 传入')
      continue
    }
    // 花括号内必须是单个合法标识符；允许 {n} 这类正常插值
    for (const token of value.match(/\{[^}]*\}/g) || []) {
      if (!/^\{\s*[\w$]+\s*\}$/.test(token)) {
        broken++
        console.log(`[FAIL] ${locale} ${key}: 花括号内不是合法标识符 ${token}（如中文 {参数名}）`)
        break
      }
    }
  }

  // 交叉验证：交给 vue-i18n 真实编译一次，双保险
  const i18n = createI18n({
    legacy: false,
    locale,
    messages: { [locale]: Object.fromEntries(messages) },
    missingWarn: false,
    fallbackWarn: false,
  })
  for (const [key] of withBrace) {
    try {
      i18n.global.t(key)
    } catch (e) {
      broken++
      console.log(`[FAIL] ${locale} ${key}: ${e.constructor.name} — ${e.message}`)
    }
  }
  console.log(`[info] ${locale}: ${sections.size} 段 / ${messages.size} 文案，含花括号 ${withBrace.length} 条，已全部检查`)
}

// ② 中英文键对齐
const zhKeys = new Set(parsed['zh-CN'].messages.keys())
const enKeys = new Set(parsed['en-US'].messages.keys())
const zhMissingInEn = [...zhKeys].filter((k) => !enKeys.has(k)).sort()
const enMissingInZh = [...enKeys].filter((k) => !zhKeys.has(k)).sort()

if (zhMissingInEn.length > 0) {
  broken++
  console.log(`\n[FAIL] en-US 缺少 ${zhMissingInEn.length} 个文案键：\n  ${zhMissingInEn.join('\n  ')}`)
}
if (enMissingInZh.length > 0) {
  broken++
  console.log(`\n[FAIL] zh-CN 缺少 ${enMissingInZh.length} 个文案键：\n  ${enMissingInZh.join('\n  ')}`)
}
console.log(`[info] 中英文键比对：zh-CN ${zhKeys.size} / en-US ${enKeys.size}（缺失 ${zhMissingInEn.length + enMissingInZh.length}）`)

if (broken > 0) {
  console.log(`\n发现 ${broken} 类问题，请修复后重新构建。`)
  process.exit(1)
}
console.log('\nOK：文案无致命花括号且中英文键完全对齐')
