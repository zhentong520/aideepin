<script setup lang='ts'>
/** chat 消息气泡薄壳（T5-09）：渲染逻辑统一在 components/common/MessageBubble.vue，仅保留业务差异 */
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import MessageBubble from '@/components/common/MessageBubble.vue'
import { UxState } from '@/components/common'
import { copyText } from '@/utils/format'
import { useIconRender } from '@/hooks/useIconRender'
import { t } from '@/locales'

const props = withDefaults(defineProps<Props>(), {
  showAvatar: true,
})
const emit = defineEmits<Emit>()

interface Props {
  dateTime?: string
  thinkingContent?: string
  text?: string
  imageUrls?: string[]
  inversion?: boolean
  regenerate?: boolean
  showAvatar?: boolean
  error?: boolean
  // 思考中
  thinking?: boolean
  // 最终答案的加载中
  loading?: boolean
  type: string // text,text-image,image

  aiModelPlatform?: string // openai,dashscope,qianfan,ollama,deepseek
  inputTokens?: number
  outputTokens?: number
  duration?: number
  toolCalls?: { toolName: string; durationMs: number; success: boolean }[]
}

interface Emit {
  (ev: 'regenerate'): void
  (ev: 'delete'): void
  (ev: 'collectSample'): void
  (ev: 'delOneImage', fileUrl: string): void
}

// 坑（2026-10-03 修）：复制此前完全无反馈，用户无法确认是否复制成功
const ms = useMessage()
const { iconRender } = useIconRender()

// 用户消息默认显示原文；切换状态持有在壳层（菜单标签依赖它）
const asRawText = ref(!!props.inversion)

const dropdownOptions = computed(() => {
  const common = [
    {
      label: t('chat.copy'),
      key: 'copyText',
      icon: iconRender({ icon: 'ri:file-copy-2-line' }),
    },
    {
      label: t('common.delete'),
      key: 'delete',
      icon: iconRender({ icon: 'ri:delete-bin-line' }),
    },
  ]

  if (!props.inversion) {
    common.unshift({
      label: asRawText.value ? t('chat.preview') : t('chat.showRawText'),
      key: 'toggleRenderType',
      icon: iconRender({ icon: asRawText.value ? 'ic:outline-code-off' : 'ic:outline-code' }),
    })
    // 收集为训练样本：回答消息才有意义（服务端按 parent_message_id 自动补全成对）
    common.splice(1, 0, {
      label: t('chat.collectSample'),
      key: 'collectSample',
      icon: iconRender({ icon: 'ri:file-add-line' }),
    })
  }

  return common
})

function handleSelect(key: string | number) {
  switch (key) {
    case 'copyText':
      // 坑（2026-10-03 修）：copyText 改为返回 Promise<boolean>（Clipboard API 优先 + execCommand 降级），
      // 这里根据真实结果给出成功/失败提示（此前点复制后毫无反应）
      copyText({ text: props.text ?? '' })
        .then((ok) => {
          if (ok)
            ms.success(t('common.copySuccess'))
          else
            ms.error(t('common.copyFailed'))
        })
      return
    case 'collectSample':
      emit('collectSample')
      return
    case 'delete':
      emit('delete')
  }
}
</script>

<template>
  <MessageBubble
    :date-time="dateTime"
    :text="text"
    :image-urls="imageUrls"
    :inversion="inversion"
    :show-avatar="showAvatar"
    :error="error"
    :thinking="thinking"
    :thinking-content="thinkingContent"
    :loading="loading"
    :type="type"
    :ai-model-platform="aiModelPlatform"
    :input-tokens="inputTokens"
    :output-tokens="outputTokens"
    :duration="duration"
    :tool-calls="toolCalls"
    :regenerate="regenerate"
    :as-raw-text="asRawText"
    :dropdown-options="dropdownOptions"
    @regenerate="emit('regenerate')"
    @del-one-image="(url: string) => emit('delOneImage', url)"
    @toggle-render-type="asRawText = !asRawText"
    @dropdown-select="handleSelect"
  >
    <template #imageEmpty>
      <UxState state="error" :text="t('chat.imageNotFound')" />
    </template>
    <slot />
  </MessageBubble>
</template>
