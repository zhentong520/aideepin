export const TOKEN_ESTIMATOR = [
  { label: 'Openai', value: 'openai' },
  { label: '通义千问', value: 'qwen' },
  { label: 'Huggingface', value: 'huggingface' },
]

export const SPLIT_STRATEGY = [
  { label: '递归分段', value: 'recursive' },
  { label: '中文分句', value: 'sentence_cn' },
  { label: '按段落', value: 'paragraph' },
  { label: '按行', value: 'line' },
  { label: '按句', value: 'sentence' },
  { label: '自定义分隔符', value: 'custom' },
]
export const OPENAI_IMAGE_SIZE_OPTIONS = [
  { label: '方(1024x1024)', value: '1024x1024' },
  { label: '竖屏(1024x1536)', value: '1024x1536' },
  { label: '横屏(1536x1024)', value: '1536x1024' },
]
export const OPENAI_IMAGE_QUALITY_OPTIONS = [
  { label: '低', value: 'low' },
  { label: '中', value: 'medium' },
  { label: '高', value: 'high' },
]
export const TONGYI_WANX_SIZE_OPTIONS = [
  { label: '方(1024x1024)', value: '1024*1024' },
  { label: '竖屏(720x1280)', value: '720*1280' },
  { label: '横屏(1280X720)', value: '1280*720' },
]
export const RESPONSE_SHOW_TYPE_OPTIONS = [
  { label: '自动', value: 1 },
  { label: '文本', value: 2 },
  { label: '音频(自动播放)', value: 3 },
  { label: '音频(不自动播放)', value: 4 },
]
export const CHAT_MESSAGE_CONTENT_TYPE = {
  auto: 1, // 自动
  text: 2, // 文本
  audio: 3, // 音频
}
/**
 * 音频合成位置：客户端(web)或服务端
 */
export const AUDIO_SYNTHESIZER_SIDE = {
  client: 'client',
  server: 'server',
}
