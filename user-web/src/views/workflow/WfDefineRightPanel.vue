<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { NInput } from 'naive-ui'
import { useWfStore } from '@/store'
import { t } from '@/locales'
import { SvgIcon } from '@/components/common'
import { getNodeConfigSummary } from '@/views/workflow/utils/workflow-util'
// T5-03：面板数据源 —— 图标/图标色/色条色/标题从节点描述文件按 type 读取
// T3-08：面板 UI 重构 —— customPanel 节点沿用原属性组件，其余由 SchemaForm 按 configSchema 生成三段式表单
import { getWfNodeDescriptor } from './components/nodes/descriptor'
import SchemaForm from './components/SchemaForm.vue'
import { emptyWorkflowNode } from '@/utils/functions'

interface Props {
  workflow: Workflow.WorkflowInfo
  uiWorkflow: Workflow.UIWorkflow
  hidePropertyPanel: boolean
  wfNode?: Workflow.WorkflowNode
}

const props = withDefaults(defineProps<Props>(), {
  hidePropertyPanel: false,
  wfNode: () => emptyWorkflowNode(),
})

const emit = defineEmits<Emit>()

interface Emit {
  (e: 'expand'): void
  (e: 'collapse'): void
}

const wfStore = useWfStore()
const nodeTitle = ref<string>(props.wfNode.title)

// T3-04：折叠时仅保留 56px 摘要条；无选中节点同样折叠
const collapsed = computed(() => props.hidePropertyPanel || !props.wfNode?.uuid)
// T5-03：按 type 取节点描述（图标/图标色/色条色/标题的单一路径）
const nodeDesc = computed(() => getWfNodeDescriptor(props.wfNode?.wfComponent?.name))
const typeColor = computed(() => nodeDesc.value?.color || '#94a3b8')
const summary = computed(() => props.wfNode ? getNodeConfigSummary(props.wfNode) : '')

watch(
  () => props.wfNode,
  (newVal) => {
    if (newVal)
      nodeTitle.value = newVal.title
  },
  { immediate: true, deep: true },
)
watch(
  () => nodeTitle,
  (newVal) => {
    if (newVal)
      wfStore.updateWfNodeTitle(props.workflow.uuid, props.wfNode.uuid, newVal.value)
  },
  { immediate: true, deep: true },
)
onMounted(() => {
  nodeTitle.value = props.wfNode.title
})
</script>

<template>
  <!-- T3-04：属性面板改为 380px 独立栏（画布自适应收窄，编辑时始终可见被编辑节点）；折叠时 56px 摘要条 -->
  <div class="wf-props-panel shrink-0 h-full bg-white border-l border-gray-200 flex flex-col" :style="{ width: collapsed ? '56px' : '380px' }">
    <!-- 折叠：56px 摘要条 -->
    <div
      v-if="collapsed" class="w-full h-full flex flex-col items-center py-3 cursor-pointer hover:bg-gray-50"
      :title="wfNode?.title || t('workflow.nodeNamePlaceholder')"
      @click="emit('expand')"
    >
      <SvgIcon
        v-if="wfNode?.uuid" class="text-xl shrink-0" :class="nodeDesc?.iconClass"
        :icon="nodeDesc?.icon"
      />
      <div class="wf-panel-vertical-text" :style="{ color: typeColor }">
        {{ wfNode?.title || summary }}
      </div>
      <SvgIcon class="text-gray-400 shrink-0" icon="ri:arrow-left-s-line" />
    </div>
    <!-- 展开：380px 属性栏 -->
    <div v-else class="w-full h-full flex flex-col overflow-hidden">
      <div class="px-3 pt-4 pb-3 border-b divide-gray-400 shrink-0">
        <div class="text-xl flex items-center h-9 mb-2">
          <SvgIcon
            class="mr-2" :class="nodeDesc?.iconClass"
            :icon="nodeDesc?.icon"
          />
          <NInput
            v-model:value="nodeTitle" :placeholder="t('workflow.nodeNamePlaceholder')" class="h-8 border-gray-100"
            style="font-size: 1rem;line-height: 1.5rem;font-weight: 700;"
          />
          <!-- 折叠按钮 -->
          <SvgIcon class="text-gray-400 cursor-pointer ml-2 shrink-0" icon="ri:arrow-right-s-line" @click="emit('collapse')" />
        </div>
        <div class="text-sm text-gray-500">
          {{ t('workflow.componentFunction') }}{{ wfNode.wfComponent.remark }}
        </div>
      </div>
      <div class="flex-1 overflow-y-auto px-3 pt-4">
        <!-- T3-08：customPanel 节点（Start/Switcher/Classifier/HttpRequest/Database/Mcp/Skill/DocumentExtractor/HumanFeedback）
             configSchema 表达不了其复杂交互，保留原属性组件；其余 11 种节点统一走 configSchema 三段式表单 -->
        <component
          :is="nodeDesc?.propertyComponent" v-if="nodeDesc?.customPanel" :key="`custom_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <SchemaForm
          v-else-if="nodeDesc" :key="`schema_${wfNode.uuid}`" :workflow="workflow"
          :wf-node="wfNode"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-panel-vertical-text {
  writing-mode: vertical-rl;
  max-height: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  margin: 8px 0;
}
</style>
