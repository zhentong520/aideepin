<template>
  <n-card :bordered="false" class="proCard">
    <BasicForm @register="register" @submit="handleSubmit" @reset="handleReset" />

    <BasicTable
      :columns="columns"
      :request="loadDataTable"
      :row-key="(row: TaskQueueInfo) => row.id"
      ref="actionRef"
      :actionColumn="actionColumn"
      :scroll-x="1500"
    />
  </n-card>
</template>

<script lang="ts" setup>
  import { h, reactive, ref } from 'vue'
  import { BasicTable, TableAction } from '@/components/Table'
  import { BasicForm, FormSchema, useForm } from '@/components/Form/index'
  import taskQueueApi from '@/api/taskQueue'
  import { getColumns } from './columns'
  const columns = getColumns()
  import { TaskQueueInfo, TaskQueueSearchReq } from '/#/taskQueue'
  import { useDialog } from 'naive-ui'
  import { t } from '@/locales'

  const statusOptions = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD'].map((s) => ({
    label: t(`taskQueue.status${s.charAt(0)}${s.slice(1).toLowerCase()}`),
    value: s,
  }))

  const schemas: FormSchema[] = [
    {
      field: 'taskType',
      component: 'NInput',
      label: t('taskQueue.taskType'),
      componentProps: {
        placeholder: t('taskQueue.taskTypePlaceholder'),
        clearable: true,
      },
    },
    {
      field: 'status',
      component: 'NSelect',
      label: t('taskQueue.status'),
      componentProps: {
        options: statusOptions,
        clearable: true,
        placeholder: t('taskQueue.statusAll'),
      },
    },
  ]

  const actionRef = ref()
  const dialog = useDialog()

  const actionColumn = reactive({
    width: 160,
    title: t('common.action'),
    key: 'action',
    fixed: 'right',
    render(record: Recordable) {
      return h(TableAction as any, {
        style: 'button',
        actions: [
          {
            label: t('taskQueue.retry'),
            onClick: handleRetry.bind(null, record),
            ifShow: () => {
              return record.status === 'FAILED' || record.status === 'DEAD'
            },
          },
          {
            label: t('taskQueue.terminate'),
            onClick: handleTerminate.bind(null, record),
            ifShow: () => {
              return record.status === 'PENDING' || record.status === 'RUNNING'
            },
          },
        ],
      })
    },
  })

  const [register, { getFieldsValue }] = useForm({
    gridProps: { cols: '1 s:1 m:2 l:3 xl:4 2xl:4' },
    labelWidth: 120,
    schemas,
  })

  const loadDataTable = async (res: any) => {
    const resp = await taskQueueApi.page({ ...getFieldsValue() } as TaskQueueSearchReq, res)
    return resp.data
  }

  function reloadTable() {
    actionRef.value.reload()
  }

  function handleSubmit(_values: Recordable) {
    reloadTable()
  }

  function handleReset(_values: Recordable) {
    reloadTable()
  }

  function handleRetry(record: TaskQueueInfo) {
    dialog.warning({
      title: t('common.tip'),
      content: t('taskQueue.retryConfirm'),
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        try {
          const { success } = await taskQueueApi.retry(record.id)
          if (success) {
            window['$message'].success(t('taskQueue.retrySuccess'))
            reloadTable()
          }
        } catch (error) {
          window['$message'].error(t('common.operationFailed'))
        }
      },
    })
  }

  function handleTerminate(record: TaskQueueInfo) {
    dialog.warning({
      title: t('common.tip'),
      content: t('taskQueue.terminateConfirm'),
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        try {
          const { success } = await taskQueueApi.terminate(record.id)
          if (success) {
            window['$message'].success(t('taskQueue.terminateSuccess'))
            reloadTable()
          }
        } catch (error) {
          window['$message'].error(t('common.operationFailed'))
        }
      },
    })
  }
</script>
