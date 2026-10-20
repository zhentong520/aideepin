import { t } from '@/locales'

// 模型厂商 / 模型类型 / MCP / 音频合成等业务枚举（自 utils/constants.ts 迁出，T5-10）

export function getDefaultModelPlatforms() {
  return [
    {
      label: t('constants.openai'),
      value: 'openai',
    },
    {
      label: t('constants.dashscope'),
      value: 'dashscope',
    },
    {
      label: t('constants.qianfan'),
      value: 'qianfan',
    },
    {
      label: t('constants.ollama'),
      value: 'ollama',
    },
    {
      label: t('constants.deepseek'),
      value: 'deepseek',
    },
    {
      label: t('constants.siliconflow'),
      value: 'siliconflow',
    },
  ]
}

export function getModelTypes() {
  return [
    {
      label: t('constants.text'),
      value: 'text',
    },
    {
      label: t('constants.image'),
      value: 'image',
    },
    {
      label: t('constants.embedding'),
      value: 'embedding',
    },
    {
      label: t('constants.rerank'),
      value: 'rerank',
    },
    {
      label: t('constants.tts'),
      value: 'tts',
    },
    {
      label: t('constants.asr'),
      value: 'asr',
    },
  ]
}

export function getModelInputTypes() {
  return [
    {
      label: t('constants.text'),
      value: 'text',
    },
    {
      label: t('constants.image'),
      value: 'image',
    },
    {
      label: t('constants.audio'),
      value: 'audio',
    },
    {
      label: t('constants.video'),
      value: 'video',
    },
  ]
}

export function getModelResponseFormatTypes() {
  return [
    {
      label: t('constants.text'),
      value: 'text',
    },
    {
      label: t('constants.json'),
      value: 'json_object',
    },
  ]
}

export function getMcpTransportType() {
  return [
    {
      label: t('constants.sse'),
      value: 'sse',
    },
    {
      label: t('constants.streamableHttp'),
      value: 'streamable_http',
    },
    {
      label: t('constants.stdio'),
      value: 'stdio',
    },
  ]
}

export function getMcpInstallType() {
  return [
    {
      label: t('constants.docker'),
      value: 'docker',
    },
    {
      label: t('constants.local'),
      value: 'local',
    },
    {
      label: t('constants.remote'),
      value: 'remote',
    },
    {
      label: t('constants.wasm'),
      value: 'wasm',
    },
  ]
}

export function getSynthesizerSide() {
  return [
    {
      label: t('constants.client'),
      value: 'client',
    },
    {
      label: t('constants.server'),
      value: 'server',
    },
  ]
}

export function getYesNo() {
  return [
    {
      label: t('constants.yes'),
      value: true,
    },
    {
      label: t('constants.no'),
      value: false,
    },
  ]
}
