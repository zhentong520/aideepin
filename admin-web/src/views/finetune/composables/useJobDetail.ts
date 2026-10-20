import { computed, onUnmounted, ref } from 'vue'
import type { Composer } from 'vue-i18n'
import { jobDetail, jobLog } from '@/api/finetune'

/** 任务详情行（详情弹窗消费的宽泛结构，字段以 jobDetail 返回为准） */
/* eslint-disable @typescript-eslint/no-explicit-any */
export interface JobDetailRow {
  uuid: string
  name: string
  status: string
  baseModelName: string
  method: string
  preset: string
  workerId: string
  createTime: string
  progress: number
  currentEpoch: number
  trainLoss: string
  evalLoss: string
  failReason: string
  hyperParams: Record<string, any>
  metrics: Record<string, any>
  [key: string]: any
}

/**
 * 任务详情弹窗：详情/日志拉取 + 5s 轮询，以及训练报告的派生展示。
 */
export function useJobDetail(ctx: { t: Composer['t'] }) {
  const { t } = ctx

  const detailVisible = ref(false);
  const detailRow = ref<JobDetailRow>({} as JobDetailRow);
  const logText = ref('');
  const logLoading = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;

  const metrics = computed<Record<string, any>>(() => detailRow.value.metrics || {});
  const hasMetrics = computed(() => Object.keys(metrics.value).length > 0);
  const runtimeText = computed(() => {
    const s = Number(metrics.value.train_runtime || 0);
    if (!s) return '-';
    return s >= 3600 ? `${(s / 3600).toFixed(2)} h` : `${Math.round(s / 60)} min`;
  });
  const paramsText = computed(() => {
    const p = Number(metrics.value.trainable_params || 0);
    const all = Number(metrics.value.total_params || 0);
    if (!p) return '-';
    const pct = all ? ((p / all) * 100).toFixed(3) + '%' : '';
    return `${p.toLocaleString()} / ${all.toLocaleString()} (${pct})`;
  });

  function fmt(v: any) {
    return v === null || v === undefined || v === '' ? '-' : v;
  }

  function presetLabel(key: string) {
    return key ? t(`finetune.preset.${key}.name`) : '-';
  }

  async function openDetail(row: JobDetailRow) {
    detailRow.value = row;
    detailVisible.value = true;
    await refreshDetail();
    if (timer) clearInterval(timer);
    timer = setInterval(refreshDetail, 5000);
  }

  async function refreshDetail() {
    if (!detailRow.value.uuid) return;
    logLoading.value = true;
    try {
      const job = await jobDetail(detailRow.value.uuid);
      detailRow.value = (job && job.data) || {};
      const log = await jobLog(detailRow.value.uuid, 200);
      logText.value = ((log.data && log.data.lines) || []).join('\n');
    } finally {
      logLoading.value = false;
    }
  }

  onUnmounted(() => {
    if (timer) clearInterval(timer);
  });

  return {
    detailVisible,
    detailRow,
    logText,
    logLoading,
    metrics,
    hasMetrics,
    runtimeText,
    paramsText,
    fmt,
    presetLabel,
    openDetail,
    refreshDetail,
  };
}
/* eslint-enable @typescript-eslint/no-explicit-any */
