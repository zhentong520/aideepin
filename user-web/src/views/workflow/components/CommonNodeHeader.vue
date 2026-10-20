<script setup lang="ts">
import { computed, h } from 'vue'
import { NDropdown, NTooltip } from 'naive-ui'
import { SvgIcon } from '@/components/common'
import { getIconByComponentName, getIconClassByComponentName, getNodeColorByComponentName, getNodeConfigSummary } from '@/views/workflow/utils/workflow-util'
import { getNodeValidationIssues } from '@/utils/workflow-validate'
import { getWfNodeDescriptor } from '../components/nodes/descriptor'
import { useWfStore } from '@/store'
import { t } from '@/locales'
import i18n from '@/locales'
const props = defineProps<Props>()
const options = [
  {
    label: t('common.delete'),
    key: 'delete',
    icon: renderIcon('ri:delete-bin-line'),
  },
]
interface Props {
  wfNode: Workflow.WorkflowNode
}
const wfStore = useWfStore()

// T3-02：节点类型色条 + 配置摘要
const typeColor = computed(() => getNodeColorByComponentName(props.wfNode.wfComponent.name))
const summary = computed(() => getNodeConfigSummary(props.wfNode))

// T3-11：未配置/失效节点实时校验（与保存校验同源，configSchema 必填字段驱动）
const issues = computed(() => getNodeValidationIssues(props.wfNode))

// T3-14：节点文档（hover 展示 用途/必填/输出结构/常见错误）
const doc = computed(() => {
  const desc = getWfNodeDescriptor(props.wfNode.wfComponent?.name)
  if (!desc)
    return null
  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  return locale.startsWith('en') ? desc.doc.en : desc.doc.zh
})

function renderIcon(icon: string) {
  return () => {
    return h(
      SvgIcon,
      {
        icon,
        class: 'text-base cursor-pointer',
      })
  }
}

function handleSelect(key: string | number) {
  if (key === 'delete') {
    wfStore.deleteNode(props.wfNode.workflowUuid, props.wfNode.uuid)
  }
}
</script>

<template>
  <!-- T3-02：按节点类型的配色色条 -->
  <div class="wf-type-bar" :style="{ backgroundColor: typeColor }" />
  <div
    class="w-full flex border-b divide-gray-400 pb-3 mb-3 font-bold text-base text-center items-center justify-center"
  >
    <div class="w-6 mr-2">
      <SvgIcon
        class="text-xl" :class="getIconClassByComponentName(wfNode.wfComponent.name)"
        :icon="getIconByComponentName(wfNode.wfComponent.name)"
      />
    </div>
    <div class="flex-1 max-h-6 overflow-hidden text-nowrap">
      {{ wfNode.title }}
    </div>
    <!-- T3-11：未配置徽标（⚠ 图标 + 数量角标，tooltip 列出缺失项；图标+文案双通道，色觉障碍友好） -->
    <NTooltip v-if="issues.length > 0" trigger="hover" :style="{ maxWidth: '260px' }">
      <template #trigger>
        <div class="wf-invalid-badge relative cursor-help mr-1">
          <SvgIcon icon="carbon:warning-alt" class="text-lg text-amber-500" />
          <span class="wf-invalid-count">{{ issues.length }}</span>
        </div>
      </template>
      <div class="flex flex-col gap-1">
        <div class="font-bold">
          {{ t('workflow.nodeInvalidBadge') }}
        </div>
        <div v-for="(issue, idx) in issues" :key="idx" class="text-xs">
          {{ t('workflow.nodeInvalidItem', { field: issue.fieldLabel }) }}
        </div>
      </div>
    </NTooltip>
    <div class="w-6 ml-2">
      <NDropdown v-if="wfNode.wfComponent.name !== 'Start'" :options="options" @select="handleSelect">
        <SvgIcon class="cursor-pointer" icon="ri:more-fill" />
      </NDropdown>
    </div>
  </div>
  <!-- T3-02：配置摘要（模型名/库名/URL 等关键配置一眼可见） -->
  <div v-if="summary" class="wf-node-summary">
    {{ summary }}
  </div>
  <!-- T3-14：节点文档 hover（用途/必填/输出结构/常见错误） -->
  <NTooltip v-if="doc" trigger="hover" placement="right" :style="{ maxWidth: '320px' }">
    <template #trigger>
      <div class="wf-doc-trigger">
        <SvgIcon icon="carbon:information" class="mr-1" />
        <span>{{ t('workflow.nodeDocTitle') }}</span>
      </div>
    </template>
    <div class="flex flex-col gap-1.5 text-xs leading-relaxed">
      <div>
        <span class="font-bold">{{ t('workflow.nodeDocPurpose') }}：</span>{{ doc.purpose }}
      </div>
      <div>
        <span class="font-bold">{{ t('workflow.nodeDocRequired') }}：</span>{{ doc.required }}
      </div>
      <div>
        <span class="font-bold">{{ t('workflow.nodeDocOutput') }}：</span>{{ doc.output }}
      </div>
      <div>
        <span class="font-bold">{{ t('workflow.nodeDocErrors') }}：</span>{{ doc.errors }}
      </div>
    </div>
  </NTooltip>
</template>

<style scoped>
/* T3-11：未配置徽标（琥珀色 + 数量角标，非纯红色标识） */
.wf-invalid-badge {
  display: flex;
  align-items: center;
}

.wf-invalid-count {
  position: absolute;
  top: -6px;
  right: -8px;
  background: #f59e0b;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  border-radius: 8px;
  padding: 2px 4px;
}

/* T3-14：节点文档触发区（hover 展开） */
.wf-doc-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #9ca3af;
  cursor: help;
  margin-top: 2px;
}

.wf-doc-trigger:hover {
  color: #2563eb;
}
</style>
