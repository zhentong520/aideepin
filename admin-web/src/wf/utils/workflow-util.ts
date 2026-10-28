/**
 * OPT-16：admin 端 workflow-util 出口。核心逻辑已迁入共享包 aideepin-wf-shared
 * （workflow-util-core，注入式：store 取模型/标题/埋点由本薄壳提供）。
 * 函数签名与迁移前保持一致，消费方（wf-designer 各 .vue）零改动。
 *
 * admin 语义（S2-07 步骤①更新）：
 * - 图标/图标色/组件标题已收编到共享包节点描述（descriptor 单一来源，与 user 端同源）；
 *   描述未收录的类型回退 i18n key
 * - 连线无校验、全局 animated:true（user 的 T3-06 校验/T3-03 着色为增强语义）
 * - 无 T3-05 历史栈埋点
 */
import { useAppStore } from '@/wf/stores'
import i18n, { t } from '@/locales'
import {
  createNewNode as coreCreateNewNode,
  createWorkflowEdge,
  updateEdgeBySourceHandle as coreUpdateEdgeBySourceHandle,
  deleteEdgesBySource as coreDeleteEdgesBySource,
  deleteEdgesBySourceHandle as coreDeleteEdgesBySourceHandle,
  getInputLabelFromParamName as coreGetInputLabelFromParamName,
  getNameByInputType as coreGetNameByInputType,
  getWfNodeDescriptorData,
  type WfUtilHooks,
} from 'aideepin-wf-shared'

export * from 'aideepin-wf-shared'

function getHooks(): WfUtilHooks {
  const appStore = useAppStore()
  return {
    getFirstLLM: (type?: string) => appStore.getFirstLLM(type),
    listImageModels: () => appStore.imageModels,
    getComponentTitle: name => getComponentTitle(name),
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

// S2-07（ADR-005）步骤①：图标/图标色收编到共享包节点描述单一来源。
// 收编前已机器 diff：20 个重叠类型 icon/iconClass 与原 switch 逐项一致（零视觉变化），
// 并自动补齐 P3 五节点（Orchestrator/Loop/Parallel/Code/SubWorkflow）此前缺失的图标。
export function getIconByComponentName(name: string) {
  return getWfNodeDescriptorData(name)?.icon ?? ''
}

export function getIconClassByComponentName(name: string) {
  return getWfNodeDescriptorData(name)?.iconClass ?? ''
}

/**
 * S2-07 步骤①：组件标题从共享包节点描述取（单一来源，zh/en 随当前 locale）。
 * 描述未收录的类型（如 Special）回退 i18n key（保留兜底，行为与收编前一致）。
 */
export function getComponentTitle(name: string): string {
  const desc = getWfNodeDescriptorData(name)
  if (!desc)
    return t(`workflow.componentTitle.${name}`)
  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  return locale.startsWith('en') ? desc.componentTitle.en : desc.componentTitle.zh
}
