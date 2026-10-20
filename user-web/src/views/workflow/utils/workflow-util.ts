import { v4 as uuidv4 } from 'uuid'
import { emptyWorkflowNode } from '@/utils/functions'
import { pushWorkflowHistory } from '@/utils/workflow-history'
import { getWfNodeDescriptor } from '@/views/workflow/components/nodes/descriptor'
import { useAppStore } from '@/store'
import { t } from '@/locales'
import i18n from '@/locales'

export function createNewNode(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  component: Workflow.WorkflowComponent,
  position: { x: number; y: number },
) {
  const newWfNode = emptyWorkflowNode()
  newWfNode.uuid = uuidv4().replace(/-/g, '')
  newWfNode.title = getComponentTitle(component.name)
  newWfNode.workflowId = workflow.id
  newWfNode.workflowUuid = workflow.uuid
  newWfNode.wfComponent = component
  newWfNode.workflowComponentId = component.id
  newWfNode.inputConfig = { user_inputs: [], ref_inputs: [] }
  newWfNode.nodeConfig = {}
  newWfNode.outputConfig = {}
  newWfNode.positionX = position.x
  newWfNode.positionY = position.y
  if (component.name === 'Classifier')
    createClassifierNode(newWfNode)
  else if (component.name === 'Answer')
    createAnswer(newWfNode)
  else if (component.name === 'Switcher')
    createSwitcherNode(workflow, newWfNode)
  else if (component.name === 'KeywordExtractor')
    createKeywordExtractor(newWfNode)
  else if (component.name === 'FaqExtractor')
    createFaqExtractor(newWfNode)
  else if (component.name === 'KnowledgeRetrieval')
    createKnowledgeRetrieval(newWfNode)
  else if (component.name === 'OpenAiImage')
    createOpenAiImage(newWfNode)
  else if (component.name === 'Tongyiwanx')
    createTongyiwanx(newWfNode)
  else if (component.name === 'Google')
    createGoogle(newWfNode)
  else if (component.name === 'HumanFeedback')
    createHumanFeedback(newWfNode)
  else if (component.name === 'MailSend')
    createMailSend(newWfNode)
  else if (component.name === 'HttpRequest')
    createHttpRequest(newWfNode)
  else if (component.name === 'Agent')
    createAgent(newWfNode)
  else if (component.name === 'Database')
    createDatabase(newWfNode)
  else if (component.name === 'Mcp')
    createMcp(newWfNode)
  else if (component.name === 'Skill')
    createSkill(newWfNode)

  workflow.nodes.push(newWfNode)
  uiWorkflow.nodes.push(wfNodeToUiNode(newWfNode))
  // T3-05：入口埋点 —— 加节点进历史栈
  pushWorkflowHistory(workflow)
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
  // T3-06：自环连线拦截
  if (params.target && params.source === params.target)
    return false

  // T3-06：重复连线拦截
  if (params.target) {
    const duplicated = params.workflow.edges.some(edge =>
      edge.sourceNodeUuid === params.source
      && edge.targetNodeUuid === params.target
      && (edge.sourceHandle || '') === (params.sourceHandle || ''),
    )
    if (duplicated)
      return false
  }

  const wfEdge = {
    id: '',
    uuid: uuidv4().replace(/-/g, ''),
    workflowId: params.workflow.id,
    sourceNodeUuid: params.source,
    sourceHandle: params.sourceHandle,
    targetNodeUuid: params.target,
    workflowUuid: params.workflow.uuid,
  }
  params.workflow.edges.push(wfEdge)
  if (params.target) {
    // T3-03：连线按数据流方向/类型着色
    const sourceNode = params.workflow.nodes.find(node => node.uuid === params.source)
    params.uiWorkflow.edges.push(buildUiEdge(wfEdge, sourceNode))
  }
  // 判断源节点是否【条件分支】或【内容分类】
  params.workflow.nodes.forEach((node) => {
    if (node.uuid === params.source) {
      if (node.wfComponent.name === 'Switcher') {
        if (wfEdge.sourceHandle === 'default_handle') {
          (node.nodeConfig as Workflow.NodeConfigSwitcher).default_target_node_uuid = wfEdge.targetNodeUuid
        } else {
          (node.nodeConfig as Workflow.NodeConfigSwitcher).cases.forEach((item) => {
            if (item.uuid === wfEdge.sourceHandle)
              item.target_node_uuid = wfEdge.targetNodeUuid
          })
        }
      } else if (node.wfComponent.name === 'Classifier') {
        (node.nodeConfig as Workflow.NodeConfigClassifier).categories.forEach((item) => {
          if (item.category_uuid === wfEdge.sourceHandle)
            item.target_node_uuid = wfEdge.targetNodeUuid
        })
      }
    }
  })
  // T3-05：入口埋点 —— 连线进历史栈
  pushWorkflowHistory(params.workflow)
  return true
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
  const wfEdge = params.workflow.edges.find(item => item.sourceHandle === params.sourceHandle)
  if (!wfEdge) {
    console.warn('no edge found for sourceHandle')
    return
  }
  wfEdge.targetNodeUuid = params.target
  const idx = params.uiWorkflow.edges.findIndex(item => item.source === params.source && item.sourceHandle === params.sourceHandle)
  if (idx > -1)
    params.uiWorkflow.edges.splice(idx, 1)

  // T3-03：连线按数据流方向/类型着色
  const sourceNode = params.workflow.nodes.find(node => node.uuid === params.source)
  const visual = getEdgeVisual(sourceNode?.wfComponent?.name || '', params.sourceHandle)
  const uiEdge = {
    id: wfEdge.uuid,
    source: wfEdge.sourceNodeUuid,
    target: wfEdge.targetNodeUuid,
    animated: visual.animated,
    style: buildEdgeStyle(visual),
    sourceHandle: params.sourceHandle,
  }
  params.uiWorkflow.edges.push(uiEdge)
  // T3-05：断线/改向进历史栈
  pushWorkflowHistory(params.workflow)
}

