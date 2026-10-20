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
