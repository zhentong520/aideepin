/**
 * OPT-16：空模型工厂集合（原 admin wf/utils/functions/index.ts、user utils/functions/empty.ts
 * 两端同源实现，19 个函数逐字节一致 → 单一来源迁入共享包；两端原文件改转发出口）。
 *
 * 外部依赖处理（零外部包依赖铁律）：
 * - date-fns format(new Date(), 'yyyy-MM-dd HH:mm:ss') → 本包 utils/datetime 的 nowDateTime()（输出逐字符等价）
 * - @vue-flow/core Edge/Node（emptyUiWorkflow，两端均零消费的死导出）→ 不迁，留在 admin 原文件
 * - CHAT_MESSAGE_CONTENT_TYPE → 本包 constant/chat-message-content-type.ts
 *
 * 全局命名空间类型（Workflow./Chat./User./Mcp./AudioPlayState 等）来自两端各自的
 * ambient d.ts（admin types/*.d.ts、user src/typings/*.d.ts），两端 tsconfig 均已 include。
 */

import { CHAT_MESSAGE_CONTENT_TYPE } from '../constant/chat-message-content-type'
import { nowDateTime } from '../utils/datetime'

// ========== 知识库 ==========

export function knowledgeBaseEmptyInfo() {
  return {
    id: '0',
    uuid: '',
    title: '',
    remark: '',
    isPublic: false,
    isStrict: true,
    starCount: 0,
    ownerUuid: '',
    ownerName: '',
    itemCount: 0,
    embeddingCount: 0,
    ingestMaxOverlap: 0,
    ingestSplitStrategy: 'sentence_cn',
    ingestMaxSegmentSize: 512,
    ingestCustomSeparator: '',
    ingestModelId: '0',
    ingestTokenEstimator: '',
    retrieveMaxResults: 0,
    retrieveMinScore: 0,
    queryLlmTemperature: 0,
    querySystemMessage: '',

    ingestModelName: '',
  }
}

export function knowledgeBaseEmptyItem() {
  return {
    id: '0',
    uuid: '',
    kbId: '0',
    kbUuid: '',
    title: '',
    brief: '',
    remark: '',
    embeddingStatus: 'NONE',
    graphicalStatus: 'NONE',
    embeddingStatusChangeTime: '',
    graphicalStatusChangeTime: '',
    embeddingHitCount: 0,
    graphHitCount: 0,
    wordCount: 0,
    isEnabled: true,
    enabledChangeTime: '',
    segmentMode: 'text',
    childMaxChunkSize: 200,
    sourceFileName: '',
    sourceFileUrl: '',
    sourceFileUuid: '',
    createTime: '',
    updateTime: '',
  }
}

export function knowledgeBaseEmptyRecord() {
  return {
    id: '0',
    uuid: '',
    kbId: '0',
    kbUuid: '',
    question: '',
    answer: '',
    createTime: nowDateTime(),
    aiModelPlatform: '',
    loading: false,
    error: false,
  }
}

// ========== AI 模型 / 绘图 ==========

export function emptyAiModel() {
  return {
    // from api
    modelId: '',
    modelName: '',
    modelTitle: '',
    modelPlatform: '',
    enable: false,
    isFree: false,
    type: 'text',
    inputTypes: 'text',
    isReasoner: false,
    isThinkingClosable: false,
    isSupportWebSearch: false,
    properties: {} as object,

    // for NSelector
    value: '',
    key: '',
    label: '',
    disabled: false,
  }
}

export function emptyDraw() {
  return {
    id: 0,
    uuid: '',
    prompt: '',
    createTime: nowDateTime(),
    interactingMethod: 1,
    processStatus: 1,
    processStatusRemark: '',
    originalImageUuid: '',
    originalImageUrl: '',
    maskImageUuid: '',
    maskImageUrl: '',
    imageUuids: [],
    imageUrls: [],
    isPublic: false,
    isStar: false,
    aiModelName: '',
    aiModelPlatform: '',
    starCount: 0,
    userUuid: '',
    userName: '',
    dynamicParams: {} as any,
    duration: 0,
  }
}