export function deleteEdgesBySource(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
) {
  const edges = workflow.edges.filter(edge => edge.sourceNodeUuid === source)
  edges.forEach((element) => {
    const edgeIndex = workflow.edges.findIndex((edge) => {
      const hit = edge.uuid === element.uuid
      if (hit)
        workflow.deleteEdges.push(edge.uuid)

      return hit
    })
    if (edgeIndex !== -1)
      workflow.edges.splice(edgeIndex, 1)

    const uiEdgeIndex = uiWorkflow.edges.findIndex(edge => edge.uuid === element.uuid)
    if (uiEdgeIndex !== -1)
      uiWorkflow.edges.splice(edgeIndex, 1)
  })
  // T3-05：入口埋点 —— 断线进历史栈
  if (edges.length > 0)
    pushWorkflowHistory(workflow)
}

export function deleteEdgesBySourceHandle(
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
  sourceHandle: string,
) {
  const edgeIndex = workflow.edges.findIndex((edge) => {
    const hit = edge.sourceNodeUuid === source && edge.sourceHandle === sourceHandle
    if (hit)
      workflow.deleteEdges.push(edge.uuid)

    return hit
  })
  if (edgeIndex !== -1)
    workflow.edges.splice(edgeIndex, 1)

  const uiEdgeIndex = uiWorkflow.edges.findIndex(edge => edge.sourceNodeUuid === source && edge.sourceHandle === sourceHandle)
  if (uiEdgeIndex !== -1)
    uiWorkflow.edges.splice(edgeIndex, 1)
}

export function wfNodeToUiNode(node: Workflow.WorkflowNode) {
  const newNode = {
    id: node.uuid,
    type: node.wfComponent.name.toLowerCase(),
    data: node,
    position: {
      x: node.positionX,
      y: node.positionY,
    },
  }
  return newNode
}

function createSwitcherNode(workflow: Workflow.WorkflowInfo, node: Workflow.WorkflowNode) {
  const startNode = workflow.nodes.find(item => item.wfComponent.name === 'Start')
  if (!startNode)
    throw new Error('Start node not found')

  const firstInput = startNode.inputConfig.user_inputs[0]
  node.nodeConfig = {
    default_target_node_uuid: '',
    cases: [
      {
        uuid: uuidv4().replace(/-/g, ''),
        operator: 'and',
        target_node_uuid: '',
        conditions:
          [
            { node_uuid: startNode.uuid, node_param_name: firstInput.name, operator: 'contains', value: '' },
          ],
      },
      {
        uuid: uuidv4().replace(/-/g, ''),
        operator: 'and',
        target_node_uuid: '',
        conditions:
          [
            { node_uuid: startNode.uuid, node_param_name: firstInput.name, operator: 'contains', value: '' },
          ],
      },
    ],
  }
}

function createClassifierNode(node: Workflow.WorkflowNode) {
  const appStore = useAppStore()
  const first = appStore.getFirstLLM('text')
  node.nodeConfig = {
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
    categories: [
      {
        category_uuid: uuidv4().replace(/-/g, ''),
        category_name: '',
        target_node_uuid: '',
      },
      {
        category_uuid: uuidv4().replace(/-/g, ''),
        category_name: '',
        target_node_uuid: '',
      },
    ],
  }
}

function createAnswer(node: Workflow.WorkflowNode) {
  const appStore = useAppStore()
  const first = appStore.getFirstLLM('text')
  node.nodeConfig = {
    prompt: '',
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
  }
}

function createKeywordExtractor(node: Workflow.WorkflowNode) {
  const appStore = useAppStore()
  const first = appStore.getFirstLLM('text')
  node.nodeConfig = {
    top_n: 5,
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
  }
}

function createFaqExtractor(node: Workflow.WorkflowNode) {
  const appStore = useAppStore()
  const first = appStore.getFirstLLM('text')
  node.nodeConfig = {
    top_n: 5,
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
  }
}

function createKnowledgeRetrieval(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    knowledge_base_uuid: '',
    knowledge_base_name: '',
    score: 0.6,
    top_n: 3,
    is_strict: true,
    default_response: '',
  }
}

