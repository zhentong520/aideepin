/**
 * 转义 HTML 字符
 * @param source
 */
export function encodeHTML(source: string) {
  return source
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 判断是否为代码块
 * @param text
 */
export function includeCode(text: string | null | undefined) {
  const regexp = /^(?:\s{4}|\t).+/gm
  return !!(text?.includes(' = ') || text?.match(regexp))
}

/**
 * 复制文本
 * 坑（2026-10-03 修）：原实现有三处问题 ——
 *   1) `if (document.execCommand('copy')) document.execCommand('copy')` 会执行两次复制，第一次返回值被丢弃；
 *   2) 无返回值，调用方无法反馈，用户点「复制」后完全不知道是否成功；
 *   3) 只用 execCommand，而该 API 已被浏览器标记废弃，在新版 Chrome/Edge 上可能直接失败或需要用户手势。
 * 现在改为：优先使用 Clipboard API（现代浏览器标准），失败/不可用时降级 execCommand，
 * 返回 Promise<boolean> 供调用方给出明确成功/失败提示。
 * @param options
 * @returns 是否复制成功
 */
export async function copyText(options: { text: string; origin?: boolean }): Promise<boolean> {
  const { text } = options
  if (!text)
    return false

  // 首选 Clipboard API
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 非安全上下文 / 无用户手势 / 权限被拒 → 走下面的降级方案
  }

  // 降级：execCommand（临时元素需隐藏，否则复制瞬间会在页面闪一下）
  const props = { origin: true, ...options }
  const input: HTMLInputElement | HTMLTextAreaElement = props.origin
    ? document.createElement('textarea')
    : document.createElement('input')
  input.setAttribute('readonly', 'readonly')
  input.value = text
  input.style.position = 'fixed'
  input.style.top = '0'
  input.style.left = '0'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(input)
  return ok
}

/**
 * 格式化毫秒时长为可读字符串
 * Format milliseconds duration to human-readable string
 * @param ms 毫秒数 | duration in milliseconds
 */
export function formatDuration(ms: number | undefined | null): string {
  if (ms == null || ms === 0)
    return ''
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}
