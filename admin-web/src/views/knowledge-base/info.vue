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
        :scroll-x="1400"
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
        :scroll-x="1100"
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
        :scroll-x="900"
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
          :scroll-x="900"
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'
import { useKbSegments } from './composables/useKbSegments'
import { useKbDocuments } from './composables/useKbDocuments'
import { useKbAuditShare } from './composables/useKbAuditShare'
import { useKbUpload } from './composables/useKbUpload'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const kbUuid = (route.params as { uuid: string }).uuid

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

const isMobile = ref(false)

// 分块抽屉 + 内容预览
const {
  segDrawer,
  segPagination,
  segColumns,
  previewDrawer,
  previewKbConfig,
  syncKbIngestConfig,
  onPreviewContent,
  onViewSegments,
  loadSegments,
} = useKbSegments()

// 文档列表（行操作回调来自 segments）
const {
  loading,
  itemList,
  tableMaxHeight,
  searchValue,
  pagination,
  checkedRowKeys,
  columns,
  onChecked,
  search,
  batchReindex,
} = useKbDocuments({
  kbUuid,
  onPreviewContent,
  onViewSegments,
})

// 操作审计 + 共享管理
const {
  auditList,
  auditLoading,
  auditPagination,
  auditColumns,
  loadAudit,
  loadShares,
  loadUsers,
  shareList,
  shareTargetId,
  sharePermission,
  shareSubmitting,
  userOptions,
  permissionOptions,
  shareColumns,
  onGrantShare,
} = useKbAuditShare(kbUuid)

// 上传弹窗（上传/抓取成功后刷新文档列表）
const {
  showUploadModal,
  uploadSegmentMode,
  uploadChildMaxChunkSize,
  segmentModeOptions,
  uploadSource,
  sourceOptions,
  methodOptions,
  uploadAccept,
  uploadAction,
  uploadHeaders,
  uploadModeTip,
  urlForm,
  apiForm,
  sourceSubmitting,
  uploadRef,
  fileList,
  uploadSubmitting,
  onUploadSubmit,
  onUploadFinish,
  onUploadError,
  submitUrlSource,
  submitApiSource,
} = useKbUpload({
  kbUuid,
  userToken: () => userStore.token,
  onUploadSuccess: () => search(1),
})

async function loadKb() {
  const resp: any = await adminKbDoc.kbInfo(kbUuid)
  Object.assign(kb, resp.data || {})
  syncKbIngestConfig(kb)
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
