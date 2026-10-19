#!/usr/bin/env node
/**
 * T0-02 sanitize 行为自测（node 脚本）
 * 断言：
 *   ① onerror 属性被清洗
 *   ② javascript: 协议被剥离
 *   ③ <script> 标签被移除
 *   ④ i18n 文案依赖的 <br> 被保留
 *   ⑤ 合法链接（https / 相对路径）不被误伤
 */
import { JSDOM } from 'jsdom'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)

// DOMPurify 需要 window / document
const dom = new JSDOM('<!doctype html><html><body></body></html>')
globalThis.window = dom.window
globalThis.document = dom.window.document

// sanitize.ts 是 TS 源码，Node 22 默认类型剥离可直接加载
const { safeHtml } = await import(pathToFileURL(resolve(here, '../src/utils/sanitize.ts')).href)

let failed = 0
function check(name, cond, detail = '') {
  if (cond) {
    console.log(`[PASS] ${name}`)
  } else {
    failed++
    console.log(`[FAIL] ${name} ${detail}`)
  }
}

// ① onerror 被清洗
const r1 = safeHtml('<img src=x onerror=alert(1)>')
check('onerror 属性被清洗', !r1.includes('onerror'), `实际输出: ${r1}`)

// ② javascript: 协议被剥离（等价于 markdown 链接 [x](javascript:alert(1)) 渲染后的 HTML）
const r2 = safeHtml('<a href="javascript:alert(1)">x</a>')
check('javascript: 协议被剥离', !r2.includes('javascript:'), `实际输出: ${r2}`)

// ③ <script> 被移除
const r3 = safeHtml('<script>alert(1)</script>ok')
check('<script> 标签被移除', !r3.includes('<script') && r3.includes('ok'), `实际输出: ${r3}`)

// ④ i18n 文案的 <br> 保留
const r4 = safeHtml('第一行<br>第二行')
check('<br> 被保留', r4.includes('<br'), `实际输出: ${r4}`)

// ⑤ 合法链接不误伤
const r5 = safeHtml('<a href="https://example.com" rel="noopener noreferrer nofollow">x</a>')
check('https 链接保留', r5.includes('https://example.com'), `实际输出: ${r5}`)
const r6 = safeHtml('<a href="/foo">x</a>')
check('相对路径链接保留', r6.includes('href="/foo"'), `实际输出: ${r6}`)

if (failed > 0) {
  console.log(`\n自测失败 ${failed} 项`)
  process.exit(1)
}
console.log('\nsanitize 自测全部通过')
