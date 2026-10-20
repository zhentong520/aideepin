<script setup lang='ts'>
import { NButton, NCard, NCollapse, NCollapseItem, NDataTable, NDrawer, NDrawerContent, NInput, NModal, NP, NSpace, NText, NUpload, NUploadDragger, useDialog, useLoadingBar, useMessage } from 'naive-ui'
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ParentChildSegmentList from './components/ParentChildSegmentList.vue'
import QaSegmentList from './components/QaSegmentList.vue'
import FilePreviewModal from './components/FilePreviewModal.vue'
import ChunkPreview from './components/ChunkPreview.vue'
import { useAuthStore } from '@/store'
import api from '@/api'
import { knowledgeBaseEmptyInfo, knowledgeBaseEmptyItem } from '@/utils/functions'
import { t } from '@/locales'
import { useDocIndex } from './composables/useDocIndex'
import { useSegments } from './composables/useSegments'
import { useQaImport } from './composables/useQaImport'

const route = useRoute()
const router = useRouter()
const ms = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()
void authStore

const { kbUuid } = route.params as { kbUuid: string; docUuid: string }
const curDocUuid = ref<string>('')
// Entry loads use the global top loading bar; poll refreshes are fully silent
const loadingBar = useLoadingBar()

const tableMaxHeight = ref<number>(400)

// ==================== 组装三个职责 composable（T5-10 拆分，行为不变） ====================
// curDoc/curKb 由主文件持有，两个 composable 共享同一 reactive 对象
const curKb = reactive<KnowledgeBase.Info>(knowledgeBaseEmptyInfo())
const curDoc = reactive<KnowledgeBase.Document>(knowledgeBaseEmptyItem())

const pollCappedHandler = () => ms.error(t('knowledgeBase.pollStopped'))

const segmentsCtx = useSegments({
  ms,
  dialog,
  loadingBar,
  curDocUuid,
  curDoc,
  loadDocInfo: async (uuid: string, silent?: boolean) => docIndexCtx.loadDocInfo(uuid, silent),
  onPollCapped: pollCappedHandler,
})

const docIndexCtx = useDocIndex({
  ms,
  dialog,
  kbUuid,
  curDocUuid,
  curDoc,
  curKb,
  segmentMode: segmentsCtx.segmentMode,
  paginationPage: computed(() => segmentsCtx.paginationReactive.page) as any,
  paginationItemCount: computed(() => segmentsCtx.paginationReactive.itemCount) as any,
  isSegmentListEmpty: () => segmentsCtx.segments.value.length === 0,
  loadList: segmentsCtx.loadList,
  onPollCapped: pollCappedHandler,
})

const qaImportCtx = useQaImport({
  curDocUuid,
  onImported: () => {
    docIndexCtx.loadDocInfo(curDocUuid.value)
    segmentsCtx.loadList(1)
  },
})

const {
  segments, loading, segmentMode, paginationReactive, editState, submitting,
  editTitle, openEdit, openAddQaPair, openAddChild, openEditQaPair, saveEdit,
  confirmDelete, confirmToggleStatus, retryRebuild, confirmRepairVector,
  confirmGenerateQa, columns, onHandlePageChange, scheduleAutoRefresh,
} = segmentsCtx

// 模板使用：重建子块确认（parent_child 列表 rechunk 事件）
const { confirmRegenerateChildren } = segmentsCtx

const {
  embeddingStatusLabel, retryDocLabel, failureRows, failureSummary,
  indexInProgress, runningSummary, loadDocInfo, resetPollFailures,
  scheduleDocRefresh, retryDocIndex, confirmGraphitize,
} = docIndexCtx

const {
  showQaImportModal, qaImportHeaders, qaImportFileList,
  onQaImportBefore, onQaImportError, onQaImportFinish, downloadQaTemplate,
} = qaImportCtx

// 正文弹窗：只读查看；编辑跳转文档编辑页——正文编辑联动标题/摘要/模式切换/自动生成
// 等一系列文档级语义，统一在编辑页完成，不在详情页复刻 saveOrUpdate 的子集
const showRawContent = ref(false)

// 分块预览抽屉：把「解析 → 清洗 → 切分」摊开，只读干跑
const showChunkPreview = ref(false)
// 预览组件用知识库当前的切分配置做默认参数（页面上可临时改）
const chunkPreviewKbConfig = computed(() => ({
  strategy: curKb.ingestSplitStrategy || 'recursive',
  maxSegmentSize: curKb.ingestMaxSegmentSize || 512,
  overlap: curKb.ingestMaxOverlap || 0,
  separator: curKb.ingestCustomSeparator || '',
  childMaxChunkSize: curDoc.childMaxChunkSize,
}))