// ========== 工作流 ==========

export function emptyWorkflowInfo() {
  return {
    id: '',
    uuid: '',
    title: '',
    remark: '',
    createTime: nowDateTime(),
    input: {} as any,
    output: {} as any,
    isPublic: false,
    userId: '',
    userUuid: '',
    userName: '',
    nodes: [] as Workflow.WorkflowNode[],
    edges: [] as Workflow.WorkflowEdge[],
    deleteNodes: [],
    deleteEdges: [],
  }
}

export function emptyWorkflowNode() {
  return {
    id: '',
    uuid: '',
    workflowId: '',
    workflowComponentId: '',
    title: '',
    remark: '',
    inputConfig: { user_inputs: [], ref_inputs: [] } as any,
    nodeConfig: {} as any,
    outputConfig: {} as any,
    positionX: 0,
    positionY: 0,

    workflowUuid: '',
    wfComponent: {} as any,
    sourceHandleIds: [],
    targetHandleIds: [],
  }
}

export function emptyWfRuntime() {
  return {
    id: '',
    uuid: '',
    workflowId: '',
    createTime: nowDateTime(),
    status: 1,
    statusRemark: '',
    input: {} as any,
    output: {} as any,
    nodes: [] as Workflow.WfRuntimeNode[],

    workflowUuid: '',
    error: false,
    loading: false,
  }
}

export function emptyWfNodeRuntime() {
  return {
    id: '',
    uuid: '',
    workflowRuntimeId: '',
    input: {},
    output: {},
    status: 1,
    statusRemark: '',
    createTime: '',

    wfRuntimeUuid: '',
    error: false,
  }
}

// ========== 聊天 / 角色 ==========

export function emptyCharacter(): Chat.Character {
  return {
    uuid: '',
    title: '',
    remark: '',
    aiSystemMessage: '',
    understandContextEnable: false,
    loadedAll: false,
    loadedFirstPageMsg: false,
    minMsgUuid: '',
    mcpIds: [],
    kbIds: [], // 关联的知识库ID
    skillIds: [], // 绑定的技能ID
    characterKnowledgeList: [], // 关联的知识库
    answerContentType: CHAT_MESSAGE_CONTENT_TYPE.auto, // 1: auto, 2: text, 3: audio
    isAutoplayAnswer: false, // 聊天时音频类型的响应内容是否自动播放
    isEnableThinking: false, // 是否启用思考过程
    isEnableWebSearch: false, // 是否启用网络搜索
    audioConfig: {
      voice: {
        param_name: '',
        model: '',
        platform: '',
      },
    }, // 语音配置
  }
}

export function emptyChatMessage(): Chat.ChatMessage {
  return {
    uuid: '',
    createTime: '',
    thinkingContent: '',
    remark: '',
    audioUuid: '',
    audioUrl: '',
    audioDuration: 0, // in seconds
    messageRole: 0,
    children: [{ // for reply
      uuid: '',
      createTime: '',
      thinkingContent: '',
      remark: '',
      audioUuid: '',
      audioUrl: '',
      audioDuration: 0,
      messageRole: 0,
      children: [],
      aiModelPlatform: '',
      attachmentUrls: [],
      isRefEmbedding: false,
      isRefGraph: false,
      isRefMemoryEmbedding: false,
      audioPlayState: emptyAudioPlayState(),
      contentType: CHAT_MESSAGE_CONTENT_TYPE.text, // 2: text, 3: audio
    }], // for reply
    aiModelPlatform: '',
    attachmentUrls: [],
    isRefEmbedding: false,
    isRefGraph: false,
    isRefMemoryEmbedding: false,
    audioPlayState: emptyAudioPlayState(),
    contentType: CHAT_MESSAGE_CONTENT_TYPE.text, // 2: text, 3: audio
  }
}

