<script setup lang="ts">
// T9-05：版本历史与一键回滚。回滚本身生成新版本，不覆盖历史。
import { onMounted, ref, watch } from 'vue'
import { NButton, NEmpty, NModal, NPagination, NPopconfirm, NTag, useMessage } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'

interface Props {
  workflow: Workflow.WorkflowInfo
  show: boolean
}
const props = defineProps<Props>()
const emit = defineEmits<{ (e: 'update:show', v: boolean): void }>()

const ms = useMessage()
const loading = ref(false)
const rollbacking = ref<number | null>(null)
const versions = ref<Workflow.WfVersion[]>([])
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)

async function load() {
  if (!props.workflow.uuid)
    return
  loading.value = true
  try {
    const { data } = await api.workflowVersionList<{ records: Workflow.WfVersion[], total: number }>(
      props.workflow.uuid, currentPage.value, pageSize)
    versions.value = data.records ?? []
    total.value = Number(data.total ?? 0)
  }
  finally {
    loading.value = false
  }
}

async function onRollback(versionNo: number) {
  rollbacking.value = versionNo
  try {
    await api.workflowVersionRollback(props.workflow.uuid, versionNo)
    ms.success(t('workflow.rollbackSuccess'))
    // 回滚后刷新：列表顶部会出现 rollback from v{n} 的新版本
    currentPage.value = 1
    await load()
  }
  catch (error: any) {
    if (error?.message)
      ms.error(error.message)
  }
  finally {
    rollbacking.value = null
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  load()
}

watch(() => props.show, (val) => {
  if (val) {
    currentPage.value = 1
    load()
  }
})

onMounted(() => {
  if (props.show)
    load()
})
</script>

<template>
  <NModal
    :show="props.show" :title="t('workflow.versionHistory')" preset="card" style="width: 90%; max-width: 720px"
    :mask-closable="true" content-style="min-height: 200px" @update:show="(v: boolean) => emit('update:show', v)"
  >
    <NEmpty v-if="!loading && !versions.length" :description="t('workflow.versionEmpty')" />
    <template v-else>
      <div v-for="v of versions" :key="v.versionNo" class="flex items-center justify-between py-2 border-b dark:border-neutral-800">
        <div class="flex items-center gap-2 min-w-0">
          <span class="font-semibold">v{{ v.versionNo }}</span>
          <NTag v-if="v.rollback" size="small" type="warning" :bordered="false">
            {{ t('workflow.versionRollbackMark') }}
          </NTag>
          <span class="text-sm text-neutral-500 truncate">{{ v.remark || v.title }}</span>
        </div>
        <div class="flex items-center gap-3 shrink-0">
          <span class="text-xs text-neutral-400">
            {{ t('workflow.versionNodeDiff', { n: v.nodeCount + (v.nodeDiff >= 0 ? `(+${v.nodeDiff})` : `(${v.nodeDiff})`) }) }}
            ·
            {{ t('workflow.versionEdgeDiff', { n: v.edgeCount + (v.edgeDiff >= 0 ? `(+${v.edgeDiff})` : `(${v.edgeDiff})`) }) }}
          </span>
          <span class="text-xs text-neutral-400">{{ v.modifierUserName || '' }} {{ v.createTime }}</span>
          <NPopconfirm @positive-click="onRollback(v.versionNo)">
            <template #trigger>
              <NButton size="tiny" type="warning" ghost :loading="rollbacking === v.versionNo">
                {{ t('workflow.versionRollback') }}
              </NButton>
            </template>
            {{ t('workflow.versionRollbackConfirm', { no: v.versionNo }) }}
          </NPopconfirm>
        </div>
      </div>
      <div class="flex justify-end mt-3">
        <NPagination :page="currentPage" :page-size="pageSize" :item-count="total" @update:page="onPageChange" />
      </div>
    </template>
  </NModal>
</template>
