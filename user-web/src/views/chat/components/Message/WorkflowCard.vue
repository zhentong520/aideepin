<script setup lang="ts">
/**
 * M1 业务编排（2026-10-30）：对话触发的业务工作流卡片。
 *
 * 数据源：question 消息的 wfRuntimeUuid（后端 backfillMessageLink 兜底 + [META] 即时下发）。
 * 行为：
 * - 3s 轮询 /workflow/runtime/info/{uuid}，终态（SUCCESS/FAIL）后自动停止轮询；
 * - DOING → 显示「中止」按钮（调 /workflow/runtime/cancel）；
 * - WAITING_INPUT → 显示续跑输入框（调 /workflow/runtime/resume，复用工作流页同款接口）；
 * - SUCCESS → 折叠展示执行输出 JSON。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NInput, NSpin, useMessage } from 'naive-ui'
import api from '@/api'
import { t } from '@/locales'

const props = defineProps<Props>()

interface Props {
  // 逗号分隔多值（一次提问理论上可触发多个工作流），M1 只展示第一个
  runtimeUuids: string
}

const ms = useMessage()

// 对齐后端 WfConstant.WorkflowProcessStatus：1=READY 2=DOING 3=SUCCESS 4=FAIL 5=WAITING_INPUT
// M2-T1/T3（2026-11-11）：+6=CANCELLING 7=CANCELED 8=PAUSED
const STATUS_READY = 1
const STATUS_DOING = 2
const STATUS_SUCCESS = 3
const STATUS_FAIL = 4
const STATUS_WAITING_INPUT = 5
const STATUS_CANCELLING = 6
const STATUS_CANCELED = 7
const STATUS_PAUSED = 8

const runtimeUuid = computed(() => (props.runtimeUuids || '').split(',')[0]?.trim() || '')

const runtime = ref<Workflow.WfRuntimeInfo | null>(null)
const loading = ref(false)
const resuming = ref(false)
const cancelling = ref(false)
const pausing = ref(false)
const feedbackContent = ref('')

let pollTimer: ReturnType<typeof setInterval> | null = null

const isTerminal = computed(() => {
  const status = runtime.value?.status
  return status === STATUS_SUCCESS || status === STATUS_FAIL || status === STATUS_CANCELED
})

const statusText = computed(() => {
  switch (runtime.value?.status) {
    case STATUS_READY:
      return t('workflow.wfStatusReady')
    case STATUS_DOING:
      return t('workflow.wfStatusDoing')
    case STATUS_SUCCESS:
      return t('workflow.wfStatusSuccess')
    case STATUS_FAIL:
      return t('workflow.wfStatusFail')
    case STATUS_WAITING_INPUT:
      return t('workflow.wfStatusWaiting')
    case STATUS_CANCELLING:
      return t('workflow.wfStatusCancelling')
    case STATUS_CANCELED:
      return t('workflow.wfStatusCanceled')
    case STATUS_PAUSED:
      return t('workflow.wfStatusPaused')
    default:
      return ''
  }
})

const outputText = computed(() => {
  const output = runtime.value?.output
  if (!output || (typeof output === 'object' && Object.keys(output).length === 0))
    return ''
  return typeof output === 'string' ? output : JSON.stringify(output, null, 2)
})

async function refresh() {
  if (!runtimeUuid.value)
    return
  try {
    const { data } = await api.workflowRuntimeInfo<Workflow.WfRuntimeInfo>(runtimeUuid.value)
    runtime.value = data
    if (isTerminal.value)
      stopPolling()
  }
  catch (e) {
    // 详情拉取失败不打断聊天；下个周期会重试
    console.error('WorkflowCard refresh error', e)
  }
}

function startPolling() {
  stopPolling()
  if (!runtimeUuid.value)
    return
  refresh()
  pollTimer = setInterval(refresh, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleCancel() {
  if (!runtimeUuid.value || cancelling.value)
    return
  cancelling.value = true
  try {
    const resp = await api.workflowRuntimeCancel<boolean>(runtimeUuid.value)
    if (resp.success && resp.data)
      ms.success(t('workflow.wfCancelSuccess'))
    else
      ms.warning(t('workflow.wfCancelFailed'))
    await refresh()
  }
  catch (e) {
    ms.error(t('workflow.wfCancelFailed'))
  }
  finally {
    cancelling.value = false
  }
}

async function handleResume() {
  if (!runtimeUuid.value || !feedbackContent.value.trim() || resuming.value)
    return
  resuming.value = true
  try {
    await api.workflowRuntimeResume<boolean>({ runtimeUuid: runtimeUuid.value, feedbackContent: feedbackContent.value.trim() })
    feedbackContent.value = ''
    ms.success(t('workflow.wfResumeSuccess'))
    await refresh()
    startPolling()
  }
  catch (e) {
    console.error('WorkflowCard resume error', e)
  }
  finally {
    resuming.value = false
  }
}

/** M2-T3：从 PAUSED 态继续（无 HumanFeedback 输入，feedbackContent 传空） */
async function handleContinue() {
  if (!runtimeUuid.value || resuming.value)
    return
  resuming.value = true
  try {
    await api.workflowRuntimeResume<boolean>({ runtimeUuid: runtimeUuid.value, feedbackContent: '' })
    ms.success(t('workflow.wfResumeSuccess'))
    await refresh()
    startPolling()
  }
  catch (e) {
    console.error('WorkflowCard continue error', e)
  }
  finally {
    resuming.value = false
  }
}

