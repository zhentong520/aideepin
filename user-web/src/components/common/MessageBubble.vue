<script setup lang='ts'>
/**
 * 消息气泡公共组件（T5-09）：chat 与 draw 两份 Message/index.vue 的合一实现。
 * 业务差异全部 props/emit 化：
 * - 下拉菜单项与选择事件由调用方持有（dropdownOptions + dropdownSelect）
 * - 思考过程折叠仅 chat 使用（传 thinkingContent 才渲染）
 * - 图片预览行为（draw 详情跳转）经 imagePreviewDisabled + imageClick 定制
 */
import { h, ref } from 'vue'
import { NButton, NCollapse, NCollapseItem, NDropdown, NIcon, NImage, NSpace, NSpin, useDialog } from 'naive-ui'
import type { DropdownOption, ImageRenderToolbarProps } from 'naive-ui'
import { Delete24Regular } from '@vicons/fluent'
import { Reload } from '@vicons/ionicons5'
import AvatarComponent from '@/views/chat/components/Message/Avatar.vue'
import TextComponent from '@/views/chat/components/Message/Text.vue'
import { SvgIcon } from '@/components/common'
import { formatDuration } from '@/utils/format'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import { t } from '@/locales'
import { useAuthStore } from '@/store'
import { getRealFileUrl } from '@/utils/functions'

import NoPic from '@/assets/no_pic.png'
interface Props {
  dateTime?: string
  text?: string
  imageUrls?: string[]
  inversion?: boolean
  showAvatar?: boolean
  error?: boolean
  loading?: boolean
  type: string // text,text-image,image
  aiModelPlatform?: string
  // 思考过程（chat）：传入才渲染折叠面板
  thinkingContent?: string
  thinking?: boolean
  // 最终答案的加载中
  regenerate?: boolean
  // 消息元信息
  inputTokens?: number
  outputTokens?: number
  duration?: number
  toolCalls?: { toolName: string; durationMs: number; success: boolean }[]
  // 原文显示（draw 的提示词固定显示原文）
  asRawText?: boolean
  // 图片预览定制（draw 点击图片打开详情而非放大预览）
  imagePreviewDisabled?: boolean
  // 侧边下拉菜单（差异全部外置）
  dropdownOptions: DropdownOption[]
  dropdownTrigger?: 'click' | 'hover'
}

withDefaults(defineProps<Props>(), {
  dateTime: '',
  text: '',
  showAvatar: true,
  asRawText: false,
  dropdownTrigger: 'hover',
})

const emit = defineEmits<Emit>()

interface Emit {
  (ev: 'regenerate'): void
  (ev: 'dropdownSelect', key: string | number): void
  (ev: 'toggleRenderType'): void
  (ev: 'delOneImage', fileUrl: string): void
  (ev: 'imageClick'): void
}

const { isMobile } = useBasicLayout()
const dialog = useDialog()
const authStore = useAuthStore()
const token = ref<string>(authStore.token)

const messageRef = ref<HTMLElement>()
const textRef = ref<HTMLElement>()

const expandedNames = ref<string[]>(['finalAnswer'])

function itemHeadClick(data: { name: string | number; expanded: boolean; event: MouseEvent }) {
  const idx = expandedNames.value.findIndex(name => name === data.name.toString())
  if (idx === -1 && data.expanded)
    expandedNames.value.push(data.name.toString())

  if (idx !== -1 && !data.expanded)
    expandedNames.value.splice(idx, 1)
}

function handleSelect(key: string | number) {
  if (key === 'toggleRenderType') {
    emit('toggleRenderType')
    return
  }
  emit('dropdownSelect', key)
}

function handleRegenerate() {
  messageRef.value?.scrollIntoView()
  emit('regenerate')
}

function handleDelImage(imageUrl: string) {
  dialog.warning({
    title: t('chat.deleteImageConfirmTitle'),
    content: t('chat.deleteImageConfirmContent'),
    positiveText: t('common.yes'),
    negativeText: t('common.no'),
    onPositiveClick: async () => {
      emit('delOneImage', imageUrl)
    },
  })
}

function renderToolbarOut2(imageUrl: string) {
  return ({ nodes }: ImageRenderToolbarProps) => {
    return [
      ...Object.values(nodes),
      h(
        NButton,
        {
          quaternary: true,
          circle: true,
          color: 'white',
          onClick: () => {
            handleDelImage(imageUrl)
          },
        },
        {
          icon: () => h(Delete24Regular),
        },
      ),
    ]
  }
}

