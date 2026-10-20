import { h } from 'vue'
import { BasicColumn } from '@/components/Table'
import { NText } from 'naive-ui'
import { useI18n } from '@/locales'

/**
 * T7-01 RAG 质量看板类型与列定义。
 * ⚠️ 三态语义：OK / NO_DATA / UNAVAILABLE；UNAVAILABLE 时不渲染任何数字（不显示 0）。
 * 指标为 null 表示「无分数」（如全部未命中时 avgTopScore），展示「-」而不是 0。
 */
export interface RagQualityDashboard {
  status: 'OK' | 'NO_DATA' | 'UNAVAILABLE'
  reason?: string
  summary: {
    totalQueries: number
    hitQueries: number
    lowScoreQueries: number
    /** 命中率（%）；totalQueries=0 时为 null */
    hitRate?: number | null
    avgHitCount?: number | null
    /** 平均 Top 分数；全部未命中时为 null（不展示 0） */
    avgTopScore?: number | null
    lowScoreShare?: number | null
    elapsedP95?: number | null
    topScoreP50?: number | null
    avgElapsedMs?: number | null
    lowScoreThreshold?: number | null
  }
  byKb: KbQualityItem[]
  feedback: {
    passCount: number
    failCount: number
  }
}

export interface KbQualityItem {
  kbUuid: string
  kbTitle: string
  queryCount: number
  hitQueries: number
  hitRate?: number | null
  avgHitCount?: number | null
  avgTopScore?: number | null
  avgElapsedMs?: number | null
}

export interface RagQualityDetailItem {
  queryId: string
  createTime: string
  userUuid: string
  queryText: string
  kbUuids: string[]
  hitCount?: number | null
  topScore?: number | null
  elapsedMs?: number | null
  embeddingModel: string
  feedbackRating: string
  feedbackComment: string
}

/** 指标 null（无分数）展示「-」；数字按精度格式化 */
export function fmtNum(v?: number | null, digits = 2): string {
  if (v == null || Number.isNaN(v)) {
    return '-'
  }
  return v.toFixed(digits)
}

export function fmtPct(v?: number | null): string {
  if (v == null || Number.isNaN(v)) {
    return '-'
  }
  return v.toFixed(1) + '%'
}

export function getByKbColumns(): BasicColumn<KbQualityItem>[] {
  const { t } = useI18n()
  return [
    { title: t('ragQuality.kbTitle'), key: 'kbTitle', width: 200 },
    { title: t('ragQuality.queryCount'), key: 'queryCount', width: 100 },
    { title: t('ragQuality.hitRate'), key: 'hitRate', width: 110, render: (row) => fmtPct(row.hitRate) },
    { title: t('ragQuality.avgHitCount'), key: 'avgHitCount', width: 130, render: (row) => fmtNum(row.avgHitCount) },
    { title: t('ragQuality.avgTopScore'), key: 'avgTopScore', width: 120, render: (row) => fmtNum(row.avgTopScore) },
    {
      title: t('ragQuality.avgElapsed'),
      key: 'avgElapsedMs',
      width: 120,
      render: (row) => (row.avgElapsedMs == null ? '-' : Math.round(row.avgElapsedMs) + ' ms'),
    },
  ]
}

export function getDetailColumns(): BasicColumn<RagQualityDetailItem>[] {
  const { t } = useI18n()
  return [
    { title: t('ragQuality.colTime'), key: 'createTime', width: 160 },
    { title: t('ragQuality.colQueryText'), key: 'queryText', width: 280, ellipsis: { tooltip: true } },
    {
      title: t('ragQuality.colKb'),
      key: 'kbUuids',
      width: 150,
      render: (row) => h(NText, { depth: 3 }, { default: () => (row.kbUuids || []).join(', ') || '-' }),
    },
    { title: t('ragQuality.colHitCount'), key: 'hitCount', width: 90 },
    {
      title: t('ragQuality.colTopScore'),
      key: 'topScore',
      width: 100,
      render: (row) => fmtNum(row.topScore, 3),
    },
    {
      title: t('ragQuality.colElapsed'),
      key: 'elapsedMs',
      width: 100,
      render: (row) => (row.elapsedMs == null ? '-' : row.elapsedMs + ' ms'),
    },
    {
      title: t('ragQuality.colFeedback'),
      key: 'feedbackRating',
      width: 90,
      render: (row) =>
        row.feedbackRating === 'PASS' ? '👍' : row.feedbackRating === 'FAIL' ? '👎' : '-',
    },
  ]
}
