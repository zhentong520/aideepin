/**
 * OPT-16：聊天消息内容类型常量（原 admin wf/utils/constant/token.ts、user utils/constant/chat.ts
 * 各存一份、定义逐字节一致 → 单一来源迁入共享包，两端原文件改转发）。
 */
export const CHAT_MESSAGE_CONTENT_TYPE = {
  auto: 1, // 自动
  text: 2, // 文本
  audio: 3, // 音频
}
