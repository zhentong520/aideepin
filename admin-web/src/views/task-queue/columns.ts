import { h } from 'vue'
import { NTag } from 'naive-ui'
import { BasicColumn } from '@/components/Table'
import { useI18n } from '@/locales'
import { TaskQueueInfo } from '/#/taskQueue'

const STATUS_LABEL_KEYS: Record<string, string> = {
  PENDING: 'taskQueue.statusPending',
  RUNNING: 'taskQueue.statusRunning',
  SUCCESS: 'taskQueue.statusSuccess',
  FAILED: 'taskQueue.statusFailed',
  DEAD: 'taskQueue.statusDead',
}

const STATUS_TAG_TYPES: Record<string, 'default' | 'info' | 'success' | 'error' | 'warning'> = {
  PENDING: 'warning',
  RUNNING: 'info',
  SUCCESS: 'success',
  FAILED: 'error',
  DEAD: 'error',
}

export function getColumns(): BasicColumn<TaskQueueInfo>[] {
  const { t } = useI18n()
  return [
    {
      title: 'id',
      key: 'id',
      width: 70,
    },
    {
      title: t('taskQueue.taskType'),
      key: 'taskType',
      width: 120,
    },
    {
      title: t('taskQueue.payload'),
      key: 'payload',
      width: 200,
      render(row) {
        if (!row.payload) return '-'
        const text = JSON.stringify(row.payload)
        return text.length > 60 ? `${text.slice(0, 60)}...` : text
      },
    },
    {
      title: t('taskQueue.status'),
      key: 'status',
      width: 100,
      render(row) {
        const label = STATUS_LABEL_KEYS[row.status]
          ? t(STATUS_LABEL_KEYS[row.status])
          : row.status
        return h(
          NTag as any,
          {
            size: 'small',
            type: STATUS_TAG_TYPES[row.status] || 'default',
            bordered: false,
          },
          { default: () => label }
        )
      },
    },
    {
      title: t('taskQueue.priority'),
      key: 'priority',
      width: 70,
    },
    {
      title: t('taskQueue.retryCount'),
      key: 'retryCount',
      width: 110,
      render(row) {
        return `${row.retryCount ?? 0} / ${row.maxRetry ?? 0}`
      },
    },
    {
      title: t('taskQueue.nextRunAt'),
      key: 'nextRunAt',
      width: 170,
      render(row) {
        return row.nextRunAt || '-'
      },
    },
    {
      title: t('taskQueue.ownerInstance'),
      key: 'ownerInstance',
      width: 150,
      render(row) {
        return row.ownerInstance || '-'
      },
    },
    {
      title: t('taskQueue.lastError'),
      key: 'lastError',
      width: 220,
      ellipsis: { tooltip: true },
      render(row) {
        return row.lastError || '-'
      },
    },
    {
      title: t('columns.createTime'),
      key: 'createTime',
      width: 170,
    },
    {
      title: t('columns.updateTime'),
      key: 'updateTime',
      width: 170,
    },
  ]
}
