// katex 样式的独立异步入口：让 vite/rollup 把 katex.min.css 连同其字体
// 一起切到按需加载的 chunk，而不是被提升进首屏。
import 'katex/dist/katex.min.css'
