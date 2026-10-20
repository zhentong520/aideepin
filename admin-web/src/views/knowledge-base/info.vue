<template>
  <div class="p-4">
    <NBreadcrumb separator=">">
      <NBreadcrumbItem href="/">
        {{ t('common.home') }}
      </NBreadcrumbItem>
      <NBreadcrumbItem href="/#/knowledge-base/list">
        {{ t('knowledgeBase.title') }}
      </NBreadcrumbItem>
      <NBreadcrumbItem :clickable="false">
        {{ kb.title }}
      </NBreadcrumbItem>
    </NBreadcrumb>

    <!-- 知识库信息 -->
    <NCard style="margin-top: 12px" :title="`${t('knowledgeBase.title')}: ${kb.title}`" hoverable>
      <template #header-extra>
        <NFlex align="center" :size="8">
          <NTag :type="kb.isPublic ? 'success' : 'default'" size="small">
            {{ kb.isPublic ? '公开' : '私有' }}
          </NTag>
          <NTag :type="kb.isStrict ? 'warning' : 'default'" size="small">
            {{ kb.isStrict ? t('knowledgeBase.isStrict') : t('common.no') }}
          </NTag>
          <NButton tertiary size="small" type="info" @click="router.push('/knowledge-base/list')">
            返回
          </NButton>
        </NFlex>
      </template>
      <NFlex :size="[24, 8]" wrap>
        <span>{{ t('knowledgeBase.ownerName') }}：{{ kb.ownerName || '-' }}</span>
        <span>{{ t('knowledgeBase.ingestModelName') }}：{{ kb.ingestModelName || '-' }}</span>
        <span>{{ t('knowledgeBase.knowledgePoints') }}：{{ kb.itemCount ?? 0 }}</span>
        <span>{{ t('knowledgeBase.vectorCount') }}：{{ kb.embeddingCount ?? 0 }}</span>
      </NFlex>
      <div v-if="kb.remark" style="margin-top: 8px; opacity: 0.8">{{ kb.remark }}</div>
    </NCard>

    <!-- 文档管理 -->
    <NCard style="margin-top: 12px" :title="t('knowledgeBase.generatedKnowledge')" hoverable>
      <div class="flex gap-3 mb-4" :class="[isMobile ? 'flex-col' : 'flex-row justify-between']">
        <div class="flex items-left gap-2">
          <NButton type="primary" size="small" @click="showUploadModal = true">
            {{ t('knowledgeBase.addByFile') }}
          </NButton>
          <NButton type="primary" size="small" :disabled="!checkedRowKeys.length" @click="batchReindex">
            {{ t('knowledgeBase.indexSelected') }}
            <template v-if="checkedRowKeys.length"> ({{ checkedRowKeys.length }}) </template>
          </NButton>
        </div>
        <div class="flex items-center">
          <NInput v-model:value="searchValue" style="width: 100%" @keyup.enter="search(1)" />
          <NButton type="primary" ghost @click="search(1)">{{ t('common.search') }}</NButton>
        </div>
      </div>

      <NDataTable
        remote
        :loading="loading"
        :max-height="tableMaxHeight"
        :columns="columns"
        :data="itemList"
        :pagination="pagination"
        :single-line="false"
        :bordered="true"
        :row-key="(row: any) => row.uuid"
        :checked-row-keys="checkedRowKeys"
        @update:checked-row-keys="onChecked"
        @update:page="(p: number) => search(p)"
      />
    </NCard>

    <!-- 操作审计流水 -->
    <NCard style="margin-top: 12px" :title="t('knowledgeBase.auditTitle')" hoverable>
      <NDataTable
        remote
        :loading="auditLoading"
        :max-height="380"
        :columns="auditColumns"
        :data="auditList"
        :pagination="auditPagination"
        :single-line="false"
        :bordered="true"
        :row-key="(row: any) => row.id"
        @update:page="(p: number) => loadAudit(p)"
      />
    </NCard>

    <!-- 细粒度共享管理 -->
    <NCard style="margin-top: 12px" :title="t('knowledgeBase.shareTitle')" hoverable>
      <NFlex :size="[12, 8]" wrap style="margin-bottom: 12px">
        <NSelect
          v-model:value="shareTargetId"
          :options="userOptions"
          :placeholder="t('knowledgeBase.shareSelectUser')"
          style="min-width: 260px"
          filterable
        />
        <NSelect v-model:value="sharePermission" :options="permissionOptions" style="width: 140px" />
        <NButton
          type="primary"
          :disabled="!shareTargetId"
          :loading="shareSubmitting"
          @click="onGrantShare"
        >
          {{ t('knowledgeBase.shareAdd') }}
        </NButton>
      </NFlex>
      <NDataTable
        v-if="shareList.length"
        :columns="shareColumns"
        :data="shareList"
        :single-line="false"
        :bordered="true"
        :row-key="(row: any) => row.id"
      />
      <UxState v-else state="empty" :text="t('knowledgeBase.shareEmpty')" />
    </NCard>

    <!-- 上传 / 来源弹窗 -->
    <!-- 坑（2026-10-03 修）：上传/来源弹窗原为固定 min-height:640px，
         小屏视口下内容与上传按钮溢出卡片之外无法操作；且点遮罩会静默丢失已选上传方式。
         改为视口相对高度 + 内容限高滚动 + 遮罩不可关。 -->
    <NModal
      v-model:show="showUploadModal"
      style="width: 90%; max-height: 85vh;"
      preset="card"
      :mask-closable="false"
      content-style="max-height: 78vh; overflow-y: auto;"
      :title="t('knowledgeBase.knowledgeItemUpload')"
    >
      <NCard :title="t('knowledgeBase.uploadDocToGenerate')" hoverable>
        <NSpace vertical>
          {{ t('knowledgeBase.docSource') }}
          <NSelect v-model:value="uploadSource" :options="sourceOptions" />
          <div style="font-size: 12px; opacity: 0.7">{{ t('knowledgeBase.docSourceTip') }}</div>
          {{ t('knowledgeBase.segmentMode') }}
          <NSelect v-model:value="uploadSegmentMode" :options="segmentModeOptions" />
          <div style="font-size: 12px; opacity: 0.7">{{ uploadModeTip }}</div>

          <template v-if="uploadSegmentMode === 'qa' && uploadSource === 'file'">
            <NP depth="3" style="font-size: 12px; margin: 0">{{ t('knowledgeBase.importQaTip') }}</NP>
          </template>
          <template v-else-if="uploadSegmentMode === 'parent_child'">
            {{ t('knowledgeBase.childMaxChunkSize') }}
            <NInputNumber v-model:value="uploadChildMaxChunkSize" :min="50" :max="4000" style="width: 100%" />
          </template>

          <!-- 本地文件 -->
          <template v-if="uploadSource === 'file'">
            <NUpload
              ref="uploadRef"
              v-model:file-list="fileList"
              multiple
              :default-upload="false"
              :max="20"
              :name="'files'"
              :headers="uploadHeaders"
              :action="uploadAction"
              :accept="uploadAccept"
              @finish="onUploadFinish"
              @error="onUploadError"
            >
              <NUploadDragger>
                <div style="margin-bottom: 12px">
                  <NIcon size="48" :depth="3"><ArchiveOutline /></NIcon>
                </div>
                <NText style="font-size: 16px">{{ t('knowledgeBase.clickOrDragToUpload') }}</NText>
                <NP depth="3" style="margin: 8px 0 0 0">
                  {{ t('knowledgeBase.supportedFileFormats') }}<br />{{ t('knowledgeBase.fileSizeLimit') }}
                </NP>
              </NUploadDragger>
            </NUpload>
            <NFlex>
              <NButton type="primary" :disabled="!fileList.length" :loading="uploadSubmitting" @click="onUploadSubmit">
                {{ t('knowledgeBase.uploadAndGenerate') }}
              </NButton>
            </NFlex>
          </template>

          <!-- 网页 URL -->
          <template v-else-if="uploadSource === 'url'">
            {{ t('knowledgeBase.sourceUrlAddress') }}
            <NInput v-model:value="urlForm.url" placeholder="https://example.com/docs" />
            {{ t('knowledgeBase.sourceTitleOptional') }}
            <NInput v-model:value="urlForm.title" :placeholder="t('knowledgeBase.sourceTitleOptional')" />
            {{ t('knowledgeBase.fetchIntervalMinutes') }}
            <NInputNumber v-model:value="urlForm.fetchIntervalMinutes" :min="0" :max="43200" style="width: 100%" />
            <span style="font-size: 12px; opacity: 0.65">{{ t('knowledgeBase.fetchIntervalTip') }}</span>
            <NFlex>
              <NButton type="primary" :loading="sourceSubmitting" :disabled="!urlForm.url.trim()" @click="submitUrlSource">
                {{ t('knowledgeBase.fetchAndCreate') }}
              </NButton>
            </NFlex>
          </template>

          <!-- API -->
          <template v-else>
            {{ t('knowledgeBase.sourceApiTitle') }}
            <NInput v-model:value="apiForm.title" :placeholder="t('knowledgeBase.sourceApiTitlePlaceholder')" />
            {{ t('knowledgeBase.sourceUrlAddress') }}
            <NInput v-model:value="apiForm.url" placeholder="https://api.example.com/v1/faq" />
            {{ t('knowledgeBase.sourceApiMethod') }}
            <NSelect v-model:value="apiForm.method" :options="methodOptions" />
            {{ t('knowledgeBase.sourceApiHeaders') }}
            <NInput v-model:value="apiForm.headers" type="textarea" :rows="3" placeholder='{"Authorization":"Bearer xxx"}' />
            <span style="font-size: 12px; opacity: 0.65">{{ t('knowledgeBase.sourceApiHeadersTip') }}</span>
            {{ t('knowledgeBase.sourceApiBody') }}
            <NInput v-model:value="apiForm.body" type="textarea" :rows="3" />
            {{ t('knowledgeBase.sourceApiContentPath') }}
            <NInput v-model:value="apiForm.contentPath" placeholder="data.list.content" />
            <span style="font-size: 12px; opacity: 0.65">{{ t('knowledgeBase.sourceApiContentPathTip') }}</span>
            {{ t('knowledgeBase.fetchIntervalMinutes') }}
            <NInputNumber v-model:value="apiForm.fetchIntervalMinutes" :min="0" :max="43200" style="width: 100%" />
            <span style="font-size: 12px; opacity: 0.65">{{ t('knowledgeBase.fetchIntervalTip') }}</span>
            <NFlex>
              <NButton type="primary" :loading="sourceSubmitting" :disabled="!apiForm.url.trim() || !apiForm.title.trim()" @click="submitApiSource">
                {{ t('knowledgeBase.fetchAndCreate') }}
              </NButton>
            </NFlex>
          </template>
        </NSpace>
      </NCard>
    </NModal>

    <!-- 内容预览：解析原文 / 清洗对照 / 分块预览（干跑试切）三合一，只读不落库 -->
    <NDrawer v-model:show="previewDrawer.show" :width="900" placement="right">
      <NDrawerContent :title="`${t('knowledgeBase.contentPreview')} - ${previewDrawer.docTitle}`" closable>
        <DocContentPreview
          v-if="previewDrawer.show"
          :doc-uuid="previewDrawer.docUuid"
          :segment-mode="previewDrawer.segmentMode"
          :kb-config="previewKbConfig"
        />
      </NDrawerContent>
    </NDrawer>

    <!-- 查看最终分块：只读展示入库后的拆解结果（text/qa/parent_child 模式感知） -->
    <NDrawer v-model:show="segDrawer.show" :width="880" placement="right">
      <NDrawerContent :title="`${t('knowledgeBase.viewSegments')} - ${segDrawer.docTitle}`" closable>
        <NDataTable
          remote
          :loading="segDrawer.loading"
          :columns="segColumns"
          :data="segDrawer.list"
          :pagination="segPagination"
          :single-line="false"
          :bordered="true"
          :row-key="(row: any) => row.uuid"
          @update:page="(p: number) => loadSegments(p)"
        />
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NBreadcrumb,
  NBreadcrumbItem,
  NButton,
  NCard,
  NDataTable,
  NDrawer,
  NDrawerContent,
  NFlex,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NP,
  NSelect,
  NSpace,
  NTag,
  NText,
  NUpload,
  NUploadDragger,
  useDialog,
  useMessage,
} from 'naive-ui'
import { ArchiveOutline } from '@vicons/ionicons5'
import type { DataTableColumns, UploadFileInfo, UploadInst } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { TableAction } from '@/components/Table'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'
import kbAuditShare from '@/api/kbAuditShare'
import userApi from '@/api/user'
import DocContentPreview from './components/DocContentPreview.vue'

