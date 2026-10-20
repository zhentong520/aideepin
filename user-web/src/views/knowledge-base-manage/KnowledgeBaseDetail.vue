<script setup lang='ts'>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { NAlert, NBreadcrumb, NBreadcrumbItem, NButton, NCard, NCheckbox, NCheckboxGroup, NDataTable, NFlex, NIcon, NInput, NInputNumber, NModal, NP, NSelect, NSpace, NTag, NText, NUpload, NUploadDragger, useDialog, useMessage } from 'naive-ui'
import { ArchiveOutline } from '@vicons/ionicons5'
import { useRoute, useRouter } from 'vue-router'
import { createColumns } from './documentColumns'
import FilePreviewModal from './components/FilePreviewModal.vue'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import { knowledgeBaseEmptyInfo, knowledgeBaseEmptyItem } from '@/utils/functions'
import { t } from '@/locales'
import { useDocUpload } from './composables/useDocUpload'
import { useDocList } from './composables/useDocList'

const ms = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const { kbUuid: curKbUuid } = route.params as { kbUuid: string; kbId: string }

const modalMainHeight = ref<number>(500)
const tableMaxHeight = ref<number>(500)
const showUploadModal = ref<boolean>(false)
const curKnowledgeBase: KnowledgeBase.Info = reactive<KnowledgeBase.Info>(knowledgeBaseEmptyInfo())

const { isMobile } = useBasicLayout()

// ==================== 职责 composable（T5-10 拆分，行为不变） ====================
const docListCtx = useDocList({ ms, dialog, curKbUuid, curKnowledgeBase })

const uploadCtx = useDocUpload({
  ms,
  curKbUuid,
  showUploadModal,
  onImported: () => docListCtx.search(1),
  onIndexCheck: () => docListCtx.indexingCheck(),
})

const {
  itemList, loading, showIndexModal, indexTypeSelected, paginationReactive,
  searchValue, checkedItemRowKeys, checkedItems,
  search, changeIndexModal, textIndexing,
  onHandleCheckedRowKeys, removeCheckedItem, onHandlePageChange, onKeyUpSearch, initData,
} = docListCtx

const {
  uploadRef, headers, fileList, uploadSegmentMode, uploadChildMaxChunkSize,
  segmentModeOptions, uploadAccept, uploadAction, uploadModeTip, uploadSource,
  sourceOptions, methodOptions, urlForm, apiForm, sourceSubmitting,
  submitUrlSource, submitApiSource, onRefetchDoc, fileListLength, uploadSubmitting,
  onUploadBefore, onUploadError, onUploadSubmit, onUploadFinish, downloadQaTemplate,
} = uploadCtx

// 文件预览
const filePreview = reactive({
  show: false,
  url: '',
  name: '',
})

const showFileContent = (selected: KnowledgeBase.Document = knowledgeBaseEmptyItem()) => {
  filePreview.url = selected.sourceFileUrl || ''
  filePreview.name = selected.sourceFileName || selected.title
  filePreview.show = true
}

const viewSegments = (row: KnowledgeBase.Document) => {
  router.push({ name: 'DocumentDetail', params: { kbUuid: curKbUuid, docUuid: row.uuid } })
}

const showGraph = (row: KnowledgeBase.Document) => {
  router.push({ name: 'DocumentGraph', params: { kbUuid: curKbUuid, docUuid: row.uuid } })
}

const editItem = (row: KnowledgeBase.Document) => {
  router.push({ name: 'DocumentEdit', params: { kbUuid: curKbUuid, docUuid: row.uuid } })
}

function rowKey(row: KnowledgeBase.Document) {
  return row.uuid
}

