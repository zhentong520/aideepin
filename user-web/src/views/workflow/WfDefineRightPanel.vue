<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { NInput } from 'naive-ui'
import { AgentNodeProperty, AnswerNodeProperty, ClassifierNodeProperty, DatabaseNodeProperty, DocumentExtractorNodeProperty, EndNodeProperty, FaqExtractorNodeProperty, GoogleNodeProperty, HttpRequestNodeProperty, HumanFeedbackNodeProperty, KeywordExtractorNodeProperty, KnowledgeRetrievalNodeProperty, MailSendNodeProperty, McpNodeProperty, OpenAiImageNodeProperty, SkillNodeProperty, StartNodeProperty, SwticherNodeProperty, TemplateNodeProperty, TongyiwanxNodeProperty } from './components/nodes'
import { useWfStore } from '@/store'
import { t } from '@/locales'
import { SvgIcon } from '@/components/common'
import { getIconByComponentName, getIconClassByComponentName, getNodeColorByComponentName, getNodeConfigSummary } from '@/utils/workflow-util'
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
const typeColor = computed(() => props.wfNode ? getNodeColorByComponentName(props.wfNode.wfComponent.name) : '#94a3b8')
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
        v-if="wfNode?.uuid" class="text-xl shrink-0" :class="getIconClassByComponentName(wfNode.wfComponent.name)"
        :icon="getIconByComponentName(wfNode.wfComponent.name)"
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
            class="mr-2" :class="getIconClassByComponentName(wfNode.wfComponent.name)"
            :icon="getIconByComponentName(wfNode.wfComponent.name)"
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
        <StartNodeProperty
          v-if="wfNode.wfComponent.name === 'Start'" :key="wfNode.uuid" :workflow="workflow"
          :wf-node="wfNode"
        />
        <AnswerNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Answer'" :key="`answer_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <ClassifierNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Classifier'" :key="`classifier_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <SwticherNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Switcher'" :key="`switcher_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <TemplateNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Template'" :key="`template_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <KeywordExtractorNodeProperty
          v-else-if="wfNode.wfComponent.name === 'KeywordExtractor'"
          :key="`keyword_${wfNode.uuid}`" :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <DocumentExtractorNodeProperty
          v-else-if="wfNode.wfComponent.name === 'DocumentExtractor'"
          :key="`document_${wfNode.uuid}`" :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <FaqExtractorNodeProperty
          v-else-if="wfNode.wfComponent.name === 'FaqExtractor'" :key="`faq_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <KnowledgeRetrievalNodeProperty
          v-else-if="wfNode.wfComponent.name === 'KnowledgeRetrieval'"
          :key="`knowledge_${wfNode.uuid}`" :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <OpenAiImageNodeProperty
          v-else-if="wfNode.wfComponent.name === 'OpenAiImage'" :key="`openaiimage_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <TongyiwanxNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Tongyiwanx'" :key="`tongyiwanx_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <GoogleNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Google'" :key="`google_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <HumanFeedbackNodeProperty
          v-else-if="wfNode.wfComponent.name === 'HumanFeedback'" :key="`feedback_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <MailSendNodeProperty
          v-else-if="wfNode.wfComponent.name === 'MailSend'" :key="`mailsend_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <HttpRequestNodeProperty
          v-else-if="wfNode.wfComponent.name === 'HttpRequest'" :key="`httprequest_${wfNode.uuid}`"
          :workflow="workflow" :ui-workflow="uiWorkflow" :wf-node="wfNode"
        />
        <AgentNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Agent'" :key="`agent_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <DatabaseNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Database'" :key="`database_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <McpNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Mcp'" :key="`mcp_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <SkillNodeProperty
          v-else-if="wfNode.wfComponent.name === 'Skill'" :key="`skill_${wfNode.uuid}`"
          :workflow="workflow" :wf-node="wfNode"
        />
        <EndNodeProperty
          v-else-if="wfNode.wfComponent.name === 'End'" :key="`end_${wfNode.uuid}`" :workflow="workflow"
          :ui-workflow="uiWorkflow" :wf-node="wfNode"
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
