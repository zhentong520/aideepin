<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDivider,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NPagination,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'
import { t } from '@/locales'
import adminKbDoc from '@/api/adminKbDoc'

/**
 * 内容预览（管理端）：把文档入库链路的三个阶段摊开看。
 *   1) 解析原文 —— 入库时解析出来的正文（raw_text；迁移 032 前的老文档回退 remark）
 *   2) 清洗对照 —— 清洗量化结果 + 「解析原文 / 清洗后正文」样本并排对照
 *   3) 分块预览 —— 可调参干跑切分，带超长/过短标记，只读不落库
 *
 * 后端 /document/info 与 /document-segment/preview 都走 checkReadPrivilege，
 * 管理员有 isAdmin 旁路，因此后台可预览任意用户的知识库文档，无需新增接口。
 */
const props = defineProps<{
  docUuid: string
  segmentMode?: string
  kbConfig?: {
    strategy?: string
    maxSegmentSize?: number
    overlap?: number
    separator?: string
    childMaxChunkSize?: number | null
  }
}>()

const ms = useMessage()
const activeTab = ref<'raw' | 'clean' | 'chunk'>('raw')

/* ---------------- 1) 解析原文 ---------------- */
// 原文可能整篇几十万字，全量塞进 DOM 会把抽屉拖死；超出只展示前 N 字符并提示。
const RAW_LIMIT = 200000
const rawLoading = ref(false)
const rawText = ref('')
const rawFrom = ref<'' | 'raw_text' | 'remark'>('')
const rawTotal = computed(() => rawText.value.length)
const rawTruncated = computed(() => rawText.value.length > RAW_LIMIT)
const rawShown = computed(() => (rawTruncated.value ? rawText.value.slice(0, RAW_LIMIT) : rawText.value))

async function loadRaw() {
  if (!props.docUuid)
    return
  rawLoading.value = true
  try {
    const resp: any = await adminKbDoc.docInfo(props.docUuid)
    const doc: any = resp?.data || {}
    rawText.value = doc.rawText || doc.remark || ''
    // raw_text 为空说明是迁移 032 之前入库的老文档，预览链路同样回退 remark
    rawFrom.value = doc.rawText ? 'raw_text' : (doc.remark ? 'remark' : '')
  } catch (e: any) {
    ms.error(e?.message ?? 'load document failed')
  } finally {
    rawLoading.value = false
  }
}

/* ---------------- 2/3) 清洗对照 + 分块预览 ---------------- */
const loading = ref(false)
const result = ref<any>(null)
const page = ref(1)
const pageSize = ref(20)
// 折叠状态：长块默认只显示前 300 字
const expanded = ref<Record<number, boolean>>({})
const childrenExpanded = ref<Record<number, boolean>>({})

const form = reactive({
  strategy: 'recursive',
  maxSegmentSize: undefined as number | undefined,
  overlap: undefined as number | undefined,
  separator: '',
  clean: true,
  mergeSoftLineBreaks: false,
  dropDuplicateLines: false,
  childMaxChunkSize: undefined as number | undefined,
})

const strategyOptions = computed(() => [
  { label: 'recursive（递归，默认）', value: 'recursive' },
  { label: 'sentence_cn（中文按句）', value: 'sentence_cn' },
  { label: 'paragraph（段落）', value: 'paragraph' },
  { label: 'line（行）', value: 'line' },
  { label: 'sentence（英文按句）', value: 'sentence' },
  { label: 'custom（自定义分隔符）', value: 'custom' },
])

// 知识库的入库配置作为默认参数（admin 的 kbInfo 返回 KnowledgeBase 实体，含 ingest* 字段）
function resetFromKb() {
  const c: any = props.kbConfig || {}
  form.strategy = c.strategy || 'recursive'
  form.maxSegmentSize = c.maxSegmentSize ?? undefined
  form.overlap = c.overlap ?? undefined
  form.separator = c.separator || ''
  form.childMaxChunkSize = c.childMaxChunkSize ?? undefined
  form.clean = true
  form.mergeSoftLineBreaks = false
  form.dropDuplicateLines = false
}

const isQaMode = computed(() => (result.value?.segmentMode || props.segmentMode) === 'qa')
const isParentChild = computed(() => (result.value?.segmentMode || props.segmentMode) === 'parent_child')

