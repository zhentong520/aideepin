<template>
  <n-card :bordered="false" class="proCard">
    <BasicForm @register="register" @submit="handleSubmit" @reset="handleReset" />

    <n-space justify="end" class="mb-2">
      <n-popconfirm @positive-click="handleCredentialBackfill">
        <template #trigger>
          <n-button secondary type="warning" :loading="credentialBackfillLoading">
            {{ t('workflow.credentialBackfill') }}
          </n-button>
        </template>
        {{ t('workflow.credentialBackfillConfirm') }}
      </n-popconfirm>
      <n-button type="info" @click="handleCreate">{{ t('common.newAdd') }}</n-button>
    </n-space>

    <BasicTable
      :columns="columns"
      :request="loadDataTable"
      :row-key="(row: Data) => row.id"
      ref="actionRef"
      :actionColumn="actionColumn"
      @update:checked-row-keys="onCheckedRow"
      :scroll-x="1400"
    />

    <n-modal
      v-model:show="showEditModal"
      :show-icon="false"
      preset="dialog"
      :title="editFormParams.label"
    >
      <n-form
        :model="editFormParams"
        :rules="newUserRules"
        ref="formRef"
        label-placement="left"
        :label-width="120"
        class="py-4"
      >
        <n-form-item :label="t('common.title')" path="title">
          <n-input :placeholder="t('common.title')" v-model:value="editFormParams.title" />
        </n-form-item>
        <n-form-item :label="t('common.isPublic')" path="isPublic">
          <n-switch v-model:value="editFormParams.isPublic" />
        </n-form-item>
        <n-form-item :label="t('common.isEnable')" path="isEnable">
          <n-switch v-model:value="editFormParams.isEnable" />
        </n-form-item>
        <n-form-item :label="t('common.description')" path="remark">
          <n-input
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            :placeholder="t('common.description')"
            v-model:value="editFormParams.remark"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="() => (showEditModal = false)">{{ t('common.cancel') }}</n-button>
          <n-button type="info" :loading="formBtnLoading" @click="confirmEditForm">{{
            t('common.confirm')
          }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
  import { h, reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { BasicTable, TableAction } from '@/components/Table'
  import { BasicForm, FormSchema, useForm } from '@/components/Form/index'
  import workflowApi from '@/api/workflow'
  import { getColumns, Data } from './columns'
  const columns = getColumns()
  const router = useRouter()
  import { type FormRules } from 'naive-ui'
  import { useDialog } from 'naive-ui'
  import { t } from '@/locales'

  const newUserRules: FormRules = {
    title: {
      required: false,
      trigger: ['blur', 'input'],
      message: () => t('common.title'),
    },
  }

  const schemas: FormSchema[] = [
    {
      field: 'title',
      component: 'NInput',
      label: t('common.title'),
      componentProps: {
        placeholder: t('common.title'),
        onInput: (e: any) => {
          console.error(e)
        },
      },
    },
    {
      field: 'isPublic',
      component: 'NSelect',
      label: t('common.isPublic'),
      componentProps: {
        options: [
          {
            label: t('common.yes'),
            value: true,
          },
          {
            label: t('common.no'),
            value: false,
          },
        ],
        onUpdateValue: (e: any) => {
          console.error(e)
        },
      },
    },
    {
      field: 'isEnable',
      component: 'NSelect',
      label: t('common.isEnable'),
      componentProps: {
        options: [
          {
            label: t('common.yes'),
            value: true,
          },
          {
            label: t('common.no'),
            value: false,
          },
        ],
        onUpdateValue: (e: any) => {
          console.error(e)
        },
      },
    },
    {
      field: 'createTime',
      component: 'NDatePicker',
      label: t('common.createTime'),
      componentProps: {
        type: 'datetimerange',
        clearable: true,
      },
    },
    {
      field: 'updateTime',
      component: 'NDatePicker',
      label: t('common.updateTime'),
      componentProps: {
        type: 'datetimerange',
        clearable: true,
      },
    },
  ]

  const formRef: any = ref(null)
  const actionRef = ref()
  const dialog = useDialog()
  const showEditModal = ref(false)
  const formBtnLoading = ref(false)
  // 新建/编辑共用弹窗；新建时走 workflowApi.add，成功后直接进设计器
  const isCreating = ref(false)
  const editFormParams = reactive({
    label: t('common.edit'),
    uuid: '',
    title: '',
    remark: '',
    isPublic: false,
    isEnable: false,
  })

  const actionColumn = reactive({
    width: 200,
    title: t('common.action'),
    key: 'action',
    fixed: 'right',
    render(record: Recordable) {
      return h(TableAction as any, {
        style: 'button',
        actions: [
          {
            label: t('route.workflowDesigner'),
            onClick: () => router.push(`/workflow/designer/${record.uuid}`),
          },
          {
            label: t('common.edit'),
            onClick: handleEdit.bind(null, record),
          },
          {
            label: t('common.disable'),
            onClick: handleDisable.bind(null, record),
            ifShow: () => {
              return record.isEnable
            },
          },
          {
            label: t('common.enable'),
            onClick: handleEnable.bind(null, record),
            ifShow: () => {
              return !record.isEnable
            },
          },
        ],
        dropDownActions: [
          {
            label: t('common.delete'),
            key: 'delete',
          },
        ],
        select: (key: string) => {
          if (key === 'delete') {
            dialog.warning({
              title: t('common.tip'),
              content: `${t('common.deleteConfirmPrefix')} ${record.title} ${t(
                'common.deleteConfirmSuffix'
              )}`,
              positiveText: t('common.positiveText'),
              negativeText: t('common.negativeText'),
              onPositiveClick: () => {
                handleDel(record)
              },
              onNegativeClick: () => {
              },
            })
          }
        },
      })
    },
  })

  const [register, { getFieldsValue }] = useForm({
    gridProps: { cols: '1 s:1 m:2 l:3 xl:4 2xl:4' },
    labelWidth: 120,
    schemas,
  })

  const loadDataTable = async (res: any) => {
    const resp = await workflowApi.search({ ...getFieldsValue() }, res)
    return resp.data
  }

  function onCheckedRow(_rowKeys: Array<string | number>) {
  }

  function reloadTable() {
    actionRef.value.reload()
  }

  function confirmEditForm(e: Event) {
    e.preventDefault()
    if (isCreating.value && !editFormParams.title.trim()) {
      window['$message'].error(t('common.fillCompleteInfo'))
      return
    }
    formBtnLoading.value = true
    formRef.value.validate(async (errors: any) => {
      if (!errors) {
        if (isCreating.value) {
          const resp = await workflowApi.add({
            title: editFormParams.title.trim(),
            remark: editFormParams.remark,
            isPublic: editFormParams.isPublic,
          })
          window['$message'].success(t('common.operationSuccess'))
          formBtnLoading.value = false
          showEditModal.value = false
          // 创建成功后直接进入设计器开始编排
          if (resp?.data?.uuid) {
            router.push(`/workflow/designer/${resp.data.uuid}`)
          } else {
            reloadTable()
          }
          return
        }
        await workflowApi.updateBaseInfo(editFormParams)
        window['$message'].success(t('common.editSuccess'))
        setTimeout(() => {
          showEditModal.value = false
          reloadTable()
        })
      } else {
        window['$message'].error(t('common.fillCompleteInfo'))
      }
      formBtnLoading.value = false
    })
  }

  function handleCreate() {
    isCreating.value = true
    editFormParams.uuid = ''
    editFormParams.title = ''
    editFormParams.remark = ''
    editFormParams.isPublic = false
    editFormParams.label = t('common.newAdd')
    showEditModal.value = true
  }

  function handleEdit(record: Recordable) {
    isCreating.value = false
    showEditModal.value = true
    Object.assign(editFormParams, record)
    editFormParams.label = t('common.edit')
  }

  async function handleEnable(record: Recordable) {
    await workflowApi.setEnable({ uuid: record.uuid, isEnable: true })
    window['$message'].success(t('common.operationSuccess'))
    reloadTable()
  }

  async function handleDisable(record: Recordable) {
    await workflowApi.setEnable({ uuid: record.uuid, isEnable: false })
    window['$message'].success(t('common.operationSuccess'))
    reloadTable()
  }

  function handleSubmit(_values: Recordable) {
    reloadTable()
  }

  function handleReset(_values: Recordable) {
  }

  async function handleDel(record: Recordable) {
    let { success } = await workflowApi.del(record.uuid)
    if (success) {
      window['$message'].success(t('common.operationSuccess'))
      reloadTable()
    }
  }

  // ===== 存量凭据回填（S-05 配套运维，二开新增）：幂等，返回迁移节点数 =====
  const credentialBackfillLoading = ref(false)
  async function handleCredentialBackfill() {
    credentialBackfillLoading.value = true
    try {
      const resp = await workflowApi.credentialBackfill()
      // 封装层在 errorMessageMode:'none' 下对 success:false 也 resolve（如 vault 开关未开时
      // 后端拒绝返回 B0003），必须显式判断 success，否则会误报「回填完成」
      if (!resp?.success) {
        window['$message'].error(resp?.message || t('common.operationFailed'))
        return
      }
      window['$message'].success(t('workflow.credentialBackfillDone', { count: resp?.data ?? 0 }))
    } finally {
      credentialBackfillLoading.value = false
    }
  }
</script>

<style lang="less" scoped></style>