const ms = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const kbUuid = (route.params as { uuid: string }).uuid

const loading = ref(false)
const showUploadModal = ref(false)
const itemList = ref<any[]>([])
const tableMaxHeight = ref(500)
const searchValue = ref('')
const isMobile = ref(false)

// ===== 二开：操作审计 & 共享管理 =====
const auditList = ref<any[]>([])
const auditLoading = ref(false)
const auditPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  prefix: () => t('common.total', { n: auditPagination.itemCount }),
})
const shareList = ref<any[]>([])
const shareTargetId = ref<number | null>(null)
const sharePermission = ref<string>('READ')
const shareSubmitting = ref(false)
const userOptions = ref<{ label: string; value: number }[]>([])
const permissionOptions = [
  { label: t('knowledgeBase.sharePermissionRead'), value: 'READ' },
  { label: t('knowledgeBase.sharePermissionWrite'), value: 'WRITE' },
]
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

const kb: any = reactive({
  title: '',
  ownerName: '',
  isPublic: false,
  isStrict: false,
  ingestModelName: '',
  itemCount: 0,
  embeddingCount: 0,
  remark: '',
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  prefix: () => t('common.total', { n: pagination.itemCount }),
})

const checkedRowKeys = ref<string[]>([])
function onChecked(keys: Array<string | number>) {
  checkedRowKeys.value = keys.map((k) => `${k}`)
}

