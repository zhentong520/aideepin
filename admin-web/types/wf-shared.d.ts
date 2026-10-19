/**
 * admin-web 的 wf 模块（T9-09 已确认与 user-web 保持双份同步）拷贝自 user-web，
 * 该模块依赖 user-web src/typings/ 下的全局命名空间声明。
 * 本文件按 user-web 原文镜像声明 wf 模块实际用到的类型。
 * ⚠️ 修改时必须与 user-web/src/typings/ 同步。
 */

declare namespace Chat {
  interface ChatMessage {
    uuid: string | ''
    contentType: number // 2: text, 3: audio
    createTime: string
    thinkingContent: string //思考过程
    remark: string
    audioUuid: string
    audioUrl: string
    audioDuration: number // in seconds
    messageRole?: number
    children: ChatMessage[] //AI回复的消息
    aiModelPlatform?: string
    attachmentUrls: string[]
    isRefMemoryEmbedding: boolean //是否引用记忆向量
    isRefEmbedding: boolean //是否是引用知识库向量
    isRefGraph: boolean //是否是引用知识库图谱

    //Frontend only
    inversion?: boolean
    error?: boolean
    thinking?: boolean //是否正在思考
    loading?: boolean
    audioPlayState: AudioPlayState
    state?: Map<string, string> //消息状态描述

    //Token observability
    inputTokens?: number
    outputTokens?: number
    duration?: number

    //MCP tool call observability
    toolCalls?: { toolName: string; durationMs: number; success: boolean }[]
  }

  //会话关联的知识库信息
  interface CharacterKnowledge {
    id: string
    uuid: string
    title: string
    isMine: boolean
    isPublic: boolean
    kbInfo: KnowledgeBase.Info
    isEnable: boolean //该知识库是否可用
  }

  interface ConfigVoice {
    param_name: string // 用于API请求的参数名称
    model: string
    platform: string
  }

  interface AudioConfig {
    voice: ConfigVoice
  }

  interface Character {
    title: string
    uuid: string
    remark: string
    aiSystemMessage: string
    understandContextEnable: boolean
    loadedAll: boolean
    loadedFirstPageMsg: boolean
    minMsgUuid?: string | ''
    mcpIds: string[]
    kbIds: string[] // 关联的知识库ID
    skillIds: number[] // 绑定的技能ID
    characterKnowledgeList: CharacterKnowledge[] //关联的知识库包装信息
    answerContentType: number // 1: auto, 2: text, 3: audio
    isAutoplayAnswer: boolean //聊天时音频类型的响应内容是否自动播放
    isEnableThinking: boolean //是否启用思考过程
    isEnableWebSearch: boolean //是否启用网络搜索
    audioConfig: AudioConfig //语音配置
  }

  interface Draw {
    id?: number
    uuid: string
    prompt: string
    aiModelName: string
    originalImageUuid?: string
    originalImageUrl: string
    maskImageUuid?: string
    maskImageUrl: string
    interactingMethod: number
    processStatus: number //1:processing,2:fail,3:success
    processStatusRemark: string

    aiModelPlatform: string
    //绘图成功后生成的图片
    imageUuids: string[]
    imageUrls: string[]
    createTime: string
    isPublic: boolean
    isStar: boolean
    starCount: number
    userUuid: string
    userName: string
    dynamicParams: any
    duration?: number
  }
}

declare namespace User {
  interface UserQuota {
    tokenByDay: number
    tokenByMonth: number
    requestTimesByDay: number
    requestTimesByMonth: number
    drawByDay: number
    drawByMonth: number
  }

  interface QuotaCost {
    freeTokenCost: {
      todayTokenCost: number
      monthTokenCost: number
    }
    freeRequestTimes: {
      todayRequestTimes: number
      monthRequestTimes: number
    }
    freeDrawTimes: {
      todayDrawTimes: number
      monthDrawTimes: number
    }
    paidTokenCost: {
      todayTokenCost: number
      monthTokenCost: number
    }
    paidRequestTimes: {
      todayRequestTimes: number
      monthRequestTimes: number
    }
    paidDrawTimes: {
      todayDrawTimes: number
      monthDrawTimes: number
    }
  }

