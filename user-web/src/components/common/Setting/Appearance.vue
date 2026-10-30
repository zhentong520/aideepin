<script lang="ts" setup>
/**
 * 画面设计（Appearance）：整个聊天框的自定义配置面板。
 * 配置存于 appStore.chatAppearance（sessionStorage appSetting），即时生效、无需保存按钮。
 */
import { computed } from 'vue'
import { NButton, NDivider, NSelect, NSlider, NSwitch } from 'naive-ui'
import type { ChatAppearance as ChatAppearanceState, ChatContentWidth, ChatMsgStyle, ChatSendButtonPosition } from '@/store/modules/app/helper'
import { useAppStore } from '@/store'
import { t } from '@/locales'

const appStore = useAppStore()

const appearance = computed<ChatAppearanceState>(() => appStore.chatAppearance)

const msgStyleOptions = computed(() => [
  { label: t('setting.msgStyleFlat'), value: 'flat' as ChatMsgStyle },
  { label: t('setting.msgStyleBubble'), value: 'bubble' as ChatMsgStyle },
])

const contentWidthOptions = computed(() => [
  { label: t('setting.widthCompact'), value: 'compact' as ChatContentWidth },
  { label: t('setting.widthWide'), value: 'wide' as ChatContentWidth },
  { label: t('setting.widthFull'), value: 'full' as ChatContentWidth },
])

const sendPositionOptions = computed(() => [
  { label: t('setting.sendRight'), value: 'right' as ChatSendButtonPosition },
  { label: t('setting.sendBelow'), value: 'below' as ChatSendButtonPosition },
])

function patch<K extends keyof ChatAppearanceState>(key: K, value: ChatAppearanceState[K]) {
  appStore.setChatAppearance({ [key]: value } as Partial<ChatAppearanceState>)
}

function resetDefaults() {
  appStore.resetChatAppearance()
}
</script>

<template>
  <div class="p-4 min-h-[200px]">
    <!-- ===== 消息区布局 ===== -->
    <NDivider title-placement="left" class="!mt-0">
      {{ t('setting.appearanceLayoutGroup') }}
    </NDivider>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.msgStyle') }}</span>
      <NSelect
        style="width: 160px" :value="appearance.msgStyle" :options="msgStyleOptions"
        @update-value="(v: ChatMsgStyle) => patch('msgStyle', v)"
      />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.contentWidth') }}</span>
      <NSelect
        style="width: 160px" :value="appearance.contentWidth" :options="contentWidthOptions"
        @update-value="(v: ChatContentWidth) => patch('contentWidth', v)"
      />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showAvatar') }}</span>
      <NSwitch :value="appearance.showAvatar" @update:value="(v: boolean) => patch('showAvatar', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showTimestamp') }}</span>
      <NSwitch :value="appearance.showTimestamp" @update:value="(v: boolean) => patch('showTimestamp', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showMetaInfo') }}</span>
      <NSwitch :value="appearance.showMetaInfo" @update:value="(v: boolean) => patch('showMetaInfo', v)" />
    </div>

    <!-- ===== 字体与气泡 ===== -->
    <NDivider title-placement="left">
      {{ t('setting.fontSize') }} / {{ t('setting.bubbleRadius') }}
    </NDivider>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.fontSize') }}</span>
      <NSlider
        class="flex-1" :min="12" :max="18" :step="1" :format-tooltip="(v: number) => `${v}px`"
        :value="appearance.fontSize" @update:value="(v: number) => patch('fontSize', v)"
      />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.bubbleRadius') }}</span>
      <NSlider
        class="flex-1" :min="0" :max="24" :step="2" :disabled="appearance.msgStyle !== 'bubble'"
        :format-tooltip="(v: number) => `${v}px`"
        :value="appearance.bubbleRadius" @update:value="(v: number) => patch('bubbleRadius', v)"
      />
    </div>

    <!-- ===== 输入区按钮 ===== -->
    <NDivider title-placement="left">
      {{ t('setting.appearanceInputGroup') }}
    </NDivider>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.sendButtonPosition') }}</span>
      <NSelect
        style="width: 160px" :value="appearance.sendButtonPosition" :options="sendPositionOptions"
        @update-value="(v: ChatSendButtonPosition) => patch('sendButtonPosition', v)"
      />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showThinkingSwitch') }}</span>
      <NSwitch :value="appearance.showThinkingSwitch" @update:value="(v: boolean) => patch('showThinkingSwitch', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showWebSearchSwitch') }}</span>
      <NSwitch :value="appearance.showWebSearchSwitch" @update:value="(v: boolean) => patch('showWebSearchSwitch', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showContextToggle') }}</span>
      <NSwitch :value="appearance.showContextToggle" @update:value="(v: boolean) => patch('showContextToggle', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showImageUpload') }}</span>
      <NSwitch :value="appearance.showImageUpload" @update:value="(v: boolean) => patch('showImageUpload', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showKnowledgeEntry') }}</span>
      <NSwitch :value="appearance.showKnowledgeEntry" @update:value="(v: boolean) => patch('showKnowledgeEntry', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showMcpEntry') }}</span>
      <NSwitch :value="appearance.showMcpEntry" @update:value="(v: boolean) => patch('showMcpEntry', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showVoiceButton') }}</span>
      <NSwitch :value="appearance.showVoiceButton" @update:value="(v: boolean) => patch('showVoiceButton', v)" />
    </div>

    <!-- ===== 消息操作 ===== -->
    <NDivider title-placement="left">
      {{ t('setting.appearanceMsgActionGroup') }}
    </NDivider>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showFeedback') }}</span>
      <NSwitch :value="appearance.showFeedback" @update:value="(v: boolean) => patch('showFeedback', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showRefEntries') }}</span>
      <NSwitch :value="appearance.showRefEntries" @update:value="(v: boolean) => patch('showRefEntries', v)" />
    </div>
    <div class="flex items-center space-x-4 mb-4">
      <span class="flex-shrink-0 w-[100px]">{{ t('setting.showQuickActions') }}</span>
      <NSwitch :value="appearance.showQuickActions" @update:value="(v: boolean) => patch('showQuickActions', v)" />
    </div>

    <div class="flex justify-end mt-6">
      <NButton size="small" @click="resetDefaults">
        {{ t('setting.resetAppearance') }}
      </NButton>
    </div>
  </div>
</template>
