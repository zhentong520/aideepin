import { h } from 'vue'
import { NTag } from 'naive-ui'
import { BasicColumn } from '@/components/Table'
import { useI18n } from '@/locales'
import { wrapTableTitle } from '@/utils'

export interface KbInfoData {
  id: string
  uuid: string
  title: string
  remark: string
  ownerUuid: string
  ownerName: string
  isPublic: boolean
  starCount: number
  itemCount: number
  embeddingCount: number
  categoryId?: number
  // ===== ACL 配置（T1-07 二开新增）=====
  defaultVisibilityGroups?: string[]
  defaultSensitivity?: 'PUBLIC' | 'ORG' | 'PRIVATE'
  createTime: string
  updateTime: string
}

// 密级展示映射：枚举 -> NTag 语义色
const sensitivityTagType: Record<string, 'success' | 'warning' | 'error'> = {
  PUBLIC: 'success',
  ORG: 'warning',
  PRIVATE: 'error',
}

// id -> 分类名，由列表页在加载分类后填充（渲染时查表，未命中显示未分类）
export const kbCategoryNames: Record<number, string> = {}

export function getColumns(): BasicColumn<KbInfoData>[] {
  const { t } = useI18n()
  return [
    {
      title: 'id',
      key: 'id',
      width: 50,
    },
    {
      title: t('columns.name'),
      key: 'title',
      width: 150,
    },
    {
      title: t('columns.ownerName'),
      key: 'ownerName',
      width: 150,
    },
    {
      title: t('columns.category'),
      key: 'categoryId',
      width: 110,
      render(row) {
        const cid = Number(row.categoryId || 0)
        return cid ? kbCategoryNames[cid] || cid : t('columns.uncategorized')
      },
    },
    {
      title: t('columns.itemCount'),
      key: 'itemCount',
      width: 80,
    },
    {
      title: t('columns.embeddingCount'),
      key: 'embeddingCount',
      width: 80,
    },
    {
      title: t('columns.starCount'),
      key: 'starCount',
      width: 80,
    },
    {
      title: t('columns.isPublic'),
      key: 'isPublic',
      width: 80,
      render(row) {
        return row.isPublic ? t('common.yes') : t('common.no')
      },
    },
    {
      // TODO(T1-07): 「待规范化」标识待后端提供 chunk 规范化标记字段后接入
      // （当前接口无该字段，按约定默认不显示）。
      title: t('columns.defaultSensitivity'),
      key: 'defaultSensitivity',
      width: 90,
      render(row) {
        const v = row.defaultSensitivity
        if (!v) return '-'
        return h(
          NTag,
          { type: sensitivityTagType[v] || 'default', bordered: false, size: 'small' },
          { default: () => t('columns.' + v) }
        )
      },
    },
    {
      key: 'ingestMaxOverlap',
      width: 100,
      title(_column) {
        return wrapTableTitle(t('columns.ingestMaxOverlap'))
      },
    },
    {
      key: 'ingestSplitStrategy',
      width: 100,
      title(_column) {
        return wrapTableTitle(t('columns.ingestSplitStrategy'))
      },
    },
    {
      key: 'ingestMaxSegmentSize',
      width: 100,
      title(_column) {
        return wrapTableTitle(t('columns.ingestMaxSegmentSize'))
      },
    },
    {
      key: 'ingestModelName',
      width: 160,
      title(_column) {
        return wrapTableTitle(t('columns.ingestModelName'))
      },
    },
    {
      key: 'retrieveMaxResults',
      width: 100,
      title(_column) {
        return wrapTableTitle(t('columns.retrieveMaxResults'))
      },
    },
    {
      key: 'retrieveMinScore',
      width: 100,
      title(_column) {
        return wrapTableTitle(t('columns.retrieveMinScore'))
      },
    },
    {
      key: 'queryLlmTemperature',
      width: 120,
      title(_column) {
        return wrapTableTitle(t('columns.queryLlmTemperature'))
      },
    },
    {
      title: t('columns.querySystemMessage'),
      key: 'querySystemMessage',
      width: 150,
    },
    {
      title: t('columns.createTime'),
      key: 'createTime',
      width: 180,
    },
    {
      title: t('columns.updateTime'),
      key: 'updateTime',
      width: 180,
    },
  ]
}
