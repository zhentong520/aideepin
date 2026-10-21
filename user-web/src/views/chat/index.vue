<script setup lang='ts'>
import type { Ref } from 'vue'
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NButton, NCollapse, NCollapseItem, NIcon, NModal, NTabPane, NTabs, useDialog, useLoadingBar, useMessage } from 'naive-ui'
import { Cat } from '@vicons/fa'
import { v4 as uuidv4 } from 'uuid'
import { AudioMessage, Message } from './components'
import { useScroll } from './hooks/useScroll'
import { useChat } from './hooks/useChat'
import { useCopyCode } from './hooks/useCopyCode'
import HeaderComponent from './components/Header/index.vue'
import PcHeader from './components/Header/pc.vue'
import InputToolbar from './InputToolbar.vue'
import InputEditor from './InputEditor.vue'
import RefGraph from './RefGraph.vue'
import RefMemory from './RefMemory.vue'
import FeedbackButtons from './components/FeedbackButtons.vue'
import LoginTip from '@/views/user/LoginTip.vue'
import { useChatSse } from './composables/useChatSse'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import { useAppStore, useAuthStore, useChatStore, useSceneStore } from '@/store'
import { CHAT_MESSAGE_CONTENT_TYPE } from '@/utils/constant'
import { getDefaultCharacter } from '@/store/modules/chat/helper'
import { SvgIcon } from '@/components/common'
import api from '@/api'
import { t } from '@/locales'
import { debounce } from '@/utils/functions/debounce'
import { emptyAudioPlayState } from '@/utils/functions'
const pageSize = 10
const appStore = useAppStore()
void appStore
const route = useRoute()
const ms = useMessage()
const dialog = useDialog()
const chatStore = useChatStore()
const sceneStore = useSceneStore()
const authStore = useAuthStore()
const loaddingBar = useLoadingBar()
const { isMobile } = useBasicLayout()
const { unshiftAnswer, updateMessageSomeFields, appendChunk } = useChat()
const { scrollRef, isAtBottom, scrollToBottom, scrollToBottomIfAtBottom, scrollTo, checkAtBottom } = useScroll()
const { uuid: curCharacterUuid } = route.params as { uuid: string }
const regenerateQuestionUuid = ref<string>('')
const inputEditorRef = ref()
const tabsActiveTab = ref<string[]>([])
const messages = computed(() => {
  return chatStore.getMsgsByCharacter(curCharacterUuid)
})
// 首页消息加载中（区分"加载中"与"真正无数据"的空状态）
// | First page messages loading (distinguishes "loading" from a truly empty state)
const firstPageLoading = computed(() => {
  return !messages.value.length && chatStore.loadingMsgs.has(curCharacterUuid)
})
const currCharacter = computed(() => chatStore.getCurCharacter || getDefaultCharacter())
// 头部标题用场景名而不是 character.title（那是后台智能体名）——见 PcHeader 的 props 注释
const currSceneTitle = computed(() => sceneStore.resolveSceneTitle(currCharacter.value.uuid))
const imageUuids = ref<string[]>([])
const isChatting = ref<boolean>(false)
const loadingMsgs = ref<boolean>(false)
const loaddingEmbeddingRef = ref<boolean>(false)
const inputRef = ref<Ref | null>(null)
const showMemoryModal = ref<boolean>(false)
const selectedMemoryMsgUuid = ref<string>('')
const showRefEmbeddingModal = ref<boolean>(false)
const showRefEmbeddingMsgUuid = ref<string>('')
const knowledgeEmbeddingRef = ref<KnowledgeBase.QaRecordEmbeddingRef[]>([])
const showRefGraphModal = ref<boolean>(false)
const showRefGraphMsgUuid = ref<string>('')

// ==================== T7-01：👍/👎 反馈 ====================
// 已投的票按 query_id 存（同一检索一票、可改票）
const feedbackVotes = ref<Record<string, 'PASS' | 'FAIL'>>({})
// 消息 uuid → query_id（[META] 的 refs 优先；页面刷新后由引用接口回填）
const msgQueryIdMap = ref<Record<string, string>>({})

function answerQueryId(answer?: Chat.ChatMessage): string {
  if (!answer) {
    return ''
  }
  const fromMeta = answer.refs?.find((r) => r.ragQueryId)?.ragQueryId
  return fromMeta || msgQueryIdMap.value[answer.uuid] || ''
}

