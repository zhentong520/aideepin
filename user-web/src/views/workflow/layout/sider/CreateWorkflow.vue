<script setup lang='ts'>
import { computed, onMounted, ref, watch } from 'vue'
import { NAvatar, NButton, NDivider, NFlex, NForm, NFormItem, NInput, NModal, NPopconfirm, NSelect, NSwitch, NTag, NTooltip, useMessage } from 'naive-ui'
import type { FormInst } from 'naive-ui'
import { SvgIcon } from '@/components/common'
import defaultAvatar from '@/assets/avatar.jpg'
import { useAuthStore, useUserStore, useWfStore } from '@/store'
import api from '@/api'
import { t } from '@/locales'
import { titleRules } from '@/utils/formRules'
import { emptyWorkflowInfo } from '@/utils/functions'

const saving = ref<boolean>(false)
const tmpWorkflow = ref<Workflow.WorkflowInfo>(emptyWorkflowInfo())
const formRef = ref<FormInst | null>(null)
// 与后端一致：标题必填（@NotBlank），长度上限为前端防御性约束
const wfRules = { title: titleRules(t('common.title')) }
// T9-06：内置官方模板，新建时可选；空串 = 空白工作流
const templates = ref<Workflow.WfTemplateInfo[]>([])
const selectedTemplateId = ref<string>('')

const wfStore = useWfStore()
const userStore = useUserStore()
const authStore = useAuthStore()
const ms = useMessage()

async function handleSave(event?: KeyboardEvent) {
  event?.stopPropagation()
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (saving.value)
    return

  saving.value = true
  const params = { uuid: tmpWorkflow.value.uuid, title: tmpWorkflow.value.title, remark: tmpWorkflow.value.remark, isPublic: tmpWorkflow.value.isPublic }
  try {
    if (!tmpWorkflow.value.uuid) {
      // T9-06：选择模板时走 create-from-template（名字冲突由后端加后缀）
      const { data: wf } = selectedTemplateId.value
        ? await api.workflowTemplateCreate<Workflow.WorkflowInfo>({
            templateId: selectedTemplateId.value,
            title: tmpWorkflow.value.title,
            remark: tmpWorkflow.value.remark,
          })
        : await api.workflowAdd<Workflow.WorkflowInfo>(params)
      wfStore.addWorkflowAndActive(wf)
    } else {
      const { data: wf } = await api.workflowBaseInfoUpdate<Workflow.WorkflowInfo>(params)
      wfStore.updateBaseInfo(tmpWorkflow.value.uuid, wf)
    }
    tmpWorkflow.value = emptyWorkflowInfo()
  } catch (error: any) {
    if (error.message) {
      ms.error(error.message, {
        duration: 2000,
      })
    }
  } finally {
    saving.value = false
    wfStore.setShowCreateView(false, '')
    ms.success(t('common.saveSuccessTip'))
  }
}

async function onDelete() {
  if (!tmpWorkflow.value.uuid) {
    ms.error(t('workflow.deleteFailedUuidEmpty'))
    return
  }
  await api.workflowDel(tmpWorkflow.value.uuid)
  wfStore.deleteWorkflow(tmpWorkflow.value.uuid)
  wfStore.setShowCreateView(false, '')
  ms.success(t('common.deleteSuccess'))
}

const viewStyle = computed(() => {
  if (wfStore.createOrEditWfUuid) {
    if (tmpWorkflow.value.userUuid === userStore.userInfo.uuid)
      return 'edit'

    return 'read'
  } else {
    return 'create'
  }
})

const title = computed(() => {
  if (viewStyle.value === 'create')
    return t('common.newAdd')
  else if (viewStyle.value === 'edit')
    return t('common.edit')
  else
    return t('common.view')
})

// T9-06：模板下拉项（首项「空白工作流」= 不选模板；NSelect 不接受 null，用空串表示不选）
const templateOptions = computed(() => [
  { label: t('workflow.templateBlank'), value: '' },
  ...templates.value.map(tpl => ({ label: tpl.title, value: tpl.id })),
])

onMounted(() => {
  // T9-06：拉取内置模板清单（失败静默——模板是增强项，不阻断新建）
  api.workflowTemplateList<Workflow.WfTemplateInfo[]>().then(({ data }) => {
    templates.value = data ?? []
  }).catch(() => {})
})

