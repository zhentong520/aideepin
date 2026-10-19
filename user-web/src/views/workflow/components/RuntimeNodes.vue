<script lang="ts" setup>
import { NImage, NImageGroup } from 'naive-ui'
import { SvgIcon } from '@/components/common'
import { getIconByComponentName, getIconClassByComponentName } from '@/utils/workflow-util'
// T5-03（第六处收敛点）：指标 chip 由节点描述文件 metrics 声明驱动，新增节点在 descriptor.ts 声明 metrics 即可显示
import { getWfNodeDescriptor } from '@/views/workflow/components/nodes/descriptor'
import { useAuthStore } from '@/store'
import { formatDuration } from '@/utils/format'
import { t } from '@/locales'
import { getRealFileUrl } from '@/utils/functions'
import TextComponent from '@/views/chat/components/Message/Text.vue'

interface Props {
  nodes: Workflow.WfRuntimeNode[]
  workflow: Workflow.WorkflowInfo
  errorMsg: string
}
defineProps<Props>()

// T3-09：失败/任意条目点击 → 画布定位到对应节点（由父级 RunDetail 转发给 WorkflowDefine）
const emit = defineEmits<{ (e: 'locate', nodeUuid: string): void }>()

const authStore = useAuthStore()

// T3-09：节点执行状态（后端 NODE_PROCESS_STATUS：1 ready / 2 doing / 3 success / 4 fail）
function nodeStatusInfo(status: number): { label: string, icon: string, cls: string } | null {
  if (status === 2)
    return { label: t('workflow.nodeRunningLabel'), icon: 'carbon:progress-bar', cls: 'wf-status-running' }
  if (status === 3)
    return { label: t('workflow.nodeDoneLabel'), icon: 'carbon:checkmark-outline', cls: 'wf-status-done' }
  if (status === 4)
    return { label: t('workflow.nodeFailedLabel'), icon: 'carbon:warning-alt', cls: 'wf-status-failed' }
  return null
}

function isFailed(node: Workflow.WfRuntimeNode): boolean {
  return node.status === 4
}

// ==================== T5-03：描述文件驱动的指标 chip ====================
type WfMetricRecord = Record<string, unknown>

interface WfMetricChip {
  key: string
  icon: string
  text: string
  tone: 'normal' | 'success' | 'error'
}

interface WfMetricRenderer {
  icon: string
  text: (value: unknown, metadata: WfMetricRecord) => string
  tone?: 'success' | 'error'
  /** 不传时默认按 metadata[key] != null 判定是否显示 */
  visible?: (metadata: WfMetricRecord) => boolean
}

// 指标渲染注册表：key 与后端 NodeExecutionMetrics 子类字段一一对应
const METRIC_RENDERERS: Record<string, WfMetricRenderer> = {
  inputTokens: { icon: '📥', text: v => `${v} tokens` },
  outputTokens: { icon: '📤', text: v => `${v} tokens` },
  modelName: { icon: '🤖', text: v => String(v) },
  retrievalCount: { icon: '📚', text: v => `${v} chunks` },
  httpStatusCode: { icon: '', text: v => `HTTP ${v}` },
  searchResultCount: { icon: '🔍', text: v => `${v} results` },
  imageModelName: { icon: '🎨', text: v => String(v) },
  imageSize: { icon: '📐', text: v => String(v) },
  sendSuccess: {
    icon: '✉️',
    text: (_v, metadata) => `sent to ${metadata.recipientCount ?? 0}`,
    tone: 'success',
    visible: metadata => metadata.sendSuccess === true,
  },
  fileCount: { icon: '📄', text: v => `${v} files` },
  extractedCharCount: { icon: '📝', text: v => `${v} chars` },
  toolName: { icon: '🧰', text: v => String(v) },
  mcpTitle: { icon: '🔌', text: v => String(v) },
  resultLength: { icon: '📝', text: v => `${v} chars` },
  skillName: { icon: '🧩', text: v => String(v) },
  mode: { icon: '', text: v => `${v === 'script' ? '▶' : '📖'} ${v}` },
  script: { icon: '🐍', text: v => String(v) },
}

function metricChipClass(tone: WfMetricChip['tone']) {
  if (tone === 'success')
    return 'bg-green-50 text-green-600'
  if (tone === 'error')
    return 'bg-red-50 text-red-500'
  return 'metric-chip'
}

/** 按节点描述文件的 metrics 声明，把执行 metadata 组装成 chip 列表 */
function getNodeMetricChips(node: Workflow.WfRuntimeNode): WfMetricChip[] {
  const metadata = (node.metadata || {}) as unknown as WfMetricRecord
  const descriptor = getWfNodeDescriptor(node.wfComponent?.name)
  if (!descriptor || descriptor.metrics.length === 0)
    return []

  const chips: WfMetricChip[] = []
  for (const key of descriptor.metrics) {
    const renderer = METRIC_RENDERERS[key]
    const value = metadata[key]
    if (renderer) {
      const show = renderer.visible ? renderer.visible(metadata) : value != null
      if (!show)
        continue

      chips.push({ key, icon: renderer.icon, text: renderer.text(value, metadata), tone: renderer.tone || 'normal' })
    }
    else if (value != null && value !== '') {
      // 描述文件声明了新指标但渲染注册表未覆盖时兜底展示（保证指标不丢）
      chips.push({ key, icon: '·', text: `${key}: ${String(value)}`, tone: 'normal' })
    }
  }
  return chips
}
</script>