async function submitFeedback(queryId: string, messageUuid: string, rating: 'PASS' | 'FAIL') {
  if (!queryId) {
    ms.warning(t('chat.feedbackNoQuery'))
    return
  }
  try {
    await api.ragFeedback({ queryId, messageUuid, rating })
    feedbackVotes.value = { ...feedbackVotes.value, [queryId]: rating }
    ms.success(t('chat.feedbackSubmitted'))
  } catch (e) {
    // 反馈是锦上添花，失败不打断聊天
    ms.error(t('chat.feedbackFailed'))
  }
}

let prevScrollTop: number
useCopyCode()

// 未知原因刷新页面，loading 状态不会重置，手动重置
messages.value.forEach((item: { loading?: boolean; uuid: string }) => {
  if (item.loading)
    updateMessageSomeFields(curCharacterUuid, item.uuid, { loading: false })
})

function sseStarted() {
  nextTick(() => {
    scrollToBottom()
  })
}

function chatMessageReceiving(questionUuid: string) {
  nextTick(() => {
    scrollToBottomIfAtBottom()
  })
}

function messageComplelted(questionUuid: string) {
  nextTick(() => {
    scrollToBottom()
  })
}

// ==================== SSE 流式控制（T5-10 拆出，见 composables/useChatSse.ts） ====================
const { fetchChatAPIOnce, stopStream, resetStream } = useChatSse({
  ms,
  messages,
  currCharacter,
  imageUuids,
  appendChunk,
  updateMessageSomeFields,
  onReceiving: () => chatMessageReceiving(''),
  onCompleted: () => messageComplelted(''),
})

function handleStop() {
  if (isChatting.value) {
    stopStream()
    isChatting.value = false
  }
  inputEditorRef.value?.handleStop()
}

// 打开记忆
function handleMemoryRefClick(qaRecordUuid: string) {
  showMemoryModal.value = true
  selectedMemoryMsgUuid.value = qaRecordUuid
}

// 打开知识库引用
async function handleEmbeddingRefClick(qaRecordUuid: string) {
  showRefEmbeddingModal.value = true
  showRefEmbeddingMsgUuid.value = qaRecordUuid
  knowledgeEmbeddingRef.value = []
  knowledgeEmbeddingRef.value = chatStore.getReferences(qaRecordUuid)
  if (knowledgeEmbeddingRef.value.length === 0) {
    loaddingEmbeddingRef.value = true
    try {
      const { data } = await api.knowledgeEmbeddingRef<KnowledgeBase.QaRecordEmbeddingRef[]>(qaRecordUuid)
      chatStore.setKnowledgeEmbeddingRefs(qaRecordUuid, data)

      // 显示最后一次点击的引用
      knowledgeEmbeddingRef.value = chatStore.getReferences(showRefEmbeddingMsgUuid.value)
    } finally {
      loaddingEmbeddingRef.value = false
    }
  }
  // T7-01：回填该消息的 query_id（页面刷新后 [META] 的 refs 已丢失，靠引用接口兜底）
  const queryId = knowledgeEmbeddingRef.value.find((r) => r.ragQueryId)?.ragQueryId
  if (queryId) {
    msgQueryIdMap.value = { ...msgQueryIdMap.value, [qaRecordUuid]: queryId }
  }
}

async function handleGraphClick(msgUuid: string) {
  showRefGraphModal.value = true
  showRefGraphMsgUuid.value = msgUuid
}

async function onRegenerate(questionUuid: string) {
  console.log(`onRegenerate,question uuid:${questionUuid}`)
  if (isChatting.value)
    return

  regenerateQuestionUuid.value = questionUuid
  const message = chatStore.getMsgByCurCharacter(questionUuid)
  if (!message)
    return

  isChatting.value = true
  resetStream()

  try {
    const answerContentType = chatStore.answerContentType(currCharacter.value, message.audioUuid)
    const answerUuid = uuidv4().replace(/-/g, '')
    const audioPlayState = emptyAudioPlayState()
    unshiftAnswer(
      curCharacterUuid,
      questionUuid,
      {
        uuid: answerUuid,
        contentType: answerContentType,
        createTime: new Date().toLocaleString(),
        thinkingContent: '',
        remark: '',
        audioUuid: '',
        audioUrl: '',
        audioDuration: 0,
        children: [],
        inversion: false,
        error: false,
        loading: true,
        attachmentUrls: [],
        isRefMemoryEmbedding: false,
        isRefEmbedding: false,
        isRefGraph: false,
        audioPlayState,
      },
    )
    await fetchChatAPIOnce(questionUuid, audioPlayState)
    selectedLatestAnswer(questionUuid)
  } catch (error: any) {
    console.error(error)
    ms.error(error ?? 'error')
  } finally {
    isChatting.value = false
  }
}

