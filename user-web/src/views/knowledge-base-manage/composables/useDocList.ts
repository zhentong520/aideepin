import { nextTick, reactive, ref } from 'vue'
import type { DialogApi, MessageApi } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'

/**
 * 文档列表状态（自 KnowledgeBaseDetail 拆出，T5-10）：
 * 搜索/分页、批量勾选（跨页记忆）、批量索引、索引完成轮询、删除与启停。
 * 行为与拆分前一致。
 */
export function useDocList(options: {
  ms: MessageApi
  dialog: DialogApi
  curKbUuid: string
  curKnowledgeBase: KnowledgeBase.Info
}) {
  const { ms, dialog, curKbUuid } = options

  const loading = ref<boolean>(false)
  const showIndexModal = ref<boolean>(false)
  const itemList = ref<KnowledgeBase.Document[]>([])
  const indexTypeSelected = ref<string[]>(['embedding'])
  const paginationReactive = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    prefix: () => t('common.total', { n: paginationReactive.itemCount }),
  })
  const searchValue = ref<string>('')
  const checkedItemRowKeys = ref<string[]>([])
  const checkedItems = ref<KnowledgeBase.Document[]>([])

  async function search(currentPage: number) {
    loading.value = true
    try {
      const resp = await api.knowledgeBaseItemSearch<PageResponse>(currentPage, paginationReactive.pageSize, curKbUuid, searchValue.value)
      setResp(currentPage, resp.data)
    } finally {
      loading.value = false
    }
  }

  function setResp(currentPage: number, data: PageResponse) {
    itemList.value = data.records
    paginationReactive.page = currentPage
    paginationReactive.itemCount = data.total
  }

  function deleteKbItem(row: KnowledgeBase.Document) {
    dialog.warning({
      title: t('knowledgeBase.deleteConfirmTitle'),
      content: t('common.deleteNotRecover'),
      positiveText: t('common.yes'),
      negativeText: t('common.no'),
      onPositiveClick: () => {
        api.knowledgeBaseItemDelete(row.uuid)
        nextTick(() => {
          itemList.value = itemList.value.filter(item => item.uuid !== row.uuid)
        })
      },
    })
  }

  function toggleStatus(row: KnowledgeBase.Document, isEnabled: boolean) {
    const action = isEnabled ? t('knowledgeBase.enable') : t('knowledgeBase.disable')
    dialog.warning({
      title: t('knowledgeBase.deleteConfirmTitle'),
      content: t('knowledgeBase.toggleStatusConfirm', { action }),
      positiveText: t('common.yes'),
      negativeText: t('common.no'),
      onPositiveClick: async () => {
        await api.knowledgeBaseItemToggleStatus(row.uuid, isEnabled)
        row.isEnabled = isEnabled
      },
    })
  }

  function changeIndexModal() {
    showIndexModal.value = true
  }

  /**
   * 索引文档
   */
  async function textIndexing() {
    if (checkedItemRowKeys.value.length === 0) {
      ms.warning(t('knowledgeBase.selectAtLeastOneRow'))
      return
    }
    if (indexTypeSelected.value.length === 0) {
      ms.warning(t('knowledgeBase.selectAtLeastOneIndexType'))
      return
    }
    if (loading.value) {
      ms.warning('indexing')
      return
    }
    showIndexModal.value = false
    loading.value = true
    try {
      await api.knowledgeBaseItemsIndexing(checkedItemRowKeys.value, indexTypeSelected.value)
      indexingCheck()
      ms.success(t('knowledgeBase.indexTaskRunning'))
      search(1)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    } finally {
      loading.value = false
    }
  }

  /**
   * 检查索引是否已经完成，如果已完成，则刷新列表
   */
  async function indexingCheck() {
    const response = await api.knowledgeBaseIndexingCheck()
    if (response.data) {
      search(1)
    } else {
      setTimeout(() => {
        indexingCheck()
      }, 3000)
    }
  }

  function onHandleCheckedRowKeys(keys: Array<string | number>, rows: object[], meta: { row: object | undefined; action: 'check' | 'uncheck' | 'checkAll' | 'uncheckAll' }) {
    checkedItemRowKeys.value = keys.map((key) => {
      return `${key}`
    })
    // 跨页面选择时，rows 中的非当前页的数据为 null，所以将 null 过滤掉，并将非当前页的值填充
    const itemMap = new Map<string, KnowledgeBase.Document>()
    const tmpItems = [] as KnowledgeBase.Document[]
    tmpItems.push(...(rows as KnowledgeBase.Document[]))
    tmpItems.push(...checkedItems.value)
    tmpItems.forEach((item) => {
      if (item)
        itemMap.set(item.uuid, item)
    })
    checkedItems.value = Array.from(itemMap.entries())
      .filter(([key]) => checkedItemRowKeys.value.includes(key))
      .map(([, value]) => value)
  }

  function removeCheckedItem(item: KnowledgeBase.Document) {
    checkedItemRowKeys.value = checkedItemRowKeys.value.filter((key) => {
      return key !== item.uuid
    })
    checkedItems.value = checkedItems.value.filter((row) => {
      return row.uuid !== item.uuid
    })
  }

  async function onHandlePageChange(currentPage: number) {
    search(currentPage)
  }

  async function onKeyUpSearch(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      search(1)
    }
  }

  async function initData() {
    search(1)
    const resp = await api.knowledgeBaseInfo<KnowledgeBase.Info>(curKbUuid)
    Object.assign(options.curKnowledgeBase, resp.data)
  }

  return {
    loading,
    showIndexModal,
    itemList,
    indexTypeSelected,
    paginationReactive,
    searchValue,
    checkedItemRowKeys,
    checkedItems,
    search,
    deleteKbItem,
    toggleStatus,
    changeIndexModal,
    textIndexing,
    indexingCheck,
    onHandleCheckedRowKeys,
    removeCheckedItem,
    onHandlePageChange,
    onKeyUpSearch,
    initData,
  }
}
