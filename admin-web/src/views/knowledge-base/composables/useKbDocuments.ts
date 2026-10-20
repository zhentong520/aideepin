import { computed, h, reactive, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import { useDialog, useMessage } from 'naive-ui'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'
import { TableAction } from '@/components/Table'
import { renderStatusTag } from './statusMeta'

interface UseKbDocumentsOptions {
  kbUuid: string
  onPreviewContent: (row: any) => void
  onViewSegments: (row: any) => void
}

// 知识库文档列表：远程分页搜索、行操作（预览/分块/重索引/启停/删除/OCR/重抓）、批量重索引

export function useKbDocuments({ kbUuid, onPreviewContent, onViewSegments }: UseKbDocumentsOptions) {
  const ms = useMessage()
  const dialog = useDialog()

  const loading = ref(false)
  const itemList = ref<any[]>([])
  const tableMaxHeight = ref(500)
  const searchValue = ref('')
  const pagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    prefix: () => t('common.total', { n: pagination.itemCount }),
  })
  const checkedRowKeys = ref<string[]>([])

  function onChecked(keys: Array<string | number>) {
    checkedRowKeys.value = keys.map((k) => `${k}`)
  }

  const columns = computed<DataTableColumns<any>>(() => [
    { title: t('common.title'), key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
    {
      title: t('knowledgeBase.docSource'),
      key: 'sourceType',
      width: 110,
      render: (row: any) => {
        const map: Record<string, string> = {
          file: t('knowledgeBase.sourceFile'),
          url: t('knowledgeBase.sourceUrl'),
          api: t('knowledgeBase.sourceApi'),
        }
        return map[row.sourceType] || row.sourceType || '-'
      },
    },
    {
      title: t('common.status'),
      key: 'embeddingStatus',
      width: 160,
      render: (row: any) => renderStatusTag(row.embeddingStatus),
    },
    {
      title: t('knowledgeBase.failReason'),
      key: 'failReason',
      minWidth: 160,
      ellipsis: { tooltip: true },
      render: (row: any) => row.failReason || '-',
    },
    { title: t('common.createTime'), key: 'createTime', width: 170 },
    {
      title: t('common.action'),
      key: 'action',
      // 主按钮 5 个（内容预览 / 查看分块 / 重建索引 / 启停 / 删除），
      // 低频且有条件的「重试OCR、重新抓取」收进「更多」下拉，避免操作列溢出被裁。
      width: 340,
      fixed: 'right',
      render(row: any) {
        const actions: any[] = []
        actions.push({
          label: t('knowledgeBase.contentPreview'),
          key: 'contentPreview',
          onClick: () => onPreviewContent(row),
        })
        actions.push({
          label: t('knowledgeBase.viewSegments'),
          key: 'segments',
          onClick: () => onViewSegments(row),
        })
        actions.push({
          label: t('knowledgeBase.reindex'),
          key: 'reindex',
          onClick: () => onRetryIndex(row),
        })
        const enableLabel = row.isEnabled === false ? t('common.enable') : t('common.disable')
        actions.push({
          label: enableLabel,
          key: 'toggle',
          onClick: () => onToggleStatus(row, row.isEnabled !== false),
        })
        actions.push({ label: t('common.delete'), key: 'delete', onClick: () => onDelete(row) })

        const dropDownActions: any[] = []
        if (row.embeddingStatus === 'FAIL') {
          dropDownActions.push({ label: t('knowledgeBase.retryOcr'), key: 'ocr' })
        }
        if (row.sourceType === 'url' || row.sourceType === 'api') {
          dropDownActions.push({ label: t('knowledgeBase.refetch'), key: 'refetch' })
        }
        return h(TableAction, {
          style: 'button',
          actions,
          dropDownActions,
          select: (key: string) => {
            if (key === 'ocr') onRetryOcr(row)
            else if (key === 'refetch') onRefetch(row)
          },
        })
      },
    },
  ])

  async function search(currentPage: number) {
    loading.value = true
    try {
      const resp: any = await adminKbDoc.docSearch({
        kbUuid,
        keyword: searchValue.value,
        currentPage,
        pageSize: pagination.pageSize,
      })
      itemList.value = resp.data?.records || []
      pagination.page = currentPage
      pagination.itemCount = resp.data?.total || 0
    } finally {
      loading.value = false
    }
  }

  async function onRetryIndex(row: any) {
    try {
      await adminKbDoc.retryIndex(row.uuid)
      ms.success(t('common.indexTaskRunning'))
      search(pagination.page)
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    }
  }

  async function onRetryOcr(row: any) {
    try {
      await adminKbDoc.retryOcr(row.uuid)
      ms.success(t('common.indexTaskRunning'))
      search(pagination.page)
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    }
  }

  async function onRefetch(row: any) {
    try {
      await adminKbDoc.refetch(row.uuid)
      ms.success(t('knowledgeBase.refetchSuccess'))
      search(pagination.page)
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    }
  }

  function onToggleStatus(row: any, isEnabled: boolean) {
    const action = isEnabled ? t('common.disable') : t('common.enable')
    dialog.warning({
      title: t('knowledgeBase.deleteConfirmTitle'),
      content: t('knowledgeBase.toggleStatusConfirm', { tip: action }),
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        await adminKbDoc.toggleStatus(row.uuid, isEnabled)
        row.isEnabled = !isEnabled
      },
    })
  }

  function onDelete(row: any) {
    dialog.warning({
      title: t('knowledgeBase.deleteConfirmTitle'),
      content: `${t('common.deleteConfirmPrefix')} ${row.title} ${t('common.deleteConfirmSuffix')}`,
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        await adminKbDoc.deleteDoc(row.uuid)
        ms.info(t('common.deleteSuccess'))
        search(pagination.page)
      },
    })
  }

  async function batchReindex() {
    if (!checkedRowKeys.value.length) {
      ms.warning(t('knowledgeBase.selectAtLeastOneRow'))
      return
    }
    try {
      for (const uuid of checkedRowKeys.value) {
        await adminKbDoc.retryIndex(uuid)
      }
      ms.success(t('common.indexTaskRunning'))
      search(pagination.page)
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    }
  }

  return {
    loading,
    itemList,
    tableMaxHeight,
    searchValue,
    pagination,
    checkedRowKeys,
    columns,
    onChecked,
    search,
    batchReindex,
  }
}