<template>
  <div>
    <div v-if="errorMsg" class="py-2 text-red-500">
      {{ t('workflow.errorLabel') }}{{ errorMsg }}
    </div>
    <div v-else-if="nodes.length === 0" class="text-center py-2 text-neutral-400">
      {{ t('common.noContent') }}
    </div>
    <div
      v-for="node in nodes" :key="node.uuid"
      class="flex flex-col space-y-2 border p-2 m-2 rounded-md" :title="node.nodeTitle"
      :class="isFailed(node) ? 'wf-runtime-failed cursor-pointer border-red-300 hover:bg-red-50/60' : 'border-gray-200'"
      :name="node.uuid"
      @click="isFailed(node) && emit('locate', node.nodeUuid)"
    >
      <div class="flex items-center space-x-1 bg-gray-100 px-2 py-1 rounded-md">
        <SvgIcon
          v-if="node.wfComponent" class="text-base" :class="getIconClassByComponentName(node.wfComponent.name)"
          :icon="getIconByComponentName(node.wfComponent.name)"
        />
        <div class="text-base">
          {{ node.nodeTitle || t('workflow.nodeTitleNotFound') }}
        </div>
        <!-- T3-09：执行状态（图标 + 文案双重标识，不单靠颜色；失败条目可点击定位到画布节点） -->
        <template v-if="nodeStatusInfo(node.status)">
          <SvgIcon
            class="ml-auto text-base shrink-0" :class="nodeStatusInfo(node.status)!.cls"
            :icon="nodeStatusInfo(node.status)!.icon"
          />
          <span class="text-sm shrink-0" :class="nodeStatusInfo(node.status)!.cls">
            {{ nodeStatusInfo(node.status)!.label }}
          </span>
        </template>
        <SvgIcon
          v-if="node.nodeUuid" class="ml-auto text-gray-400 hover:text-blue-600 cursor-pointer shrink-0"
          icon="ri:focus-3-line" :title="t('workflow.replayLocate')"
          @click.stop="emit('locate', node.nodeUuid)"
        />
      </div>
      <!-- T3-09：失败原因展示（statusRemark） -->
      <div v-if="isFailed(node) && node.statusRemark" class="wf-runtime-failed-reason">
        <SvgIcon icon="carbon:warning-alt" class="mr-1 shrink-0" />
        {{ node.statusRemark }}
      </div>
      <div v-if="node.duration || node.metadata" class="flex flex-wrap gap-1.5 px-2 py-1 text-xs text-gray-500">
        <span v-if="formatDuration(node.duration)" class="metric-chip">
          ⏱ {{ formatDuration(node.duration) }}
        </span>
        <!-- T5-03：指标 chip 由节点描述文件 metrics 驱动（原 10 个按 metadata.type 硬编码的 template 分支收敛于此） -->
        <span
          v-for="chip in getNodeMetricChips(node)" :key="chip.key" class="px-1.5 py-0.5 rounded"
          :class="metricChipClass(chip.tone)"
        >
          {{ chip.icon ? `${chip.icon} ` : '' }}{{ chip.text }}
        </span>
      </div>
      <div class="flex flex-col space-y-2">
        <div class="text-base border-b border-gray-200 py-1">
          {{ t('common.input') }}
        </div>
        <div v-for="(content, name) in node.input" :key="`input_${name}`" class="flex">
          <div class="min-w-24 pr-2">
            {{ name }}
          </div>
          <div>
            {{ content.value || t('common.noContent') }}
          </div>
        </div>
        <div class="text-base border-b border-gray-200 py-1">
          {{ t('common.output') }}
        </div>
        <div v-for="(content, name) in node.output" :key="`onput_${name}`" class="flex">
          <template v-if="content.type === 4">
            <NImageGroup>
              <NImage
                v-for="url in content.value" :key="url" :src="`${getRealFileUrl(url)}?token=${authStore.token}`"
                width="100"
              />
            </NImageGroup>
          </template>
          <template v-else>
            <div class="min-w-24 pr-2">
              {{ name }}
            </div>
            <div>
              <TextComponent :inversion="false" :text="content.value || t('common.noContent')" :as-raw-text="false" />
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Compact info chip used to display per-node observability metrics (tokens, model, counts...). */
.metric-chip {
  background-color: rgb(243 244 246); /* tailwind bg-gray-100 */
  padding: 0.125rem 0.375rem;          /* tailwind py-0.5 px-1.5 */
  border-radius: 0.25rem;              /* tailwind rounded */
}

/* T3-09：执行状态（图标+文案双通道，色觉障碍友好） */
.wf-status-running {
  color: #2563eb;
}

.wf-status-done {
  color: #16a34a;
}

.wf-status-failed {
  color: #dc2626;
  font-weight: 600;
}

.wf-runtime-failed-reason {
  display: flex;
  align-items: flex-start;
  font-size: 13px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  padding: 4px 8px;
  word-break: break-all;
}
</style>
