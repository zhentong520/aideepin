import { computed, onDeactivated, onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'
import type { DialogApi, MessageApi } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'
import { usePollFailureGuard } from './usePollFailureGuard'

/**
 * 文档信息加载 + 索引状态轮询（自 DocumentDetail 拆出，T5-10）：
 * 文档/知识库信息、向量与图谱双维度索引状态、失败明细、重试与图谱化入口。
 * 行为保持与拆分前一致：静默轮询、失败封顶、DOING→落定联动分段列表刷新。
 */
export function useDocIndex(options: {
  ms: MessageApi
  dialog: DialogApi
  kbUuid: string
  curDocUuid: Ref<string>
  curDoc: KnowledgeBase.Document
  curKb: KnowledgeBase.Info
  segmentMode: Ref<'text' | 'qa' | 'parent_child'>
  paginationPage: Ref<number>
  paginationItemCount: Ref<number>
  isSegmentListEmpty: () => boolean
  loadList: (page: number, silent?: boolean) => Promise<void>
  onPollCapped: () => void
}) {
  const { ms, dialog, kbUuid, onPollCapped, curDoc, curKb } = options

  const embeddingStatusLabel = computed(() => {
    switch (curDoc.embeddingStatus) {
      case 'DOING':
        return t('knowledgeBase.statusProcessing')
      case 'DONE':
        return t('knowledgeBase.statusVectorized')
      case 'FAIL':
        return t('knowledgeBase.statusFailed')
      default:
        return t('knowledgeBase.statusPending')
    }
  })

  // Retry label mirrors what retryIndex actually retries (all failed dimensions, possibly both);
  // QA mode with no pairs triggers AI generation instead
  const retryDocLabel = computed(() => {
    if (options.segmentMode.value === 'qa' && options.paginationItemCount.value === 0)
      return t('knowledgeBase.aiGenerateQa')
    const embeddingFailed = curDoc.embeddingStatus === 'FAIL'
    const graphFailed = curDoc.graphicalStatus === 'FAIL'
    if (embeddingFailed && graphFailed)
      return t('knowledgeBase.retryBothIndex')
    if (graphFailed)
      return t('knowledgeBase.retryGraphitize')
    return t('knowledgeBase.retryVectorize')
  })

  // Failure list: the doc row keeps one fail_reason only; per-dimension reasons come from the task table
  const indexFailures = ref<KnowledgeBase.IndexFailure[]>([])

  const failureRows = computed(() => {
    const rows: { label: string; reason: string }[] = []
    const findByType = (type: string) => indexFailures.value.find(f => f.taskType === type)
    if (curDoc.embeddingStatus === 'FAIL')
      rows.push({ label: t('knowledgeBase.vectorize'), reason: findByType('embedding')?.failReason || curDoc.failReason || t('knowledgeBase.statusFailed') })
    if (curDoc.graphicalStatus === 'FAIL')
      rows.push({ label: t('knowledgeBase.graphLabel'), reason: findByType('graphical')?.failReason || curDoc.failReason || t('knowledgeBase.statusFailed') })
    return rows
  })

  const failureSummary = computed(() => {
    const dims: string[] = []
    if (curDoc.embeddingStatus === 'FAIL')
      dims.push(t('knowledgeBase.vectorize'))
    if (curDoc.graphicalStatus === 'FAIL')
      dims.push(t('knowledgeBase.graphLabel'))
    return t('knowledgeBase.indexFailedSummary', { dims: dims.join(', ') })
  })

  // Indexing in progress = executing (DOING) or queued (unfinished task rows): retry enqueues
  // without setting DOING, so the doc stays FAIL while queued and taskUnfinished tells queued
  // apart from finally failed
  const taskUnfinished = ref(false)
  const indexRunning = computed(() => curDoc.embeddingStatus === 'DOING' || curDoc.graphicalStatus === 'DOING')
  // Retry submit lock: keep the "processing" state (button hidden) for at least 5s — it covers
  // the request round-trip window before taskUnfinished is confirmed and rapid re-clicks on error
  const retrySubmitting = ref(false)
  const indexInProgress = computed(() => indexRunning.value || taskUnfinished.value || retrySubmitting.value)

  const runningSummary = computed(() => {
    const dims: string[] = []
    if (curDoc.embeddingStatus === 'DOING' || (curDoc.embeddingStatus === 'FAIL' && (taskUnfinished.value || retrySubmitting.value)))
      dims.push(t('knowledgeBase.vectorize'))
    if (curDoc.graphicalStatus === 'DOING' || (curDoc.graphicalStatus === 'FAIL' && (taskUnfinished.value || retrySubmitting.value)))
      dims.push(t('knowledgeBase.graphLabel'))
    return t('knowledgeBase.indexRunningSummary', { dims: dims.join(', ') })
  })

  // While queued or executing, refresh every 3s (doc status + queue status) until it ends:
  // done collapses the section, a final failure restores the retry button; a page reload
  // mid-queue also recovers the processing state from the task status.
  const { resetPollFailures, recordPollFailure, canPoll } = usePollFailureGuard(onPollCapped)

  let docRefreshTimer: ReturnType<typeof setTimeout> | null = null
  function scheduleDocRefresh() {
    if (docRefreshTimer)
      clearTimeout(docRefreshTimer)
    if (indexInProgress.value && canPoll())
      docRefreshTimer = setTimeout(() => loadDocInfo(options.curDocUuid.value, true), 3000)
  }

  // silent=true is a poll refresh: skip re-fetching KB info, swallow errors (next tick retries);
  // entry-load progress is the caller's top loading bar, and post-retry refresh has no overlay
  // QA generation (and index runs) finish asynchronously: on the DOING -> settled transition
  // reload the segment list too — the list poller only runs while some segment row is rebuilding
  // and an empty list (generation in progress) otherwise never picks up the finished pairs
  async function loadDocInfo(docUuid: string, silent = false) {
    try {
      const docResp = await api.knowledgeBaseItemInfo<KnowledgeBase.Document>(docUuid)
      const wasDoing = curDoc.embeddingStatus === 'DOING'
      Object.assign(curDoc, docResp.data)
      if (wasDoing && curDoc.embeddingStatus !== 'DOING')
        options.loadList(options.paginationPage.value, true)
      if (!silent) {
        const kbResp = await api.knowledgeBaseInfo<KnowledgeBase.Info>(kbUuid)
        Object.assign(curKb, kbResp.data)
      }
      indexFailures.value = []
      if (curDoc.embeddingStatus === 'FAIL' || curDoc.graphicalStatus === 'FAIL') {
        // Queued vs finally failed is decided by the queue status; a failed fetch degrades to
        // "finally failed" (retryable) without blocking the page
        try {
          const [failures, progress] = await Promise.all([
            api.documentIndexFailures<KnowledgeBase.IndexFailure[]>(docUuid),
            api.documentIndexProgress<boolean>(docUuid),
          ])
          indexFailures.value = failures.data || []
          taskUnfinished.value = !!progress.data
        } catch {
          indexFailures.value = []
          taskUnfinished.value = false
        }
      } else {
        taskUnfinished.value = false
      }
      // 列表为空时（如新建的 QA 文档）用文档自身的分段模式初始化，保证空态下也能新增
      if (options.isSegmentListEmpty() && curDoc.segmentMode)
        options.segmentMode.value = curDoc.segmentMode as 'text' | 'qa' | 'parent_child'
      resetPollFailures()
    } catch (error: any) {
      if (!silent)
        ms.error(error.message ?? 'error')
      else
        recordPollFailure()
    } finally {
      scheduleDocRefresh()
    }
  }

  // Document-level retry: gated on the status columns; guarded against re-trigger while running
  async function retryDocIndex() {
    if (indexInProgress.value)
      return
    retrySubmitting.value = true
    // Hold for at least 5s: covers the round-trip before taskUnfinished is confirmed and rapid
    // re-clicks on error; indexInProgress takes over seamlessly when the lock expires
    setTimeout(() => {
      retrySubmitting.value = false
    }, 5000)
    try {
      await api.knowledgeBaseItemRetryIndex(options.curDocUuid.value)
      await loadDocInfo(options.curDocUuid.value)
      await options.loadList(options.paginationPage.value)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    }
  }

  // One-click graph extraction for this doc (same endpoint as the batch index modal,
  // graphical only); enqueue marks the dimension DOING so the poll takes over
  async function graphitize() {
    if (indexInProgress.value)
      return
    try {
      await api.knowledgeBaseItemsIndexing([options.curDocUuid.value], ['graphical'])
      ms.success(t('knowledgeBase.indexTaskRunning'))
      await loadDocInfo(options.curDocUuid.value)
    } catch (error: any) {
      ms.error(error.message ?? 'error')
    }
  }

  // Graph extraction runs the KB ingest model and consumes its tokens; confirm before enqueuing
  function confirmGraphitize() {
    if (indexInProgress.value)
      return
    dialog.warning({
      title: t('knowledgeBase.clickToGraphitize'),
      content: t('knowledgeBase.graphitizeConfirm', { model: curKb.ingestModelName || '-' }),
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => graphitize(),
    })
  }

  // KeepAlive 隐藏本页时暂停文档状态轮询
  onDeactivated(() => {
    if (docRefreshTimer)
      clearTimeout(docRefreshTimer)
  })

  onUnmounted(() => {
    if (docRefreshTimer)
      clearTimeout(docRefreshTimer)
  })

  return {
    curKb,
    curDoc,
    embeddingStatusLabel,
    retryDocLabel,
    failureRows,
    failureSummary,
    indexInProgress,
    runningSummary,
    loadDocInfo,
    resetPollFailures,
    scheduleDocRefresh,
    retryDocIndex,
    confirmGraphitize,
  }
}