watch(() => wfStore.createOrEditWfUuid, (val) => {
  if (val) {
    tmpWorkflow.value.uuid = val
    const wf = wfStore.getWorkflowInfo(val)
    if (wf) {
      tmpWorkflow.value.title = wf.title
      tmpWorkflow.value.remark = wf.remark
      tmpWorkflow.value.isPublic = wf.isPublic
      tmpWorkflow.value.userUuid = wf.userUuid
      tmpWorkflow.value.userName = wf.userName
      tmpWorkflow.value.nodes = wf.nodes
    }
  } else {
    tmpWorkflow.value = emptyWorkflowInfo()
    selectedTemplateId.value = ''
  }
})
</script>

<template>
  <!-- 坑（2026-10-03 修）：新建/编辑工作流是长表单，原弹窗不限高且遮罩可点，小屏下保存按钮不可见、误点遮罩静默丢内容 -->
  <NModal
    v-model:show="wfStore.showCreateOrEditView" :title="title" style="min-width:200px; max-width: 600px;"
    preset="card" :mask-closable="false" content-style="max-height: 80vh; overflow-y: auto;"
  >
    <NFlex vertical>
      <NFlex v-show="viewStyle === 'read'" justify="space-between">
        <NTag size="large" :bordered="false" :color="{ color: '#ff000000' }">
          {{ tmpWorkflow.userName || userStore.userInfo.name }}
          <template #avatar>
            <NAvatar
              :src="`/api/user/avatar/${tmpWorkflow.userUuid || authStore.token}`" size="large"
              :fallback-src="defaultAvatar" color="#ff0000000"
            />
          </template>
        </NTag>
        <NFlex>
          <NTooltip trigger="hover">
            <template #trigger>
              <NTag size="medium" :bordered="false" round :color="{ color: '#ff000000' }">
                {{ tmpWorkflow.nodes.length }}
                <template #icon>
                  <SvgIcon icon="healthicons:lymph-nodes-outline" />
                </template>
              </NTag>
            </template>
            {{ t('workflow.nodeLabel') }}
          </NTooltip>
        </NFlex>
      </NFlex>
      <NDivider v-show="viewStyle === 'read'" />
      <div v-show="viewStyle !== 'read'">
        <NFlex class="grow" justify="space-between" vertical>
          <NForm ref="formRef" :model="{ title: tmpWorkflow.title }" :rules="wfRules">
            <NFormItem :label="t('common.title')" path="title" :show-require-mark="true">
              <NInput v-model:value="tmpWorkflow.title" type="text" size="large" :placeholder="t('workflow.titlePlaceholder')" />
            </NFormItem>
          </NForm>
          <NFormItem :label="t('workflow.remarkLabel')" :show-feedback="false">
            <NInput v-model:value="tmpWorkflow.remark" type="text" size="large" />
          </NFormItem>
          <!-- T9-06：新建时可选官方模板（编辑已有工作流时不显示） -->
          <NFormItem v-if="viewStyle === 'create' && templates.length" :label="t('workflow.templateLabel')" :show-feedback="false">
            <NSelect v-model:value="selectedTemplateId" clearable size="large" :options="templateOptions" />
          </NFormItem>
          <NFormItem :label="t('workflow.isPublicLabel')" :show-feedback="false">
            <NSwitch v-model:value="tmpWorkflow.isPublic">
              <template #checked>
                {{ t('common.yes') }}
              </template>
              <template #unchecked>
                {{ t('common.no') }}
              </template>
            </NSwitch>
          </NFormItem>
          <div class="flex justify-end space-x-4">
            <NButton
              v-show="tmpWorkflow.uuid" type="primary" :loading="saving" :disabled="saving"
              @click="handleSave()"
            >
              {{ t('workflow.updateLabel') }}
            </NButton>
            <NButton
              v-show="!tmpWorkflow.uuid" type="primary" :loading="saving" :disabled="saving"
              @click="handleSave()"
            >
              {{ t('common.newAdd') }}
            </NButton>
            <NPopconfirm placement="top" @positive-click.stop="onDelete">
              <template #trigger>
                <NButton v-show="tmpWorkflow.uuid" :disabled="saving" type="error" ghost>
                  {{ t('common.delete') }}
                </NButton>
              </template>
              {{ t('workflow.deleteConfirmLabel') }}
            </NPopconfirm>
          </div>
        </NFlex>
      </div>
      <div v-show="viewStyle === 'read'" class="flex flex-col space-y-2">
        <div class="text-base">
          {{ tmpWorkflow.title }}
        </div>
        <div>{{ tmpWorkflow.remark }}</div>
      </div>
    </NFlex>
  </NModal>
</template>
