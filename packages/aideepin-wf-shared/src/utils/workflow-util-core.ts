/**
 * OPT-16：工作流设计器核心纯逻辑（原 admin wf/utils/workflow-util.ts、user
 * views/workflow/utils/workflow-util.ts 两端同源实现 → 单一来源迁入共享包）。
 *
 * 依赖注入模式（对齐 SvgIcon.iconComp 先例）：端特有基础设施由薄壳经 hooks 注入——
 * - getFirstLLM / listImageModels：admin @/wf/stores、user @/store/modules/app 的模型选择
 * - getNodeTitle：admin `t('workflow.componentTitle.x')`、user descriptor 单一来源
 * - onNodeOrEdgeChanged（T3-05 历史栈埋点）：仅 user 注入，admin 传空
 * admin 端 T3-03 连线着色 / T3-06 连线校验为 user 增强：createWorkflowEdge / updateEdgeBySourceHandle
 * 的可选参数 opened 支持两端各自行为（admin 保持旧语义，user 保持增强语义）。
 *
 * 零外部包依赖铁律：uuid 包 → 本包 utils/uuid 的 newUuid()（等价实现）。
 */

import { newUuid } from '../utils/uuid'
import { emptyWorkflowNode } from '../functions/empty'

// ========== 端特有基础设施注入点 ==========

/** 与 admin appStore.getFirstLLM('text') / user store getter getFirstLLM 同形 */
export type GetFirstLLM = (type?: string) => { modelPlatform: string; modelName: string } | undefined

/** 与 admin appStore.imageModels 同形（取 image 模型列表） */
export type ListImageModels = () => { modelName: string; modelPlatform: string }[]

/** 节点标题解析：admin 走 i18n t()，user 走 descriptor.ts 单一来源 */
export type GetComponentTitle = (componentName: string) => string

/** 节点/连线变更埋点（user T3-05 历史栈）；admin 传 no-op */
export type NotifyChange = (workflow: Workflow.WorkflowInfo) => void

export interface WfUtilHooks {
  getFirstLLM: GetFirstLLM
  listImageModels: ListImageModels
  getComponentTitle: GetComponentTitle
  onNodeOrEdgeChanged: NotifyChange
}

export const noNotify: NotifyChange = () => {}

// ========== 节点创建 ==========

export function createNewNode(
  hooks: WfUtilHooks,
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  component: Workflow.WorkflowComponent,
  position: { x: number; y: number },
) {
  const newWfNode = emptyWorkflowNode()
  newWfNode.uuid = newUuid()
  newWfNode.title = hooks.getComponentTitle(component.name)
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
    createClassifierNode(hooks, newWfNode)
  else if (component.name === 'Answer')
    createAnswer(hooks, newWfNode)
  else if (component.name === 'Switcher')
    createSwitcherConfig(workflow, newWfNode)
  else if (component.name === 'KeywordExtractor')
    createKeywordExtractor(hooks, newWfNode)
  else if (component.name === 'FaqExtractor')
    createFaqExtractor(hooks, newWfNode)
  else if (component.name === 'KnowledgeRetrieval')
    createKnowledgeRetrieval(newWfNode)
  else if (component.name === 'OpenAiImage')
    createOpenAiImage(newWfNode)
  else if (component.name === 'Tongyiwanx')
    createTongyiwanx(hooks, newWfNode)
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
}

// ========== 连线 ==========

/**
 * 创建连线。
 * - admin 语义（strict=false）：无校验、uiEdge 全局 animated:true、返回 void
 * - user 语义（strict=true）：T3-06 自环/重复拦截 + T3-03 着色 + T3-05 埋点，返回是否创建成功
 */
