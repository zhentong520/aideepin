<template>
  <div class="chat-page-wrap">
    <n-spin :show="loading">
      <div v-if="loadError" class="state-box">
        <n-result status="404" :title="t('chatpage.notFound')" :description="t('chatpage.notFoundDesc')">
          <template #footer>
            <n-button type="primary" @click="goHome">{{ t('chatpage.backHome') }}</n-button>
          </template>
        </n-result>
      </div>

      <div v-else-if="page" class="page-body">
        <div class="page-head">
          <h1 class="page-title">{{ page.name }}</h1>
        </div>

        <div
          class="blocks"
          :class="{ 'blocks-canvas': isCanvasLayout }"
          :style="isCanvasLayout ? { minHeight: `${canvasMinHeight}px` } : undefined"
        >
          <div
            v-for="(block, idx) in blocks"
            :key="`${block.type}-${idx}`"
            :style="blockStyle(block)"
          >
            <!-- 欢迎语 -->
            <div v-if="block.type === 'welcome'" class="block block-welcome">
              <h2 class="welcome-title">{{ block.title }}</h2>
              <p v-if="block.subtitle" class="welcome-subtitle">{{ block.subtitle }}</p>
            </div>

            <!-- 快捷提问 -->
            <div v-else-if="block.type === 'quickPrompts' && block.items?.length" class="block block-quick">
              <n-button
                v-for="(item, i) in block.items"
                :key="i"
                size="medium"
                secondary
                round
                @click="startChat(item)"
              >
                {{ item }}
              </n-button>
            </div>

            <!-- 自定义文案 -->
            <div v-else-if="block.type === 'richText' && block.content" class="block block-rich">
              <p class="rich-text">{{ block.content }}</p>
            </div>

            <!-- 场景信息 -->
            <div v-else-if="block.type === 'sceneInfo' && sceneInfo" class="block block-scene">
              <div class="scene-name">{{ sceneInfo.title }}</div>
              <div v-if="sceneInfo.remark" class="scene-remark">{{ sceneInfo.remark }}</div>
            </div>
          </div>
        </div>

        <div class="start-row">
          <n-button type="primary" size="large" :loading="resolving" :disabled="!page.sceneUuid" @click="startChat()">
            {{ t('chatpage.startChat') }}
          </n-button>
          <span v-if="!page.sceneUuid" class="start-hint">{{ t('chatpage.noSceneBound') }}</span>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { NButton, NResult, NSpin, useMessage } from 'naive-ui'
  import type { ChatPageBlock, ChatPageInfo } from '@/api/chatpage'
  import { useSceneStore } from '@/store'
  import { t } from '@/locales'
  import api from '@/api'

  const route = useRoute()
  const router = useRouter()
  const message = useMessage()
  const sceneStore = useSceneStore()

  const loading = ref(false)
  const resolving = ref(false)
  const loadError = ref(false)
  const page = ref<ChatPageInfo | null>(null)

  const blocks = computed<ChatPageBlock[]>(() => page.value?.pageSchema?.blocks ?? [])

  // P2 画布布局：全部区块带 layout 时按画布坐标自由摆放，任一缺失回退流式（数组顺序）
  const isCanvasLayout = computed(
    () => blocks.value.length > 0 && blocks.value.every((b) => b.layout),
  )

  const canvasMinHeight = computed(() => {
    const maxY = Math.max(...blocks.value.map((b) => b.layout?.y ?? 0), 0)
    return maxY + 320
  })

  function blockStyle(block: ChatPageBlock) {
    if (!isCanvasLayout.value || !block.layout) return undefined
    return {
      position: 'absolute' as const,
      left: `${block.layout.x}px`,
      top: `${block.layout.y}px`,
      width: `${block.layout.w}px`,
    }
  }

  const sceneInfo = computed(() => {
    if (!page.value?.sceneUuid) return null
    return sceneStore.scenes.find((s) => s.uuid === page.value!.sceneUuid) ?? null
  })

  async function load() {
    const uuid = route.params.uuid as string
    if (!uuid) {
      loadError.value = true
      return
    }
    loading.value = true
    try {
      // 页面配置与场景列表并行拉取：sceneInfo 区块和会话解析都依赖场景缓存
      const [pageRes] = await Promise.all([
        api.fetchChatPage<ChatPageInfo>(uuid),
        sceneStore.loadScenes().catch(() => undefined),
      ])
      page.value = pageRes.data
    } catch (e) {
      loadError.value = true
    } finally {
      loading.value = false
    }
  }

  /**
   * 解析「当前用户 × 页面绑定智能体」的会话 uuid。
   * agentUuid 覆盖优先；否则走场景默认智能体（sceneStore 已缓存）。
   */
  async function resolveConversation(): Promise<string> {
    if (!page.value) return ''
    if (page.value.agentUuid) {
      const { data } = await api.resolveAgentConv<string>(page.value.agentUuid)
      if (data) return data
    }
    const entry = sceneStore.entries.find((e) => e.scene.uuid === page.value!.sceneUuid)
    if (!entry) return ''
    if (entry.characterUuid) return entry.characterUuid
    if (entry.agent) {
      const { data } = await api.resolveAgentConv<string>(entry.agent.uuid)
      return data || ''
    }
    return ''
  }

  async function startChat(prefill?: string) {
    if (!page.value) return
    resolving.value = true
    try {
      const convUuid = await resolveConversation()
      if (!convUuid) {
        message.warning(t('chatpage.noAvailableAgent'))
        return
      }
      router.push({ path: `/chat/${convUuid}`, query: prefill ? { prefill } : undefined })
    } catch (e) {
      message.error(t('chatpage.resolveFailed'))
    } finally {
      resolving.value = false
    }
  }

  function goHome() {
    router.push('/chat/default')
  }

  onMounted(load)
</script>

<style scoped>
  .chat-page-wrap {
    min-height: 100vh;
    display: flex;
    justify-content: center;
    padding: 48px 16px;
  }
  .chat-page-wrap :deep(.n-spin-container),
  .chat-page-wrap :deep(.n-spin-content) {
    width: 100%;
  }
  .page-body {
    max-width: 640px;
    width: 100%;
  }
  .page-head {
    margin-bottom: 24px;
  }
  .page-title {
    font-size: 22px;
    font-weight: 600;
  }
  .blocks {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .blocks-canvas {
    position: relative;
    display: block;
  }
  .welcome-title {
    font-size: 18px;
    font-weight: 500;
  }
  .welcome-subtitle {
    color: #666;
    margin-top: 6px;
  }
  .block-quick {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
  .rich-text {
    color: #555;
    line-height: 1.7;
    white-space: pre-wrap;
  }
  .block-scene {
    border: 1px solid #e5e5e5;
    border-radius: 10px;
    padding: 14px 16px;
  }
  .scene-name {
    font-weight: 500;
  }
  .scene-remark {
    color: #777;
    font-size: 13px;
    margin-top: 4px;
  }
  .start-row {
    margin-top: 32px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .start-hint {
    color: #999;
    font-size: 13px;
  }
  .state-box {
    padding-top: 10vh;
  }
</style>
