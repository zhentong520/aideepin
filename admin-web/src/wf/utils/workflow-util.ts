/**
 * OPT-16：admin 端 workflow-util 出口。核心逻辑已迁入共享包 aideepin-wf-shared
 * （workflow-util-core，注入式：store 取模型/标题/埋点由本薄壳提供）。
 * 函数签名与迁移前保持一致，消费方（wf-designer 各 .vue）零改动。
 *
 * admin 语义（与迁移前一致）：
 * - 标题走 i18n t('workflow.componentTitle.x')（user 端走 descriptor，语义分叉不收）
 * - 连线无校验、全局 animated:true（user 的 T3-06 校验/T3-03 着色为增强语义）
 * - 无 T3-05 历史栈埋点
 */
import { useAppStore } from '@/wf/stores'
import { t } from '@/locales'
import {
  createNewNode as coreCreateNewNode,
  createWorkflowEdge,
  updateEdgeBySourceHandle as coreUpdateEdgeBySourceHandle,
  deleteEdgesBySource as coreDeleteEdgesBySource,
  deleteEdgesBySourceHandle as coreDeleteEdgesBySourceHandle,
  getInputLabelFromParamName as coreGetInputLabelFromParamName,
  getNameByInputType as coreGetNameByInputType,
  type WfUtilHooks,
} from 'aideepin-wf-shared'

export * from 'aideepin-wf-shared'

function getHooks(): WfUtilHooks {
  const appStore = useAppStore()
  return {
    getFirstLLM: (type?: string) => appStore.getFirstLLM(type),
    listImageModels: () => appStore.imageModels,
    getComponentTitle: name => t(`workflow.componentTitle.${name}`),
    onNodeOrEdgeChanged: () => {},
  }
}

export function createNewNode(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  component: Workflow.WorkflowComponent,
  position: { x: number; y: number },
) {
  coreCreateNewNode(getHooks(), workflow, uiWorkflow, component, position)
}

export function createNewEdge(params: {
  workflow: Workflow.WorkflowInfo
  uiWorkflow: Workflow.UIWorkflow
  source: string
  sourceHandle: string
  target: string
},
) {
  createWorkflowEdge(getHooks(), params)
}

export function updateEdgeBySourceHandle(params:
{
  workflow: Workflow.WorkflowInfo
  uiWorkflow: Workflow.UIWorkflow
  source: string
  sourceHandle: string
  target: string
},
) {
  coreUpdateEdgeBySourceHandle(getHooks(), params)
}

export function deleteEdgesBySource(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
) {
  coreDeleteEdgesBySource(getHooks(), workflow, uiWorkflow, source)
}

export function deleteEdgesBySourceHandle(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
  sourceHandle: string,
) {
  coreDeleteEdgesBySourceHandle(workflow, uiWorkflow, source, sourceHandle)
}

export function getInputLabelFromParamName(workflow: Workflow.WorkflowInfo, nodeUuid: string, nodeParamName: string) {
  return coreGetInputLabelFromParamName(workflow, nodeUuid, nodeParamName)
}

export function getNameByInputType(type: number) {
  return coreGetNameByInputType(type)
}

// 图标/图标色：admin 走 switch 硬编码（user 端走 descriptor.ts 单一来源，语义分叉不收共享包）
export function getIconByComponentName(name: string) {
  switch (name.toLowerCase()) {
    case 'answer':
      return 'carbon:question-answering'
    case 'classifier':
      return 'carbon:type-pattern'
    case 'knowledgeretrieval':
      return 'carbon:connect-target'
    case 'documentextractor':
      return 'carbon:ibm-knowledge-catalog-standard'
    case 'keywordextractor':
      return 'carbon:api-key'
    case 'faqextractor':
      return 'fluent-mdl2:book-answers'
    case 'switcher':
      return 'oui:logstash-if'
    case 'template':
      return 'carbon:prompt-template'
    case 'openaiimage':
      return 'solar:pallete-2-linear'
    case 'tongyiwanx':
      return 'solar:pallete-2-linear'
    case 'google':
      return 'ri:google-line'
    case 'humanfeedback':
      return 'covid:transmission-virus-human-transmit-2'
    case 'mailsend':
      return 'carbon:mail-all'
    case 'httprequest':
      return 'carbon:http'
    case 'agent':
      return 'carbon:bot'
    case 'database':
      return 'carbon:data-base'
    case 'mcp':
      return 'carbon:tool-kit'
    case 'skill':
      return 'tabler:puzzle'
    case 'end':
      return 'carbon:closed-caption'
    case 'start':
      return 'carbon:play-outline'
    default:
      return ''
  }
}

export function getIconClassByComponentName(name: string) {
  switch (name.toLowerCase()) {
    case 'answer':
      return 'text-green-800'
    case 'classifier':
      return 'text-violet-900'
    case 'knowledgeretrieval':
      return 'text-stone-900'
    case 'documentextractor':
      return 'text-rose-900'
    case 'keywordextractor':
      return 'text-cyan-900'
    case 'faqextractor':
      return 'text-teal-600'
    case 'switcher':
      return 'text-yellow-900'
    case 'template':
      return 'text-sky-800'
    case 'openaiimage':
      return 'text-fuchsia-700'
    case 'tongyiwanx':
      return 'text-fuchsia-700'
    case 'google':
      return 'text-emerald-900'
    case 'humanfeedback':
      return 'text-zinc-800'
    case 'mailsend':
      return 'text-amber-800'
    case 'httprequest':
      return 'text-slate-800'
    case 'agent':
      return 'text-indigo-800'
    case 'database':
      return 'text-indigo-800'
    case 'mcp':
      return 'text-teal-700'
    case 'skill':
      return 'text-orange-700'
    case 'end':
      return 'text-orange-800'
    case 'start':
      return 'text-blue-900'
    default:
      return ''
  }
}
