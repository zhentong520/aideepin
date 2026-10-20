<script setup lang='ts'>
/**
 * 应用侧边栏布局壳（T5-09）：chat / workflow / knowledge-base 三份 sider/index.vue 的合一实现。
 * 只负责 NLayoutSider 折叠与移动端遮罩；列表内容由 default slot 提供，底部操作条走 footer slot。
 */
import type { CSSProperties } from 'vue'
import { computed, watch } from 'vue'
import { NLayoutSider } from 'naive-ui'
import { useAppStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'

withDefaults(defineProps<{
  width?: number
}>(), {
  width: 260,
})

const appStore = useAppStore()
const { isMobile } = useBasicLayout()

const collapsed = computed(() => appStore.siderCollapsed)

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
}

const getMobileClass = computed<CSSProperties>(() => {
  if (isMobile.value) {
    return {
      position: 'fixed',
      zIndex: 50,
    }
  }
  return {}
})

const mobileSafeArea = computed(() => {
  if (isMobile.value) {
    return {
      paddingBottom: 'env(safe-area-inset-bottom)',
    }
  }
  return {}
})

watch(
  isMobile,
  (val) => {
    appStore.setSiderCollapsed(val)
  },
  {
    immediate: true,
    flush: 'post',
  },
)
</script>

<template>
  <NLayoutSider
    :collapsed="collapsed" :collapsed-width="0" :width="width" :show-trigger="isMobile ? false : true"
    position="absolute" bordered :style="getMobileClass" @update-collapsed="handleUpdateCollapsed"
  >
    <div class="relative flex h-full flex-col" :style="mobileSafeArea">
      <main class="flex min-h-0 flex-1 flex-col pb-4">
        <div class="min-h-0 flex-1 overflow-hidden">
          <slot />
        </div>
      </main>
      <div
        v-if="$slots.footer"
        class="absolute bottom-0 left-0 right-0 px-4 py-3 border-t border-gray-200 bg-white dark:border-neutral-800 dark:bg-[#18181c]"
      >
        <slot name="footer" />
      </div>
    </div>
  </NLayoutSider>
  <template v-if="isMobile">
    <div v-show="!collapsed" class="fixed inset-0 z-40 bg-black/40" @click="handleUpdateCollapsed" />
  </template>
</template>
