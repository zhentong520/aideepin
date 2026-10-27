/**
 * OPT-16：user 端 workflow-util 出口。核心逻辑已迁入共享包 aideepin-wf-shared
 * （workflow-util-core，注入式：store 取模型/descriptor 标题/T3-05 历史栈埋点由本薄壳提供）。
 * 函数签名与迁移前保持一致，消费方（views/workflow 各 .vue）零改动。
 *
 * user 语义（与迁移前一致）：createNewEdge 带 T3-06 校验 + T3-03 着色 + T3-05 埋点
 * （对应共享包 strict 模式）；标题/图标走 descriptor.ts 单一来源（T5-03）。
 */
import { pushWorkflowHistory } from '@/utils/workflow-history'
import { getWfNodeDescriptor } from '@/views/workflow/components/nodes/descriptor'
import { useAppStore } from '@/store'
import { t } from '@/locales'
import i18n from '@/locales'
import {
  createNewNode as coreCreateNewNode,
  createWorkflowEdge,
  updateEdgeBySourceHandle as coreUpdateEdgeBySourceHandle,
  deleteEdgesBySource as coreDeleteEdgesBySource,
  deleteEdgesBySourceHandle as coreDeleteEdgesBySourceHandle,
  getInputLabelFromParamName as coreGetInputLabelFromParamName,
  getNameByInputType as coreGetNameByInputType,
  getEdgeVisual as coreGetEdgeVisual,
  buildEdgeStyle as coreBuildEdgeStyle,
  buildUiEdge as coreBuildUiEdge,
  wfNodeToUiNode as coreWfNodeToUiNode,
  type WfUtilHooks,
} from 'aideepin-wf-shared'

export * from 'aideepin-wf-shared'

