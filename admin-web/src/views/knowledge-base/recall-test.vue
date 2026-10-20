<template>
  <n-card :bordered="false" class="proCard recall-test">
    <!-- 查询区 -->
    <n-space vertical :size="12">
      <!-- 没用 n-space：它会把子项包进 .n-space-item（flex:0 0 auto），
           子元素的 flex:1 落不到包裹层上，会让 textarea 宽度塌成 0 -->
      <div class="recall-query-row">
        <n-select
          v-model:value="kbUuid"
          :options="kbOptions"
          :loading="kbLoading"
          filterable
          :placeholder="t('kbRecall.kbPlaceholder')"
          style="width: 320px; flex: 0 0 auto"
          @update:value="onKbChange"
        />
        <n-input
          v-model:value="query"
          class="recall-query-input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          :placeholder="t('kbRecall.queryPlaceholder')"
          @keydown.enter.exact.prevent="run"
        />
        <n-button
          type="primary"
          :loading="running"
          :disabled="!kbUuid || !query.trim()"
          style="flex: 0 0 auto"
          @click="run"
        >
          {{ t('kbRecall.run') }}
        </n-button>
      </div>

      <!-- 参数区：留空即沿用知识库配置 -->
      <n-collapse :default-expanded-names="['params']">
        <n-collapse-item :title="t('kbRecall.paramsTitle')" name="params">
          <template #header-extra>
            <n-text depth="3" style="font-size: 12px">{{ t('kbRecall.paramsHint') }}</n-text>
          </template>
          <n-grid :cols="4" :x-gap="12" :y-gap="8" responsive="screen" item-responsive>
            <n-form-item-gi :label="t('kbRecall.field.topK')" :span="1">
              <n-input-number v-model:value="form.topK" :min="0" :placeholder="kbDefault('retrieveMaxResults')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.minScore')" :span="1">
              <n-input-number v-model:value="form.minScore" :min="0" :max="1" :step="0.05" :placeholder="kbDefault('retrieveMinScore')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :span="1">
              <template #label>
                <n-space align="center" :size="4">
                  <span>{{ t('kbRecall.field.hybridEnabled') }}</span>
                  <n-tooltip trigger="hover" style="max-width: 360px">
                    <template #trigger><n-icon size="14" :depth="3"><HelpCircleOutline /></n-icon></template>
                    {{ t('kbRecall.tip.hybrid') }}
                  </n-tooltip>
                </n-space>
              </template>
              <n-switch v-model:value="form.hybridEnabled" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.keywordLimit')" :span="1">
              <n-input-number v-model:value="form.keywordLimit" :min="1" :placeholder="kbDefault('retrieveKeywordLimit')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.rrfK')" :span="1">
              <n-input-number v-model:value="form.rrfK" :min="1" :placeholder="kbDefault('retrieveRrfK')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.rerankEnabled')" :span="1">
              <n-switch v-model:value="form.rerankEnabled" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.rerankTopN')" :span="1">
              <n-input-number v-model:value="form.rerankTopN" :min="0" :placeholder="kbDefault('retrieveRerankTopN')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.rerankMinScore')" :span="1">
              <n-input-number v-model:value="form.rerankMinScore" :min="0" :max="1" :step="0.05" :placeholder="kbDefault('retrieveRerankMinScore')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.neighborEnabled')" :span="1">
              <n-switch v-model:value="form.neighborEnabled" />
            </n-form-item-gi>
            <n-form-item-gi :label="t('kbRecall.field.neighborWindow')" :span="1">
              <n-input-number v-model:value="form.neighborWindow" :min="0" :placeholder="kbDefault('retrieveNeighborWindow')" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi :span="2">
              <n-space>
                <n-button size="small" @click="resetOverrides">{{ t('kbRecall.resetParams') }}</n-button>
                <n-text depth="3" style="font-size: 12px">
                  {{ t('kbRecall.overrode', { fields: overrodeLabel }) }}
                </n-text>
              </n-space>
            </n-form-item-gi>
          </n-grid>
        </n-collapse-item>
      </n-collapse>
    </n-space>

    <!-- 结果区 -->
    <n-divider v-if="result" />

    <template v-if="result">
      <!-- 漏斗 -->
      <n-grid :cols="8" :x-gap="8" :y-gap="8" responsive="screen" item-responsive>
        <n-gi v-for="card in funnelCards" :key="card.label" :span="1">
          <n-card size="small" embedded>
            <n-statistic :label="card.label" :value="card.value" />
            <n-text v-if="card.sub" depth="3" style="font-size: 12px">{{ card.sub }}</n-text>
          </n-card>
        </n-gi>
      </n-grid>

      <n-space vertical :size="8" style="margin-top: 12px">
        <n-alert v-if="!result.hits?.length" type="warning" :bordered="false">
          {{ t('kbRecall.empty') }}
        </n-alert>
        <n-alert v-for="(note, i) in result.notes" :key="i" type="info" :bordered="false" style="font-size: 12px">
          {{ note }}
        </n-alert>
        <n-alert v-if="result.stage?.rerankExecuted" type="success" :bordered="false" style="font-size: 12px">
          {{ t('kbRecall.rerankRan', { model: result.params?.rerankModelName || '-', input: result.stage?.rerankInput, output: result.stage?.rerankReturned }) }}
        </n-alert>
      </n-space>

      <n-data-table
        :columns="columns"
        :data="result.hits"
        :row-key="(row: any) => row.rank"
        :bordered="false"
        size="small"
        :scroll-x="1100"
        style="margin-top: 12px"
      />
    </template>

    <UxState v-else state="empty" :text="t('kbRecall.idle')" class="!py-15" />
  </n-card>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref, watch } from 'vue'
  import {
    NAlert,
    NButton,
    NCard,
    NCollapse,
    NCollapseItem,
    NDataTable,
    NDivider,
    NFormItemGi,
    NGi,
    NGrid,
    NIcon,
    NInput,
    NInputNumber,
    NSelect,
    NSpace,
    NStatistic,
    NSwitch,
    NTag,
    NText,
    NTooltip,
    useMessage,
  } from 'naive-ui'
  import { HelpCircleOutline } from '@vicons/ionicons5'
  import { useI18n } from 'vue-i18n'
  import { useRoute } from 'vue-router'
  import kbApi from '@/api/knowledgeBase'

  const { t } = useI18n()
  const route = useRoute()
  const message = useMessage()

  /** 知识库下拉项 */
  interface KbOption {
    title: string
    uuid: string
  }

  /** 命中行：字段以后端 recallTest 返回为准，数值型均可能为 null */
  interface RecallHit {
    source: string
    kind?: string
    storeScore: number | null
    cosine: number
    rerankScore: number | null
    docTitle?: string
    position: number | null
    poolRank?: number | null
    keywordRank?: number | null
    rrfScore?: number | null
    content: string
    truncated?: boolean
    [key: string]: unknown
  }

  /** 召回测试响应（stage/params 为后端宽泛结构，展示侧按可缺省处理） */
  interface RecallResult {
    stage?: Record<string, any>
    params?: Record<string, any>
    hits?: RecallHit[]
    costMs?: number
    [key: string]: any
  }

  const kbUuid = ref<string | null>(null)
  const kbOptions = ref<Array<{ label: string; value: string }>>([])
  const kbLoading = ref(false)
  const kbConfig = ref<Record<string, any>>({})

  const query = ref('')
  const running = ref(false)
  const result = ref<RecallResult | null>(null)

  // null = 沿用知识库配置；只把用户真正改过的字段发给后端
  const emptyForm = () => ({
    topK: null,
    minScore: null,
    hybridEnabled: null,
    keywordLimit: null,
    rrfK: null,
    rerankEnabled: null,
    rerankTopN: null,
    rerankMinScore: null,
    neighborEnabled: null,
    neighborWindow: null,
  })
  const form = reactive<Record<string, any>>(emptyForm())
  const overrodeFields = ref<string[]>([])

  const overrodeLabel = computed(() =>
    overrodeFields.value.length ? overrodeFields.value.join(', ') : t('kbRecall.noneOverrode')
  )

  watch(
    () => JSON.stringify(form),
    () => {
      overrodeFields.value = Object.keys(emptyForm()).filter((k) => form[k] !== null && form[k] !== undefined)
    },
    { immediate: true }
  )

  function kbDefault(field: string): string | undefined {
    const v = kbConfig.value?.[field]
    if (v === null || v === undefined || v === '') {
      return undefined
    }
    return t('kbRecall.fromKb', { value: String(v) })
  }

  function resetOverrides() {
    Object.assign(form, emptyForm())
  }

  const funnelCards = computed(() => {
    const s = result.value?.stage
    const p = result.value?.params
    if (!s || !p) {
      return []
    }
    return [
      { label: t('kbRecall.stage.pool'), value: s.poolReturned, sub: t('kbRecall.stage.poolSub', { requested: s.poolRequested }) },
      { label: t('kbRecall.stage.resolved'), value: s.poolResolved, sub: t('kbRecall.stage.resolvedSub') },
      { label: t('kbRecall.stage.aboveMinScore'), value: s.poolAboveMinScore, sub: t('kbRecall.stage.filteredSub', { n: s.filteredByMinScore }) },
      { label: t('kbRecall.stage.keyword'), value: s.keywordReturned, sub: p.hybridEnabled ? '' : t('kbRecall.stage.off') },
      { label: t('kbRecall.stage.fused'), value: s.fusedReturned, sub: p.hybridEnabled ? t('kbRecall.stage.rrf', { k: p.rrfK }) : t('kbRecall.stage.pureVector') },
      { label: t('kbRecall.stage.rerank'), value: s.rerankExecuted ? s.rerankReturned : '-', sub: s.rerankExecuted ? '' : t('kbRecall.stage.notRun') },
      { label: t('kbRecall.stage.final'), value: s.finalReturned, sub: '' },
      { label: t('kbRecall.stage.cost'), value: result.value?.costMs, sub: t('kbRecall.stage.costSub', { embed: s.embeddingMs, vector: s.vectorMs, keyword: s.keywordMs, rerank: s.rerankMs }) },
    ]
  })

  function scoreCell(hit: RecallHit) {
    if (hit.storeScore === null || hit.storeScore === undefined) {
      return h(NText, { depth: 3 }, { default: () => '-' })
    }
    return h(NSpace, { vertical: true, size: 0 }, {
      default: () => [
        h(NText, { strong: true }, { default: () => hit.storeScore!.toFixed(4) }),
        h(NText, { depth: 3, style: 'font-size: 12px' }, { default: () => t('kbRecall.cos', { v: hit.cosine.toFixed(4) }) }),
        hit.rerankScore !== null && hit.rerankScore !== undefined
          ? h(NText, { depth: 3, style: 'font-size: 12px' }, { default: () => t('kbRecall.rerankScore', { v: hit.rerankScore!.toFixed(4) }) })
          : null,
      ],
    })
  }

  const sourceColor: Record<string, string> = {
    vector: 'info',
    keyword: 'warning',
    'vector+keyword': 'success',
  }

  const columns = computed(() => [
    { title: t('kbRecall.col.rank'), key: 'rank', width: 60 },
    { title: t('kbRecall.col.score'), key: 'storeScore', width: 140, render: scoreCell },
    {
      title: t('kbRecall.col.source'),
      key: 'source',
      width: 130,
      render: (row: RecallHit) =>
        h(NSpace, { size: 4, vertical: true }, {
          default: () => [
            h(NTag, { size: 'small', type: (sourceColor[row.source] || 'default') as any, bordered: false }, { default: () => row.source }),
            h(NText, { depth: 3, style: 'font-size: 12px' }, { default: () => row.kind }),
          ],
        }),
    },
    {
      title: t('kbRecall.col.doc'),
      key: 'docTitle',
      width: 220,
      render: (row: RecallHit) =>
        h(NText, {}, {
          default: () =>
            `${row.docTitle || t('kbRecall.unknownDoc')}${row.position === null || row.position === undefined ? '' : t('kbRecall.position', { n: row.position + 1 })}`,
        }),
    },
    {
      title: t('kbRecall.col.rankDetail'),
      key: 'rankDetail',
      width: 150,
      render: (row: RecallHit) =>
        h(NText, { depth: 3, style: 'font-size: 12px' }, {
          default: () => [
            row.poolRank ? t('kbRecall.poolRank', { n: row.poolRank }) : null,
            row.keywordRank ? ` ${t('kbRecall.keywordRank', { n: row.keywordRank })}` : null,
            row.rrfScore !== null && row.rrfScore !== undefined ? ` RRF ${row.rrfScore.toFixed(4)}` : null,
          ].filter(Boolean).join(' · ') || '-',
        }),
    },
    {
      title: t('kbRecall.col.content'),
      key: 'content',
      render: (row: RecallHit) =>
        h('pre', { class: 'recall-content' }, row.content + (row.truncated ? '\n…' : '')),
    },
  ])

  async function loadKbs() {
    kbLoading.value = true
    try {
      const res: any = await kbApi.search({}, { current: 1, size: 100 })
      const records = res?.data?.records || res?.records || []
      kbOptions.value = records.map((item: KbOption) => ({ label: item.title, value: item.uuid }))
    } catch (e) {
      message.error(t('kbRecall.loadKbFailed'))
    } finally {
      kbLoading.value = false
    }
  }

  async function onKbChange(uuid: string) {
    result.value = null
    resetOverrides()
    try {
      const res: any = await kbApi.getInfo(uuid)
      kbConfig.value = (res?.data || {}) as Record<string, any>
    } catch (e) {
      kbConfig.value = {}
      message.error(t('kbRecall.loadKbFailed'))
    }
  }

  async function run() {
    if (!kbUuid.value || !query.value.trim()) {
      return
    }
    running.value = true
    try {
      const payload: any = { kbUuid: kbUuid.value, query: query.value.trim() }
      Object.keys(form).forEach((key) => {
        const value = (form as any)[key]
        if (value !== null && value !== undefined) {
          payload[key] = value
        }
      })
      const res: any = await kbApi.recallTest(payload)
      result.value = res?.data || null
      if (!result.value) {
        message.error(t('kbRecall.runFailed'))
      }
    } catch (e: unknown) {
      message.error((e instanceof Error ? e.message : '') || t('kbRecall.runFailed'))
    } finally {
      running.value = false
    }
  }

  onMounted(async () => {
    await loadKbs()
    const preselect = route.query.kbUuid as string | undefined
    if (preselect && kbOptions.value.some((item) => item.value === preselect)) {
      kbUuid.value = preselect
      await onKbChange(preselect)
    }
  })
</script>

<style lang="less" scoped>
  .recall-test {
    .recall-query-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .recall-query-input {
      flex: 1 1 auto;
      min-width: 0;
    }

    :deep(.recall-content) {
      margin: 0;
      max-height: 150px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 12px;
      line-height: 1.6;
    }
  }
</style>
