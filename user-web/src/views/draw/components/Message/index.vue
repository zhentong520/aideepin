<script setup lang='ts'>
/** draw 消息气泡薄壳（T5-09）：渲染逻辑统一在 components/common/MessageBubble.vue，仅保留业务差异 */
import { computed } from 'vue'
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
  draw: Chat.Draw
  inversion?: boolean
  showAvatar?: boolean
  error?: boolean
  loading?: boolean
  type: string // text,text-image,image
  imageUrls?: string[]
}

interface Emit {
  (ev: 'openDetail', uuid: string): void
  (ev: 'delete'): void
  (ev: 'delOneImage', fileUrl: string): void
  (ev: 'setPublic', uuid: string, publicOrPrivate: boolean): void
}

// 坑（2026-10-03 修）：复制此前完全无反馈，用户无法确认是否复制成功
const ms = useMessage()
const { iconRender } = useIconRender()

const dropdownOptions = computed(() => {
  const setPublicOrPrivate = props.draw.isPublic
    ? {
        label: t('draw.privateLabel'),
        key: 'setPrivate',
        icon: iconRender({ icon: 'ri:lock-line' }),
      }
    : {
        label: t('draw.publicLabel'),
        key: 'setPublic',
        icon: iconRender({ icon: 'ri:cloud-line' }),
      }

  return [
    {
      label: t('chat.copy'),
      key: 'copyText',
      icon: iconRender({ icon: 'ri:file-copy-2-line' }),
    },
    setPublicOrPrivate,
    {
      label: t('common.delete'),
      key: 'delete',
      icon: iconRender({ icon: 'ri:delete-bin-line' }),
    },
  ]
})

function handleSelect(key: string | number) {
  switch (key) {
    case 'copyText':
      // 坑（2026-10-03 修）：copyText 返回 Promise<boolean>，按真实结果提示成功/失败
      copyText({ text: props.draw.prompt ?? '' })
        .then((ok) => {
          if (ok)
            ms.success(t('common.copySuccess'))
          else
            ms.error(t('common.copyFailed'))
        })
      return
    case 'setPublic':
      emit('setPublic', props.draw.uuid, true)
      return
    case 'setPrivate':
      emit('setPublic', props.draw.uuid, false)
      return
    case 'delete':
      emit('delete')
  }
}
</script>

<template>
  <MessageBubble
    :date-time="draw.createTime"
    :text="draw.prompt"
    :image-urls="imageUrls"
    :inversion="inversion"
    :show-avatar="showAvatar"
    :error="error"
    :loading="loading"
    :type="type"
    :ai-model-platform="draw.aiModelPlatform"
    :duration="draw.duration"
    :as-raw-text="true"
    :image-preview-disabled="true"
    dropdown-trigger="click"
    :dropdown-options="dropdownOptions"
    @del-one-image="(url: string) => emit('delOneImage', url)"
    @image-click="emit('openDetail', draw.uuid)"
    @dropdown-select="handleSelect"
  >
    <template #imageEmpty>
      <UxState state="error" :text="t('draw.imageNotFound')" />
    </template>
    <slot />
  </MessageBubble>
</template>
