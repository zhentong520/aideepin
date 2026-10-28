<script setup lang="ts">
// S2-07 步骤④（Task #49）SFC 下沉试点：user/admin 两端 StartNodeProperty 同源单点。
// ⚠️ 本文件在共享包（link:，无自身 node_modules）内，禁止裸导入 vue/naive-ui/uuid
// 或使用宿主 '@/xxx' 路径——一切依赖经 '#wf-shared-vendor' 拿宿主实现（两端各有
// 一个同名供应商模块重导出；解析在宿主上下文完成，vite alias + tsconfig paths 生效）。
// Workflow 命名空间为宿主全局环境声明，宿主程序编译本文件时可见。
import { computed, h, reactive, ref } from '#wf-shared-vendor'
import {
  NButton,
  NCollapse,
  NCollapseItem,
  NDataTable,
  NFlex,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSwitch,
  SvgIcon,
  getNameByInputType,
  t,
  useWfStore,
  uuidv4,
} from '#wf-shared-vendor'

interface Props {
  workflow: Workflow.WorkflowInfo
  wfNode: Workflow.WorkflowNode
}
const props = defineProps<Props>()
const wfStore = useWfStore()
const showModal = ref<boolean>(false)
const tmpItem = reactive<Workflow.NodeIODefinition>({
  uuid: '',
  type: 1,
  name: '',
  title: '',
  required: false,

  limit: 10,
  multiple: false,
})
const options = [
  {
    label: t('workflow.variableTypeText'),
    value: 1,
  },
  {
    label: t('workflow.variableTypeNumber'),
    value: 2,
  },
  {
    label: t('workflow.variableTypeFile'),
    value: 4,
  },
  {
    label: t('workflow.variableTypeBoolean'),
    value: 5,
  },
]
const columns = [
  {
    title: t('workflow.variableName'),
    key: 'name',
  },
  {
    title: t('workflow.variableTitle'),
    key: 'title',
  },
  {
    title: t('workflow.variableType'),
    key: 'type',
    render(row: { type: number }) {
      return getNameByInputType(row.type)
    },
  },
  {
    title: t('workflow.variableRequired'),
    key: 'required',
    render(row: { required: boolean }) {
      return row.required ? t('common.yes') : t('common.no')
    },
  },
  {
    title: t('common.operate'),
    key: 'actions',
    render(row: Workflow.NodeIODefinition) {
      return h(
        'div',
        { class: 'flex gap-2' },
        {
          default: () => [
            h(
              SvgIcon,
              {
                icon: 'carbon:edit',
                class: 'text-base cursor-pointer',
                onClick: () => onEdit(row),
              },
            ),
            h(
              SvgIcon,
              {
                icon: 'carbon:delete',
                class: 'text-base cursor-pointer',
                onClick: () => onDelete(row),
              },
            ),
          ],
        },
      )
    },
  },
]

const submitStatus = computed(() => {
  if (tmpItem.name && tmpItem.title)
    return true

  return false
})

function onEdit(row: Workflow.NodeIODefinition) {
  showModal.value = true
  const idx = props.wfNode.inputConfig.user_inputs.findIndex(item => item.uuid === row.uuid)
  Object.assign(tmpItem, props.wfNode.inputConfig.user_inputs[idx])
}

function onDelete(row: Workflow.NodeIODefinition) {
  const idx = props.wfNode.inputConfig.user_inputs.findIndex(item => item.uuid === row.uuid)
  wfStore.deleteUserInput(props.workflow.uuid, props.wfNode.uuid, idx)
}

function onShowModal() {
  showModal.value = true
  Object.assign(tmpItem, { uuid: uuidv4().replace(/-/g, ''), type: 1, name: '', title: '', required: false })
}

function submitForm() {
  showModal.value = false
  const idx = props.wfNode.inputConfig.user_inputs.findIndex(item => item.uuid === tmpItem.uuid)
  if (idx > -1) {
    Object.assign(props.wfNode.inputConfig.user_inputs[idx], { ...tmpItem })
  } else {
    wfStore.addUserInputToNode(props.workflow.uuid, props.wfNode.uuid, { ...tmpItem })
    Object.assign(tmpItem, { uuid: '', type: 1, name: '', label: '', required: false })
  }
}
</script>

<template>
  <div class="flex flex-col w-full space-y-1">
    <NCollapse :default-expanded-names="['1']">
      <NCollapseItem name="1" class="border border-gray-200 rounded-md m-2 px-3 pb-3">
        <template #header>
          <div class="text-xl">
            {{ t('workflow.inputLabel') }}
          </div>
        </template>
        <NDataTable :columns="columns" :data="wfNode.inputConfig.user_inputs" />
      </NCollapseItem>
    </NCollapse>
    <br>
    <NButton @click="onShowModal">
      {{ t('workflow.nodeAddNew') }}
    </NButton>
  </div>
  <!-- 坑（2026-10-03 修）：节点属性表单原用固定 max-height:700px，视口更矮时内容溢出且「确定」不可见；点遮罩静默丢参 -->
  <NModal
    v-model:show="showModal" style="width: 90%; max-width: 600px" preset="card" :mask-closable="false"
    content-style="max-height: 80vh; overflow-y: auto;" :title="t('workflow.nodeSetting')"
  >
    <div class="flex flex-col w-full justify-between space-y-4">
      <div>
        {{ t('workflow.variableType') }}
        <NSelect v-model:value="tmpItem.type" :options="options" />
      </div>
      <div>
        {{ t('workflow.nodeName') }}
        <NInput v-model:value="tmpItem.name" maxlength="50" show-count />
      </div>
      <div>
        {{ t('workflow.variableDisplayTitle') }}
        <NInput v-model:value="tmpItem.title" maxlength="50" show-count />
      </div>
      <div>
        {{ t('workflow.variableIsRequired') }}
        <NSwitch v-model:value="tmpItem.required" size="small" />
      </div>
      <div v-if="tmpItem.type === 3">
        {{ t('workflow.variableMultiSelect') }}
        <NSwitch v-model:value="tmpItem.multiple" />
      </div>
      <div v-if="tmpItem.type === 4">
        {{ t('workflow.variableMaxFileCount') }}
        <NInputNumber v-model:value="tmpItem.limit" />
      </div>
      <NFlex justify="end" style="margin-top: 20px">
        <NButton
          block type="primary" :disabled="!submitStatus" @click="
            () => {
              submitForm();
            }
          "
        >
          {{ t('common.confirm') }}
        </NButton>
      </NFlex>
    </div>
  </NModal>
</template>