function selectedLatestAnswer(questionUuid: string) {
  nextTick(() => {
    console.log('fetchChatAPIOnce nextTick')
    const index = messages.value.findIndex((msg: { uuid: string }) => msg.uuid === questionUuid)
    if (index !== -1 && messages.value[index].children[0]) {
      tabsActiveTab.value[index] = `tab_${messages.value[index].children[0].uuid}`
      console.log(`tabsActiveTab[${index}]: ${tabsActiveTab.value[index]}`)
    }
  })
}

async function loadMoreMessage(callback?: Function) {
  if (currCharacter.value.loadedAll || loadingMsgs.value)
    return

  loadingMsgs.value = true
  loaddingBar.start()
  try {
    const minMsgUuid = chatStore.getCurCharacter?.minMsgUuid || ''
    const { data } = await api.fetchMessages<Chat.CharacterMsgListResp>(curCharacterUuid, minMsgUuid, pageSize)

    if (data.msgList.length < pageSize) {
      chatStore.updateCharacter(curCharacterUuid, { minMsgUuid: data.minMsgUuid, loadedAll: true })
      ms.warning(t('common.noMore'), {
        duration: 3000,
      })
    } else {
      chatStore.updateCharacter(curCharacterUuid, { minMsgUuid: data.minMsgUuid })
      chatStore.unshiftMessages(curCharacterUuid, data.msgList)
    }
  } catch (error) {
    console.error(`loadMoreMessage${error}`)
  } finally {
    loadingMsgs.value = false
    loaddingBar.finish()

    if (callback)
      callback()
  }
}

const handleLoadMoreMessage = debounce(loadMoreMessage, 300)
async function handleScroll(event: any) {
  const scrollTop = event.target.scrollTop
  const lastScrollClient = event.target.scrollHeight
  if (scrollTop < 50 && (scrollTop < prevScrollTop || prevScrollTop === undefined)) {
    handleLoadMoreMessage(() => {
      nextTick(() => {
        scrollTo(event.target.scrollHeight - lastScrollClient)
      })
    })
  }
  prevScrollTop = scrollTop
  checkAtBottom()
}

/** 收集这条回答（及其问题）为微调训练样本，进候选池等待审核 */
async function handleCollectSample(answerUuid: string) {
  try {
    await api.messageCollectSample(answerUuid)
    ms.success(t('chat.collectSampleSuccess'))
  } catch (e: any) {
    const msg = e?.message || ''
    // 后端对重复收集返回参数错误，这里给用户可读的区分
    ms.error(t('chat.collectSampleFailed') + (msg ? `: ${msg}` : ''))
  }
}

function handleDelete(questionUuid: string, answerUuid: string, isQuestion = false) {
  if (isChatting.value)
    return

  let tip = t('chat.deleteMessageConfirm')
  if (isQuestion)
    tip = t('chat.deleteQuestionAlsoDeleteAnswer')

  dialog.warning({
    title: t('chat.deleteMessage'),
    content: tip,
    positiveText: t('common.yes'),
    negativeText: t('common.no'),
    onPositiveClick: () => {
      if (isQuestion) {
        chatStore.deleteQuestion(curCharacterUuid, questionUuid)
        api.messageDel(questionUuid)
      } else {
        chatStore.deleteAnswer(curCharacterUuid, questionUuid, answerUuid)
        api.messageDel(answerUuid)
        setTimeout(() => {
          selectedLatestAnswer(questionUuid)
        }, 3000)
      }
    },
  })
}

const footerClass = computed(() => {
  let classes = ['p-4']
  if (isMobile.value)
    classes = ['sticky', 'left-0', 'bottom-0', 'right-0', 'p-2', 'pr-3', 'overflow-hidden']
  return classes
})

function toggleUsingContext() {
  api.characterToggleUsingContext(currCharacter.value.uuid, !currCharacter.value.understandContextEnable)
  currCharacter.value.understandContextEnable = !currCharacter.value.understandContextEnable
  if (currCharacter.value.understandContextEnable)
    ms.success(t('chat.turnOnContext'))
  else
    ms.warning(t('chat.turnOffContext'))
}

