<template>
  <n-card :bordered="false" class="proCard">
    <div class="flex items-center gap-3 mb-4">
      <n-date-picker
        v-model:value="timeRange"
        type="datetimerange"
        clearable
        :default-value="defaultRange"
      />
      <n-button type="primary" :loading="loading" @click="load">
        {{ t('ragQuality.query') }}
      </n-button>
    </div>

    <!-- ⚠️ 数据源故障：显示「数据不可用」，绝不显示 0（0 会被误读为检索质量完美） -->
    <n-alert v-if="resp && resp.status === 'UNAVAILABLE'" type="error" class="mb-4">
      {{ t('ragQuality.unavailable') }} — {{ resp.reason || t('ragQuality.unavailableTip') }}
    </n-alert>

    <!-- 查询正常但范围内无检索：空态，与「不可用」区分 -->
    <n-empty v-else-if="resp && resp.status === 'NO_DATA'" :description="t('ragQuality.noData')" class="my-16" />

    <template v-else-if="resp && resp.status === 'OK'">
      <!-- 五指标卡片 -->
      <n-grid :cols="5" :x-gap="12" class="mb-4">
        <n-gi v-for="card in metricCards" :key="card.label">
          <n-card size="small" :bordered="true">
            <div class="text-gray-400 text-xs mb-1">{{ card.label }}</div>
            <div class="text-xl font-semibold">{{ card.value }}</div>
            <div v-if="card.sub" class="text-gray-400 text-xs mt-1">{{ card.sub }}</div>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- 👍👎 反馈统计 + 检索总量 -->
      <n-descriptions bordered :column="4" size="small" class="mb-4">
        <n-descriptions-item :label="t('ragQuality.totalQueries')">
          {{ resp.summary.totalQueries }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('ragQuality.feedbackPass')">
          👍 {{ resp.feedback.passCount }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('ragQuality.feedbackFail')">
          👎 {{ resp.feedback.failCount }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('ragQuality.lowScoreThreshold')">
          {{ fmtNum(resp.summary.lowScoreThreshold, 2) }}
        </n-descriptions-item>
      </n-descriptions>

      <!-- 按知识库下钻 -->
      <n-card :title="t('ragQuality.byKb')" size="small" class="mb-4">
        <n-data-table
          :columns="byKbColumns"
          :data="resp.byKb"
          :row-key="(row: KbQualityItem) => row.kbUuid"
          :bordered="false"
          size="small"
        />
      </n-card>

      <!-- 明细下钻 + CSV 导出 + 一键转评测题 -->
      <n-card :title="t('ragQuality.detailTitle')" size="small">
        <div class="flex items-center gap-3 mb-3 flex-wrap">
          <n-select
            v-model:value="detailScope"
            :options="scopeOptions"
            style="width: 180px"
            @update:value="reloadDetail"
          />
          <n-input
            v-model:value="detailKbUuid"
            :placeholder="t('ragQuality.kbUuidPlaceholder')"
            style="width: 240px"
            clearable
            @clear="reloadDetail"
          />
          <n-button @click="reloadDetail">{{ t('ragQuality.query') }}</n-button>
          <n-button :loading="exporting" @click="exportCsv">{{ t('ragQuality.exportCsv') }}</n-button>
          <n-button type="warning" :loading="converting" @click="showToEvalModal = true">
            {{ t('ragQuality.toEval') }}
          </n-button>
        </div>
        <n-data-table
          remote
          :columns="detailColumns"
          :data="detailRows"
          :loading="detailLoading"
          :pagination="detailPagination"
          :row-key="(row: RagQualityDetailItem) => row.queryId"
          :bordered="false"
          size="small"
          :scroll-x="1100"
        />
      </n-card>
    </template>

    <!-- 一键转评测题确认弹窗 -->
    <n-modal
      v-model:show="showToEvalModal"
      preset="dialog"
      type="warning"
      :title="t('ragQuality.toEval')"
      :positive-text="t('ragQuality.toEvalConfirm')"
      :negative-text="t('common.cancel')"
      :loading="converting"
      @positive-click="convertToEval"
    >
      <div class="text-sm">{{ t('ragQuality.toEvalTip') }}</div>
      <div class="text-xs text-gray-400 mt-2">
        {{ t('ragQuality.toEvalScope') }}: {{ scopeLabel(detailScope) }}
        <template v-if="detailKbUuid">｜kb: {{ detailKbUuid }}</template>
      </div>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref } from 'vue'
  import { useMessage } from 'naive-ui'
  import {
    KbQualityItem,
    RagQualityDashboard,
    RagQualityDetailItem,
    RagQualityDetailReq,
    fmtNum,
    fmtPct,
    getByKbColumns,
    getDetailColumns,
  } from './dashboard'
  import ragQualityApi from '@/api/ragQuality'
  import { t } from '@/locales'

  const message = useMessage()
  const loading = ref(false)
  const resp = ref<RagQualityDashboard | null>(null)

  // 默认最近 7 天
  const now = Date.now()
  const defaultRange = [now - 7 * 24 * 3600 * 1000, now] as [number, number]
  const timeRange = ref<[number, number] | null>(defaultRange)

  const byKbColumns = getByKbColumns()
  const detailColumns = getDetailColumns()

  // ---------- 明细下钻 ----------
  const detailScope = ref('all')
  const detailKbUuid = ref('')
  const detailRows = ref<RagQualityDetailItem[]>([])
  const detailLoading = ref(false)
  const detailPagination = ref({ page: 1, pageSize: 20, itemCount: 0, showSizePicker: true, pageSizes: [20, 50, 100], onChange: (page: number) => { detailPagination.value.page = page; loadDetail() }, onUpdatePageSize: (size: number) => { detailPagination.value.pageSize = size; detailPagination.value.page = 1; loadDetail() } })

  const scopeOptions = computed(() => [
    { label: t('ragQuality.scopeAll'), value: 'all' },
    { label: t('ragQuality.scopeMissed'), value: 'missed' },
    { label: t('ragQuality.scopeLowScore'), value: 'low_score' },
    { label: t('ragQuality.scopeFailed'), value: 'failed' },
  ])

  function scopeLabel(scope: string) {
    return scopeOptions.value.find((o) => o.value === scope)?.label || scope
  }

  // ---------- 五指标卡片 ----------
  const metricCards = computed(() => {
    const s = resp.value?.summary
    if (!s) {
      return []
    }
    return [
      { label: t('ragQuality.hitRate'), value: fmtPct(s.hitRate), sub: `${s.hitQueries}/${s.totalQueries}` },
      { label: t('ragQuality.avgHitCount'), value: fmtNum(s.avgHitCount) },
      { label: t('ragQuality.avgTopScore'), value: fmtNum(s.avgTopScore, 3), sub: `P50 ${fmtNum(s.topScoreP50, 3)}` },
      { label: t('ragQuality.lowScoreShare'), value: fmtPct(s.lowScoreShare), sub: `${s.lowScoreQueries}/${s.totalQueries}` },
      { label: t('ragQuality.elapsedP95'), value: s.elapsedP95 == null ? '-' : Math.round(s.elapsedP95) + ' ms', sub: `avg ${s.avgElapsedMs == null ? '-' : Math.round(s.avgElapsedMs) + ' ms'}` },
    ]
  })

  function fmt(ts: number) {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }

  function detailReq(): RagQualityDetailReq {
    const [begin, end] = timeRange.value || defaultRange
    return {
      beginTime: fmt(begin),
      endTime: fmt(end),
      kbUuid: detailKbUuid.value || undefined,
      scope: detailScope.value,
    }
  }

  async function load() {
    if (!timeRange.value) {
      return
    }
    loading.value = true
    try {
      const r = await ragQualityApi.qualityDashboard({ beginTime: fmt(timeRange.value[0]), endTime: fmt(timeRange.value[1]) })
      resp.value = r.data
      await loadDetail()
    } finally {
      loading.value = false
    }
  }

  function reloadDetail() {
    detailPagination.value.page = 1
    loadDetail()
  }

  async function loadDetail() {
    detailLoading.value = true
    try {
      const p = detailPagination.value
      const r = await ragQualityApi.qualityDetail(detailReq(), { current: p.page, size: p.pageSize })
      detailRows.value = r.data?.records || []
      p.itemCount = Number(r.data?.total || 0)
    } finally {
      detailLoading.value = false
    }
  }

  // ---------- CSV 导出 ----------
  const exporting = ref(false)
  async function exportCsv() {
    exporting.value = true
    try {
      const r = await ragQualityApi.qualityDetailExport(detailReq())
      const blob = new Blob([r.data], { type: 'text/csv;charset=utf-8' })
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `rag-quality-detail-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '')}.csv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } finally {
      exporting.value = false
    }
  }

  // ---------- 一键转评测题 ----------
  const showToEvalModal = ref(false)
  const converting = ref(false)
  async function convertToEval() {
    converting.value = true
    try {
      const r = await ragQualityApi.toEval({ ...detailReq(), limit: 200 })
      const { generatedCount, skippedCount } = r.data || {}
      message.success(t('ragQuality.toEvalDone').replace('{generated}', String(generatedCount ?? 0)).replace('{skipped}', String(skippedCount ?? 0)))
      showToEvalModal.value = false
    } catch (e) {
      // 错误提示由 axios 拦截器统一弹出（如 dataset-dir 未配置）
      showToEvalModal.value = false
    } finally {
      converting.value = false
    }
  }

  onMounted(load)
</script>

<style lang="less" scoped></style>
