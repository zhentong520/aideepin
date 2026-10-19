<script setup lang='ts'>
import { nextTick, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { NButton, NTabPane, NTabs, useMessage } from 'naive-ui'
import SubList from './SubList.vue'
import { useAuthStore, useWfStore } from '@/store'
import api from '@/api'
import { t } from '@/locales'

const currentPage = ref<number>(1)
const pageSize = 20
const wfStore = useWfStore()
const { activeUuid, myWorkflows, publicWorkflows, selectedType } = storeToRefs<any>(wfStore)
const authStore = useAuthStore()
const authStoreRef = ref<AuthState>(authStore)
const innerHeight = window.innerHeight < 800 ? 800 : window.innerHeight - 60
const ms = useMessage()
const importing = ref<boolean>(false)
const importInputRef = ref<HTMLInputElement | null>(null)

async function initAll() {
  await loadWfComponents()
  await initMyList()
  await initPublicList()
}

async function initWhenNotLogin() {
  await loadWfComponents()
  await initPublicList()
}

async function loadWfComponents() {
  if (wfStore.wfComponents.length > 0)
    return

  const { data: components } = await api.workflowComponents<Workflow.WorkflowComponent[]>()
  if (components && components.length > 0)
    wfStore.setWorkflowComponents(components)
}

async function initMyList() {
  if (wfStore.loadingMyWorkflows || wfStore.myWorkflows.length > 0)
    return
  wfStore.setLoadingMyWorkflows(true)
  try {
    const { data } = await api.workflowSearchMine<Workflow.InfoListResp>('', currentPage.value, pageSize)
    if (data.records) {
      nextTick(() => {
        wfStore.appendWorkflows(data.records, true)
        // 首次打开工作流时
        if (activeUuid.value === 'default') {
          wfStore.setActiveAndGo(myWorkflows.value[0].uuid)
        }
      })
    }
  } catch (e) {
    console.error(e)
  } finally {
    wfStore.setLoadingMyWorkflows(false)
  }
}

async function initPublicList() {
  if (wfStore.loadingPublicWorkflows || wfStore.publicWorkflows.length > 0)
    return
  wfStore.setLoadingPublicWorkflows(true)
  try {
    const { data: publicData } = await api.workflowSearchPublic<Workflow.InfoListResp>('', currentPage.value, pageSize)
    if (publicData.records)
      wfStore.appendWorkflows(publicData.records, false)
  } catch (e) {
    console.error(e)
  } finally {
    wfStore.setLoadingPublicWorkflows(false)
  }
}

function handleAdd(this: any) {
  wfStore.setShowCreateView(true, '')
}

// ==================== T3-14：导入工作流（JSON 文件 → POST /workflow/import） ====================
function triggerImport() {
  importInputRef.value?.click()
}

async function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 允许连续选择同一个文件
  input.value = ''
  if (!file)
    return

  let payload: unknown
  try {
    payload = JSON.parse(await file.text())
  } catch {
    ms.error(t('workflow.importFileInvalid'))
    return
  }

  importing.value = true
  try {
    const { data } = await api.workflowImport<Workflow.WorkflowInfo>(payload)
    ms.success(t('workflow.importSuccess'))
    if (data?.uuid)
      wfStore.addWorkflowAndActive(data)
  } catch (err: any) {
    const res = err as { code?: string, message?: string }
    // A0079：缺失知识包 —— 错误体列出全部缺失 uuid，必须展示清楚
    if (res?.code === 'A0079')
      ms.error(`${t('workflow.importMissingKbTitle')}${res.message || ''}`, { duration: 8000 })
    else
      ms.error(`${t('workflow.importFailed')}${res?.message ? `：${res.message}` : ''}`, { duration: 8000 })
  } finally {
    importing.value = false
  }
}

watch(
  () => authStoreRef.value.token,
  (newVal) => {
    if (newVal)
      initMyList()
  },
)

onMounted(() => {
  if (authStoreRef.value.token)
    initAll()
  else
    initWhenNotLogin()
})
</script>

<template>
  <NTabs v-model:value="selectedType" tab-class="h-10" pane-class="h-full" type="line" justify-content="space-evenly">
    <NTabPane name="mine" :tab="t('common.mine')" size="small">
      <div class="flex flex-col space-y-2" :style="`height:${innerHeight}px`">
        <div class="px-4 flex flex-col space-y-2">
          <NButton dashed block @click="handleAdd">
            {{ t('workflow.newApp') }}
          </NButton>
          <!-- T3-14：导入工作流（JSON） -->
          <NButton dashed block :loading="importing" @click="triggerImport">
            {{ t('workflow.importBtn') }}
          </NButton>
          <input
            ref="importInputRef" type="file" accept=".json,application/json" class="hidden"
            @change="onImportFileChange"
          >
        </div>
        <SubList :list="myWorkflows" :active-wf-uuid="activeUuid" />
      </div>
    </NTabPane>
    <NTabPane name="public" :tab="t('common.public')">
      <SubList :list="publicWorkflows" :active-wf-uuid="activeUuid" />
    </NTabPane>
  </NTabs>
</template>
