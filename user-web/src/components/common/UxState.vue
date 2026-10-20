<script setup lang="ts">
/**
 * T4-03 交互四态统一组件：loading（骨架屏）/ empty / error
 * 文案由调用方经 t() 传入（不新增 i18n key）；全站空/加载/错误态统一走本组件，消灭「白屏 + 无提示」
 */
import { computed } from 'vue'
import { NButton, NEmpty, NSkeleton } from 'naive-ui'

const props = withDefaults(defineProps<{
  state?: 'loading' | 'empty' | 'error'
  text?: string
  retryText?: string
  showRetry?: boolean
  skeletonRows?: number
}>(), {
  state: 'empty',
  skeletonRows: 4,
})

const emit = defineEmits<{ (e: 'retry'): void }>()

const isError = computed(() => props.state === 'error')
const isEmpty = computed(() => props.state === 'empty' || isError.value)
</script>

<template>
  <div v-if="state === 'loading'" class="w-full flex flex-col gap-s12 p-s24">
    <NSkeleton
      v-for="i in skeletonRows"
      :key="i"
      text
      :sharp="false"
      size="medium"
      class="!w-full"
      :style="{ width: i === skeletonRows ? '62%' : '100%' }"
    />
  </div>
  <div v-else-if="isEmpty" class="w-full flex items-center justify-center py-12">
    <NEmpty :description="text" :size="isError ? 'large' : 'medium'">
      <template v-if="isError && showRetry" #extra>
        <NButton size="small" @click="emit('retry')">
          {{ retryText }}
        </NButton>
      </template>
    </NEmpty>
  </div>
</template>
