<script lang="ts" setup>
import { ref, watch } from 'vue'
import { NModal } from 'naive-ui'
import EditConvDetail from './EditConvDetail.vue'
import { useAuthStore, useMcpStore } from '@/store'
import { emptyCharacter } from '@/utils/functions'
import api from '@/api'

interface Props {
  showModal: boolean
  character: Chat.Character
}
interface Emit {
  (ev: 'showModal', show: boolean): void
}
const props = withDefaults(defineProps<Props>(), {
  showModal: false,
})
const emit = defineEmits<Emit>()
const mcpStore = useMcpStore()
const authStore = useAuthStore()
const innerShow = ref<boolean>(props.showModal)
const tmpCharacter = ref<Chat.Character>(emptyCharacter())

function initEditCharacter(item: Chat.Character) {
  Object.assign(tmpCharacter.value, item)
}

function handleSubmitted() {
  emit('showModal', false)
}

watch(() => props.showModal, (val) => {
  innerShow.value = val
})

watch(() => props.character, (val) => {
  if (val)
    initEditCharacter(val)
})

watch(() => innerShow.value, (val) => {
  if (!val)
    emit('showModal', false)
})

// Load user MCP list when user is logged in
async function loadMyUserMcpList() {
  if (mcpStore.userMcpLoading || !authStore.token || mcpStore.myUserMcpList.length > 0)
    return
  try {
    mcpStore.setUserMcpLoading(true)
    const { data } = await api.userMcpList<Mcp.UserMcpListResp>(1, 200)
    if (data.records.length > 0)
      mcpStore.appendMyUserMcpList(data.records)
  } catch (error) {
    console.error(error)
  } finally {
    mcpStore.setUserMcpLoading(false)
  }
}
watch(
  () => authStore.token,
  () => {
    if (authStore.token)
      loadMyUserMcpList()
  },
  { immediate: true },
)
</script>

<template>
  <!-- 坑（2026-10-03 修）：本弹窗是「新建/编辑会话角色」表单，用户可能已填入大量内容。
       naive-ui NModal 默认 maskClosable=true，点遮罩即关窗且不提示 → 静默丢失用户输入。
       参考项目内 ApiKeyModal.vue 的既有写法，改为遮罩不可关，只能显式点取消/保存/关闭。 -->
  <NModal
    v-model:show="innerShow" style="width: 90%; max-width: 640px" preset="card" :mask-closable="false"
    :title="`${tmpCharacter.title} - 编辑`"
  >
    <EditConvDetail :character="tmpCharacter" @submitted="handleSubmitted" />
  </NModal>
</template>
