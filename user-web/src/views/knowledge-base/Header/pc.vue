<script lang="ts" setup>
import { h, ref } from 'vue'
import PageHeaderBar from '@/components/common/PageHeaderBar.vue'
import { ApiKeyModal, SvgIcon } from '@/components/common'
import { useAuthStore } from '@/store'
import { t } from '@/locales'
import { knowledgeBaseEmptyInfo } from '@/utils/functions'
import KbInfo from '@/views/knowledge-base/Header/KbInfo.vue'

interface Props {
  knowledgeBase: KnowledgeBase.Info
}
const props = withDefaults(defineProps<Props>(), {
  knowledgeBase: () => knowledgeBaseEmptyInfo(),
})
const showEditModal = ref(false)
const showApiKeyModal = ref(false)
const authStore = useAuthStore()

const options = [
  {
    label: t('common.detail'),
    key: 'detail',
    icon: () => h(SvgIcon, { icon: 'carbon:information', class: 'text-base cursor-pointer' }),
  },
  {
    label: t('extApi.apiAccess'),
    key: 'api',
    icon: () => h(SvgIcon, { icon: 'carbon:api', class: 'text-base cursor-pointer' }),
  },
]

function handleSelect(key: string | number) {
  if (key === 'detail') {
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
  <PageHeaderBar :title="props.knowledgeBase?.title ?? ''" :options="options" :sticky="false" @select="handleSelect">
    <template #modals>
      <KbInfo v-if="props.knowledgeBase && props.knowledgeBase.uuid" :show-modal="showEditModal" :knowledge-base="props.knowledgeBase" @show-modal="showOrCloseModal" />
      <ApiKeyModal v-model:show="showApiKeyModal" type="knowledge" :uuid="props.knowledgeBase?.uuid ?? ''" :title="props.knowledgeBase?.title ?? t('knowledgeBase.knowledgeBase')" />
    </template>
  </PageHeaderBar>
</template>
