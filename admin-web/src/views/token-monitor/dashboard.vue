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
        {{ t('tokenMonitor.query') }}
      </n-button>
    </div>

    <!-- ⚠️ 数据源故障：显示「数据不可用」，绝不显示 0（0 会被误读为没花钱） -->
    <n-alert v-if="resp && resp.status === 'UNAVAILABLE'" type="error" class="mb-4">
      {{ t('tokenMonitor.unavailable') }} — {{ resp.reason || t('tokenMonitor.unavailableTip') }}
    </n-alert>

    <!-- 查询正常但范围内无记录：空态，与「不可用」区分 -->
    <n-empty
      v-else-if="resp && resp.status === 'NO_DATA'"
      :description="t('tokenMonitor.noData')"
      class="my-16"
    />

    <template v-else-if="resp && resp.status === 'OK'">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <n-card :title="t('tokenMonitor.byModel')" size="small">
          <div ref="modelChartRef" style="height: 320px"></div>
        </n-card>
        <n-card :title="t('tokenMonitor.byChannel')" size="small">
          <div ref="channelChartRef" style="height: 320px"></div>
        </n-card>
      </div>

      <n-card :title="t('tokenMonitor.byModelTable')" size="small" class="mb-4">
        <n-data-table
          :columns="modelColumns"
          :data="resp.byModel"
          :row-key="(row: any) => row.groupKey"
          :bordered="false"
          size="small"
        />
        <div class="mt-2 text-gray-400 text-xs">{{ unpricedTip }}</div>
      </n-card>

      <n-card :title="t('tokenMonitor.byChannelTable')" size="small" class="mb-4">
        <n-data-table
          :columns="channelColumns"
          :data="resp.byChannel"
          :row-key="(row: any) => row.groupKey"
          :bordered="false"
          size="small"
        />
      </n-card>

      <n-descriptions bordered :column="4" size="small">
        <n-descriptions-item :label="t('tokenMonitor.callCount')">
          {{ resp.summary.callCount }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('columns.inputTokens')">
          {{ resp.summary.inputTokens }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('columns.outputTokens')">
          {{ resp.summary.outputTokens }}
        </n-descriptions-item>
        <n-descriptions-item :label="t('tokenMonitor.totalCost')">
          <template v-if="resp.summary.priced">
            {{ formatCost(resp.summary.totalCost) }} {{ resp.currency }}
          </template>
          <template v-else>{{ t('tokenMonitor.unpriced') }}</template>
        </n-descriptions-item>
      </n-descriptions>
    </template>
  </n-card>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref, Ref } from 'vue'
  import { useECharts } from '@/hooks/web/useECharts'
  import tokenMonitorApi from '@/api/tokenMonitor'
  import { t } from '@/locales'
  import { formatCost, getModelColumns, getChannelColumns, CostDashboard } from './dashboard'

  const loading = ref(false)
  const resp = ref<CostDashboard | null>(null)
  // 列内 useI18n 必须在 setup 上下文取
  const modelColumns = getModelColumns()
  const channelColumns = getChannelColumns()

  // 默认最近 7 天
  const now = Date.now()
  const defaultRange = [now - 7 * 24 * 3600 * 1000, now] as [number, number]
  const timeRange = ref<[number, number] | null>(defaultRange)

  const modelChartRef = ref<HTMLDivElement | null>(null)
  const channelChartRef = ref<HTMLDivElement | null>(null)
  const { setOptions: setModelOptions } = useECharts(modelChartRef as Ref<HTMLDivElement>)
  const { setOptions: setChannelOptions } = useECharts(channelChartRef as Ref<HTMLDivElement>)

  const unpricedTip = computed(() => t('tokenMonitor.unpricedTip'))

  function fmt(ts: number) {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return (
      d.getFullYear() +
      '-' +
      pad(d.getMonth() + 1) +
      '-' +
      pad(d.getDate()) +
      ' ' +
      pad(d.getHours()) +
      ':' +
      pad(d.getMinutes()) +
      ':' +
      pad(d.getSeconds())
    )
  }

  async function load() {
    if (!timeRange.value) {
      return
    }
    loading.value = true
    try {
      const r = await tokenMonitorApi.costDashboard(fmt(timeRange.value[0]), fmt(timeRange.value[1]))
      resp.value = r.data
      renderCharts()
    } finally {
      loading.value = false
    }
  }

  function renderCharts() {
    const data = resp.value
    if (!data || data.status !== 'OK') {
      return
    }
    setModelOptions({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          name: t('tokenMonitor.tokens'),
          type: 'pie',
          radius: ['40%', '65%'],
          data: data.byModel.map((m) => ({
            name: m.groupKey,
            value: (m.inputTokens || 0) + (m.outputTokens || 0),
          })),
        },
      ],
    })
    setChannelOptions({
      tooltip: { trigger: 'axis' },
      grid: { left: '1%', right: '2%', bottom: 0, containLabel: true },
      xAxis: { type: 'category', data: data.byChannel.map((c) => c.groupKey) },
      yAxis: { type: 'value' },
      series: [
        { name: t('columns.inputTokens'), type: 'bar', data: data.byChannel.map((c) => c.inputTokens) },
        { name: t('columns.outputTokens'), type: 'bar', data: data.byChannel.map((c) => c.outputTokens) },
      ],
    })
  }

  onMounted(load)
</script>

<style lang="less" scoped></style>
