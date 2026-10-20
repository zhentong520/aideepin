/**
 * T4-01 design token 单一来源
 *
 * 消费方式：
 * - TS/Vue：import { tokens, brandScale } from 'aideepin-tokens'
 * - tailwind.config.js：require('../packages/aideepin-tokens/src/index.ts')（jiti 加载，支持 TS）
 * - CSS 变量：import 'aideepin-tokens/tokens.css'（由 scripts/build-tokens.ts 生成）
 *
 * ⚠️ 修改任何值后需重新执行 `node scripts/build-tokens.ts` 重新生成 dist/tokens.css
 */

export const tokens = {
  light: {
    primary: '#2d8cf0',
    success: '#19be6b',
    warning: '#f0a020',
    danger: '#d03050',
    info: '#2563eb',
    // 危险色作为正文文本时使用（#d03050 对白底仅 4.1:1，不达 WCAG AA；T4-04）
    'danger-text': '#bb2a44',
    bg: '#ffffff',
    'bg-subtle': '#f6f8fa',
    border: '#e5e6eb',
    text: '#1f2328',
    // text-muted 对白底 ≥4.5:1（T4-04：#6e7781 仅 4.38:1，加深为 #5c6773 = 5.8:1）
    'text-muted': '#5c6773',
  },
  dark: {
    primary: '#5ecfff',
    success: '#3fb950',
    warning: '#d29922',
    danger: '#f85149',
    info: '#58a6ff',
    'danger-text': '#ff7b72',
    bg: '#0d1117',
    'bg-subtle': '#161b22',
    border: '#30363d',
    text: '#e6edf3',
    'text-muted': '#8b949e',
  },
} as const

// 主色阶（HSL 派生，主色 = 蓝 #2d8cf0）
export const brandScale = {
  50: '#f1f7fe',
  100: '#deedfd',
  200: '#bcdbfa',
  300: '#92c3f7',
  400: '#67acf4',
  500: '#2d8cf0',
  600: '#1077e4',
  700: '#0e63be',
  800: '#0b4f98',
  900: '#083c72',
} as const

export const radius = { sm: '4px', md: '8px', lg: '12px' } as const

// ⚠️ 禁 10/13/30px 等非档位间距；Tailwind 侧以 s{px} 命名键消费（p-s12 / gap-s24）
export const space = [4, 8, 12, 16, 24, 32, 48] as const

export const shadow = {
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
} as const

export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1100,
  overlay: 1200,
  modal: 1300,
  toast: 1400,
} as const

// 断点统一（T4-07）：与 Tailwind 默认一致，显式声明为单一来源
export const breakpoints = {
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px',
} as const

/**
 * T4-06：CodeEditor 高亮主题（Primer 色 → token）
 * light = GitHub Light，dark = GitHub Dark（暗色下编辑器自动融合，T4-04）
 */
export const codeTheme = {
  light: {
    bg: '#f6f8fa',
    border: '#e5e6eb',
    text: '#1f2328',
    caret: '#1f2328',
    selection: '#cfe3ff',
    'bg-subtle': '#eef1f4',
    meta: '#8250df',
    head: '#0550ae',
    bold: '#1f2328',
    code: '#0a3069',
    link: '#0969da',
    bullet: '#cf222e',
    quote: '#6e7781',
    str: '#0a3069',
    key: '#0550ae',
    num: '#0550ae',
    kw: '#cf222e',
    fn: '#8250df',
    var: '#953800',
    com: '#6e7781',
  },
  dark: {
    bg: '#161b22',
    border: '#30363d',
    text: '#e6edf3',
    caret: '#e6edf3',
    selection: '#264f78',
    'bg-subtle': '#21262d',
    meta: '#d2a8ff',
    head: '#79c0ff',
    bold: '#e6edf3',
    code: '#a5d6ff',
    link: '#58a6ff',
    bullet: '#f85149',
    quote: '#8b949e',
    str: '#a5d6ff',
    key: '#79c0ff',
    num: '#79c0ff',
    kw: '#ff7b72',
    fn: '#d2a8ff',
    var: '#ffa657',
    com: '#8b949e',
  },
} as const