function getHooks(): WfUtilHooks {
  const appStore = useAppStore()
  return {
    getFirstLLM: (type?: string) => appStore.getFirstLLM(type),
    listImageModels: () => appStore.imageModels,
    getComponentTitle: name => getComponentTitle(name),
    onNodeOrEdgeChanged: workflow => pushWorkflowHistory(workflow),
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

/**
 * T3-06：创建连线（含合法性校验）
 * - 自环连线（source === target）拦截
 * - 重复连线（同 source + sourceHandle + target）拦截
 * @returns 是否创建成功
 */
export function createNewEdge(params: {
  workflow: Workflow.WorkflowInfo
  uiWorkflow: Workflow.UIWorkflow
  source: string
  sourceHandle: string
  target: string
},
): boolean {
  return createWorkflowEdge(getHooks(), params, { strict: true })
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
  coreUpdateEdgeBySourceHandle(getHooks(), params, { strict: true })
}

export function deleteEdgesBySource(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
) {
  coreDeleteEdgesBySource(getHooks(), workflow, uiWorkflow, source, { strict: true })
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

export function wfNodeToUiNode(node: Workflow.WorkflowNode) {
  return coreWfNodeToUiNode(node)
}

/**
 * T5-03：组件标题从节点描述文件取（单一来源）；locales componentTitle key 保留作兜底
 */
export function getComponentTitle(name: string): string {
  const descriptor = getWfNodeDescriptor(name)
  if (descriptor) {
    const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
    return locale.startsWith('en') ? descriptor.componentTitle.en : descriptor.componentTitle.zh
  }
  return t(`workflow.componentTitle.${name}`)
}

/**
 * T5-03：图标从节点描述文件取（原 switch 硬编码收敛到 descriptor.ts）
 */
export function getIconByComponentName(name: string) {
  return getWfNodeDescriptor(name)?.icon || ''
}

/**
 * T5-03：图标颜色 class 从节点描述文件取
 */
export function getIconClassByComponentName(name: string) {
  return getWfNodeDescriptor(name)?.iconClass || ''
}

/**
 * T5-03：按 7 类节点类型返回色条颜色 —— 从节点描述文件读取（原 switch 硬编码收敛到 descriptor.ts）
 * 开始 / LLM / 知识库 / 条件 / 输出(结束) / 插件 / 其他
 */
export function getNodeColorByComponentName(name: string) {
  return getWfNodeDescriptor(name)?.color || '#94a3b8'
}

/**
 * T3-02：节点配置摘要 —— 取 nodeConfig 中第一个有意义的值，一行展示
 * （LLM 节点显示模型名、知识库节点显示库名、HTTP 节点显示 URL……）
 */
export function getNodeConfigSummary(node: Workflow.WorkflowNode): string {
  const cfg = (node.nodeConfig || {}) as Record<string, unknown>
  const candidates = ['model_name', 'knowledge_base_name', 'skill_name', 'url', 'query', 'to_mails', 'tool_name', 'prompt', 'sql']
  for (const key of candidates) {
    const value = cfg[key]
    if (typeof value === 'string' && value.trim()) {
      const text = value.trim().replace(/\s+/g, ' ')
      return text.length > 32 ? `${text.slice(0, 32)}…` : text
    }
  }
  return ''
}

/**
 * T3-03：连线按数据流方向/类型着色
 */
export function getEdgeVisual(sourceComponentName: string, sourceHandle?: string) {
  return coreGetEdgeVisual(sourceComponentName, sourceHandle)
}

export function buildEdgeStyle(visual: ReturnType<typeof coreGetEdgeVisual>): Record<string, string | number> {
  return coreBuildEdgeStyle(visual)
}

/**
 * 由业务边对象构建 UI 边（T3-03：按数据流方向/类型着色，替代原全局 animated:true）
 */
export function buildUiEdge(wfEdge: Workflow.WorkflowEdge, sourceNode?: Workflow.WorkflowNode) {
  return coreBuildUiEdge(wfEdge, sourceNode)
}

/**
 * 按 workflow.nodes/edges 全量重建 UI 画布数据（撤销/重做恢复时使用）
 */
export function rebuildUiWorkflow(workflow: Workflow.WorkflowInfo, uiWorkflow: Workflow.UIWorkflow) {
  const nodeById = new Map(workflow.nodes.map(node => [node.uuid, node]))
  const newUiNodes = workflow.nodes.map(node => wfNodeToUiNode(node))
  const newUiEdges = workflow.edges
    .filter(edge => nodeById.has(edge.sourceNodeUuid))
    .map(edge => buildUiEdge(edge, nodeById.get(edge.sourceNodeUuid)))
  uiWorkflow.nodes.splice(0, uiWorkflow.nodes.length, ...newUiNodes)
  uiWorkflow.edges.splice(0, uiWorkflow.edges.length, ...newUiEdges)
}

/**
 * 复制节点时，重映射 nodeConfig 内部指向旧节点 uuid 的连线目标（Switcher/Classifier）
 */
export function remapClonedNodeConfig(node: Workflow.WorkflowNode, uuidMap: Map<string, string>) {
  const cfg = node.nodeConfig as Record<string, unknown> | undefined
  if (!cfg)
    return

  if (node.wfComponent.name === 'Switcher') {
    const switcher = cfg as unknown as Workflow.NodeConfigSwitcher
    if (switcher.default_target_node_uuid && uuidMap.has(switcher.default_target_node_uuid))
      switcher.default_target_node_uuid = uuidMap.get(switcher.default_target_node_uuid)!

    switcher.cases.forEach((item) => {
      if (item.target_node_uuid && uuidMap.has(item.target_node_uuid))
        item.target_node_uuid = uuidMap.get(item.target_node_uuid)!
    })
  } else if (node.wfComponent.name === 'Classifier') {
    const classifier = cfg as unknown as Workflow.NodeConfigClassifier
    classifier.categories.forEach((item) => {
      if (item.target_node_uuid && uuidMap.has(item.target_node_uuid))
        item.target_node_uuid = uuidMap.get(item.target_node_uuid)!
    })
  }
}

/**
 * T3-07：一键自动布局 —— 分层网格整理（BFS 分层 + 层内顺序排列，不引重依赖）
 */
export function autoLayoutWorkflow(workflow: Workflow.WorkflowInfo, uiWorkflow: Workflow.UIWorkflow) {
  const nodes = workflow.nodes
  if (nodes.length === 0)
    return

  const edges = workflow.edges
  const inDegree = new Map(nodes.map(node => [node.uuid, 0]))
  edges.forEach((edge) => {
    if (inDegree.has(edge.targetNodeUuid) && edge.sourceNodeUuid !== edge.targetNodeUuid)
      inDegree.set(edge.targetNodeUuid, inDegree.get(edge.targetNodeUuid)! + 1)
  })

  // BFS 分层：从无入边节点开始
  const layers: string[][] = []
  let frontier = nodes.filter(node => (inDegree.get(node.uuid) || 0) === 0).map(node => node.uuid)
  const visited = new Set(frontier)
  while (frontier.length > 0) {
    layers.push(frontier)
    const next: string[] = []
    for (const uuid of frontier) {
      edges.forEach((edge) => {
        if (edge.sourceNodeUuid === uuid && !visited.has(edge.targetNodeUuid)) {
          visited.add(edge.targetNodeUuid)
          next.push(edge.targetNodeUuid)
        }
      })
    }
    frontier = next
  }
  // 有环/孤岛兜底：未访问节点追加为一层
  const rest = nodes.map(node => node.uuid).filter(uuid => !visited.has(uuid))
  if (rest.length > 0)
    layers.push(rest)

  const H_GAP = 360
  const V_GAP = 190
  layers.forEach((layer, layerIdx) => {
    layer.forEach((uuid, nodeIdx) => {
      const wfNode = nodes.find(node => node.uuid === uuid)
      const uiNode = uiWorkflow.nodes.find(item => item.id === uuid)
      const x = 20 + layerIdx * H_GAP
      const y = 20 + nodeIdx * V_GAP
      if (wfNode) {
        wfNode.positionX = x
        wfNode.positionY = y
      }
      if (uiNode)
        uiNode.position = { x, y }
    })
  })
}