defineExpose({ messageRef })
</script>

<template>
  <div ref="messageRef" class="flex w-full mb-6 overflow-hidden" :class="[{ 'flex-row-reverse': inversion }]">
    <div
      v-if="showAvatar"
      class="flex items-center justify-center flex-shrink-0 h-8 overflow-hidden rounded-full basis-8"
      :class="[inversion ? 'ml-2' : 'mr-2']"
    >
      <AvatarComponent :name="inversion ? 'user' : aiModelPlatform" />
    </div>
    <div class="overflow-hidden text-sm " :class="[inversion ? 'items-end' : 'items-start']">
      <p class="text-xs text-[#b4bbc4]" :class="[inversion ? 'text-right' : 'text-left']">
        {{ dateTime }}
        <span v-if="inputTokens != null" class="ml-1">📥 {{ inputTokens }}</span>
        <span v-if="outputTokens != null" class="ml-1">📤 {{ outputTokens }}</span>
        <span v-if="duration != null && formatDuration(duration)" class="ml-1">⏱ {{ formatDuration(duration) }}</span>
        <template v-if="toolCalls?.length">
          <span class="ml-1">🔧 {{ toolCalls.length }}</span>
          <span v-for="(tool, idx) in toolCalls" :key="idx" class="ml-1 text-[10px] opacity-70">
            {{ tool.toolName }}({{ formatDuration(tool.durationMs) }}{{ tool.success ? '' : '✗' }})
          </span>
        </template>
      </p>
      <div class="flex items-start gap-1 mt-2" :class="[inversion ? 'flex-row-reverse' : 'flex-row']">
        <template v-if="type === 'text' || type === 'text-image'">
          <NCollapse
            v-if="thinkingContent" :default-expanded-names="['finalAnswer']" :expanded-names="expandedNames"
            @item-header-click="itemHeadClick"
          >
            <NCollapseItem :title="t('chat.deepThinking')" name="thinking">
              <TextComponent
                ref="textRef" :inversion="inversion" :error="error" :text="thinkingContent"
                :loading="thinking" :as-raw-text="asRawText ?? inversion"
              />
            </NCollapseItem>
            <NCollapseItem :title="t('chat.finalAnswer')" name="finalAnswer">
              <TextComponent
                ref="textRef" :inversion="inversion" :error="error" :text="text" :loading="loading"
                :as-raw-text="asRawText ?? inversion"
              />
            </NCollapseItem>
          </NCollapse>
          <TextComponent
            v-else ref="textRef" :inversion="inversion" :error="error" :text="text" :loading="loading"
            :as-raw-text="asRawText ?? inversion"
          />
          <div class="flex flex-col">
            <button
              v-if="regenerate"
              class="mb-2 transition text-neutral-400 hover:text-neutral-800 dark:hover:text-neutral-300"
              @click="handleRegenerate"
            >
              <SvgIcon icon="ri:restart-line" />
            </button>
            <NDropdown
              :trigger="(isMobile || dropdownTrigger === 'click') ? 'click' : 'hover'"
              :placement="!inversion ? 'right' : 'left'" :options="dropdownOptions" @select="handleSelect"
            >
              <button class="transition text-neutral-400 hover:text-neutral-800 dark:hover:text-neutral-200">
                <SvgIcon icon="ri:more-2-fill" />
              </button>
            </NDropdown>
          </div>
        </template>
      </div>
      <NSpace class="mt-1" :style="inversion ? 'justify-content:flex-end;' : ''">
        <!-- render image -->
        <template v-if="type === 'image' || type === 'text-image'">
          <template v-if="loading">
            <NSpin size="medium">
              <template #icon>
                <NIcon>
                  <Reload />
                </NIcon>
              </template>
            </NSpin>
          </template>
          <template v-else>
            <template v-if="!imageUrls || imageUrls.length === 0">
              <slot name="imageEmpty" />
            </template>
            <template v-if="imageUrls && imageUrls.length > 0">
              <NSpace>
                <template v-for="imageUrl in imageUrls" :key="imageUrl">
                  <NImage
                    v-if="imageUrl" width="100" :src="`${getRealFileUrl(imageUrl)}?token=${token}`"
                    :fallback-src="NoPic" :render-toolbar="renderToolbarOut2(imageUrl)"
                    :preview-disabled="imagePreviewDisabled" @click="imagePreviewDisabled && emit('imageClick')"
                  />
                </template>
              </NSpace>
            </template>
          </template>
        </template>
      </NSpace>
      <slot />
    </div>
  </div>
</template>