async function run() {
  if (!props.docUuid)
    return
  loading.value = true
  try {
    // 空值不下发，让后端回退到知识库当前配置
    const resp: any = await adminKbDoc.segmentPreview(props.docUuid, {
      strategy: form.strategy,
      maxSegmentSize: form.maxSegmentSize,
      overlap: form.overlap,
      separator: form.separator,
      clean: form.clean,
      mergeSoftLineBreaks: form.mergeSoftLineBreaks,
      dropDuplicateLines: form.dropDuplicateLines,
      childMaxChunkSize: form.childMaxChunkSize,
      page: page.value,
      pageSize: pageSize.value,
    })
    result.value = resp?.data || null
    expanded.value = {}
    childrenExpanded.value = {}
  } catch (e: any) {
    ms.error(e?.message ?? 'preview failed')
  } finally {
    loading.value = false
  }
}

function rerun() {
  page.value = 1
  run()
}

function resetAndRun() {
  resetFromKb()
  rerun()
}

function onPageChange(next: number) {
  page.value = next
  run()
}

function onPageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  run()
}

/* ---------------- 展示辅助 ---------------- */
const PREVIEW_LEN = 300
function displayContent(content: string, index: number) {
  const text = content ?? ''
  if (expanded.value[index] || text.length <= PREVIEW_LEN)
    return text
  return `${text.slice(0, PREVIEW_LEN)}…`
}

const paramsText = computed(() => {
  const p = result.value?.params
  if (!p)
    return '-'
  const parts = [
    `${p.strategy}`,
    `size ${p.maxSegmentSize}`,
    `overlap ${p.overlap}`,
    p.cleanEnabled ? 'clean on' : 'clean off',
  ]
  if (p.childMaxChunkSize)
    parts.push(`child ${p.childMaxChunkSize}`)
  return parts.join(' / ')
})

function load() {
  page.value = 1
  resetFromKb()
  loadRaw()
  run()
}

onMounted(load)
watch(() => props.docUuid, load)
</script>

