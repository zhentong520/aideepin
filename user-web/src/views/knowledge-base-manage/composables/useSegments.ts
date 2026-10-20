import { computed, h, onDeactivated, onUnmounted, reactive, ref } from 'vue'
import type { Ref } from 'vue'
import type { DataTableColumns, DialogApi, MessageApi } from 'naive-ui'
import { NButton } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'
import ExpandableText from '../components/ExpandableText.vue'
import { createStatusColumn, isSegmentRebuilding } from '../components/segment-status'
import { usePollFailureGuard } from './usePollFailureGuard'

/**
 * 分段/问答对列表状态机（自 DocumentDetail 拆出，T5-10）：
 * 列表加载与分页、三态（text/qa/parent_child）推断、编辑弹窗状态机、
 * 删除/启停/重建子块/修复向量等确认流。
 * 行为保持与拆分前一致。
 */
export function useSegments(options: {
  ms: MessageApi
  dialog: DialogApi
  loadingBar: { start: () => void; finish: () => void; error: () => void }
  curDocUuid: Ref<string>
  curDoc: KnowledgeBase.Document
  loadDocInfo: (docUuid: string, silent?: boolean) => Promise<void>
  onPollCapped: () => void
}) {
  const { ms, dialog, loadingBar, onPollCapped } = options
  const { recordPollFailure, canPoll } = usePollFailureGuard(onPollCapped)

  const segments = ref<KnowledgeBase.Segment[]>([])
  const loading = ref(false)
  // 模式由返回结构推断：questions 有值为 qa，children 有值为 parent_child
  const segmentMode = ref<'text' | 'qa' | 'parent_child'>('text')
  const paginationReactive = reactive({
    page: 1,
    pageSize: 20,
    itemCount: 0,
    prefix: () => t('common.total', { n: paginationReactive.itemCount }),
  })

  // Edit/add modal state: type picks the save API (qaPair = pair edit; question + isNew + no
  // answerSegmentId = add QA pair). Both use dynamic question inputs — one input per question
  const editState = reactive<{
    show: boolean
    type: 'segment' | 'question' | 'child' | 'qaPair'
    id?: string
    docUuid: string
    answerSegmentId?: string
    parentSegmentId?: string
    content: string
    questions: string[]
    answerContent?: string
    isNew: boolean
    // add-QA-pair retry progress: question texts already persisted this attempt
    savedQuestions?: string[]
  }>({
    show: false,
    type: 'segment',
    docUuid: '',
    content: '',
    questions: [],
    isNew: false,
  })
  const submitting = ref(false)

  function editTitle() {
    if (editState.type === 'qaPair')
      return t('knowledgeBase.editQaPair')
    const prefix = editState.isNew
      ? (editState.type === 'question' ? t('knowledgeBase.addQaPair') : t('knowledgeBase.childChunks'))
      : t('common.edit')
    return prefix
  }

  // silent=true is a rebuild poll: no table loading flash; user actions (paging) use non-silent
  async function loadList(currentPage: number, silent = false) {
    if (!silent)
      loading.value = true
    try {
      const resp = await api.documentSegmentList<PageResponse>(options.curDocUuid.value, currentPage, paginationReactive.pageSize)
      segments.value = resp.data.records
      if (segments.value.length > 0) {
        if (segments.value[0].questions)
          segmentMode.value = 'qa'
        else if (segments.value[0].children)
          segmentMode.value = 'parent_child'
        else
          segmentMode.value = 'text'
      }
      paginationReactive.page = currentPage
      paginationReactive.itemCount = resp.data.total
      // 删除末页最后一条后回退到第一页，避免停留在空页
      if (segments.value.length === 0 && currentPage > 1) {
        await loadList(1, silent)
        return
      }
    } catch (error: any) {
      if (!silent)
        ms.error(error.message ?? 'error')
      else
        recordPollFailure()
    } finally {
      loading.value = false
      scheduleAutoRefresh()
    }
  }

  // 存在重建中的段时定时刷新列表，直至全部完成
  let refreshTimer: ReturnType<typeof setTimeout> | null = null
  function scheduleAutoRefresh() {
    if (refreshTimer)
      clearTimeout(refreshTimer)
    if (segments.value.some(s => isSegmentRebuilding(s)) && canPoll())
      refreshTimer = setTimeout(() => loadList(paginationReactive.page, true), 3000)
  }

  function openEdit(type: 'segment' | 'child', row: any) {
    Object.assign(editState, {
      show: true,
      type,
      id: row.id,
      docUuid: options.curDocUuid.value,
      answerSegmentId: row.answerSegmentId,
      parentSegmentId: row.parentSegmentId,
      content: row.content,
      isNew: false,
    })
  }

  function openAddQaPair() {
    Object.assign(editState, {
      show: true,
      type: 'question',
      id: undefined,
      docUuid: options.curDocUuid.value,
      answerSegmentId: undefined,
      answerContent: '',
      content: '',
      questions: [''],
      isNew: true,
      savedQuestions: [],
    })
  }

  function openAddChild(parentSegmentId: string) {
    Object.assign(editState, {
      show: true,
      type: 'child',
      id: undefined,
      docUuid: options.curDocUuid.value,
      parentSegmentId,
      content: '',
      isNew: true,
    })
  }

  // Pair edit: one input per question (prefilled), answer in its own box; saved via content diff
  function openEditQaPair(row: KnowledgeBase.Segment) {
    Object.assign(editState, {
      show: true,
      type: 'qaPair',
      id: row.id,
      docUuid: options.curDocUuid.value,
      answerSegmentId: row.id,
      content: '',
      questions: (row.questions || []).map(q => q.content),
      answerContent: row.content,
      isNew: false,
    })
  }

  async function saveEdit() {
    // Question-set types (pair edit / add QA pair): one input per question; trim, drop blanks, dedupe
    const questionListType = editState.type === 'qaPair' || (editState.type === 'question' && editState.isNew)
    const questions = [...new Set(editState.questions.map(q => q.trim()).filter(q => q.length > 0))]
    if (questionListType) {
      if (questions.length === 0 || !(editState.answerContent || '').trim()) {
        ms.warning(t('common.inputPlaceholder'))
        return
      }
    } else if (!editState.content.trim()) {
      ms.warning(t('common.inputPlaceholder'))
      return
    }
    try {
      submitting.value = true
      if (editState.type === 'segment') {
        await api.documentSegmentSaveOrUpdate({ id: editState.id!, docUuid: editState.docUuid, content: editState.content } as KnowledgeBase.Segment)
      } else if (editState.type === 'qaPair') {
        // Server diffs by content: unchanged questions keep vectors, answer-only changes do not
        // re-embed — hence the generic save-success message
        await api.documentQaPairSaveOrUpdate({
          docUuid: editState.docUuid,
          answerSegmentId: editState.answerSegmentId,
          answerContent: editState.answerContent,
          questions,
        })
        ms.success(t('common.saveSuccess'))
        editState.show = false
        loadList(paginationReactive.page)
        return
      } else if (editState.type === 'question') {
        // Add QA pair: the first unsaved question creates the answer segment (unless a previous
        // attempt already created it); the rest attach to it. Persisted texts are tracked so a
        // mid-loop failure retries the missing ones instead of duplicating the answer segment and
        // already-saved questions (the backend also rejects duplicate question texts); text keys
        // survive list edits between attempts, an index counter would not
        const saved = new Set(editState.savedQuestions ?? [])
        for (const question of questions) {
          if (saved.has(question))
            continue
          const resp = await api.documentSegmentQuestionSaveOrUpdate<any>({
            docUuid: editState.docUuid,
            answerContent: editState.answerSegmentId ? undefined : editState.answerContent,
            answerSegmentId: editState.answerSegmentId,
            content: question,
          })
          if (!editState.answerSegmentId)
            editState.answerSegmentId = resp.data?.answerSegmentId
          saved.add(question)
          editState.savedQuestions = [...saved]
        }
      } else {
        await api.documentSegmentChildSaveOrUpdate({
          id: editState.id,
          docUuid: editState.docUuid,
          parentSegmentId: editState.parentSegmentId,
          content: editState.content,
        })
      }
      ms.success(t('knowledgeBase.segmentSavedAndReindexing'))
      editState.show = false
      loadList(paginationReactive.page)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    } finally {
      submitting.value = false
    }
  }

  function confirmDelete(type: 'segment' | 'child', uuid: string) {
    // Parent segment deletion cascades to its child chunks and their vectors
    const parent = type === 'segment' && segmentMode.value === 'parent_child'
    dialog.warning({
      title: parent ? t('knowledgeBase.deleteParent') : t('common.delete'),
      content: parent ? t('knowledgeBase.deleteParentConfirm') : t('common.deleteConfirmTip'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          if (type === 'segment')
            await api.documentSegmentDel(uuid)
          else
            await api.documentSegmentChildDel(uuid)
          ms.success(t('common.deleteSuccess'))
          loadList(paginationReactive.page)
        } catch (error: any) {
          ms.error(error.message ?? 'error')
        }
      },
    })
  }

  // 停用分段会删除其向量与图谱数据（同步清理，耗时随图谱足迹增长）；启用会重新生成（图谱抽取消耗模型额度），操作前均需确认。
  // 文档未图谱化时后端只处理向量，提示词同步收敛为仅向量口径
  function confirmToggleStatus(row: KnowledgeBase.Segment, isEnabled: boolean) {
    const hasGraph = options.curDoc.graphicalStatus === 'DONE'
    const content = isEnabled
      ? (hasGraph ? t('knowledgeBase.segmentEnableConfirm') : t('knowledgeBase.segmentEnableVectorOnlyConfirm'))
      : (hasGraph ? t('knowledgeBase.segmentDisableConfirm') : t('knowledgeBase.segmentDisableVectorOnlyConfirm'))
    dialog.warning({
      title: isEnabled ? t('knowledgeBase.enable') : t('knowledgeBase.disable'),
      content,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        // The toggle request runs the index cleanup/rebuild setup synchronously; keep a visible
        // global indicator next to the dialog button spinner until it settles
        loadingBar.start()
        try {
          await api.documentSegmentToggleStatus({ uuid: row.uuid, isEnabled })
          ms.success(t('common.saveSuccess'))
          await loadList(paginationReactive.page)
          loadingBar.finish()
        } catch (error: any) {
          loadingBar.error()
          ms.error(error.message ?? 'error')
        }
      },
    })
  }

  function retryRebuild(row: KnowledgeBase.Segment) {
    confirmToggleStatus(row, true)
  }

  // Repair vector drift: server clears missing embedding ids and enqueues re-embedding
  function confirmRepairVector(row: KnowledgeBase.Segment) {
    dialog.warning({
      title: t('knowledgeBase.vectorMissing'),
      content: t('knowledgeBase.vectorMissingConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await api.documentSegmentRepairVector(row.uuid)
          ms.success(t('knowledgeBase.segmentSavedAndReindexing'))
          loadList(paginationReactive.page)
        } catch (error: any) {
          ms.error(error.message ?? 'error')
        }
      },
    })
  }

  // （重新）生成问答对：空=直接生成；已有数据=确认后替换式重新生成（服务端守卫在跑/生成中）
  function confirmGenerateQa() {
    if (paginationReactive.itemCount > 0) {
      dialog.warning({
        title: t('knowledgeBase.regenerateQaTitle'),
        content: t('knowledgeBase.regenerateQaConfirm'),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: () => doGenerateQa(),
      })
      return
    }
    doGenerateQa()
  }

  async function doGenerateQa() {
    try {
      await api.knowledgeBaseItemAutoGenerateQa(options.curDocUuid.value)
      await options.loadDocInfo(options.curDocUuid.value)
      await loadList(1)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    }
  }

  // Rechunk replaces every child chunk under the parent with a fresh split of the parent content
  function confirmRegenerateChildren(row: KnowledgeBase.Segment) {
    dialog.warning({
      title: t('knowledgeBase.rechunkChildren'),
      content: t('knowledgeBase.rechunkChildrenConfirm'),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        try {
          await api.documentSegmentChildRegenerate({ id: row.id, docUuid: options.curDocUuid.value })
          ms.success(t('knowledgeBase.segmentSavedAndReindexing'))
          loadList(paginationReactive.page)
        } catch (error: any) {
          ms.error(error.message ?? 'error')
        }
      },
    })
  }

  // Text mode table columns; qa and parent_child modes render their own list components
  const createColumns = (): DataTableColumns<KnowledgeBase.Segment> => [
    {
      title: '#',
      key: 'position',
      width: 60,
      render: row => row.position + 1,
    },
    {
      title: t('knowledgeBase.docFragment'),
      key: 'content',
      render: row => h(ExpandableText, { text: row.content, lines: 3 }),
    },
    {
      title: t('knowledgeBase.segmentHitCount'),
      key: 'hitCount',
      width: 90,
    },
    {
      title: t('knowledgeBase.wordCount'),
      key: 'wordCount',
      width: 90,
    },
    createStatusColumn(t, { onRetry: retryRebuild, onRepair: confirmRepairVector }),
    {
      title: t('common.action'),
      key: 'actions',
      width: 140,
      render: row => h('div', { class: 'flex items-center gap-2' }, {
        default: () => [
          h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openEdit('segment', row) }, { default: () => t('common.edit') }),
          h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => confirmToggleStatus(row, row.isEnabled === false) }, { default: () => row.isEnabled === false ? t('knowledgeBase.enable') : t('knowledgeBase.disable') }),
          h(NButton, { text: true, type: 'error', size: 'small', onClick: () => confirmDelete('segment', row.uuid) }, { default: () => t('common.delete') }),
        ],
      }),
    },
  ]

  const columns = computed<DataTableColumns<KnowledgeBase.Segment>>(() => createColumns())

  async function onHandlePageChange(currentPage: number) {
    loadList(currentPage)
  }

  // KeepAlive 隐藏本页时暂停列表轮询，返回时由 onActivated 恢复
  onDeactivated(() => {
    if (refreshTimer)
      clearTimeout(refreshTimer)
  })

  onUnmounted(() => {
    if (refreshTimer)
      clearTimeout(refreshTimer)
  })

  return {
    segments,
    loading,
    segmentMode,
    paginationReactive,
    editState,
    submitting,
    editTitle,
    loadList,
    scheduleAutoRefresh,
    openEdit,
    openAddQaPair,
    openAddChild,
    openEditQaPair,
    saveEdit,
    confirmDelete,
    confirmToggleStatus,
    confirmRegenerateChildren: confirmRegenerateChildren,
    retryRebuild,
    confirmRepairVector,
    confirmGenerateQa,
    columns,
    onHandlePageChange,
  }
}