/** M2-T3：DOING 态主动暂停（节点边界生效） */
async function handlePause() {
  if (!runtimeUuid.value || pausing.value)
    return
  pausing.value = true
  try {
    const resp = await api.workflowRuntimePause<boolean>(runtimeUuid.value)
    if (resp.success && resp.data)
      ms.success(t('workflow.wfPauseSuccess'))
    else
      ms.warning(t('workflow.wfPauseFailed'))
    await refresh()
  }
  catch (e) {
    ms.error(t('workflow.wfPauseFailed'))
  }
  finally {
    pausing.value = false
  }
}

onMounted(startPolling)
onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="wf-card">
    <div class="wf-card-header">
      <span class="wf-card-title">{{ t('workflow.wfCardTitle') }}</span>
      <NSpin v-if="loading && !runtime" :size="14" class="wf-card-spin" />
      <span v-else-if="runtime" class="wf-card-status" :class="`wf-card-status-${runtime.status}`">
        {{ statusText }}
      </span>
    </div>

    <div v-if="runtime">
      <div v-if="(runtime.status === STATUS_FAIL || runtime.status === STATUS_CANCELED) && runtime.statusRemark" class="wf-card-error">
        {{ runtime.statusRemark }}
      </div>

      <div v-if="runtime.status === STATUS_SUCCESS && outputText" class="wf-card-output">
        <div class="wf-card-output-title">{{ t('workflow.wfOutputTitle') }}</div>
        <pre class="wf-card-output-pre">{{ outputText }}</pre>
      </div>

      <div v-if="runtime.status === STATUS_DOING || runtime.status === STATUS_READY" class="wf-card-actions">
        <NButton size="tiny" :loading="pausing" @click="handlePause">
          {{ t('workflow.wfPause') }}
        </NButton>
        <NButton size="tiny" :loading="cancelling" @click="handleCancel">
          {{ t('workflow.wfCancel') }}
        </NButton>
      </div>

      <div v-if="runtime.status === STATUS_PAUSED" class="wf-card-actions">
        <NButton size="tiny" type="primary" :loading="resuming" @click="handleContinue">
          {{ t('workflow.wfContinue') }}
        </NButton>
        <NButton size="tiny" :loading="cancelling" @click="handleCancel">
          {{ t('workflow.wfCancel') }}
        </NButton>
      </div>

      <div v-if="runtime.status === STATUS_WAITING_INPUT" class="wf-card-resume">
        <NInput
          v-model:value="feedbackContent" type="textarea" size="small"
          :placeholder="t('workflow.wfResumePlaceholder')" :autosize="{ minRows: 1, maxRows: 4 }"
        />
        <NButton size="tiny" type="primary" :disabled="!feedbackContent.trim()" :loading="resuming" @click="handleResume">
          {{ t('workflow.wfResumeSubmit') }}
        </NButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-card {
  margin: 6px 0;
  padding: 10px 12px;
  border: 1px solid rgba(128, 128, 128, 0.25);
  border-radius: 8px;
  font-size: 13px;
  max-width: 640px;
}

.wf-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wf-card-title {
  font-weight: 600;
}

.wf-card-spin {
  display: inline-flex;
}

.wf-card-status {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.15);
}

.wf-card-status-2 {
  background: rgba(45, 140, 240, 0.15);
  color: #2d8cf0;
}

.wf-card-status-3 {
  background: rgba(24, 160, 88, 0.15);
  color: #18a058;
}

.wf-card-status-4 {
  background: rgba(208, 48, 79, 0.15);
  color: #d0304f;
}

.wf-card-status-5 {
  background: rgba(240, 160, 32, 0.18);
  color: #f0a020;
}

/* M2-T1：6=CANCELLING 过渡态（灰橙）7=CANCELED 中性灰 8=PAUSED 橙 */
.wf-card-status-6 {
  background: rgba(240, 160, 32, 0.12);
  color: rgba(240, 160, 32, 0.8);
}

.wf-card-status-7 {
  background: rgba(128, 128, 128, 0.18);
  color: rgba(128, 128, 128, 0.9);
}

.wf-card-status-8 {
  background: rgba(240, 160, 32, 0.18);
  color: #f0a020;
}

.wf-card-error {
  margin-top: 6px;
  color: #d0304f;
  white-space: pre-wrap;
  word-break: break-word;
}

.wf-card-output {
  margin-top: 6px;
}

.wf-card-output-title {
  font-size: 12px;
  color: rgba(128, 128, 128, 0.9);
}

.wf-card-output-pre {
  margin: 4px 0 0;
  padding: 8px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.1);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
}

.wf-card-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.wf-card-resume {
  margin-top: 8px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
</style>
