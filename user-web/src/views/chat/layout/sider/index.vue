<script setup lang='ts'>
import type { CSSProperties } from 'vue'
import { computed, watch } from 'vue'
import { NLayoutSider } from 'naive-ui'
import List from './List.vue'
import { useAppStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'

/**
 * 侧边栏现在只是「场景列表」的容器。
 *
 * 2026-10-05：场景与智能体都由管理员在管理后台配置，对所有用户共享。
 * 用户端因此**移除了「新建场景」入口**（原来那个弹窗等于让终端用户替管理员做配置），
 * 这里也不再挂 CreateConv —— 用户端只读场景列表。
 */
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
    :collapsed="collapsed" :collapsed-width="0" :width="260" :show-trigger="isMobile ? false : true"
    position="absolute" bordered :style="getMobileClass" @update-collapsed="handleUpdateCollapsed"
  >
    <div class="flex flex-col h-full" :style="mobileSafeArea">
      <main class="flex flex-col flex-1 min-h-0">
        <div class="flex-1 min-h-0 pb-4 overflow-hidden">
          <List />
        </div>
      </main>
      <!-- <Footer /> -->
    </div>
  </NLayoutSider>
  <template v-if="isMobile">
    <div v-show="!collapsed" class="fixed inset-0 z-40 bg-black/40" @click="handleUpdateCollapsed" />
  </template>
</template>
