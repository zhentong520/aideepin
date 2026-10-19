<script lang="ts" setup>
import { computed, nextTick } from 'vue'
import { HoverButton, SvgIcon } from '@/components/common'
import { useAppStore, useChatStore, useSceneStore } from '@/store'

interface Props {
  usingContext: boolean
}

interface Emit {
  (ev: 'export'): void
  (ev: 'toggleUsingContext'): void
}

defineProps<Props>()

const emit = defineEmits<Emit>()

const appStore = useAppStore()
const chatStore = useChatStore()

const collapsed = computed(() => appStore.siderCollapsed)
const currentChatHistory = computed(() => chatStore.getCurCharacter)

const sceneStore = useSceneStore()
// 标题优先用场景名：currentChatHistory.title 是从后台智能体模板复制出来的会话名，
// 展示会漏「智能体」这个后台概念给用户。与 PcHeader 保持一致。
const headerTitle = computed(() => {
  const sceneTitle = sceneStore.resolveSceneTitle(currentChatHistory.value?.uuid ?? '')
  return sceneTitle || currentChatHistory.value?.title || ''
})

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
}

function onScrollToTop() {
  const scrollRef = document.querySelector('#scrollRef')
  if (scrollRef)
    nextTick(() => scrollRef.scrollTop = 0)
}

// function handleExport() {
//   emit('export')
// }

function toggleUsingContext() {
  emit('toggleUsingContext')
}
</script>

<template>
  <header
    class="sticky top-0 left-0 right-0 z-30 border-b dark:border-neutral-800 bg-white/80 dark:bg-black/20 backdrop-blur"
  >
    <div class="relative flex items-center justify-between min-w-0 overflow-hidden h-14">
      <div class="flex items-center">
        <button
          class="flex items-center justify-center w-11 h-11"
          @click="handleUpdateCollapsed"
        >
          <SvgIcon v-if="collapsed" class="text-2xl" icon="ri:align-justify" />
          <SvgIcon v-else class="text-2xl" icon="ri:align-right" />
        </button>
      </div>
      <h1
        class="flex-1 px-4 pr-6 overflow-hidden cursor-pointer select-none text-ellipsis whitespace-nowrap"
        @dblclick="onScrollToTop"
      >
        {{ headerTitle }}
      </h1>
      <div class="flex items-center space-x-2">
        <HoverButton @click="toggleUsingContext">
          <span class="text-xl" :class="{ 'text-[#4b9e5f]': usingContext, 'text-[#a8071a]': !usingContext }">
            <SvgIcon icon="ri:chat-history-line" />
          </span>
        </HoverButton>
        <!-- <HoverButton @click="handleExport">
          <span class="text-xl text-[#4f555e] dark:text-white">
            <SvgIcon icon="ri:download-2-line" />
          </span>
        </HoverButton> -->
      </div>
    </div>
  </header>
</template>
