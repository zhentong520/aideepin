import DOMPurify from 'dompurify'

// 与任务书 T0-02 标准实现一致：
// - ALLOWED_TAGS 白名单：只放行展示类标签（含 <br> 供 i18n 文案换行）
// - ALLOWED_URI_REGEXP：限制 https/mailto/tel/#/相对路径，剥离 javascript: 等危险协议
const ALLOWED_URI = /^(?:https?:|mailto:|tel:|#|\/)/i

export function safeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'a', 'b', 'i', 'em', 'strong', 'code', 'pre', 'br', 'p', 'ul', 'ol', 'li',
      'h1', 'h2', 'h3', 'h4', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'span', 'div', 'sup', 'sub', 'img',
    ],
    ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'class', 'src', 'alt', 'width', 'height'],
    ALLOWED_URI_REGEXP: ALLOWED_URI,
  })
}