// 序号列宽度按总条数位数自适应；下限按 3 位数计算——旧下限 40px 扣除单元格内边距后
// 连两位数都放不下会换行
// Serial-number column width auto-fits to the digit count of total rows, with a floor
// wide enough for 3 digits (the old 40px floor made two-digit numbers wrap)
const serialColWidth = computed(() => {
  const digits = Math.max(String(Math.max(paginationReactive.itemCount, 1)).length, 3)
  return digits * 10 + 28
})
const columns = computed(() => {
  const cols = createColumns({ viewSegments, showGraph, showFileContent, editItem, deleteKbItem: docListCtx.deleteKbItem, toggleStatus: docListCtx.toggleStatus, refetchDoc: onRefetchDoc })
  cols.splice(1, 0, {
    title: '#',
    key: 'serialNumber',
    width: serialColWidth.value,
    align: 'center',
    render: (_row, index) => (paginationReactive.page - 1) * paginationReactive.pageSize + index + 1,
  })
  return cols
})

const inited = ref<boolean>(false)
onMounted(async () => {
  modalMainHeight.value = window.innerHeight - 150
  tableMaxHeight.value = window.innerHeight - 420
  if (curKnowledgeBase.title === '')
    await initData()
  inited.value = true
})
// Refresh the current page on return from the edit/detail pages; returning from the KB edit
// page also reloads the KB info (title/description/visibility may have changed)
const cameFromRoute = ref('')
router.afterEach((to, from) => {
  if (to.name === 'KnowledgeBaseManageDetail')
    cameFromRoute.value = (from.name as string) || ''
})
onActivated(() => {
  if (!inited.value)
    return
  if (cameFromRoute.value === 'KnowledgeBaseEdit') {
    initData()
    search(paginationReactive.page)
    return
  }
  if (['DocumentAdd', 'DocumentEdit', 'DocumentDetail'].includes(cameFromRoute.value))
    search(paginationReactive.page)
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
      <NBreadcrumbItem :clickable="false">
        {{ curKnowledgeBase.title }}
      </NBreadcrumbItem>
    </NBreadcrumb>
    <NCard
      style="margin-top: 12px"
      :title="`${t('knowledgeBase.knowledgeBase')}: ${curKnowledgeBase.title}(${curKnowledgeBase.isPublic ? t('common.public') : t('common.private')})`" hoverable
    >
      <template #header-extra>
        <NFlex align="center" :size="8">
          <NButton tertiary size="small" type="info" @click="router.push({ name: 'KnowledgeBaseEdit', params: { kbUuid: curKbUuid } })">
            {{ t('common.edit') }}
          </NButton>
        </NFlex>
      </template>
      {{ curKnowledgeBase.remark }}
    </NCard>
    <NCard style="margin-top: 12px" :title="t('knowledgeBase.generatedKnowledge')" hoverable>
      <div class="flex gap-3 mb-4" :class="[isMobile ? 'flex-col' : 'flex-row justify-between']">
        <div class="flex items-left gap-2">
          <NButton type="primary" size="small" @click="router.push({ name: 'DocumentAdd', params: { kbUuid: curKbUuid } })">
            {{ t('knowledgeBase.addByForm') }}
          </NButton>
          <NButton type="primary" size="small" @click="() => showUploadModal = !showUploadModal">
            {{ t('knowledgeBase.addByFile') }}
          </NButton>
          <NButton type="primary" size="small" @click="changeIndexModal()">
            {{ t('knowledgeBase.indexSelected') }}
            <template v-if="checkedItemRowKeys.length > 0">
              ({{ checkedItemRowKeys.length }}{{ t('knowledgeBase.item') }})
            </template>
          </NButton>
        </div>
        <div class="flex items-center">
          <NInput v-model:value="searchValue" style="width: 100%" @keyup="onKeyUpSearch" />
          <NButton type="primary" ghost @click="search(1)">
            {{ t('common.search') }}
          </NButton>
        </div>
      </div>
      <NDataTable
        remote :loading="loading" :max-height="tableMaxHeight" :columns="columns" :data="itemList" :pagination="paginationReactive"
        :single-line="false" :bordered="true" :row-key="rowKey" :checked-row-keys="checkedItemRowKeys"
        @update:checked-row-keys="onHandleCheckedRowKeys" @update:page="onHandlePageChange"
      />
    </NCard>
  </div>

  <!-- Upload files -->
  <!-- 坑（2026-10-03 修）：上传弹窗原为固定 min-height:700px，小屏视口下内容与上传按钮溢出卡片无法操作；点遮罩还会静默丢失已选上传方式 -->
  <NModal
    v-model:show="showUploadModal" style="width: 90%; max-height: 85vh;" preset="card" :mask-closable="false"
    content-style="max-height: 78vh; overflow-y: auto;" :title="t('knowledgeBase.knowledgeItemUpload')"
  >
    <NCard style="margin-top: 12px" :title="t('knowledgeBase.uploadDocToGenerate')" hoverable>
      <NSpace vertical>
        {{ t('knowledgeBase.docSource') }}
        <NSelect v-model:value="uploadSource" :options="sourceOptions" />
        <div style="font-size: 12px; opacity: 0.7">
          {{ t('knowledgeBase.docSourceTip') }}
        </div>
        {{ t('knowledgeBase.segmentMode') }}
        <NSelect v-model:value="uploadSegmentMode" :options="segmentModeOptions" />
        <div style="font-size: 12px; opacity: 0.7">
          {{ uploadModeTip }}
        </div>
        <!-- qa mode: format note and template download -->
        <template v-if="uploadSegmentMode === 'qa' && uploadSource === 'file'">
          <NP depth="3" style="font-size: 12px; margin: 0;">
            {{ t('knowledgeBase.importQaTip') }}
          </NP>
          <div>
            <NButton type="primary" ghost size="small" @click="downloadQaTemplate">
              {{ t('knowledgeBase.downloadTemplate') }}
            </NButton>
          </div>
        </template>
        <template v-else-if="uploadSegmentMode === 'parent_child'">
          {{ t('knowledgeBase.childMaxChunkSize') }}
          <NInputNumber v-model:value="uploadChildMaxChunkSize" :min="50" :max="4000" style="width: 100%" />
          <span style="font-size: 12px; opacity: 0.65;">
            {{ t('knowledgeBase.childMaxChunkSizeTip') }}
          </span>
        </template>
        <template v-if="uploadSource === 'file'">
          <NUpload
            ref="uploadRef" v-model:file-list="fileList" multiple directory-dnd
            :action="uploadAction" :accept="uploadAccept"
            :default-upload="false" :max="20" :headers="headers" @before-upload="onUploadBefore" @finish="onUploadFinish"
            @error="onUploadError"
          >
            <NUploadDragger>
              <div style="margin-bottom: 12px">
                <NIcon size="48" :depth="3">
                  <ArchiveOutline />
                </NIcon>
              </div>
              <NText style="font-size: 16px">
                {{ t('knowledgeBase.clickOrDragToUpload') }}
              </NText>
              <NP depth="3" style="margin: 8px 0 0 0">
                <template v-if="uploadSegmentMode === 'qa'">
                  {{ t('knowledgeBase.qaUploadFileHint') }}<br>
                </template>
                <template v-else>
                  {{ t('knowledgeBase.supportedFileFormats') }}<br>
                </template>
                {{ t('knowledgeBase.fileSizeLimit') }}
              </NP>
            </NUploadDragger>
          </NUpload>
          <NFlex>
            <NButton type="primary" :disabled="!fileListLength" :loading="uploadSubmitting" @click="onUploadSubmit">
              {{ t('knowledgeBase.uploadAndGenerate') }}
            </NButton>
          </NFlex>
        </template>

        <!-- 网页 URL：服务端抓取页面正文入库 -->
        <template v-else-if="uploadSource === 'url'">
          {{ t('knowledgeBase.sourceUrlAddress') }}
          <NInput v-model:value="urlForm.url" placeholder="https://example.com/docs/getting-started" />
          {{ t('knowledgeBase.sourceTitleOptional') }}
          <NInput v-model:value="urlForm.title" :placeholder="t('knowledgeBase.sourceTitlePlaceholder')" />
          {{ t('knowledgeBase.fetchIntervalMinutes') }}
          <NInputNumber v-model:value="urlForm.fetchIntervalMinutes" :min="0" :max="43200" style="width: 100%" />
          <span style="font-size: 12px; opacity: 0.65;">
            {{ t('knowledgeBase.fetchIntervalTip') }}
          </span>
          <NFlex>
            <NButton type="primary" :loading="sourceSubmitting" :disabled="!urlForm.url" @click="submitUrlSource">
              {{ t('knowledgeBase.fetchAndCreate') }}
            </NButton>
          </NFlex>
        </template>

        <!-- API 接口：服务端按配置调用接口并抽取正文 -->
        <template v-else>
          {{ t('knowledgeBase.sourceApiTitle') }}
          <NInput v-model:value="apiForm.title" :placeholder="t('knowledgeBase.sourceApiTitlePlaceholder')" />
          {{ t('knowledgeBase.sourceUrlAddress') }}
          <NInput v-model:value="apiForm.url" placeholder="https://api.example.com/v1/faq" />
          {{ t('knowledgeBase.sourceApiMethod') }}
          <NSelect v-model:value="apiForm.method" :options="methodOptions" />
          {{ t('knowledgeBase.sourceApiHeaders') }}
          <NInput v-model:value="apiForm.headers" type="textarea" :rows="3" placeholder="Authorization 等请求头（JSON 对象）" />
          <span style="font-size: 12px; opacity: 0.65;">
            {{ t('knowledgeBase.sourceApiHeadersTip') }}
          </span>
          {{ t('knowledgeBase.sourceApiBody') }}
          <NInput v-model:value="apiForm.body" type="textarea" :rows="3" />
          {{ t('knowledgeBase.sourceApiContentPath') }}
          <NInput v-model:value="apiForm.contentPath" placeholder="data.list.content" />
          <span style="font-size: 12px; opacity: 0.65;">
            {{ t('knowledgeBase.sourceApiContentPathTip') }}
          </span>
          {{ t('knowledgeBase.fetchIntervalMinutes') }}
          <NInputNumber v-model:value="apiForm.fetchIntervalMinutes" :min="0" :max="43200" style="width: 100%" />
          <span style="font-size: 12px; opacity: 0.65;">
            {{ t('knowledgeBase.fetchIntervalTip') }}
          </span>
          <NFlex>
            <NButton type="primary" :loading="sourceSubmitting" :disabled="!apiForm.url || !apiForm.title" @click="submitApiSource">
              {{ t('knowledgeBase.fetchAndCreate') }}
            </NButton>
          </NFlex>
        </template>
      </NSpace>
    </NCard>
  </NModal>

  <!-- 坑（2026-10-03 修）：索引方式选择弹窗为表单类，误点遮罩会静默丢失选择 -->
  <NModal
    v-model:show="showIndexModal" style="width: 90%; max-width:550px" preset="card" :mask-closable="false"
    :title="t('knowledgeBase.selectIndexType')"
  >
    <NFlex vertical>
      <NAlert :title="t('common.tip')" type="info">
        {{ t('knowledgeBase.graphExplanation') }}
      </NAlert>
      <NCheckboxGroup v-model:value="indexTypeSelected" class="my-2">
        <NFlex vertical>
          <NCheckbox value="embedding" :label="t('knowledgeBase.vectorize')" />
          <NCheckbox value="graphical" :label="t('knowledgeBase.graphitize')" />
        </NFlex>
      </NCheckboxGroup>
      <div class="flex flex-wrap space-x-2">
        <NTag
          v-for="checkedItem in checkedItems" :key="`_${checkedItem.uuid}`" :bordered="false" type="info" closable
          size="small" class="mt-1" @close="removeCheckedItem(checkedItem)"
        >
          {{ checkedItem.title }}
        </NTag>
        <NTag v-if="checkedItems.length === 0" :bordered="false" type="warning" size="small">
          {{ t('knowledgeBase.selectKnowledgeFirst') }}
        </NTag>
      </div>
      <NButton
        type="primary" size="small" :disabled="checkedItems.length === 0 || indexTypeSelected.length === 0"
        @click="textIndexing()"
      >
        {{ t('common.confirm') }}
      </NButton>
    </NFlex>
  </NModal>
  <FilePreviewModal v-model:show="filePreview.show" :file-url="filePreview.url" :file-name="filePreview.name" />
</template>