<template>
  <div class="doc-content-preview">
    <NTabs v-model:value="activeTab" type="line" animated>
      <!-- ============ 1) 解析原文 ============ -->
      <NTabPane name="raw" :tab="t('knowledgeBase.previewTabRaw')">
        <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
          {{ t('knowledgeBase.previewRawTip') }}
        </NAlert>
        <NSpin :show="rawLoading">
          <NCard size="small" :title="t('knowledgeBase.previewRawTitle')">
            <template #header-extra>
              <NSpace :size="10" align="center" :wrap="false">
                <NTag v-if="rawFrom" size="small" :bordered="false">
                  {{
                    rawFrom === 'raw_text'
                      ? t('knowledgeBase.previewRawSourceRawText')
                      : t('knowledgeBase.previewRawSourceRemark')
                  }}
                </NTag>
                <NText depth="3" style="font-size: 12px">
                  {{ t('knowledgeBase.previewRawTotal', { n: rawTotal }) }}
                </NText>
              </NSpace>
            </template>
            <NAlert v-if="rawTruncated" type="warning" :bordered="false" style="margin-bottom: 8px">
              {{ t('knowledgeBase.previewRawTruncated', { n: RAW_LIMIT }) }}
            </NAlert>
            <NEmpty v-if="!rawText" :description="t('knowledgeBase.previewRawEmpty')" />
            <pre v-else class="raw-box">{{ rawShown }}</pre>
          </NCard>
        </NSpin>
      </NTabPane>

      <!-- ============ 2) 清洗对照 ============ -->
      <NTabPane name="clean" :tab="t('knowledgeBase.previewTabClean')">
        <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
          {{ t('knowledgeBase.chunkPreviewTip') }}
        </NAlert>
        <NSpin :show="loading">
          <template v-if="result">
            <NCard size="small" :title="t('knowledgeBase.previewCleanStats')">
              <NSpace :wrap="true" :size="16">
                <span>{{ t('knowledgeBase.rawChars') }}: <b>{{ result.clean?.rawChars ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.cleanedChars') }}: <b>{{ result.clean?.cleanedChars ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.removedChars') }}: <b>{{ result.clean?.removedChars ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.rawLines') }}: <b>{{ result.clean?.rawLines ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.cleanedLines') }}: <b>{{ result.clean?.cleanedLines ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.mergedLines') }}: <b>{{ result.clean?.mergedLines ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.droppedLines') }}: <b>{{ result.clean?.droppedLines ?? '-' }}</b></span>
              </NSpace>
              <NDivider />
              <NCollapse :default-expanded-names="['diff']">
                <NCollapseItem :title="t('knowledgeBase.previewTextDiff')" name="diff">
                  <div class="diff-row">
                    <div class="diff-col">
                      <NText depth="3" style="font-size: 12px">
                        {{ t('knowledgeBase.previewRawSample') }}
                      </NText>
                      <pre class="sample-box">{{ result.rawSample }}</pre>
                    </div>
                    <div class="diff-col">
                      <NText depth="3" style="font-size: 12px">
                        {{ t('knowledgeBase.previewCleanedSample') }}
                      </NText>
                      <pre class="sample-box">{{ result.cleanedSample }}</pre>
                    </div>
                  </div>
                </NCollapseItem>
              </NCollapse>
            </NCard>

          </template>
          <NEmpty v-else :description="t('common.noData')" />
        </NSpin>
      </NTabPane>

      <!-- ============ 3) 分块预览 ============ -->
      <NTabPane name="chunk" :tab="t('knowledgeBase.previewTabChunk')">
        <NSpin :show="loading">
          <template v-if="result">
            <!-- 试切参数：与切分结果同屏，改完点「重新试切」立刻重跑（不落库） -->
            <NCard size="small" :title="t('knowledgeBase.previewParams')">
              <NForm label-placement="left" label-width="130" size="small">
                <NFormItem :label="t('knowledgeBase.previewStrategy')">
                  <NSelect v-model:value="form.strategy" :options="strategyOptions" style="max-width: 260px" />
                </NFormItem>
                <NFormItem :label="t('knowledgeBase.previewMaxSegmentSize')">
                  <NInputNumber v-model:value="form.maxSegmentSize" :min="50" :max="8000" style="max-width: 200px" />
                </NFormItem>
                <NFormItem :label="t('knowledgeBase.previewOverlap')">
                  <NInputNumber v-model:value="form.overlap" :min="0" :max="2000" style="max-width: 200px" />
                </NFormItem>
                <NFormItem v-if="form.strategy === 'custom'" :label="t('knowledgeBase.previewSeparator')">
                  <NInput v-model:value="form.separator" placeholder="\n" style="max-width: 260px" />
                </NFormItem>
                <NFormItem v-if="isParentChild" :label="t('knowledgeBase.childChunkSize')">
                  <NInputNumber v-model:value="form.childMaxChunkSize" :min="50" :max="4000" style="max-width: 200px" />
                </NFormItem>
                <NFormItem :label="t('knowledgeBase.cleanEnabled')">
                  <NSwitch v-model:value="form.clean" />
                </NFormItem>
                <NFormItem :label="t('knowledgeBase.mergeSoftLineBreaks')">
                  <NSwitch v-model:value="form.mergeSoftLineBreaks" :disabled="!form.clean" />
                </NFormItem>
                <NFormItem :label="t('knowledgeBase.dropDuplicateLines')">
                  <NSwitch v-model:value="form.dropDuplicateLines" :disabled="!form.clean" />
                </NFormItem>
              </NForm>
              <NSpace>
                <NButton size="small" type="primary" :loading="loading" @click="rerun">
                  {{ t('knowledgeBase.rerunPreview') }}
                </NButton>
                <NButton size="small" quaternary @click="resetAndRun">
                  {{ t('knowledgeBase.resetParams') }}
                </NButton>
              </NSpace>
            </NCard>

            <NAlert v-if="result.truncated" type="warning" :bordered="false" style="margin: 12px 0">
              {{ t('knowledgeBase.previewTruncated') }}
            </NAlert>
            <NAlert v-if="result.warning" type="warning" :bordered="false" style="margin-bottom: 12px">
              {{ result.warning }}
            </NAlert>

            <NCard size="small" :title="t('knowledgeBase.previewSplitStats')">
              <NSpace :wrap="true" :size="16">
                <span>{{ t('knowledgeBase.chunkCount') }}: <b>{{ result.stats?.chunkCount ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.avgChars') }}: <b>{{ result.stats?.avgChars ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.maxChars') }}: <b>{{ result.stats?.maxChars ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.avgTokens') }}: <b>{{ result.stats?.avgTokens ?? '-' }}</b></span>
                <span>{{ t('knowledgeBase.maxTokens') }}: <b>{{ result.stats?.maxTokens ?? '-' }}</b></span>
                <span>
                  {{ t('knowledgeBase.oversizeChunks') }}:
                  <b :class="{ bad: (result.stats?.oversizeCount ?? 0) > 0 }">{{ result.stats?.oversizeCount ?? 0 }}</b>
                </span>
                <span>
                  {{ t('knowledgeBase.tinyChunks') }}:
                  <b :class="{ warn: (result.stats?.tinyCount ?? 0) > 0 }">{{ result.stats?.tinyCount ?? 0 }}</b>
                </span>
                <span v-if="result.childChunkCount">
                  {{ t('knowledgeBase.childChunkTotal') }}: <b>{{ result.childChunkCount }}</b>
                </span>
              </NSpace>
              <NText depth="3" style="font-size: 12px">
                {{ t('knowledgeBase.previewEffectiveParams') }}: {{ paramsText }}
              </NText>
            </NCard>

            <NCard size="small" :title="`${t('knowledgeBase.chunkList')} (${result.totalChunks ?? 0})`" style="margin-top: 12px">
              <NEmpty v-if="!result.chunks?.length" :description="t('knowledgeBase.chunkEmpty')" />
              <template v-else-if="!isQaMode">
                <div v-for="item in result.chunks" :key="item.index" class="chunk-item">
                  <div class="chunk-head">
                    <NTag size="small" :bordered="false">#{{ (item.index ?? 0) + 1 }}</NTag>
                    <NText depth="3" style="font-size: 12px">
                      {{ item.charCount }} {{ t('knowledgeBase.wordCountShort') }} · {{ item.tokenCount }} tokens
                    </NText>
                    <NTag v-if="item.oversize" size="small" type="error">{{ t('knowledgeBase.oversize') }}</NTag>
                    <NTag v-if="item.tiny" size="small" type="warning">{{ t('knowledgeBase.tiny') }}</NTag>
                    <NButton
                      v-if="(item.content ?? '').length > PREVIEW_LEN"
                      text
                      size="tiny"
                      @click="expanded[item.index ?? 0] = !expanded[item.index ?? 0]"
                    >
                      {{ expanded[item.index ?? 0] ? t('common.collapse') : t('common.expand') }}
                    </NButton>
                    <NButton
                      v-if="item.children && item.children.length"
                      text
                      size="tiny"
                      @click="childrenExpanded[item.index ?? 0] = !childrenExpanded[item.index ?? 0]"
                    >
                      {{ t('knowledgeBase.childChunks') }} ({{ item.children.length }})
                    </NButton>
                  </div>
                  <pre class="chunk-content">{{ displayContent(item.content, item.index ?? 0) }}</pre>
                  <div v-if="item.children && item.children.length && childrenExpanded[item.index ?? 0]" class="children-box">
                    <div v-for="child in item.children" :key="child.index" class="child-item">
                      <NText depth="3" style="font-size: 12px">
                        #{{ (child.index ?? 0) + 1 }} · {{ child.charCount }} {{ t('knowledgeBase.wordCountShort') }}
                      </NText>
                      <pre class="chunk-content">{{ displayContent(child.content, 100000 + (child.index ?? 0)) }}</pre>
                    </div>
                  </div>
                </div>
                <div v-if="(result.totalChunks ?? 0) > pageSize" class="pager">
                  <NPagination
                    :page="page"
                    :page-size="pageSize"
                    :item-count="result.totalChunks ?? 0"
                    :page-sizes="[10, 20, 50, 100]"
                    show-size-picker
                    @update:page="onPageChange"
                    @update:page-size="onPageSizeChange"
                  />
                </div>
              </template>
            </NCard>
          </template>
          <NEmpty v-else :description="t('common.noData')" />
        </NSpin>
      </NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.doc-content-preview :deep(.n-tab-pane) {
  padding-top: 8px;
}

.raw-box {
  max-height: 62vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f7f9;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}

.diff-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.diff-col {
  flex: 1;
  min-width: 260px;
}

.sample-box {
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f7f9;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.7;
  margin: 4px 0 0;
}

.chunk-item {
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
}

.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chunk-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.children-box {
  margin-top: 6px;
  padding-left: 10px;
  border-left: 2px solid #eee;
}

.child-item {
  margin-bottom: 6px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.bad {
  color: #d03050;
}

.warn {
  color: #f0a020;
}
</style>
