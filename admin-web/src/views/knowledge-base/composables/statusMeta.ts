import { h } from 'vue'
import { NTag } from 'naive-ui'
import { t } from '@/locales'

// 文档/分块通用状态元信息（SUCCESS/FAIL/DOING → 标签类型与文案）
export function statusMeta(s: string) {
  switch (s) {
    case 'SUCCESS':
      return { type: 'success' as const, text: t('common.normal') }
    case 'FAIL':
      return { type: 'error' as const, text: t('common.abnormal') }
    case 'DOING':
      return { type: 'warning' as const, text: t('common.status') + '…' }
    default:
      return { type: 'default' as const, text: s || '-' }
  }
}

export function renderStatusTag(s: string) {
  const m = statusMeta(s)
  return h(NTag, { type: m.type, size: 'small' }, { default: () => m.text })
}
