import type { Ref } from 'vue'
import type { MessageApi } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'
import { useAppStore, useChatStore, useSceneStore } from '@/store'
import { AUDIO_SYNTHESIZER_SIDE, CHAT_MESSAGE_CONTENT_TYPE } from '@/utils/constant'

/**
 * SSE 流式控制（自 chat/index.vue 拆出，T5-10）：
 * fetchChatAPIOnce 的事件回调（思考/正文/音频/工具调用/元数据/错误）全部集中在此，
 * 与视图解耦，可独立测试。消息的 DOM 滚动等视图反应经 onReceiving/onCompleted 回调注入。
 */
export function useChatSse(options: {
  ms: MessageApi
  messages: Ref<any[]>
  currCharacter: Ref<Chat.Character>
  imageUuids: Ref<string[]>
  appendChunk: (characterUuid: string, msgUuid: string, chunk: string, thinking?: boolean) => void
  updateMessageSomeFields: (characterUuid: string, uuid: string, fields: Record<string, any>) => void
  onReceiving: () => void
  onCompleted: () => void
}) {
  const { ms, appendChunk, updateMessageSomeFields, onReceiving, onCompleted } = options
  const appStore = useAppStore()
  const chatStore = useChatStore()
  const sceneStore = useSceneStore()

  let controller = new AbortController()

  const fetchChatAPIOnce = async (regenerateQuestionUuid: string, childAudioPlayState: AudioPlayState) => {
    const characterUuid = options.currCharacter.value.uuid
    const character = chatStore.getCharacterByUuid(characterUuid)
    if (!character) {
      ms.error(t('chat.characterNotFound'))
      return
    }
    // 2026-10-05：模型不再由用户选择。改为从「当前会话 → 场景 → 默认智能体」解析。
    // 解析不到就明确报错，绝不回退到 appStore.selectedLLM 或空模型——
    // 那会让请求带着空 modelName 发出去，表现为一个难以定位的服务端错误。
    const model = sceneStore.resolveModelByCharacter(characterUuid)
    if (!model) {
      ms.error(t('chat.sceneModelNotBound'))
      return
    }
    api.sseProcess({
      options: {
        prompt: '',
        characterUuid,
        regenerateQuestionUuid,
        modelPlatform: model.modelPlatform,
        modelName: model.modelName,
        imageUrls: options.imageUuids.value,
        audioUuid: '',
        audioDuration: 0,
      },
      signal: controller.signal,
      startCallback(chunk) {
      },
      stateChanged: (state) => {
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question)
          return

        question.state = new Map(Object.entries(JSON.parse(state)))
      },
      thinkingDataReceived: (chunk) => {
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question) {
          ms.error(t('chat.questionNotFound'))
          return
        }
        try {
          for (let i = 0; i < chunk.length; i++) {
            appendChunk(
              characterUuid,
              question.children[0].uuid,
              chunk[i],
              true, // thinking is true
            )
            onReceiving()
          }
        } catch (error) {
          console.error(error)
        }
        // 推理阶段无需显示状态
        question.state = new Map<string, string>()
      },
      messageReceived: (chunk) => {
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question) {
          ms.error(t('chat.questionNotFound'))
          return
        }
        try {
          for (let i = 0; i < chunk.length; i++) {
            appendChunk(
              characterUuid,
              question.children[0].uuid,
              chunk[i],
            )
            onReceiving()
          }
        } catch (error) {
          console.error(error)
        }
        const answerContentType = chatStore.answerContentType(character, question.audioUuid)
        const ttsPartText = chunk.replace('\n', '')
        if (ttsPartText && appStore.audioSynthesizerSide === AUDIO_SYNTHESIZER_SIDE.client && answerContentType === CHAT_MESSAGE_CONTENT_TYPE.audio && character.isAutoplayAnswer) {
          // settimeout是防止执行太快导致 AudioMessage 中的 watch 没有触发
          setTimeout(() => {
            childAudioPlayState.msgPart = chunk
          }, 0)
        }
      },
      audioDataReceived(audioFrame) {
        // AudioMessage 监听pcmPart的变化并决定要不要自动播放
        if (appStore.audioSynthesizerSide !== AUDIO_SYNTHESIZER_SIDE.client && audioFrame)
          childAudioPlayState.audioFrame = audioFrame
      },
      toolCallReceived: (data) => {
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question)
          return
        const answer = question.children[0]
        if (!answer.toolCalls)
          answer.toolCalls = []
        answer.toolCalls.push(data)
      },
      doneCallback: (chunk) => {
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question) {
          ms.error(t('chat.questionNotFound'))
          return
        }
        const answer = question.children[0]
        if (chunk.includes('[META]')) {
          const meta = chunk.replace('[META]', '')
          const metaData: Chat.MetaData = JSON.parse(meta)
          updateMessageSomeFields(characterUuid, question.uuid, { ...metaData.question, inputTokens: metaData.question.inputTokens, loading: false, state: new Map<string, string>() })
          // inputTokens/outputTokens 由 AnswerMeta 直接提供 | inputTokens/outputTokens provided directly by AnswerMeta
          updateMessageSomeFields(characterUuid, answer.uuid, { ...metaData.answer, inputTokens: metaData.answer.inputTokens, outputTokens: metaData.answer.outputTokens, duration: metaData.answer.duration, uuid: answer.uuid, loading: false })
          if (metaData.audioInfo) {
            answer.audioPlayState.audioUrl = metaData.audioInfo.url
            answer.audioDuration = metaData.audioInfo.duration
            answer.audioUuid = metaData.audioInfo.uuid
          }
        } else {
          updateMessageSomeFields(characterUuid, regenerateQuestionUuid, { loading: false })
          updateMessageSomeFields(characterUuid, answer.uuid, { uuid: answer.uuid, loading: false })
        }
        onCompleted()
      },
      errorCallback: (error) => {
        ms.warning(error)
        const question = options.messages.value.find((q: { uuid: string }) => q.uuid === regenerateQuestionUuid)
        if (!question) {
          ms.error(t('chat.questionNotFound'))
          return
        }
        updateMessageSomeFields(characterUuid, question.children[0].uuid, { remark: `${t('common.systemTip')}${error}`, loading: false })
      },
    })
  }

  /** 中止当前流（停止生成 / 页面卸载）。 */
  function stopStream() {
    controller.abort()
  }

  /** 开启新流前重建 AbortController。 */
  function resetStream() {
    controller = new AbortController()
  }

  return { fetchChatAPIOnce, stopStream, resetStream }
}
