<script setup lang='ts'>
import { NAlert, NButton, NCard, NCollapse, NCollapseItem, NDivider, NEmpty, NForm, NFormItem, NInput, NInputNumber, NPagination, NSelect, NSpace, NSpin, NSwitch, NTag, NText } from 'naive-ui'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { t } from '@/locales'
import api from '@/api'

/**
 * 分块预览：把「解析 → 清洗 → 切分」三步的中间产物摊开。
 * 纯只读干跑：改参数立刻重跑，不写库、不向量化，满意后再去改知识库正式参数重建索引。
 */
const props = defineProps<{
  docUuid: string
  kbConfig: {
    strategy: string
    maxSegmentSize: number
    overlap: number
    separator: string
    childMaxChunkSize?: number
  }
  segmentMode?: string
}>()

const ms = useMessage()
const loading = ref(false)
const result = ref<KnowledgeBase.ChunkPreview | null>(null)
const page = ref(1)
const pageSize = ref(20)
// 展开的长文本块：默认折叠，避免一屏塞进整篇正文
const expanded = ref<Record<number, boolean>>({})
// 父子模式下展开子块的父块
const childrenExpanded = ref<Record<number, boolean>>({})

const form = reactive({
  strategy: '',
  maxSegmentSize: 512,
  overlap: 0,
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

function resetFromKb() {
  form.strategy = props.kbConfig.strategy || 'recursive'
  form.maxSegmentSize = props.kbConfig.maxSegmentSize || 512
  form.overlap = props.kbConfig.overlap || 0
  form.separator = props.kbConfig.separator || ''
  form.childMaxChunkSize = props.kbConfig.childMaxChunkSize
}

async function run() {
  if (!props.docUuid) {
    ms.warning(t('knowledgeBase.selectKnowledgeFirst'))
    return
  }
  loading.value = true
  try {
    const resp = await api.documentSegmentPreview<KnowledgeBase.ChunkPreview>(props.docUuid, {
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
    result.value = resp.data
    expanded.value = {}
    childrenExpanded.value = {}
  } catch (error: any) {
    ms.error(error.message ?? 'error')
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  run()
}

// 块内容默认只显示前 300 字，点一下看全文
const PREVIEW_LEN = 300
function displayContent(content: string, index: number) {
  if (expanded.value[index] || content.length <= PREVIEW_LEN)
    return content
  return `${content.slice(0, PREVIEW_LEN)}…`
}

const isParentChild = computed(() => props.segmentMode === 'parent_child')

watch(() => props.docUuid, () => {
  page.value = 1
  resetFromKb()
  run()
})

onMounted(() => {
  resetFromKb()
  run()
})

defineExpose({ run })
</script>

<template>
  <div class="chunk-preview">
    <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
      {{ t('knowledgeBase.chunkPreviewTip') }}
    </NAlert>

    <NCard size="small" :title="t('knowledgeBase.previewParams')">
      <NForm label-placement="left" label-width="120" size="small">
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
        <NButton size="small" type="primary" :loading="loading" @click="run">
          {{ t('knowledgeBase.rerunPreview') }}
        </NButton>
        <NButton size="small" quaternary @click="resetFromKb(); page = 1; run()">
          {{ t('knowledgeBase.resetParams') }}
        </NButton>
      </NSpace>
    </NCard>

    <NSpin :show="loading">
      <div v-if="result">
        <!-- 清洗环节：解析原文 → 清洗后正文 -->
        <NCard size="small" :title="t('knowledgeBase.previewCleanStats')" style="margin-top: 12px">
          <NSpace :wrap="true" :size="16">
            <span>{{ t('knowledgeBase.rawChars') }}: <b>{{ result.clean.rawChars }}</b></span>
            <span>{{ t('knowledgeBase.cleanedChars') }}: <b>{{ result.clean.cleanedChars }}</b></span>
            <span>{{ t('knowledgeBase.removedChars') }}: <b>{{ result.clean.removedChars }}</b></span>
            <span>{{ t('knowledgeBase.rawLines') }}: <b>{{ result.clean.rawLines }}</b></span>
            <span>{{ t('knowledgeBase.cleanedLines') }}: <b>{{ result.clean.cleanedLines }}</b></span>
            <span>{{ t('knowledgeBase.mergedLines') }}: <b>{{ result.clean.mergedLines }}</b></span>
            <span>{{ t('knowledgeBase.droppedLines') }}: <b>{{ result.clean.droppedLines }}</b></span>
          </NSpace>
          <NDivider />
          <NCollapse>
            <NCollapseItem :title="t('knowledgeBase.previewTextDiff')" name="diff">
              <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                <div style="flex: 1; min-width: 260px;">
                  <NText depth="3" style="font-size: 12px;">
                    {{ t('knowledgeBase.previewRawSample') }}
                  </NText>
                  <pre class="sample-box">{{ result.rawSample }}</pre>
                </div>
                <div style="flex: 1; min-width: 260px;">
                  <NText depth="3" style="font-size: 12px;">
                    {{ t('knowledgeBase.previewCleanedSample') }}
                  </NText>
                  <pre class="sample-box">{{ result.cleanedSample }}</pre>
                </div>
              </div>
            </NCollapseItem>
          </NCollapse>
        </NCard>

        <NAlert v-if="result.truncated" type="warning" :bordered="false" style="margin-top: 12px">
          {{ t('knowledgeBase.previewTruncated') }}
        </NAlert>
        <NAlert v-if="result.warning" type="warning" :bordered="false" style="margin-top: 12px">
          {{ result.warning }}
        </NAlert>

        <!-- 切分统计 -->
        <NCard size="small" :title="t('knowledgeBase.previewSplitStats')" style="margin-top: 12px">
          <NSpace :wrap="true" :size="16">
            <span>{{ t('knowledgeBase.chunkCount') }}: <b>{{ result.stats.chunkCount }}</b></span>
            <span>{{ t('knowledgeBase.avgChars') }}: <b>{{ result.stats.avgChars }}</b></span>
            <span>{{ t('knowledgeBase.maxChars') }}: <b>{{ result.stats.maxChars }}</b></span>
            <span>{{ t('knowledgeBase.avgTokens') }}: <b>{{ result.stats.avgTokens }}</b></span>
            <span>{{ t('knowledgeBase.maxTokens') }}: <b>{{ result.stats.maxTokens }}</b></span>
            <span>
              {{ t('knowledgeBase.oversizeChunks') }}:
              <b :style="{ color: (result.stats.oversizeCount ?? 0) > 0 ? '#d03050' : undefined }">{{ result.stats.oversizeCount }}</b>
            </span>
            <span>
              {{ t('knowledgeBase.tinyChunks') }}:
              <b :style="{ color: (result.stats.tinyCount ?? 0) > 0 ? '#f0a020' : undefined }">{{ result.stats.tinyCount }}</b>
            </span>
            <span v-if="result.childChunkCount">{{ t('knowledgeBase.childChunkTotal') }}: <b>{{ result.childChunkCount }}</b></span>
          </NSpace>
          <NText depth="3" style="font-size: 12px;">
            {{ t('knowledgeBase.previewEffectiveParams') }}: {{ result.params.strategy }} / size {{ result.params.maxSegmentSize }} / overlap {{ result.params.overlap }}
          </NText>
        </NCard>

        <!-- 块列表 -->
        <NCard size="small" :title="`${t('knowledgeBase.chunkList')} (${result.totalChunks})`" style="margin-top: 12px">
          <NEmpty v-if="!result.chunks.length" :description="t('knowledgeBase.chunkEmpty')" />
          <div v-for="item in result.chunks" :key="item.index" class="chunk-item">
            <div class="chunk-head">
              <NTag size="small" :bordered="false">
                #{{ (item.index ?? 0) + 1 }}
              </NTag>
              <NText depth="3" style="font-size: 12px;">
                {{ item.charCount }} {{ t('knowledgeBase.wordCountShort') }} · {{ item.tokenCount }} tokens
              </NText>
              <NTag v-if="item.oversize" size="small" type="error">
                {{ t('knowledgeBase.oversize') }}
              </NTag>
              <NTag v-if="item.tiny" size="small" type="warning">
                {{ t('knowledgeBase.tiny') }}
              </NTag>
              <NButton
                v-if="item.content.length > PREVIEW_LEN" text size="tiny"
                @click="expanded[item.index ?? 0] = !expanded[item.index ?? 0]"
              >
                {{ expanded[item.index ?? 0] ? t('common.collapse') : t('common.expand') }}
              </NButton>
              <NButton
                v-if="item.children && item.children.length" text size="tiny"
                @click="childrenExpanded[item.index ?? 0] = !childrenExpanded[item.index ?? 0]"
              >
                {{ t('knowledgeBase.childChunks') }} ({{ item.children.length }})
              </NButton>
            </div>
            <pre class="chunk-content">{{ displayContent(item.content, item.index ?? 0) }}</pre>
            <div v-if="item.children && item.children.length && childrenExpanded[item.index ?? 0]" class="children-box">
              <div v-for="child in item.children" :key="child.index" class="child-item">
                <NText depth="3" style="font-size: 12px;">
                  #{{ (child.index ?? 0) + 1 }} · {{ child.charCount }} {{ t('knowledgeBase.wordCountShort') }}
                </NText>
                <pre class="chunk-content">{{ displayContent(child.content, 100000 + (child.index ?? 0)) }}</pre>
              </div>
            </div>
          </div>
          <div v-if="result.totalChunks > pageSize" style="margin-top: 12px; display: flex; justify-content: flex-end;">
            <NPagination
              :page="page" :page-size="pageSize" :item-count="result.totalChunks"
              :page-sizes="[10, 20, 50, 100]" show-size-picker
              @update:page="onPageChange"
              @update:page-size="(size: number) => { pageSize = size; page = 1; run() }"
            />
          </div>
        </NCard>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.sample-box {
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f6f7f9;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
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
</style>
