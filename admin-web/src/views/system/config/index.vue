<template>
  <n-card :bordered="false" class="proCard">
    <n-alert :title="t('common.tip')" type="info" :bordered="true" closable>
      {{ t('sysConfig.editTip') }}
    </n-alert>

    <BasicForm @register="register" @submit="handleSubmit" @reset="handleReset" />

    <BasicTable
      :columns="columns"
      :request="loadDataTable"
      :row-key="(row: SysConfigRow) => row.name"
      ref="actionRef"
      :actionColumn="actionColumn"
      :scroll-x="900"
    />
  </n-card>
</template>

<script lang="ts" setup>
  import { h, reactive, ref } from 'vue'
  import { BasicTable, TableAction, BasicColumn } from '@/components/Table'
  import { BasicForm, FormSchema, useForm } from '@/components/Form/index'
  import { useDialog, NInput } from 'naive-ui'
  import sysConfigApi from '@/api/sysConfig.js'
  import { t } from '@/locales'

  interface SysConfigRow {
    name: string
    value: string
  }

  const columns: BasicColumn<SysConfigRow>[] = [
    {
      title: t('sysConfig.name'),
      key: 'name',
      width: 320,
    },
    {
      title: t('sysConfig.value'),
      key: 'value',
      render(row) {
        if (!row.value) return '-'
        const text = String(row.value)
        return text.length > 80 ? `${text.slice(0, 80)}...` : text
      },
    },
  ]

  const schemas: FormSchema[] = [
    {
      field: 'keyword',
      component: 'NInput',
      label: t('sysConfig.keyword'),
      componentProps: {
        placeholder: t('sysConfig.keywordPlaceholder'),
        clearable: true,
      },
    },
  ]

  const actionRef = ref()
  const dialog = useDialog()

  const actionColumn = reactive({
    width: 120,
    title: t('common.action'),
    key: 'action',
    fixed: 'right',
    render(record: Recordable) {
      return h(TableAction as any, {
        style: 'button',
        actions: [
          {
            label: t('sysConfig.edit'),
            onClick: handleEdit.bind(null, record),
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
    const resp = await sysConfigApi.search(
      { ...getFieldsValue() } as { keyword?: string },
      res.current,
      res.size
    )
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

  function handleEdit(record: SysConfigRow) {
    const editingValue = ref(String(record.value ?? ''))
    dialog.info({
      title: `${t('sysConfig.edit')} - ${record.name}`,
      content: () =>
        h(NInput, {
          type: 'textarea',
          value: editingValue.value,
          'onUpdate:value': (v: string) => {
            editingValue.value = v
          },
          autosize: { minRows: 3, maxRows: 12 },
          placeholder: t('sysConfig.valuePlaceholder'),
        }),
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        try {
          // 沿用现有约定：axios 拦截器在 data 为空对象时把 params 兜底塞进 body，
          // 后端 @RequestBody SysConfigEditDto 由此收到 {name, value}
          const { success } = await sysConfigApi.edit({
            name: record.name,
            value: editingValue.value,
          })
          if (success) {
            window['$message'].success(t('sysConfig.editSuccess'))
            reloadTable()
          }
        } catch (error) {
          window['$message'].error(t('common.operationFailed'))
        }
      },
    })
  }
</script>