function createOpenAiImage(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    prompt: '',
    size: '1024x1024',
    quality: 'standard',
  }
}

function createTongyiwanx(node: Workflow.WorkflowNode) {
  const appStore = useAppStore()
  const opts = appStore.imageModels.filter((item) => {
    if (item.modelName.includes('wanx-background'))
      return false

    return item.modelPlatform === 'dashscope'
  })
  node.nodeConfig = {
    model_name: opts.length > 0 ? opts[0].modelName : '',
    prompt: '',
    size: '1024*1024',
    seed: -1,
  }
}

function createGoogle(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    query: '',
    country: 'cn',
    language: 'zh-cn',
    top_n: 5,
  }
}

function createHumanFeedback(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    tip: '',
  }
}

function createMailSend(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    sender_type: 1,
    cc_mails: '',
    to_mails: '',
    subject: '',
    content: '',
    smtp: {
      host: '',
      port: 465,
    },
    sender: {
      name: '',
      mail: '',
      password: '',
    },
  }
}

function createHttpRequest(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    method: 'GET',
    url: '',
    content_type: 'text/plain', // text/plain, application/json, application/x-www-form-urlencoded, multipart/form-data
    headers: [{ name: 'Accept', value: '*/*' }, { name: 'Cache-Control', value: 'no-cache' }, { name: 'Connection', value: 'keep-alive' }],
    params: [],
    text_body: '',
    json_body: {},
    form_data_body: [],
    form_urlencoded_body: [],
    body: {},
    timeout: 10,
    retry_times: 0,
    clear_html: false,
  }
}

function createAgent(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    character_uuid: '',
    enable_rag: true,
    enable_mcp: true,
    enable_web_search: false,
  }
}

// 数据库节点：默认输出文本、最多 100 行、不替换占位符
function createDatabase(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    datasource_uuid: '',
    sql: '',
    output_format: 'text',
    limit: 100,
    replace_input: false,
  }
}

// MCP 工具节点：默认不限定服务（= 当前用户全部已启用服务），入参为空 JSON
function createMcp(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    mcp_ids: [],
    tool_name: '',
    arguments: '{}',
    output_format: 'text',
  }
}

// 技能节点：默认读取技能指令正文（不需要脚本）
function createSkill(node: Workflow.WorkflowNode) {
  node.nodeConfig = {
    skill_uuid: '',
    skill_name: '',
    mode: 'instruction',
    script: '',
    args: '',
  }
}

export function getInputLabelFromParamName(workflow: Workflow.WorkflowInfo, nodeUuid: string, nodeParamName: string) {
  const node = workflow.nodes.find(node => node.uuid === nodeUuid)
  if (!node)
    return ''

  if (node.wfComponent.name === 'Start') {
    const inputConfig = node.inputConfig
    const input = inputConfig.user_inputs.find(input => input.name === nodeParamName)
    if (input)
      return input.title
  } else {
    return node.title
  }
}

export function getNameByInputType(type: number) {
  switch (type) {
    case 1:
      return '文本'
    case 2:
      return '数字'
    case 3:
      return '下拉选项'
    case 4:
      return '文件列表'
    default:
      return 'Unknown'
  }
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
 * - 条件分支（Switcher/Classifier 的分支）：琥珀色 + 流动动画
 * - 条件默认分支：灰色虚线（静态，表示兜底流向）
 * - 普通数据流：石板蓝，静态实线
 */
export interface EdgeVisual {
  color: string
  animated: boolean
  dashed: boolean
}

export function getEdgeVisual(sourceComponentName: string, sourceHandle?: string): EdgeVisual {
  const name = (sourceComponentName || '').toLowerCase()
  if (name === 'switcher' || name === 'classifier') {
    if (sourceHandle === 'default_handle')
      return { color: '#9ca3af', animated: false, dashed: true }

    return { color: '#f59e0b', animated: true, dashed: false }
  }
  return { color: '#64748b', animated: false, dashed: false }
}

export function buildEdgeStyle(visual: EdgeVisual): Record<string, string | number> {
  const style: Record<string, string | number> = { stroke: visual.color, strokeWidth: 1.5 }
  if (visual.dashed)
    style.strokeDasharray = '6 4'

  return style
}

/**
 * 由业务边对象构建 UI 边（T3-03：按数据流方向/类型着色，替代原全局 animated:true）
 */
export function buildUiEdge(wfEdge: Workflow.WorkflowEdge, sourceNode?: Workflow.WorkflowNode) {
  const visual = getEdgeVisual(sourceNode?.wfComponent?.name || '', wfEdge.sourceHandle || '')
  return {
    id: wfEdge.uuid,
    source: wfEdge.sourceNodeUuid,
    target: wfEdge.targetNodeUuid,
    type: 'special',
    animated: visual.animated,
    style: buildEdgeStyle(visual),
    sourceHandle: wfEdge.sourceHandle ? wfEdge.sourceHandle : undefined,
    data: wfEdge,
  }
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
