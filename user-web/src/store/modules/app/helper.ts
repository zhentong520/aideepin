import { ss } from '@/utils/storage'
import { emptyAiModel, emptySysConfigInfo } from '@/utils/functions'

const LOCAL_NAME = 'appSetting'

export type Theme = 'light' | 'dark' | 'auto'

export type Language = 'zh-CN' | 'en-US'

/** 聊天消息样式：bubble=气泡卡片，flat=平铺文档式（默认，保持旧版观感） */
export type ChatMsgStyle = 'bubble' | 'flat'

/** 聊天内容宽度：compact=窄幅居中，wide=宽幅（默认，旧行为），full=全宽 */
export type ChatContentWidth = 'compact' | 'wide' | 'full'

/** 发送按钮位置：right=输入框右侧（默认），below=输入框下方独立行 */
export type ChatSendButtonPosition = 'right' | 'below'

/**
 * 「画面设计」配置（设置弹窗 Appearance Tab）：整个聊天框的自定义。
 * 新增字段必须同步 defaultChatAppearance()，getLocalSetting 已做逐字段兜底合并，
 * 旧 sessionStorage 中缺字段的用户自动落到默认值。
 */
export interface ChatAppearance {
  // —— 消息区布局 ——
  msgStyle: ChatMsgStyle
  contentWidth: ChatContentWidth
  showAvatar: boolean
  showTimestamp: boolean
  showMetaInfo: boolean
  // —— 字体与气泡 ——
  fontSize: number // px，12~18
  bubbleRadius: number // px，0~24，仅 bubble 模式生效
  // —— 输入区按钮显隐 ——
  showThinkingSwitch: boolean
  showWebSearchSwitch: boolean
  showContextToggle: boolean
  showImageUpload: boolean
  showKnowledgeEntry: boolean
  showMcpEntry: boolean
  showVoiceButton: boolean
  sendButtonPosition: ChatSendButtonPosition
  // —— 消息操作显隐 ——
  showFeedback: boolean
  showRefEntries: boolean
  showQuickActions: boolean
}

export function defaultChatAppearance(): ChatAppearance {
  return {
    msgStyle: 'flat',
    contentWidth: 'wide',
    showAvatar: true,
    showTimestamp: true,
    showMetaInfo: true,
    fontSize: 14,
    bubbleRadius: 10,
    showThinkingSwitch: true,
    showWebSearchSwitch: true,
    showContextToggle: true,
    showImageUpload: true,
    showKnowledgeEntry: true,
    showMcpEntry: true,
    showVoiceButton: true,
    sendButtonPosition: 'right',
    showFeedback: true,
    showRefEntries: true,
    showQuickActions: true,
  }
}

export interface AppState {
  siderCollapsed: boolean
  theme: Theme
  language: Language | ''
  chatAppearance: ChatAppearance

  selectedSearchEngine: string
  selectedLLM: AiModelInfo
  selectedImageModel: AiModelInfo
  searchEngines: SearchEngineInfo[]
  llms: AiModelInfo[]
  imageModels: AiModelInfo[]
  sysConfigInfo: SysConfigInfo
}

export function defaultSetting(): AppState {
  return {
    siderCollapsed: false,
    theme: 'light',
    language: '',
    chatAppearance: defaultChatAppearance(),
    selectedSearchEngine: '',
    selectedLLM: emptyAiModel(),
    selectedImageModel: emptyAiModel(),
    searchEngines: [],
    llms: [],
    imageModels: [],
    sysConfigInfo: emptySysConfigInfo(),
  }
}

export function getLocalSetting(): AppState {
  const localSetting: AppState | undefined = ss.get(LOCAL_NAME)
  const merged = { ...defaultSetting(), ...localSetting }
  // chatAppearance 是嵌套对象，浅合并会让旧存档里新增字段全部丢失，必须逐字段兜底
  merged.chatAppearance = {
    ...defaultChatAppearance(),
    ...(localSetting?.chatAppearance ?? {}),
  }
  return merged
}

export function setLocalSetting(setting: AppState): void {
  ss.set(LOCAL_NAME, setting)
}
