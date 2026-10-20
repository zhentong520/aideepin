<script setup lang='ts'>
/**
 * 页面顶栏（T5-09）：chat/Header/pc.vue 与 knowledge-base/Header/pc.vue 的合一实现。
 * 标题 + 「更多」下拉；菜单项由调用方传入，弹窗类差异内容放 modals slot。
 */
import { NButton, NDropdown } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { SvgIcon } from '@/components/common'

withDefaults(defineProps<{
  title: string
  options: DropdownOption[]
  // 与 chat 头部一致的吸顶样式；kb 页面外层已有 sticky 容器时传 false
  sticky?: boolean
}>(), {
  sticky: true,
})

const emit = defineEmits<Emit>()

interface Emit {
  (e: 'select', key: string | number): void
}
</script>

<template>
  <header
    class="border-b dark:border-neutral-800 bg-white/80 dark:bg-black/20 backdrop-blur"
    :class="sticky ? 'sticky top-0 left-0 right-0 z-30' : 'left-0 top-0 z-30'"
  >
    <div class="relative flex items-center justify-between max-w-screen-xl px-4 m-auto h-12">
      <div class="flex items-center flex-col mx-2">
        <p class="text-sm">
          {{ title }}
        </p>
      </div>
      <NDropdown :options="options" @select="(key: string | number) => emit('select', key)">
        <NButton quaternary circle size="small">
          <template #icon>
            <SvgIcon icon="ri:more-fill" />
          </template>
        </NButton>
      </NDropdown>
    </div>
    <slot name="modals" />
  </header>
</template>