export function emptyQuota(): User.Config {
  return {
    userQuota: {
      requestTimesByDay: 0,
      requestTimesByMonth: 0,
      tokenByDay: 0,
      tokenByMonth: 0,
      drawByDay: 0,
      drawByMonth: 0,
    },
    quotaCost: {
      paidRequestTimes: {
        todayRequestTimes: 0,
        monthRequestTimes: 0,
      },
      paidTokenCost: {
        todayTokenCost: 0,
        monthTokenCost: 0,
      },
      paidDrawTimes: {
        todayDrawTimes: 0,
        monthDrawTimes: 0,
      },
      freeTokenCost: {
        todayTokenCost: 0,
        monthTokenCost: 0,
      },
      freeRequestTimes: {
        todayRequestTimes: 0,
        monthRequestTimes: 0,
      },
      freeDrawTimes: {
        todayDrawTimes: 0,
        monthDrawTimes: 0,
      },
    },
  }
}

export function emptyMcp(): Mcp.McpInfo {
  return {
    id: '',
    uuid: '',
    title: '',
    transportType: '',
    sseUrl: '',
    sseTimeout: 0,
    stdioCommand: '',
    stdioArg: '',
    presetParams: [],
    customizedParamDefinitions: [],
    installType: 'git',
    website: '',
    remark: '',
    isEnable: false,
    configured: false, // 是否已配置
  }
}

export function emptyUserMcp(): Mcp.UserMcp {
  return {
    id: '',
    uuid: '',
    userId: '',
    mcpId: '',
    mcpCustomizedParams: [],
    isEnable: false,
    mcpInfo: emptyMcp(), // mcp信息
  }
}

export function emptyAudioPlayState(): AudioPlayState {
  return {
    audioUrl: '',
    playDuration: 0,
    playing: false,
    audio: null, // Audio object
    text: '', // Text to be read
    showText: false, // Whether to show the text
    audioUuid: '', // UUID of the audio file
    msgPart: '', // 聊天时不断接收到的消息片段，即时播放时使用
    audioFrame: '', // audio frame
  }
}

export function emptyAsrSetting(): AsrSetting {
  return {
    model_name: '', // ASR model name
    platform: '', // ASR platform, eg: local or remote
    max_record_duration: 60, // Maximum recording duration in seconds
    max_file_size: 10 * 1024 * 1024, // Maximum file
  }
}

export function emptyTtsSetting(): TtsSetting {
  return {
    synthesizer_side: 'client', // client | server
    model_name: '', // TTS model name
    platform: '', // TTS platform, eg: local or remote
  }
}

export function emptySysConfigInfo(): SysConfigInfo {
  return {
    asrSetting: emptyAsrSetting(),
    ttsSetting: emptyTtsSetting(),
    availableVoices: [],
    defaultLocale: 'zh-CN',
  }
}

// ========== 文件 URL 助手（admin 原 functions/index.ts 独有 5 函数中可直收的 4 个；
// user 端同源实现位于 utils/functions/files.ts，逐字节一致）==========

export function getRealFileUrl(fileUrl: string) {
  if (!fileUrl.includes('http') && !fileUrl.includes('/api'))
    return `/api${fileUrl}`
  else
    return fileUrl
}

export function changeFileUrlToUuid(fileUrl: string) {
  if (!fileUrl.includes('/'))
    return fileUrl
  return fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
}

export function fillCompleteUrl(fileUrl: string) {
  if (!fileUrl.includes('/') || fileUrl.indexOf('/api') === 0)
    return fileUrl
  return `/api${fileUrl}`
}

export function calcImageUrls(draw: Chat.Draw) {
  for (let i = 0; i < draw.imageUrls.length; i++) {
    const url = draw.imageUrls[i]
    if (!url.includes('http')) {
      if (draw.isPublic)
        draw.imageUrls[i] = `/api/draw/public/image/${draw.uuid}/${changeFileUrlToUuid(url)}`
      else
        draw.imageUrls[i] = `/api${url}`
    }
  }
}

// ========== 日期（admin/user 同名同实现，两端均无 .vue 消费者）==========

export function getCurrentDate() {
  const date = new Date()
  const day = date.getDate()
  const month = date.getMonth() + 1
  const year = date.getFullYear()
  return `${year}-${month}-${day}`
}
