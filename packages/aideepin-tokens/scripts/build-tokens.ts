/**
 * T4-01 buildTokens()：把 light/dark tokens 输出为 :root + html.dark 的 CSS 变量文件
 * 运行：node scripts/build-tokens.ts（Node ≥22.6 原生支持 TS strip types）
 * 产物：dist/tokens.css（两端 import 'aideepin-tokens/tokens.css'）
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { tokens, brandScale, radius, space, shadow, zIndex, codeTheme } from '../src/index.ts'

const kebab = (s: string) => s.replace(/[A-Z]/g, m => `-${m.toLowerCase()}`)

const block = (entries: [string, string][], indent = '  ') =>
  entries.map(([k, v]) => `${indent}--adi-${kebab(k)}: ${v};`).join('\n')

const colorEntries = (obj: Record<string, string>) => Object.entries(obj) as [string, string][]

const rootVars = [
  block(colorEntries(tokens.light)),
  '',
  block(colorEntries(brandScale).map(([k, v]) => [`brand-${k}`, v])),
  '',
  block(Object.entries(radius).map(([k, v]) => [`radius-${k}`, v])),
  '',
  block(space.map(px => [`space-s${px}`, `${px}px`])),
  '',
  block(Object.entries(shadow).map(([k, v]) => [`shadow-${k}`, v])),
  '',
  block(Object.entries(zIndex).map(([k, v]) => [`z-${k}`, v])),
  '',
  block(colorEntries(codeTheme.light).map(([k, v]) => [`code-${k}`, v])),
].join('\n')

const darkVars = [
  block(colorEntries(tokens.dark)),
  '',
  block(colorEntries(codeTheme.dark).map(([k, v]) => [`code-${k}`, v])),
].join('\n')

const css = `/* 自动生成（T4-01）：node scripts/build-tokens.ts —— 勿手改，改 src/index.ts 后重新生成 */
:root {
${rootVars}
}

html.dark {
${darkVars}
}
`

const outFile = resolve(dirname(fileURLToPath(import.meta.url)), '../dist/tokens.css')
mkdirSync(dirname(outFile), { recursive: true })
writeFileSync(outFile, css, 'utf8')
console.log(`[aideepin-tokens] generated ${outFile}`)