function imagesChange(uuids: string[]) {
  imageUuids.value = uuids
}

watch(
  () => currCharacter.value.loadedFirstPageMsg,
  () => {
    if (currCharacter.value.loadedFirstPageMsg)
      scrollToBottom()
  },
  { immediate: true },
)

onMounted(() => {
  console.info('chat,onmounted')
  nextTick(() => {
    scrollToBottom()
  })
  if (inputRef.value && !isMobile.value)
    inputRef.value?.focus()
})

onUnmounted(() => {
  if (isChatting.value)
    stopStream()
})

onActivated(async () => {
  console.log('onActivated')
  if (!curCharacterUuid && chatStore.active)
    await chatStore.setActive(chatStore.active)

  scrollToBottom()
})

onDeactivated(() => {
  console.log('onDeactivated')
})
</script>

<template>
  <div class="chat-box flex flex-col w-full h-full">
    <HeaderComponent
      v-if="isMobile" :using-context="currCharacter.understandContextEnable"
      @toggle-using-context="toggleUsingContext"
    />
    <PcHeader v-if="!isMobile" :character="currCharacter" :title="currSceneTitle" />
    <main class="relative flex-1 overflow-hidden">
      <div ref="scrollRef" class="h-full overflow-hidden overflow-y-auto" @scroll="handleScroll">
        <div
          class="w-full max-w-screen-xl m-auto dark:bg-[#101014]"
          :class="[isMobile ? 'p-2' : 'p-4']"
        >
          <template v-if="!authStore.token">
            <LoginTip />
          </template>
          <template v-else-if="!messages.length">
            <div class="flex items-center justify-center mt-4 text-center text-neutral-400">
              <NIcon :component="Cat" size="32" />
              <SvgIcon v-if="firstPageLoading" icon="line-md:loading-loop" class="w-8 h-8 pl-1" />
              <span v-else class="pl-1 text-sm">{{ t('chat.noRecord') }}</span>
            </div>
          </template>

          <template v-else>
            <div v-for="(qaMessage, index) of messages" :key="index" class="pb-3">
              <!-- 用户消息 start -->

              <!-- 多模态的请求消息，携带有附件 -->
              <template v-if="qaMessage.attachmentUrls.length > 0">
                <!-- 语音聊天 -->
                <AudioMessage
                  v-if="qaMessage.contentType === CHAT_MESSAGE_CONTENT_TYPE.audio" :inversion="true"
                  :character="currCharacter" :message-uuid="qaMessage.uuid" :date-time="qaMessage.createTime"
                  :audio-play-state="qaMessage.audioPlayState" :duration="qaMessage.audioDuration"
                  @delete="handleDelete(qaMessage.uuid, '', true)"
                />
                <!-- 文本聊天 -->
                <Message
                  v-else :date-time="qaMessage.createTime" :text="qaMessage.remark"
                  :image-urls="qaMessage.attachmentUrls" type="text-image" :inversion="true" :error="qaMessage.error"
                  :loading="false" @regenerate="onRegenerate(qaMessage.uuid)"
                  @delete="handleDelete(qaMessage.uuid, '', true)"
                />
              </template>
              <!-- 非多模态的请求消息，没有附件 -->
              <template v-if="qaMessage.attachmentUrls.length === 0">
                <!-- 语音聊天 -->
                <AudioMessage
                  v-if="qaMessage.contentType === CHAT_MESSAGE_CONTENT_TYPE.audio" :inversion="true"
                  :character="currCharacter" :message-uuid="qaMessage.uuid" :date-time="qaMessage.createTime"
                  :audio-play-state="qaMessage.audioPlayState" :duration="qaMessage.audioDuration"
                  @delete="handleDelete(qaMessage.uuid, '', true)"
                />
                <!-- 文本聊天 -->
                <Message
                  v-else :date-time="qaMessage.createTime" :text="qaMessage.remark" type="text" :inversion="true"
                  :error="qaMessage.error" :loading="false" @regenerate="onRegenerate(qaMessage.uuid)"
                  @delete="handleDelete(qaMessage.uuid, '', true)"
                />
              </template>

              <!-- 用户消息 end -->

              <!-- LLM回复 start -->
              <!-- LLM的多条回复消息 -->
              <template v-if="qaMessage.children.length > 1">
                <NTabs
                  v-model:value="tabsActiveTab[index]" pane-wrapper-style="margin: -30px -30px"
                  pane-style="padding-left: 4px; box-sizing: border-box;" type="bar" placement="left" size="small"
                  animated
                >
                  <NTabPane
                    v-for="(answer, index) of qaMessage.children" :key="`tab_${answer.uuid}`"
                    :name="`tab_${answer.uuid}`" :tab="`${t('chat.answer')} ${index + 1}`"
                  >
                    <AudioMessage
                      v-if="answer.contentType === CHAT_MESSAGE_CONTENT_TYPE.audio" :character="currCharacter"
                      :duration="answer.audioDuration" :inversion="false" :message-uuid="answer.uuid"
                      :date-time="answer.createTime" :audio-play-state="answer.audioPlayState" :loading="answer.loading"
                      :ai-model-platform="answer.aiModelPlatform" @delete="handleDelete(qaMessage.uuid, answer.uuid)"
                    >
                      <div class="flex items-center space-x-2 mt-2">
                        <NButton
                          v-if="!!answer && !answer.loading && answer.isRefMemoryEmbedding" size="tiny" text
                          type="primary" @click="handleMemoryRefClick(answer.uuid)"
                        >
                          {{ t('chat.memory') }}
                        </NButton>
                        <NButton
                          v-if="!!answer && !answer.loading && answer.isRefEmbedding" size="tiny" text
                          type="primary" @click="handleEmbeddingRefClick(answer.uuid)"
                        >
                          {{ t('chat.reference') }}
                        </NButton>
                        <NButton
                          v-if="!!answer && !answer.loading && answer.isRefGraph" size="tiny" text type="primary"
                          @click="handleGraphClick(answer.uuid)"
                        >
                          {{ t('chat.graph') }}
                        </NButton>
                        <FeedbackButtons
                          v-if="!!answer && !answer.loading && answer.isRefEmbedding"
                          :query-id="answerQueryId(answer)"
                          :message-uuid="answer.uuid"
                          :vote="feedbackVotes[answerQueryId(answer)]"
                          @vote="(r: 'PASS' | 'FAIL') => submitFeedback(answerQueryId(answer), answer.uuid, r)"
                        />
                      </div>
                    </AudioMessage>
                    <Message
                      v-else :show-avatar="false" :date-time="answer.createTime" :thinking="answer.thinking"
                      :thinking-content="answer.thinkingContent" :text="answer.remark" type="text" :inversion="false"
                      :regenerate="true" :error="answer.error" :loading="answer.loading"
                      :input-tokens="answer.inputTokens" :output-tokens="answer.outputTokens"
                      :duration="answer.duration"
                      :tool-calls="answer.toolCalls"
                      :ai-model-platform="answer.aiModelPlatform" @regenerate="onRegenerate(qaMessage.uuid)"
                      @delete="handleDelete(qaMessage.uuid, answer.uuid)"
                      @collect-sample="handleCollectSample(answer.uuid)"
                    >
                      <div class="flex items-center space-x-2 mt-2">
                        <NButton
                          v-if="!!answer && !answer.loading && answer.isRefMemoryEmbedding" size="tiny" text
                          type="primary" @click="handleMemoryRefClick(answer.uuid)"
                        >
                          {{ t('chat.memory') }}
                        </NButton>
                        <NButton
                          v-if="!!answer && !answer.loading && answer.isRefEmbedding" size="tiny" text
                          type="primary" @click="handleEmbeddingRefClick(answer.uuid)"
                        >
                          {{ t('chat.reference') }}
                        </NButton>
                        <NButton
                          v-if="!!answer && !answer.loading && qaMessage.isRefGraph" size="tiny" text
                          type="primary" @click="handleGraphClick(answer.uuid)"
                        >
                          {{ t('chat.graph') }}
                        </NButton>
                      </div>
                    </Message>
                  </NTabPane>
                </NTabs>
              </template>

              <!-- LLM的单条回复消息 -->
              <template v-if="qaMessage.children.length === 1">
                <AudioMessage
                  v-if="qaMessage.children[0].contentType === CHAT_MESSAGE_CONTENT_TYPE.audio"
                  :character="currCharacter" :duration="qaMessage.children[0].audioDuration" :inversion="false"
                  :message-uuid="qaMessage.children[0].uuid" :date-time="qaMessage.children[0].createTime"
                  :audio-play-state="qaMessage.children[0].audioPlayState" :loading="qaMessage.children[0].loading"
                  :ai-model-platform="qaMessage.children[0].aiModelPlatform"
                  @delete="handleDelete(qaMessage.uuid, qaMessage.children[0].uuid)"
                >
                  <div class="flex items-center space-x-2 mt-2">
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefMemoryEmbedding" size="tiny" text
                      type="primary" @click="handleMemoryRefClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.memory') }}
                    </NButton>
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefEmbedding"
                      size="tiny" text type="primary" @click="handleEmbeddingRefClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.reference') }}
                    </NButton>
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefGraph"
                      size="tiny" text type="primary" @click="handleGraphClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.graph') }}
                    </NButton>
                    <FeedbackButtons
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefEmbedding"
                      :query-id="answerQueryId(qaMessage.children[0])"
                      :message-uuid="qaMessage.children[0].uuid"
                      :vote="feedbackVotes[answerQueryId(qaMessage.children[0])]"
                      @vote="(r: 'PASS' | 'FAIL') => submitFeedback(answerQueryId(qaMessage.children[0]), qaMessage.children[0].uuid, r)"
                    />
                  </div>
                </AudioMessage>
                <Message
                  v-else :date-time="qaMessage.children[0].createTime" :thinking="qaMessage.children[0].thinking"
                  :thinking-content="qaMessage.children[0].thinkingContent" :text="qaMessage.children[0].remark"
                  type="text" :inversion="qaMessage.children[0].inversion" :regenerate="true"
                  :error="qaMessage.children[0].error" :loading="qaMessage.children[0].loading"
                  :input-tokens="qaMessage.children[0].inputTokens" :output-tokens="qaMessage.children[0].outputTokens"
                  :duration="qaMessage.children[0].duration"
                  :tool-calls="qaMessage.children[0].toolCalls"
                  :ai-model-platform="qaMessage.children[0].aiModelPlatform" @regenerate="onRegenerate(qaMessage.uuid)"
                  @delete="handleDelete(qaMessage.uuid, qaMessage.children[0].uuid)"
                  @collect-sample="handleCollectSample(qaMessage.children[0].uuid)"
                >
                  <div class="flex items-center space-x-4 mt-2">
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefMemoryEmbedding" size="tiny" text
                      type="primary" @click="handleMemoryRefClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.memory') }}
                    </NButton>
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefEmbedding"
                      size="tiny" text type="primary" @click="handleEmbeddingRefClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.reference') }}
                    </NButton>
                    <NButton
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefGraph"
                      size="tiny" text type="primary" @click="handleGraphClick(qaMessage.children[0].uuid)"
                    >
                      {{ t('chat.graph') }}
                    </NButton>
                    <FeedbackButtons
                      v-if="!!qaMessage.children[0] && !qaMessage.children[0].loading && qaMessage.children[0].isRefEmbedding"
                      :query-id="answerQueryId(qaMessage.children[0])"
                      :message-uuid="qaMessage.children[0].uuid"
                      :vote="feedbackVotes[answerQueryId(qaMessage.children[0])]"
                      @vote="(r: 'PASS' | 'FAIL') => submitFeedback(answerQueryId(qaMessage.children[0]), qaMessage.children[0].uuid, r)"
                    />
                  </div>
                </Message>
              </template>

              <!-- LLM回复 end -->

              <!-- 状态栏 -->
              <div
                v-if="qaMessage.state && qaMessage.state.get('remark')"
                class="my-2 text-sm bg-gray-200 px-2 py-1 rounded-md"
              >
                {{ qaMessage.state.get('remark')!.startsWith('state.') ? t(`chat.${qaMessage.state.get('remark')!}`) : qaMessage.state.get('remark') }}
              </div>
            </div>
          </template>
        </div>
        <div class="sticky bottom-0 left-0 flex justify-center">
          <NButton v-if="isChatting" size="tiny" @click="handleStop">
            <template #icon>
              <SvgIcon icon="ri:stop-circle-line" />
            </template>
            {{ t('common.stopRequest') }}
          </NButton>
        </div>
      </div>
      <Transition name="scroll-to-bottom">
        <button
          v-if="!isAtBottom && messages.length > 0"
          type="button"
          class="absolute bottom-4 right-4 z-10 flex items-center justify-center w-8 h-8 rounded-full border border-neutral-200 bg-white/90 shadow-md transition-colors hover:bg-white dark:border-neutral-700 dark:bg-neutral-800/90 dark:hover:bg-neutral-800"
          :aria-label="t('chat.scrollToBottom')"
          @click="scrollToBottom(true)"
        >
          <SvgIcon icon="ri:arrow-down-s-line" class="text-lg" />
        </button>
      </Transition>
    </main>
    <footer :class="footerClass">
      <div class="w-full max-w-screen-xl m-auto border-t">
        <InputToolbar @images-change="imagesChange" />
        <InputEditor
          ref="inputEditorRef" :character-uuid="curCharacterUuid" :image-uuids="imageUuids"
          @sse-started="sseStarted" @message-receiving="chatMessageReceiving" @message-complelted="messageComplelted"
          @is-chatting="(chatting) => isChatting = chatting"
        />
      </div>
    </footer>

    <!-- 坑（2026-10-03 修）：以下三个为「只读」检索结果弹窗（引用材料/命中记忆/引用图谱），
         内容长度不可控且可能远超视口，原弹窗不限高 → 底部内容被推出视口无法查看。
         统一加 content-style 限高滚动（只读弹窗保留遮罩可关，避免用户被强制卡住）。 -->
    <NModal
      v-model:show="showRefEmbeddingModal" style="max-width: 80%;" preset="card"
      :title="t('chat.referenceMaterial')" content-style="max-height: 80vh; overflow-y: auto;"
    >
      <div v-show="knowledgeEmbeddingRef.length === 0" class="flex items-center justify-center h-64">
        <span v-show="!loaddingEmbeddingRef">{{ t('common.noData') }}</span>
        <SvgIcon v-show="loaddingEmbeddingRef" icon="line-md:loading-loop" class="text-2xl text-green-800 w-12 h-12" />
      </div>
      <NCollapse v-show="knowledgeEmbeddingRef.length > 0" :default-expanded-names="['refer_0']">
        <NCollapseItem
          v-for="(reference, idx) of knowledgeEmbeddingRef" :key="reference.embeddingId" :title="`${t('chat.reference')}${idx + 1}`"
          :name="`refer_${idx}`"
        >
          <!-- 按分段模式展示：qa=命中问题+答案，parent_child=命中子块+父段，text=段文本 -->
          <div v-if="reference.segmentMode === 'qa'" class="flex flex-col gap-1">
            <div><span style="font-size: 12px; opacity: 0.65;">{{ t('knowledgeBase.refHitQuestion') }}:</span> {{ reference.matchedText }}</div>
            <div><span style="font-size: 12px; opacity: 0.65;">{{ t('knowledgeBase.qaAnswer') }}:</span> {{ reference.text }}</div>
          </div>
          <div v-else-if="reference.segmentMode === 'parent_child'" class="flex flex-col gap-1">
            <div><span style="font-size: 12px; opacity: 0.65;">{{ t('knowledgeBase.refHitChunk') }}:</span> {{ reference.matchedText }}</div>
            <div><span style="font-size: 12px; opacity: 0.65;">{{ t('knowledgeBase.refParent') }}:</span> {{ reference.text }}</div>
          </div>
          <template v-else>
            {{ reference.text }}
          </template>
        </NCollapseItem>
      </NCollapse>
    </NModal>

    <NModal
      v-model:show="showMemoryModal" display-directive="show" style="max-width: 80%;"
      preset="card" :title="t('chat.hitMemory')" content-style="max-height: 80vh; overflow-y: auto;"
    >
      <RefMemory :msg-uuid="selectedMemoryMsgUuid" />
    </NModal>

    <NModal
      v-model:show="showRefGraphModal" display-directive="show" style="max-width: 80%;" preset="card"
      :title="t('chat.referenceGraph')" content-style="max-height: 80vh; overflow-y: auto;"
    >
      <RefGraph :msg-uuid="showRefGraphMsgUuid" />
    </NModal>
  </div>
</template>

<style scoped>
.scroll-to-bottom-enter-active,
.scroll-to-bottom-leave-active {
  transition: all 0.2s ease;
}
.scroll-to-bottom-enter-from,
.scroll-to-bottom-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>

<!-- <style scoped lang="less">
.chat-box {
  :deep(.n-tabs-tab-wrapper) {
    height: 20px !important;
  }
}
</style> -->
