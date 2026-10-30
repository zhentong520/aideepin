<script setup lang='ts'>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { NLayout, NLayoutContent } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import Sider from './sider/index.vue'
import { useBasicLayout } from '@/wf/hooks'
import { useAppStore, useWfStore } from '@/wf/stores'
import { useDesignerStore } from '../useDesignerStore'
import api from '@/wf/api'

const appStore = useAppStore()
const wfStore = useWfStore()
const designerStore = useDesignerStore()
// activeUuid 属于设计器会话状态（T5-10 迁入 useDesignerStore），从 wfStore 解构会是 undefined
const { activeUuid } = storeToRefs(designerStore)
const uroute = useRoute()
const { uuid: currWfUuid } = uroute.params as { uuid: string; viewType: string }
const { isMobile } = useBasicLayout()
const collapsed = computed(() => appStore.siderCollapsed)
const getMobileClass = computed(() => {
  if (isMobile.value)
    return ['rounded-none', 'shadow-none']
  return ['rounded-md', 'dark:border-neutral-800']
})
const getContainerClass = computed(() => {
  return [
    'h-full',
    { 'pl-[260px]': !isMobile.value && !collapsed.value },
  ]
})

/**
 * admin 的 .layout-content 使用 min-height:100vh（高度为 auto），无法为后代的
 * height:100% 提供确定基准，会让 vue-flow 画布塌陷为 0 高。
 * 这里显式把设计器根容器锚定到视口，重建确定高度链。
 */
const rootRef = ref<HTMLElement>()
function applyViewportHeight() {
  const el = rootRef.value
  if (!el)
    return
  const top = el.getBoundingClientRect().top
  const h = Math.max(window.innerHeight - top - 10, 420)
  // 必须带 important：容器上的 h-full 等工具类会与之竞争
  el.style.setProperty('height', `${h}px`, 'important')
  el.style.setProperty('min-height', '0', 'important')
}

// F5刷新时
if (currWfUuid !== 'default' && activeUuid.value === 'default') {
  designerStore.setActive(currWfUuid)
}

onMounted(async () => {
  applyViewportHeight()
  window.addEventListener('resize', applyViewportHeight)
  // 模型注册表：LLM 选择器（Answer/Classifier/KeywordExtractor 等）与图片模型选择器（通义万相）
  // 都读 appStore.llms/imageModels —— 此前无人调用 fetch 导致下拉恒为空（2026-10-30 用户反馈 #19/#22 根因）
  appStore.fetchLLMs()
  appStore.fetchImageModels()
  const { data: operators } = await api.workflowOperators<Workflow.Operator[]>()
  if (operators && operators.length > 0) {
    wfStore.setOperators(operators)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', applyViewportHeight)
})
</script>

<template>
  <div ref="rootRef" class="h-full dark:bg-[#24272e] transition-all" :class="[isMobile ? 'p-0' : '']">
    <div class="h-full overflow-hidden" :class="getMobileClass">
      <NLayout class="z-40 transition" :class="getContainerClass" has-sider>
        <Sider />
        <NLayoutContent class="h-full">
          <RouterView v-slot="{ Component, route }">
            <KeepAlive>
              <component :is="Component" :key="route.fullPath" />
            </KeepAlive>
          </RouterView>
        </NLayoutContent>
      </NLayout>
    </div>
  </div>
</template>
