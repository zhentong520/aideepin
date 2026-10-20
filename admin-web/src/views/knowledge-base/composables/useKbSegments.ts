import { computed, h, reactive } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'
import { renderStatusTag, statusMeta } from './statusMeta'

// 文档分块抽屉 + 内容预览抽屉（复用用户端 /document-segment 接口，管理员旁路）

export function useKbSegments() {
  // ===== 查看文档最终分块 =====
  const segDrawer = reactive({
    show: false,
    loading: false,
    docUuid: '',
    docTitle: '',
    list: [] as any[],
  })
  const segPagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    prefix: () => t('common.total', { n: segPagination.itemCount }),
  })

  function onViewSegments(row: any) {
    segDrawer.docUuid = row.uuid
    segDrawer.docTitle = row.title
    segDrawer.show = true
    loadSegments(1)
  }

  async function loadSegments(currentPage: number) {
    segDrawer.loading = true
    try {
      const resp: any = await adminKbDoc.segmentList(segDrawer.docUuid, currentPage, segPagination.pageSize)
      segDrawer.list = resp.data?.records || []
      segPagination.itemCount = resp.data?.total || 0
      segPagination.page = currentPage
    } finally {
      segDrawer.loading = false
    }
  }

  // ===== 内容预览（解析原文 / 清洗对照 / 分块预览，三合一） =====
  const previewDrawer = reactive({
    show: false,
    docUuid: '',
    docTitle: '',
    segmentMode: 'text',
    childMaxChunkSize: undefined as number | undefined | null,
  })

  function onPreviewContent(row: any) {
    previewDrawer.docUuid = row.uuid
    previewDrawer.docTitle = row.title
    previewDrawer.segmentMode = row.segmentMode || 'text'
    previewDrawer.childMaxChunkSize = row.childMaxChunkSize ?? undefined
    previewDrawer.show = true
  }

  // 知识库的入库切分配置作为干跑默认参数（kbInfo 返回的是 KnowledgeBase 实体，含 ingest* 字段）
  const previewKbConfig = computed(() => ({
    strategy: (kbIngestConfig.strategy || 'recursive') as string,
    maxSegmentSize: kbIngestConfig.maxSegmentSize,
    overlap: kbIngestConfig.overlap,
    separator: kbIngestConfig.separator || '',
    childMaxChunkSize: previewDrawer.childMaxChunkSize,
  }))

  // 由宿主（info.vue）在 loadKb 后回填，避免 composable 直接依赖 kb reactive
  const kbIngestConfig = reactive({
    strategy: '',
    maxSegmentSize: undefined as number | undefined | null,
    overlap: undefined as number | undefined | null,
    separator: '',
  })

  function syncKbIngestConfig(kb: any) {
    kbIngestConfig.strategy = kb.ingestSplitStrategy
    kbIngestConfig.maxSegmentSize = kb.ingestMaxSegmentSize
    kbIngestConfig.overlap = kb.ingestMaxOverlap
    kbIngestConfig.separator = kb.ingestCustomSeparator
  }

  // 展开行：parent_child 显示子块、qa 显示问题，其余模式无展开内容
  function renderSegExpand(row: any) {
    const box = (text: string) =>
      h(
        'div',
        {
          style:
            'font-size:12px;line-height:1.7;white-space:pre-wrap;background:rgba(128,128,128,.08);padding:8px var(--adi-space-s12);border-radius:4px',
        },
        text
      )
    const wrap = (nodes: any[]) => h('div', { style: 'padding:4px 8px;display:flex;flex-direction:column;gap:6px' }, nodes)
    if (row.children?.length) {
      return wrap(row.children.map((c: any) => box(`[${c.position ?? '-'}] ${c.content ?? ''}`)))
    }
    if (row.questions?.length) {
      return wrap(row.questions.map((q: any) => box(`Q: ${q.content ?? ''}`)))
    }
    return h('span', { style: 'opacity:.5' }, '-')
  }

  const segColumns = computed<DataTableColumns<any>>(() => [
    { type: 'expand', renderExpand: (row: any) => renderSegExpand(row) },
    { title: '#', key: 'position', width: 56 },
    {
      title: t('common.status'),
      key: 'embeddingStatus',
      width: 110,
      render: (row: any) => renderStatusTag(row.embeddingStatus),
    },
    { title: t('knowledgeBase.wordCount'), key: 'wordCount', width: 90 },
    { title: t('knowledgeBase.hitCount'), key: 'hitCount', width: 90 },
    {
      title: t('knowledgeBase.segmentContent'),
      key: 'content',
      minWidth: 380,
      render: (row: any) =>
        h(
          'div',
          {
            style: 'white-space:pre-wrap;font-size:12px;line-height:1.7;max-height:180px;overflow:auto',
          },
          row.content || '-'
        ),
    },
  ])

  return {
    segDrawer,
    segPagination,
    segColumns,
    onViewSegments,
    loadSegments,
    previewDrawer,
    onPreviewContent,
    previewKbConfig,
    syncKbIngestConfig,
    statusMeta,
  }
}
