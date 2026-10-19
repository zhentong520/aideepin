// T2-05：katex 字体懒加载。
// katex.min.css 的 @font-face 引用 59 个字体文件（首屏约 200KB），
// 全局引入会让无公式场景也付出字体加载成本，改为渲染到公式时按需加载。
let loaded = false
let loading: Promise<void> | null = null

export async function ensureKatexStyle() {
  if (loaded)
    return
  if (!loading) {
    // 通过包装模块动态加载，避免 vite 把 css 提升为首屏资源
    loading = import('./katexStyle').then(() => {
      loaded = true
    })
  }
  await loading
}

/** 检测文本是否可能含 LaTeX 公式（$...$、$$...$$、\(...\)、\[...\]） */
export function mayContainFormula(text: string) {
  return text.includes('$') || text.includes('\\(') || text.includes('\\[')
}
