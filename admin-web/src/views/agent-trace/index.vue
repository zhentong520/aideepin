<template>
  <n-card :bordered="false" class="proCard">
    <div class="flex items-center gap-3 mb-4">
      <n-input
        v-model:value="traceId"
        :placeholder="t('agentTrace.traceIdPlaceholder')"
        clearable
        style="max-width: 420px"
        @keyup.enter="loadTrace"
      />
      <n-button type="primary" :loading="loading" @click="loadTrace">
        {{ t('agentTrace.search') }}
      </n-button>
      <n-button quaternary @click="loadRecent">
        {{ t('agentTrace.recent') }}
      </n-button>
    </div>

    <!-- 时间线：单次对话调用全链路（入口→检索→工具→LLM→回复） -->
    <template v-if="spans.length">
      <n-alert type="info" class="mb-4" :show-icon="false">
        {{ t('agentTrace.totalSpans') }}: {{ spans.length }} · traceId: {{ currentTraceId }}
      </n-alert>
      <n-timeline>
        <n-timeline-item
          v-for="span in spans"
          :key="span.spanId"
          :type="span.status === 'ERROR' ? 'error' : spanTypeColor(span.spanType)"
          :title="span.name"
          :content="spanSummary(span)"
        />
      </n-timeline>
    </template>

    <n-empty v-else-if="searched" :description="t('agentTrace.empty')" class="my-16" />

    <!-- 最近的 trace 列表 -->
    <template v-if="recentRows.length">
      <n-divider title-placement="left">{{ t('agentTrace.recent') }}</n-divider>
      <n-data-table
        :columns="recentColumns"
        :data="recentRows"
        :row-key="(row: any) => row.traceId"
        :bordered="false"
        size="small"
        :row-props="rowProps"
      />
      <div class="mt-2 text-gray-400 text-xs">{{ t('agentTrace.recentTip') }}</div>
    </template>
  </n-card>
</template>

<script lang="ts" setup>
  import { h, onMounted, ref } from 'vue'
  import { NButton, NText } from 'naive-ui'
  import agentTraceApi from '@/api/agentTrace'
  import { t, useI18n } from '@/locales'

  interface TraceSpan {
    traceId: string
    spanId: string
    parentSpanId: string
    name: string
    spanType: string
    status: string
    startTime: string
    endTime?: string
    durationMs: number
    detail?: Record<string, any>
  }

  const { t: $t } = useI18n()
  const traceId = ref('')
  const currentTraceId = ref('')
  const loading = ref(false)
  const searched = ref(false)
  const spans = ref<TraceSpan[]>([])
  const recentRows = ref<TraceSpan[]>([])

  const recentColumns = [
    { title: 'traceId', key: 'traceId', width: 280 },
    { title: $t('agentTrace.spanName'), key: 'name', width: 160 },
    { title: $t('agentTrace.startTime'), key: 'startTime', width: 180 },
    {
      title: $t('agentTrace.duration'),
      key: 'durationMs',
      width: 100,
      render: (row: TraceSpan) => `${row.durationMs} ms`,
    },
    {
      title: '',
      key: 'action',
      width: 80,
      render: (row: TraceSpan) =>
        h(
          NButton,
          { size: 'tiny', onClick: () => openTrace(row.traceId) },
          { default: () => $t('agentTrace.view') }
        ),
    },
  ]

  function rowProps(row: TraceSpan) {
    return {
      style: 'cursor: pointer',
      onClick: () => openTrace(row.traceId),
    }
  }

  function spanTypeColor(type: string): 'info' | 'success' | 'warning' | 'default' {
    switch (type) {
      case 'entry':
        return 'info'
      case 'llm':
        return 'success'
      case 'tool':
      case 'retrieval':
        return 'warning'
      default:
        return 'default'
    }
  }

  function spanSummary(span: TraceSpan): string {
    const parts = [`${span.startTime} · ${span.durationMs} ms`]
    const detail = span.detail || {}
    if (detail.modelName) {
      parts.push(String(detail.modelName))
    }
    if (detail.inputTokens != null) {
      parts.push(`in ${detail.inputTokens} / out ${detail.outputTokens} tokens`)
    }
    if (detail.retrievalCount != null) {
      parts.push(`${$t('agentTrace.retrievalCount')}: ${detail.retrievalCount}`)
    }
    return parts.join(' · ')
  }

  async function openTrace(id: string) {
    currentTraceId.value = id
    traceId.value = id
    await loadTrace()
  }

  async function loadTrace() {
    if (!traceId.value.trim()) {
      return
    }
    loading.value = true
    try {
      const r = await agentTraceApi.spans(traceId.value.trim())
      spans.value = r.data || []
      searched.value = true
    } finally {
      loading.value = false
    }
  }

  async function loadRecent() {
    const r = await agentTraceApi.recent(20)
    recentRows.value = r.data || []
  }

  onMounted(loadRecent)
</script>

<style lang="less" scoped></style>
