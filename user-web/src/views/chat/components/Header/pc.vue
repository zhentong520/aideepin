<script lang="ts" setup>
import { h, ref } from 'vue'
import PageHeaderBar from '@/components/common/PageHeaderBar.vue'
import { SvgIcon } from '@/components/common'
import EditConv from './EditConv.vue'
import { ApiKeyModal } from '@/components/common'
import { useAuthStore } from '@/store'
import { t } from '@/locales'
import { emptyCharacter } from '@/utils/functions'

interface Props {
  character: Chat.Character
  /**
   * 场景名（用户端认知里的「这是哪个对话」）。
   * 传了就优先显示它；不传才退回 character.title。
   * 见 scene store 的 resolveSceneTitle：character.title 是后台智能体名，
   * 直接展示会漏后台概念给用户。
   */
  title?: string
}
const props = withDefaults(defineProps<Props>(), {
  character: () => emptyCharacter(),
  title: '',
})
const showEditModal = ref(false)
const showApiKeyModal = ref(false)
const authStore = useAuthStore()

const options = [
  {
    label: t('common.edit'),
    key: 'edit',
    icon: () => h(SvgIcon, { icon: 'carbon:edit', class: 'text-base cursor-pointer' }),
  },
  {
    label: t('extApi.apiAccess'),
    key: 'api',
    icon: () => h(SvgIcon, { icon: 'carbon:api', class: 'text-base cursor-pointer' }),
  },
]

function handleSelect(key: string | number) {
  if (key === 'edit') {
    if (!authStore.checkLoginOrShow())
      return
    showEditModal.value = true
  } else if (key === 'api') {
    showApiKeyModal.value = true
  }
}
function showOrCloseModal(show: boolean) {
  showEditModal.value = show
}
</script>

<template>
  <PageHeaderBar :title="title || props.character?.title || ''" :options="options" @select="handleSelect">
    <template #modals>
      <EditConv v-if="props.character" :show-modal="showEditModal" :character="props.character" @show-modal="showOrCloseModal" />
      <ApiKeyModal v-if="props.character" v-model:show="showApiKeyModal" type="character" :uuid="props.character.uuid" :title="props.character.title" />
    </template>
  </PageHeaderBar>
</template>
