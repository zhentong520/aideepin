<script lang="ts" setup>
import { computed } from 'vue'
import { NAvatar } from 'naive-ui'
// 直接引组件文件而非 '@/components/common' 聚合出口：后者会连带导出 Setting/PromptStore
// 等依赖 store 的组件，容易形成 Avatar -> components/common -> store -> ... -> Avatar 的环。
import SvgIcon from '@/components/common/SvgIcon/index.vue'
import { useUserStore } from '@/store'
import { isString } from '@/utils/is'
import defaultAvatar from '@/assets/avatar.jpg'

interface Props {
  name?: string
  imageSize?: number
}
defineProps<Props>()

const userStore = useUserStore()

const avatar = computed(() => userStore.userInfo.avatar)
</script>

<template>
  <!-- User's avatar -->
  <template v-if="name === 'user'">
    <NAvatar
      v-if="isString(avatar) && avatar.length > 0"
      :size="imageSize ? imageSize : 32"
      :src="avatar"
      :fallback-src="defaultAvatar"
      :style="{ backgroundColor: 'transparent' }"
    />
    <NAvatar
      v-else
      round
      :size="imageSize ? imageSize : 32"
      :src="defaultAvatar"
      style="background-color: transparent"
    />
  </template>
  <!--
    AI 侧头像（2026-10-05 改）：
    原来这里按 aiModelPlatform 分支显示 DeepSeek / 通义千问 / 文心 / OpenAI / Ollama 等厂商 logo，
    等于把底层模型厂商贴在每一条回答上，与「界面不暴露模型与厂商」的要求冲突。
    现在统一用中性的机器人图标，`name` 只用来区分用户/AI，不再用于选择厂商图。
    厂商 logo 资源仍保留在 assets/（管理端配置模型时可能用得上），只是对话气泡不再引用。
  -->
  <NAvatar
    v-else
    round
    :size="imageSize ? imageSize : 32"
    class="border border-brand-500 text-brand-500 bg-transparent"
  >
    <SvgIcon icon="ri:robot-2-line" class="text-base" />
  </NAvatar>
</template>
