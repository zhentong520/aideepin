import { h } from 'vue'
import { BasicColumn } from '@/components/Table'
import { useI18n } from '@/locales'
import { NText } from 'naive-ui'

/**
 * T6-13 成本看板列定义与类型。
 * ⚠️ 后端 status 语义：OK=正常（可能含 priced=false 的行）、NO_DATA=范围内无记录、
 * UNAVAILABLE=数据源故障——此时不渲染任何 0 数字。
 */
export interface CostAggItem {
  groupKey: string
  modelPlatform?: string
  callCount: number
  inputTokens: number
  outputTokens: number
  inputCost?: string | null
  outputCost?: string | null
  totalCost?: string | null
  priced: boolean
}

export interface CostDashboard {
  status: 'OK' | 'NO_DATA' | 'UNAVAILABLE'
  reason?: string
  currency: string
  byModel: CostAggItem[]
  byChannel: CostAggItem[]
  summary: {
    callCount: number
    inputTokens: number
    outputTokens: number
    totalCost?: string | null
    priced: boolean
  }
}

export function formatCost(v?: string | null): string {
  if (v == null) {
    return '-'
  }
  const n = Number(v)
  if (Number.isNaN(n)) {
    return String(v)
  }
  return n >= 1 ? n.toFixed(2) : n.toFixed(6).replace(/0+$/, '').replace(/\.$/, '')
}

export function getModelColumns(): BasicColumn<CostAggItem>[] {
  const { t } = useI18n()
  return [
    { title: t('tokenMonitor.model'), key: 'groupKey', width: 220 },
    { title: t('columns.modelPlatform'), key: 'modelPlatform', width: 120 },
    { title: t('tokenMonitor.callCount'), key: 'callCount', width: 90 },
    { title: t('columns.inputTokens'), key: 'inputTokens', width: 110 },
    { title: t('columns.outputTokens'), key: 'outputTokens', width: 110 },
    {
      title: t('tokenMonitor.totalCost'),
      key: 'totalCost',
      width: 140,
      render(row) {
        if (!row.priced) {
          return h(NText, { depth: 3 }, { default: () => t('tokenMonitor.unpriced') })
        }
        return formatCost(row.totalCost)
      },
    },
  ]
}

export function getChannelColumns(): BasicColumn<CostAggItem>[] {
  const { t } = useI18n()
  return [
    { title: t('tokenMonitor.channel'), key: 'groupKey', width: 200 },
    { title: t('tokenMonitor.callCount'), key: 'callCount', width: 90 },
    { title: t('columns.inputTokens'), key: 'inputTokens', width: 110 },
    { title: t('columns.outputTokens'), key: 'outputTokens', width: 110 },
  ]
}