export function createWorkflowEdge(
  hooks: WfUtilHooks,
  params: {
    workflow: Workflow.WorkflowInfo
    uiWorkflow: Workflow.UIWorkflow
    source: string
    sourceHandle: string
    target: string
  },
  options?: { strict?: boolean },
): boolean {
  const strict = options?.strict ?? false

  if (strict) {
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
  }

  const wfEdge = {
    id: '',
    uuid: newUuid(),
    workflowId: params.workflow.id,
    sourceNodeUuid: params.source,
    sourceHandle: params.sourceHandle,
    targetNodeUuid: params.target,
    workflowUuid: params.workflow.uuid,
  }
  params.workflow.edges.push(wfEdge)
  if (params.target) {
    if (strict) {
      // T3-03：连线按数据流方向/类型着色
      const sourceNode = params.workflow.nodes.find(node => node.uuid === params.source)
      params.uiWorkflow.edges.push(buildUiEdge(wfEdge, sourceNode))
    } else {
      const uiEdge = {
        id: wfEdge.uuid,
        source: wfEdge.sourceNodeUuid,
        target: wfEdge.targetNodeUuid,
        type: 'special',
        animated: true,
        sourceHandle: params.sourceHandle ? params.sourceHandle : undefined,
        data: wfEdge,
      }
      params.uiWorkflow.edges.push(uiEdge)
    }
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
  if (strict)
    hooks.onNodeOrEdgeChanged(params.workflow)

  return strict ? true : false as boolean
}

// 连线按数据流方向/类型着色（T3-03，user 语义）
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

/** 由业务边对象构建 UI 边（T3-03：按数据流方向/类型着色） */
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

/** 断线/改向（user 版含 T3-03 着色与 T3-05 埋点；admin 版无着色无埋点） */
export function updateEdgeBySourceHandle(
  hooks: WfUtilHooks,
  params: {
    workflow: Workflow.WorkflowInfo
    uiWorkflow: Workflow.UIWorkflow
    source: string
    sourceHandle: string
    target: string
  },
  options?: { strict?: boolean },
) {
  const strict = options?.strict ?? false
  const wfEdge = params.workflow.edges.find(item => item.sourceHandle === params.sourceHandle)
  if (!wfEdge) {
    console.warn('no edge found for sourceHandle')
    return
  }
  wfEdge.targetNodeUuid = params.target
  const idx = params.uiWorkflow.edges.findIndex(item => item.source === params.source && item.sourceHandle === params.sourceHandle)
  if (idx > -1)
    params.uiWorkflow.edges.splice(idx, 1)

  if (strict) {
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
    hooks.onNodeOrEdgeChanged(params.workflow)
  } else {
    const uiEdge = {
      id: wfEdge.uuid,
      source: wfEdge.sourceNodeUuid,
      target: wfEdge.targetNodeUuid,
      animated: true,
      sourceHandle: params.sourceHandle,
    }
    params.uiWorkflow.edges.push(uiEdge)
  }
}

/** 按源节点删边（user 版删除后 T3-05 埋点） */
export function deleteEdgesBySource(
  hooks: WfUtilHooks,
  workflow: Workflow.WorkflowInfo,
  uiWorkflow: Workflow.UIWorkflow,
  source: string,
  options?: { strict?: boolean },
) {
  const strict = options?.strict ?? false
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
  if (strict && edges.length > 0)
    hooks.onNodeOrEdgeChanged(workflow)
}

/** 按源节点+把手删边（两端语义一致，无埋点） */
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

// ========== 节点默认配置（按组件名） ==========

function createClassifierNode(hooks: WfUtilHooks, node: Workflow.WorkflowNode) {
  const first = hooks.getFirstLLM('text')
  node.nodeConfig = {
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
    categories: [
      {
        category_uuid: newUuid(),
        category_name: '',
        target_node_uuid: '',
      },
      {
        category_uuid: newUuid(),
        category_name: '',
        target_node_uuid: '',
      },
    ],
  }
}

function createAnswer(hooks: WfUtilHooks, node: Workflow.WorkflowNode) {
  const first = hooks.getFirstLLM('text')
  node.nodeConfig = {
    prompt: '',
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
  }
}

function createKeywordExtractor(hooks: WfUtilHooks, node: Workflow.WorkflowNode) {
  const first = hooks.getFirstLLM('text')
  node.nodeConfig = {
    top_n: 5,
    model_platform: first?.modelPlatform || '',
    model_name: first?.modelName || '',
  }
}

function createFaqExtractor(hooks: WfUtilHooks, node: Workflow.WorkflowNode) {
  const first = hooks.getFirstLLM('text')
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

function createTongyiwanx(hooks: WfUtilHooks, node: Workflow.WorkflowNode) {
  const opts = hooks.listImageModels().filter((item) => {
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

/** Switcher 默认配置：需要 workflow 上下文找 Start 节点取首个用户入参（两端实现一致） */
export function createSwitcherConfig(workflow: Workflow.WorkflowInfo, node: Workflow.WorkflowNode) {
  const startNode = workflow.nodes.find(item => item.wfComponent.name === 'Start')
  if (!startNode)
    throw new Error('Start node not found')

  const firstInput = startNode.inputConfig.user_inputs[0]
  node.nodeConfig = {
    default_target_node_uuid: '',
    cases: [
      {
        uuid: newUuid(),
        operator: 'and',
        target_node_uuid: '',
        conditions:
          [
            { node_uuid: startNode.uuid, node_param_name: firstInput.name, operator: 'contains', value: '' },
          ],
      },
      {
        uuid: newUuid(),
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

// ========== 查询助手（两端语义一致） ==========

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