// 上传白名单：与后端解析能力对齐（txt/md/pdf/Office；图片需开启 OCR）
const DOC_FILE_ACCEPT = '.txt,.md,.markdown,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.png,.jpg,.jpeg,.bmp,.webp'
const uploadSegmentMode = ref<string>('text')
const uploadChildMaxChunkSize = ref<number | null>(200)
const segmentModeOptions = [
  { label: t('knowledgeBase.segmentModeText'), value: 'text' },
  { label: t('knowledgeBase.segmentModeQa'), value: 'qa' },
  { label: t('knowledgeBase.segmentModeParentChild'), value: 'parent_child' },
]
const uploadSource = ref<string>('file')
const sourceOptions = [
  { label: t('knowledgeBase.sourceFile'), value: 'file' },
  { label: t('knowledgeBase.sourceUrl'), value: 'url' },
  { label: t('knowledgeBase.sourceApi'), value: 'api' },
]
const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
]
const uploadAccept = computed(() => (uploadSegmentMode.value === 'qa' ? '.xlsx,.xls,.csv' : DOC_FILE_ACCEPT))
const uploadAction = computed(
  () => `/api/knowledge-base/uploadDocs/${kbUuid}?indexAfterUpload=true&indexTypes=embedding`
)
const uploadHeaders = computed(() => ({ Authorization: userStore.token }))
const uploadModeTip = computed(
  () =>
    ({
      text: t('knowledgeBase.segmentModeText'),
      qa: t('knowledgeBase.importQaTip'),
      parent_child: t('knowledgeBase.childMaxChunkSizeTip'),
    }[uploadSegmentMode.value] ?? '')
)

