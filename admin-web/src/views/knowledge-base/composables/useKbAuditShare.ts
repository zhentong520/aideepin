import { computed, h, reactive, ref } from 'vue'
import { NTag } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { useDialog, useMessage } from 'naive-ui'
import { t } from '@/locales'
import kbAuditShare from '@/api/kbAuditShare'
import userApi from '@/api/user'
import { TableAction } from '@/components/Table'

// 操作审计流水 + 细粒度共享管理

export function useKbAuditShare(kbUuid: string) {
  const ms = useMessage()
  const dialog = useDialog()

  // ===== 操作审计 =====
  const auditList = ref<any[]>([])
  const auditLoading = ref(false)
  const auditPagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    prefix: () => t('common.total', { n: auditPagination.itemCount }),
  })

  const ACTION_LABELS: Record<string, string> = {
    KB_CREATE: t('knowledgeBase.auditActCreate'),
    KB_UPDATE: t('knowledgeBase.auditActUpdate'),
    KB_DELETE: t('knowledgeBase.auditActDelete'),
    KB_DOC_UPLOAD: t('knowledgeBase.auditActDocUpload'),
    DOC_CREATE: t('knowledgeBase.auditActDocCreate'),
    DOC_UPDATE: t('knowledgeBase.auditActDocUpdate'),
    DOC_DELETE: t('knowledgeBase.auditActDocDelete'),
    DOC_TOGGLE: t('knowledgeBase.auditActDocToggle'),
    DOC_REINDEX: t('knowledgeBase.auditActDocReindex'),
    DOC_OCR: t('knowledgeBase.auditActDocOcr'),
    SHARE_GRANT: t('knowledgeBase.auditActShareGrant'),
    SHARE_REVOKE: t('knowledgeBase.auditActShareRevoke'),
    AUDIT_VIEW: t('knowledgeBase.auditActView'),
  }

  const auditColumns = computed(() => [
    {
      title: t('knowledgeBase.auditAction'),
      key: 'action',
      width: 150,
      render: (row: any) =>
        h(NTag, { size: 'small' }, { default: () => ACTION_LABELS[row.action] || row.action || '-' }),
    },
    { title: t('knowledgeBase.auditTargetType'), key: 'targetType', width: 100 },
    {
      title: t('knowledgeBase.auditTarget'),
      key: 'targetUuid',
      minWidth: 160,
      ellipsis: { tooltip: true },
    },
    { title: t('knowledgeBase.auditOperator'), key: 'operatorName', width: 120 },
    {
      title: t('knowledgeBase.auditResult'),
      key: 'success',
      width: 90,
      render: (row: any) =>
        h(
          NTag,
          { type: row.success ? 'success' : 'error', size: 'small' },
          { default: () => (row.success ? t('common.normal') : t('common.failed')) }
        ),
    },
    {
      title: t('knowledgeBase.auditDetail'),
      key: 'detail',
      minWidth: 160,
      ellipsis: { tooltip: true },
      render: (row: any) => row.detail || '-',
    },
    { title: t('knowledgeBase.auditTime'), key: 'createTime', width: 180 },
  ])

  async function loadAudit(currentPage = 1) {
    auditLoading.value = true
    try {
      const resp: any = await kbAuditShare.listAudit(kbUuid, currentPage, auditPagination.pageSize)
      auditList.value = resp.data?.records || []
      auditPagination.page = currentPage
      auditPagination.itemCount = resp.data?.total || 0
    } finally {
      auditLoading.value = false
    }
  }

  // ===== 共享管理 =====
  const shareList = ref<any[]>([])
  const shareTargetId = ref<number | null>(null)
  const sharePermission = ref<string>('READ')
  const shareSubmitting = ref(false)
  const userOptions = ref<{ label: string; value: number }[]>([])
  const permissionOptions = [
    { label: t('knowledgeBase.sharePermissionRead'), value: 'READ' },
    { label: t('knowledgeBase.sharePermissionWrite'), value: 'WRITE' },
  ]

  function userName(id: number) {
    const u = userOptions.value.find((o) => o.value === id)
    return u ? u.label : `${id}`
  }

  const shareColumns = computed<DataTableColumns<any>>(() => [
    {
      title: t('knowledgeBase.shareTargetUser'),
      key: 'targetId',
      minWidth: 200,
      render: (row: any) => userName(row.targetId),
    },
    {
      title: t('knowledgeBase.sharePermission'),
      key: 'permission',
      width: 110,
      render: (row: any) =>
        h(
          NTag,
          { size: 'small', type: row.permission === 'WRITE' ? 'warning' : 'default' },
          {
            default: () =>
              row.permission === 'WRITE'
                ? t('knowledgeBase.sharePermissionWrite')
                : t('knowledgeBase.sharePermissionRead'),
          }
        ),
    },
    { title: t('knowledgeBase.shareGrantor'), key: 'grantorName', width: 120 },
    { title: t('knowledgeBase.shareGrantedAt'), key: 'createTime', width: 180 },
    {
      title: t('common.action'),
      key: 'action',
      width: 120,
      fixed: 'right',
      render: (row: any) =>
        h(TableAction, {
          style: 'button',
          actions: [{ label: t('knowledgeBase.shareRevoke'), key: 'revoke', onClick: () => onRevokeShare(row) }],
        } as any),
    },
  ])

  async function loadShares() {
    const resp: any = await kbAuditShare.listShares(kbUuid)
    shareList.value = resp.data || []
  }

  async function loadUsers() {
    try {
      const resp: any = await userApi.search({}, { current: 1, size: 200 })
      const records = resp.data?.records || []
      userOptions.value = records.map((u: any) => ({
        label: `${u.name || u.email || u.id}${u.email ? ` (${u.email})` : ''}`,
        value: u.id,
      }))
    } catch {
      // 忽略：用户列表拉取失败不影响主流程
    }
  }

  async function onGrantShare() {
    if (!shareTargetId.value) {
      ms.warning(t('knowledgeBase.shareNoUser'))
      return
    }
    shareSubmitting.value = true
    try {
      await kbAuditShare.grantShare(kbUuid, shareTargetId.value as number, sharePermission.value)
      ms.success(t('knowledgeBase.shareGrantedSuccess'))
      shareTargetId.value = null
      await loadShares()
    } catch (e: any) {
      ms.error(e?.message || t('common.failed'))
    } finally {
      shareSubmitting.value = false
    }
  }

  function onRevokeShare(row: any) {
    dialog.warning({
      title: t('knowledgeBase.deleteConfirmTitle'),
      content: t('knowledgeBase.shareRevokeConfirm'),
      positiveText: t('common.positiveText'),
      negativeText: t('common.negativeText'),
      onPositiveClick: async () => {
        await kbAuditShare.revokeShare(kbUuid, row.targetId)
        ms.info(t('knowledgeBase.shareRevokedSuccess'))
        await loadShares()
      },
    })
  }

  return {
    auditList,
    auditLoading,
    auditPagination,
    auditColumns,
    loadAudit,
    shareList,
    shareTargetId,
    sharePermission,
    shareSubmitting,
    userOptions,
    permissionOptions,
    shareColumns,
    loadUsers,
    loadShares,
    onGrantShare,
  }
}