  interface Config {
    secretKey?: string
    locale?: string
    userQuota: UserQuota
    quotaCost: QuotaCost
  }
}

declare namespace Mcp {
  interface McpPresetParam {
    name: string
    title: string
    value: any
    require_encrypt: boolean
    enctrypted: boolean
  }

  interface CustomizedParamDefinition {
    //接口定义字段
    name: string
    title: string
    require_encrypt: boolean

    //前端增加字段
    value: any
    enctrypted: boolean
  }

  interface McpInfo {
    id: string
    uuid: string
    title: string
    //sse | streamable_http | stdio
    transportType: string
    sseUrl: string
    sseTimeout: number
    stdioCommand: string
    stdioArg: string
    presetParams: McpPresetParam[]
    customizedParamDefinitions: CustomizedParamDefinition[]
    installType: string
    website: string
    remark: string
    isEnable: boolean

    // 前端增加字段
    // 当前用户是否配置了该MCP
    configured: boolean
  }

  interface McpCustomizedParam {
    //与McpInfo.customizedParamDefinitions中的name一一对应
    name: string
    value: any
    enctrypted: boolean
  }

  interface UserMcp {
    id: string
    uuid: string
    userId: string
    mcpId: string
    mcpCustomizedParams: McpCustomizedParam[]
    isEnable: boolean
    mcpInfo: McpInfo
  }
}

declare namespace KnowledgeBase {
  interface Info {
    id: string
    uuid: string
    title: string
    remark: string
    isPublic: boolean
    isStrict: boolean
    starCount: number
    ownerUuid: string
    ownerName: string
    loadingRecords?: boolean
    itemCount: number
    embeddingCount: number
    ingestMaxOverlap: number
    ingestSplitStrategy: string
    ingestMaxSegmentSize: number
    ingestCustomSeparator: string
    ingestModelId: string
    ingestTokenEstimator: string
    retrieveMaxResults: number
    retrieveMinScore: number
    queryLlmTemperature: number
    querySystemMessage: string
    /** 知识库分类 id，0=未分类 */
    categoryId?: number

    ingestModelName: string
  }

  interface InfoListResp {
    total: number
    records: Info[]
  }
}

// ===== 以下与 user-web/src/typings/global.d.ts 同步 =====

interface AuthState {
  token: string
  showLoginModal: boolean
}

interface AiModelInfo {
  //from api
  modelId: string
  modelName: string
  modelTitle: string
  modelPlatform: string
  enable: boolean
  isFree: boolean
  /**
   * Model usage type: 'text' | 'image' | 'vision' | 'embedding' | 'rerank' | 'asr' | 'tts'.
   * Mirrors backend ai_model.type; use this (not inputTypes) to filter by purpose —
   * many text models support image input and would be wrongly excluded by inputTypes.
   */
  type: string
  inputTypes: string
  isReasoner: boolean
  isThinkingClosable: boolean
  isSupportWebSearch: boolean
  properties: object
  healthStatus?: string
  healthReason?: string

  //for NSelector
  value: string
  key: string
  label: string
  disabled: boolean
}

interface AudioPlayState {
  audioUrl: string
  audioUuid: string // UUID of the audio file
  playDuration: number
  playing: boolean
  audio: any // Audio object
  text: string // The text content corresponding to the audio
  showText: boolean // Whether to show the text

  msgPart: string // 聊天时不断接收到的消息片段，即时播放时使用
  audioFrame: string // 聊天时不断接收到的音频片段(已经过base64编码)，即时播放时使用
}

interface TtsSetting {
  synthesizer_side: string // TTS synthesizer, eg: client or server
  model_name: string
  platform: string // TTS model platform
}

interface AsrSetting {
  model_name: string
  platform: string
  max_record_duration: number // Maximum recording duration in seconds
  max_file_size: number // Maximum file size in bytes
}

interface ModelVoice {
  name: string
  remark: string
  param_name: string
  lang: string
}

interface SysConfigInfo {
  ttsSetting: TtsSetting
  asrSetting: AsrSetting
  availableVoices: ModelVoice[]
  defaultLocale: string
}