const urlForm = reactive({ url: '', title: '', fetchIntervalMinutes: null as number | null })
const apiForm = reactive({
  title: '',
  url: '',
  method: 'GET',
  headers: '',
  body: '',
  contentPath: '',
  fetchIntervalMinutes: null as number | null,
})
const sourceSubmitting = ref(false)
const uploadRef = ref<UploadInst | null>(null)
const fileList = ref<UploadFileInfo[]>([])
const uploadSubmitting = ref(false)
let uploadHasError = false

function statusMeta(s: string) {
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

const columns = computed<DataTableColumns<any>>(() => [
  { title: t('common.title'), key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: t('knowledgeBase.docSource'),
    key: 'sourceType',
    width: 110,
    render: (row: any) => {
      const map: Record<string, string> = {
        file: t('knowledgeBase.sourceFile'),
        url: t('knowledgeBase.sourceUrl'),
        api: t('knowledgeBase.sourceApi'),
      }
      return map[row.sourceType] || row.sourceType || '-'
    },
  },
  {
    title: t('common.status'),
    key: 'embeddingStatus',
    width: 160,
    render: (row: any) => {
      const m = statusMeta(row.embeddingStatus)
      return h(NTag, { type: m.type, size: 'small' }, { default: () => m.text })
    },
  },
  {
    title: t('knowledgeBase.failReason'),
    key: 'failReason',
    minWidth: 160,
    ellipsis: { tooltip: true },
    render: (row: any) => row.failReason || '-',
  },
  { title: t('common.createTime'), key: 'createTime', width: 170 },
  {
    title: t('common.action'),
    key: 'action',
    // 主按钮 5 个（内容预览 / 查看分块 / 重建索引 / 启停 / 删除），
    // 低频且有条件的「重试OCR、重新抓取」收进「更多」下拉，避免操作列溢出被裁。
    width: 340,
    fixed: 'right',
    render(row: any) {
      const actions: any[] = []
      actions.push({
        label: t('knowledgeBase.contentPreview'),
        key: 'contentPreview',
        onClick: () => onPreviewContent(row),
      })
      actions.push({
        label: t('knowledgeBase.viewSegments'),
        key: 'segments',
        onClick: () => onViewSegments(row),
      })
      actions.push({
        label: t('knowledgeBase.reindex'),
        key: 'reindex',
        onClick: () => onRetryIndex(row),
      })
      const enableLabel = row.isEnabled === false ? t('common.enable') : t('common.disable')
      actions.push({
        label: enableLabel,
        key: 'toggle',
        onClick: () => onToggleStatus(row, row.isEnabled !== false),
      })
      actions.push({ label: t('common.delete'), key: 'delete', onClick: () => onDelete(row) })

      const dropDownActions: any[] = []
      if (row.embeddingStatus === 'FAIL') {
        dropDownActions.push({ label: t('knowledgeBase.retryOcr'), key: 'ocr' })
      }
      if (row.sourceType === 'url' || row.sourceType === 'api') {
        dropDownActions.push({ label: t('knowledgeBase.refetch'), key: 'refetch' })
      }
      return h(
        // @ts-ignore
        TableAction,
        {
          style: 'button',
          actions,
          dropDownActions,
          select: (key: string) => {
            if (key === 'ocr') onRetryOcr(row)
            else if (key === 'refetch') onRefetch(row)
          },
        }
      )
    },
  },
])

// 表格操作按钮组件复用 @/components/Table 的 TableAction（已在顶部导入）

// ===== 二开：查看文档最终分块（复用用户端 /document-segment/list，管理员旁路） =====
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

// ===== 二开：内容预览（解析原文 / 清洗对照 / 分块预览，三合一） =====
// 复用用户端 /document/info 与 /document-segment/preview，管理员 isAdmin 旁路已生效，后端零改动。
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
  strategy: kb.ingestSplitStrategy || 'recursive',
  maxSegmentSize: kb.ingestMaxSegmentSize ?? undefined,
  overlap: kb.ingestMaxOverlap ?? undefined,
  separator: kb.ingestCustomSeparator || '',
  childMaxChunkSize: previewDrawer.childMaxChunkSize,
}))

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
    render: (row: any) => {
      const m = statusMeta(row.embeddingStatus)
      return h(NTag, { type: m.type, size: 'small' }, { default: () => m.text })
    },
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