function goEditDocument() {
  showRawContent.value = false
  router.push(`/kb-manage/${kbUuid}/document/${curDocUuid.value}`)
}

function goGraph() {
  router.push({ name: 'DocumentGraph', params: { kbUuid, docUuid: curDocUuid.value } })
}

// Attachment preview mirrors the list page's attachment column
const filePreview = reactive({
  show: false,
  url: '',
  name: '',
})

async function openAttachment() {
  try {
    const resp = await api.documentAttachment<{ name: string; url: string }>(curDocUuid.value)
    if (resp.data?.url) {
      filePreview.url = resp.data.url
      filePreview.name = resp.data.name || curDoc.title
      filePreview.show = true
    }
  } catch (error: any) {
    ms.error(error.message ?? 'error')
  }
}

watch(
  () => route.params.docUuid,
  (docUuid) => {
    const uuid = Array.isArray(docUuid) ? docUuid[0] : docUuid
    if (uuid) {
      curDocUuid.value = uuid
      // Entry loads share the global top loading bar; no local spinners
      loadingBar.start()
      Promise.all([loadDocInfo(uuid), segmentsCtx.loadList(1)]).finally(() => loadingBar.finish())
    }
  },
  { immediate: true },
)

onMounted(() => {
  tableMaxHeight.value = window.innerHeight - 420
})

// KeepAlive caches this page per route: mid-indexing navigation away must pause the pollers
// (the deactivated instance keeps running otherwise) and coming back must resume them;
// re-activation also clears the failure cap so a recovered network can resume polling
onActivated(() => {
  resetPollFailures()
  scheduleDocRefresh()
  scheduleAutoRefresh()
})
</script>

