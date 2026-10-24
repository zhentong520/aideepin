import DOMPurify from 'dompurify'

// 与任务书 T0-02 标准实现一致：
// - ALLOWED_TAGS 白名单：只放行展示类标签（含 <br> 供 i18n 文案换行）
// - ALLOWED_URI_REGEXP：协议白名单（https/mailto/tel）+ 相对路径放行，剥离 javascript: 等危险协议。
//   OPT-13 修正（2026-10-24）：旧正则 /^(?:https?:|mailto:|tel:|#|\/)/i 会把所有「裸相对路径」
//   （如 src="avatar.png"、href="page.html"）整段剥掉（vitest 冒烟实测 <img src=x> → <img>），
//   改为 DOMPurify 官方默认形状：字母开头的无冒号串（相对路径）放行，带冒号的必须命中协议白名单。
const ALLOWED_URI = /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i

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