function userName(id: number) {
  const u = userOptions.value.find((o) => o.value === id)
  return u ? u.label : `${id}`
}

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

async function search(currentPage: number) {
  loading.value = true
  try {
    const resp: any = await adminKbDoc.docSearch({
      kbUuid,
      keyword: searchValue.value,
      currentPage,
      pageSize: pagination.pageSize,
    })
    itemList.value = resp.data?.records || []
    pagination.page = currentPage
    pagination.itemCount = resp.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadKb() {
  const resp: any = await adminKbDoc.kbInfo(kbUuid)
  Object.assign(kb, resp.data || {})
}

function onUploadSubmit() {
  if (uploadSubmitting.value) return
  uploadHasError = false
  uploadSubmitting.value = true
  uploadRef.value?.submit()
  closeWhenUploadDone()
}

function closeWhenUploadDone() {
  setTimeout(() => {
    if (!showUploadModal.value) {
      uploadSubmitting.value = false
      return
    }
    const busy = fileList.value.some((f) => f.status === 'pending' || f.status === 'uploading')
    if (busy) {
      closeWhenUploadDone()
      return
    }
    uploadSubmitting.value = false
    if (!uploadHasError) {
      showUploadModal.value = false
      search(1)
    }
  }, 1000)
}

function onUploadFinish({ file, event }: { file: UploadFileInfo; event?: ProgressEvent }) {
  let success = false
  let message = ''
  try {
    const resp = JSON.parse((event?.target as XMLHttpRequest)?.response || '{}')
    success = !!resp.success
    message = resp.message || ''
  } catch {
    success = false
  }
  if (!success) {
    uploadHasError = true
    const row = fileList.value.find((f) => f.id === file.id)
    if (row) row.status = 'error'
    ms.error(message || t('common.uploadFailed'))
  } else {
    ms.success(t('common.uploadSuccess'))
  }
}

function onUploadError() {
  uploadHasError = true
  ms.error(t('common.uploadFailed'))
}

function parseHeaderInput(text: string): Record<string, string> | undefined {
  const trimmed = (text || '').trim()
  if (!trimmed) return undefined
  try {
    const parsed = JSON.parse(trimmed)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('not object')
    return parsed as Record<string, string>
  } catch {
    ms.error(t('knowledgeBase.sourceApiHeadersTip'))
    return undefined
  }
}

async function submitUrlSource() {
  if (!urlForm.url.trim()) {
    ms.error(t('knowledgeBase.sourceUrlRequired'))
    return
  }
  sourceSubmitting.value = true
  try {
    const resp: any = await adminKbDoc.fromUrl({
      kbUuid,
      url: urlForm.url.trim(),
      title: urlForm.title.trim() || undefined,
      fetchIntervalMinutes: urlForm.fetchIntervalMinutes,
    })
    if (resp?.success !== false) {
      ms.success(t('knowledgeBase.fetchSuccess'))
      showUploadModal.value = false
      search(1)
    }
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    sourceSubmitting.value = false
  }
}

async function submitApiSource() {
  if (!apiForm.url.trim() || !apiForm.title.trim()) {
    ms.error(t('knowledgeBase.sourceApiRequired'))
    return
  }
  const headers = parseHeaderInput(apiForm.headers)
  if (apiForm.headers.trim() && !headers) return
  sourceSubmitting.value = true
  try {
    const resp: any = await adminKbDoc.fromApi({
      kbUuid,
      title: apiForm.title.trim(),
      url: apiForm.url.trim(),
      method: apiForm.method,
      headers,
      body: apiForm.body || undefined,
      contentPath: apiForm.contentPath.trim() || undefined,
      fetchIntervalMinutes: apiForm.fetchIntervalMinutes,
    })
    if (resp?.success !== false) {
      ms.success(t('knowledgeBase.fetchSuccess'))
      showUploadModal.value = false
      search(1)
    }
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  } finally {
    sourceSubmitting.value = false
  }
}

async function onRetryIndex(row: any) {
  try {
    await adminKbDoc.retryIndex(row.uuid)
    ms.success(t('common.indexTaskRunning'))
    search(pagination.page)
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  }
}

async function onRetryOcr(row: any) {
  try {
    await adminKbDoc.retryOcr(row.uuid)
    ms.success(t('common.indexTaskRunning'))
    search(pagination.page)
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  }
}

async function onRefetch(row: any) {
  try {
    await adminKbDoc.refetch(row.uuid)
    ms.success(t('knowledgeBase.refetchSuccess'))
    search(pagination.page)
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  }
}

function onToggleStatus(row: any, isEnabled: boolean) {
  const action = isEnabled ? t('common.disable') : t('common.enable')
  dialog.warning({
    title: t('knowledgeBase.deleteConfirmTitle'),
    content: t('knowledgeBase.toggleStatusConfirm', { tip: action }),
    positiveText: t('common.positiveText'),
    negativeText: t('common.negativeText'),
    onPositiveClick: async () => {
      await adminKbDoc.toggleStatus(row.uuid, isEnabled)
      row.isEnabled = !isEnabled
    },
  })
}

function onDelete(row: any) {
  dialog.warning({
    title: t('knowledgeBase.deleteConfirmTitle'),
    content: `${t('common.deleteConfirmPrefix')} ${row.title} ${t('common.deleteConfirmSuffix')}`,
    positiveText: t('common.positiveText'),
    negativeText: t('common.negativeText'),
    onPositiveClick: async () => {
      await adminKbDoc.deleteDoc(row.uuid)
      ms.info(t('common.deleteSuccess'))
      search(pagination.page)
    },
  })
}

async function batchReindex() {
  if (!checkedRowKeys.value.length) {
    ms.warning(t('knowledgeBase.selectAtLeastOneRow'))
    return
  }
  try {
    for (const uuid of checkedRowKeys.value) {
      await adminKbDoc.retryIndex(uuid)
    }
    ms.success(t('common.indexTaskRunning'))
    search(pagination.page)
  } catch (e: any) {
    ms.error(e?.message || t('common.failed'))
  }
}

onMounted(() => {
  tableMaxHeight.value = Math.max(window.innerHeight - 420, 300)
  isMobile.value = window.innerWidth < 768
  loadKb()
  search(1)
  loadAudit(1)
  loadShares()
  loadUsers()
})
</script>

<style lang="less" scoped></style>