<template>
  <div class="p-4">
    <NBreadcrumb separator=">">
      <NBreadcrumbItem href="/">
        {{ t('common.home') }}
      </NBreadcrumbItem>
      <NBreadcrumbItem href="/#/kb-manage">
        {{ t('knowledgeBase.myKnowledgeBase') }}
      </NBreadcrumbItem>
      <NBreadcrumbItem :href="`/#/kb-manage/${kbUuid}`">
        {{ curKb.title }}
      </NBreadcrumbItem>
      <NBreadcrumbItem :clickable="false">
        {{ curDoc.title }}
      </NBreadcrumbItem>
    </NBreadcrumb>
    <NCard style="margin-top: 12px" :title="curDoc.title" hoverable>
      <div style="white-space: pre-wrap;">
        {{ curDoc.brief }}
      </div>
      <div class="flex flex-wrap gap-x-6 gap-y-1 mt-2" style="font-size: 12px; opacity: 0.7;">
        <span>{{ t('knowledgeBase.vectorize') }}: {{ embeddingStatusLabel }}</span>
        <span>{{ t('knowledgeBase.wordCount') }}: {{ curDoc.wordCount }}</span>
        <span>{{ t('knowledgeBase.embeddingHitCount') }}: {{ curDoc.embeddingHitCount }}</span>
        <span>{{ t('knowledgeBase.createTime') }}: {{ curDoc.createTime }}</span>
        <span>{{ t('knowledgeBase.updateTime') }}: {{ curDoc.updateTime }}</span>
      </div>
      <!-- content → metadata → actions -->
      <div class="flex items-center gap-4 mt-3">
        <!-- remark is the document's own content regardless of segment mode -->
        <NButton text type="primary" size="tiny" @click="showRawContent = true">
          {{ t('knowledgeBase.viewRawContent') }}
        </NButton>
        <!-- 分块预览：看正文被清洗、切成了什么，调参试切不落库 -->
        <NButton text type="primary" size="tiny" @click="showChunkPreview = true">
          {{ t('knowledgeBase.chunkPreview') }}
        </NButton>
        <!-- Only file-converted documents carry a source file -->
        <NButton v-if="curDoc.sourceFileId" text type="primary" size="tiny" @click="openAttachment">
          {{ t('knowledgeBase.viewAttachment') }}
        </NButton>
        <!-- Not graphitized yet: hint text with a one-click generate action (confirm dialog
             first, since extraction runs the KB ingest model and consumes tokens) -->
        <span v-if="curDoc.graphicalStatus === 'NONE'" style="font-size: 12px;">
          {{ t('knowledgeBase.graphNotGenerated') }}
          <NButton text type="primary" size="tiny" :disabled="indexInProgress" @click="confirmGraphitize">
            {{ t('knowledgeBase.clickToGraphitize') }}
          </NButton>
        </span>
        <NButton v-else text type="primary" size="tiny" @click="goGraph">
          {{ t('knowledgeBase.openGraph') }}
        </NButton>
      </div>
    </NCard>
    <NCard style="margin-top: 12px" :title="segmentMode === 'qa' ? t('knowledgeBase.qaPairList') : t('knowledgeBase.segmentList')" hoverable>
      <template #header-extra>
        <NSpace v-if="segmentMode === 'qa' && curDoc.embeddingStatus !== 'DOING'" :wrap="false" :size="8">
          <NButton size="small" type="primary" @click="openAddQaPair">
            {{ t('knowledgeBase.addQaPair') }}
          </NButton>
          <NButton size="small" ghost @click="showQaImportModal = true">
            {{ t('knowledgeBase.importQa') }}
          </NButton>
          <NButton size="small" ghost @click="confirmGenerateQa">
            {{ paginationReactive.itemCount > 0 ? t('knowledgeBase.regenerateQa') : t('knowledgeBase.aiGenerateQa') }}
          </NButton>
        </NSpace>
      </template>
      <ParentChildSegmentList
        v-if="segmentMode === 'parent_child'"
        :segments="segments" :page="paginationReactive.page" :page-size="paginationReactive.pageSize"
        :item-count="paginationReactive.itemCount" :loading="loading"
        @edit-segment="seg => openEdit('segment', seg)"
        @edit-child="child => openEdit('child', child)"
        @add-child="openAddChild"
        @delete-child="uuid => confirmDelete('child', uuid)"
        @delete-segment="uuid => confirmDelete('segment', uuid)"
        @toggle-status="seg => confirmToggleStatus(seg, seg.isEnabled === false)"
        @rechunk="confirmRegenerateChildren"
        @retry="retryRebuild"
        @repair="confirmRepairVector"
        @update:page="onHandlePageChange"
      />
      <QaSegmentList
        v-else-if="segmentMode === 'qa'"
        :segments="segments" :page="paginationReactive.page" :page-size="paginationReactive.pageSize"
        :item-count="paginationReactive.itemCount" :loading="loading" :max-height="tableMaxHeight"
        @edit-pair="openEditQaPair"
        @delete-segment="uuid => confirmDelete('segment', uuid)"
        @toggle-status="seg => confirmToggleStatus(seg, seg.isEnabled === false)"
        @retry="retryRebuild"
        @repair="confirmRepairVector"
        @update:page="onHandlePageChange"
      />
      <NDataTable
        v-else
        remote :loading="loading" :max-height="tableMaxHeight" :columns="columns" :data="segments"
        :row-key="(row: KnowledgeBase.Segment) => row.uuid"
        :pagination="paginationReactive" :single-line="false" :bordered="true" @update:page="onHandlePageChange"
      />
    </NCard>

    <!-- Failure list: collapsed by default, the header keeps failures visible; expanding shows
         per-dimension reasons. While retrying it switches to the running state (button hidden)
         and auto-refreshes via polling -->
    <NCollapse v-if="failureRows.length > 0 || indexInProgress" style="margin-top: 12px">
      <NCollapseItem name="indexFailures">
        <template #header>
          <div class="flex items-center justify-between w-full pr-2">
            <span :style="{ color: indexInProgress ? 'var(--adi-warning)' : 'var(--adi-danger-text)', fontWeight: '600' }">
              {{ indexInProgress ? runningSummary : failureSummary }}
            </span>
            <NButton v-if="!indexInProgress" size="small" type="error" @click.stop="retryDocIndex">
              {{ retryDocLabel }}
            </NButton>
          </div>
        </template>
        <ul v-if="failureRows.length > 0" class="flex flex-col gap-1 m-0 p-0" style="list-style: none;">
          <li v-for="row in failureRows" :key="row.label">
            <span style="font-weight: 600;">{{ row.label }}:</span> {{ row.reason }}
          </li>
        </ul>
      </NCollapseItem>
    </NCollapse>

    <!-- 坑（2026-10-03 修）：问答/问题编辑弹窗含输入框，误点遮罩会静默丢失已编辑内容 -->
    <NModal
      v-model:show="editState.show" style="width: 60%;" preset="card" :mask-closable="false"
      content-style="max-height: 80vh; overflow-y: auto;" :title="editTitle()"
    >
      <NSpace vertical>
        <!-- Question set (add QA pair / pair edit): one input per question; the API layer
             collapses any newlines so users never need to care -->
        <template v-if="editState.type === 'qaPair' || (editState.type === 'question' && editState.isNew)">
          <div>{{ t('knowledgeBase.qaQuestion') }}</div>
          <div v-for="(_, idx) in editState.questions" :key="idx" class="flex items-center gap-2">
            <NInput v-model:value="editState.questions[idx]" :placeholder="t('knowledgeBase.qaQuestionInputPlaceholder')" />
            <NButton
              v-if="editState.questions.length > 1" text type="error" size="tiny"
              @click="editState.questions.splice(idx, 1)"
            >
              ✕
            </NButton>
          </div>
          <NButton dashed size="small" @click="editState.questions.push('')">
            + {{ t('knowledgeBase.addQuestion') }}
          </NButton>
          <span style="font-size: 12px; opacity: 0.65;">{{ t('knowledgeBase.qaQuestionMultiTip') }}</span>
        </template>
        <!-- text segment / qa answer / parent chunk: multiline content -->
        <template v-else>
          <NInput
            v-model:value="editState.content"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 16 }"
          />
        </template>
        <!-- Answer input for add-pair / pair-edit; questions first, matching column order -->
        <template v-if="editState.type === 'qaPair' || (editState.type === 'question' && editState.isNew)">
          {{ t('knowledgeBase.qaAnswer') }}
          <NInput
            v-model:value="editState.answerContent"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
          />
        </template>
        <div class="flex justify-end gap-2">
          <NButton @click="editState.show = false">
            {{ t('common.cancel') }}
          </NButton>
          <NButton type="primary" :loading="submitting" @click="saveEdit">
            {{ t('common.confirm') }}
          </NButton>
        </div>
      </NSpace>
    </NModal>

    <!-- 坑（2026-10-03 修）：原始内容弹窗长度不可控，原不限高 → 底部内容被推出视口 -->
    <NModal
      v-model:show="showRawContent" style="width: 70%" preset="card" content-style="max-height: 80vh; overflow-y: auto;"
      :title="curDoc.title"
    >
      <div style="white-space: pre-wrap; max-height: 65vh; overflow: auto; font-size: 13px;">
        {{ curDoc.remark }}
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton size="small" @click="showRawContent = false">
            {{ t('common.cancel') }}
          </NButton>
          <NButton size="small" type="primary" @click="goEditDocument">
            {{ t('common.edit') }}
          </NButton>
        </div>
      </template>
    </NModal>

    <!-- 分块预览抽屉：解析 → 清洗 → 切分 的中间产物，只读干跑 -->
    <NDrawer v-model:show="showChunkPreview" :width="920" placement="right">
      <NDrawerContent :title="`${t('knowledgeBase.chunkPreview')} - ${curDoc.title}`" closable>
        <ChunkPreview
          :doc-uuid="curDocUuid"
          :kb-config="chunkPreviewKbConfig"
          :segment-mode="segmentMode"
        />
      </NDrawerContent>
    </NDrawer>

    <!-- 坑（2026-10-03 修）：QA 导入弹窗含输入配置，误点遮罩会静默丢失 -->
    <NModal
      v-model:show="showQaImportModal" style="width: 60%;" preset="card" :mask-closable="false"
      content-style="max-height: 80vh; overflow-y: auto;" :title="t('knowledgeBase.importQaTo', { name: curDoc.title })"
    >
      <NSpace vertical>
        <NP>{{ t('knowledgeBase.importQaTip') }}</NP>
        <NUpload
          v-model:file-list="qaImportFileList"
          :max="1" accept=".xlsx,.xls,.csv" directory-dnd
          :action="`/api/document/importQa/${curDocUuid}`"
          :headers="qaImportHeaders" @before-upload="onQaImportBefore" @finish="onQaImportFinish"
          @error="onQaImportError"
        >
          <NUploadDragger>
            <NText style="font-size: 16px">
              {{ t('knowledgeBase.clickOrDragToUpload') }}
            </NText>
            <NP depth="3" style="margin: 8px 0 0 0">
              XLSX / CSV
            </NP>
          </NUploadDragger>
        </NUpload>
        <NSpace>
          <NButton type="primary" ghost @click="downloadQaTemplate">
            {{ t('knowledgeBase.downloadTemplate') }}
          </NButton>
        </NSpace>
      </NSpace>
    </NModal>
    <FilePreviewModal v-model:show="filePreview.show" :file-url="filePreview.url" :file-name="filePreview.name" />
  </div>
</template>
