<script setup lang="ts">
// T9-01：触发器管理（定时 cron / webhook）。webhook 地址带 token，复制给外部系统 POST 即触发。
import { onMounted, ref, watch } from 'vue'
import { NButton, NEmpty, NInput, NModal, NPopconfirm, NSwitch, NTag, useMessage } from 'naive-ui'
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
const saving = ref(false)
const triggers = ref<Workflow.WfTrigger[]>([])
const newCron = ref('')

async function load() {
  if (!props.workflow.uuid)
    return
  loading.value = true
  try {
    const { data } = await api.workflowTriggerList<Workflow.WfTrigger[]>(props.workflow.uuid)
    triggers.value = data ?? []
  }
  finally {
    loading.value = false
  }
}

async function addSchedule() {
  if (!newCron.value.trim()) {
    return
  }
  saving.value = true
  try {
    await api.workflowTriggerSave({
      workflowUuid: props.workflow.uuid,
      triggerType: 'schedule',
      cronExpr: newCron.value.trim(),
    })
    newCron.value = ''
    ms.success(t('workflow.triggerSaveSuccess'))
    await load()
  }
  catch (error: any) {
    if (error?.message)
      ms.error(error.message)
  }
  finally {
    saving.value = false
  }
}

async function addWebhook() {
  saving.value = true
  try {
    await api.workflowTriggerSave({
      workflowUuid: props.workflow.uuid,
      triggerType: 'webhook',
    })
    ms.success(t('workflow.triggerSaveSuccess'))
    await load()
  }
  catch (error: any) {
    if (error?.message)
      ms.error(error.message)
  }
  finally {
    saving.value = false
  }
}

async function onEnable(trigger: Workflow.WfTrigger, enable: boolean) {
  await api.workflowTriggerEnable(trigger.uuid, enable)
  trigger.isEnable = enable
}

async function onDelete(trigger: Workflow.WfTrigger) {
  await api.workflowTriggerDel(trigger.uuid)
  await load()
}

async function copyUrl(url: string) {
  await navigator.clipboard.writeText(url)
  ms.success(t('workflow.triggerCopyUrl'))
}

watch(() => props.show, (val) => {
  if (val)
    load()
})

onMounted(() => {
  if (props.show)
    load()
})
</script>

<template>
  <NModal
    :show="props.show" :title="t('workflow.triggerConfig')" preset="card" style="width: 90%; max-width: 720px"
    :mask-closable="true" content-style="min-height: 200px" @update:show="(v: boolean) => emit('update:show', v)"
  >
    <NEmpty v-if="!loading && !triggers.length" :description="t('workflow.triggerEmpty')" />
    <template v-else>
      <div v-for="trigger of triggers" :key="trigger.uuid" class="flex items-center justify-between py-2 border-b dark:border-neutral-800">
        <div class="flex items-center gap-2 min-w-0">
          <NTag size="small" :type="trigger.triggerType === 'schedule' ? 'info' : 'success'" :bordered="false">
            {{ trigger.triggerType === 'schedule' ? t('workflow.triggerTypeSchedule') : t('workflow.triggerTypeWebhook') }}
          </NTag>
          <span v-if="trigger.triggerType === 'schedule'" class="text-sm font-mono">{{ trigger.cronExpr }}</span>
          <span v-else class="text-xs text-neutral-500 truncate max-w-[340px]" :title="trigger.webhookUrl">
            {{ trigger.webhookUrl }}
          </span>
          <NButton v-if="trigger.triggerType === 'webhook'" size="tiny" text @click="copyUrl(trigger.webhookUrl)">
            {{ t('workflow.triggerCopyUrl') }}
          </NButton>
        </div>
        <div class="flex items-center gap-3 shrink-0">
          <span class="text-xs text-neutral-400">
            {{ trigger.lastFireTime ?? t('workflow.triggerLastFireNever') }}
          </span>
          <NSwitch size="small" :value="trigger.isEnable" @update:value="(v: boolean) => onEnable(trigger, v)" />
          <NPopconfirm @positive-click="onDelete(trigger)">
            <template #trigger>
              <NButton size="tiny" type="error" ghost>
                {{ t('common.delete') }}
              </NButton>
            </template>
            {{ t('workflow.deleteConfirmLabel') }}
          </NPopconfirm>
        </div>
      </div>
    </template>
    <div class="flex items-center gap-2 mt-4">
      <NInput
        v-model:value="newCron" size="small" class="max-w-[320px]" :placeholder="t('workflow.triggerCronPlaceholder')"
        @keyup.enter="addSchedule"
      />
      <NButton size="small" type="info" ghost :loading="saving" :disabled="!newCron.trim()" @click="addSchedule">
        {{ t('workflow.triggerAddSchedule') }}
      </NButton>
      <NButton size="small" type="success" ghost :loading="saving" @click="addWebhook">
        {{ t('workflow.triggerAddWebhook') }}
      </NButton>
    </div>
  </NModal>
</template>
