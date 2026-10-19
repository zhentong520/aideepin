declare namespace Workflow {
  import type { Node, Edge } from '@vue-flow/core'

  interface WorkflowComponent {
    id: string
    uuid: string
    name: string
    title: string
    remark: string
    displayOrder: number
    isEnable: boolean
  }

  interface WorkflowNode {
    id: string
    uuid: string
    workflowId: string
    workflowComponentId: string
    title: string
    remark: string
    inputConfig: { user_inputs: NodeIODefinition[], ref_inputs: NodeIORefDinition[] }
    nodeConfig: NodeConfig
    outputConfig: object
    positionX: number
    positionY: number

    workflowUuid: string
    wfComponent: WorkflowComponent
    sourceHandleIds: string[]
    targetHandleIds: string[]
  }

  interface WorkflowEdge {
    id: string
    uuid: string
    workflowId: string
    sourceNodeUuid: string
    sourceHandle: string
    targetNodeUuid: string

    workflowUuid: string
  }

  // 工作流信息
  interface WorkflowInfo {
    id: string
    uuid: string
    title: string
    remark: string
    isPublic: boolean
    nodes: WorkflowNode[]
    edges: WorkflowEdge[]
    userId: string
    userUuid: string
    userName: string
    createTime: string

    deleteNodes: string[]
    deleteEdges: string[]
  }

  interface WorkflowUpdateReq {
    uuid: string
    nodes: WorkflowNode[]
    edges: WorkflowEdge[]
    deleteNodes: string[]
    deleteEdges: string[]
  }

  // 工作流运行时信息
  interface WorkflowRuntime {
    id: string
    uuid: string
    workflowId: string
    input: any
    output: any
    status: number
    statusRemark: string
    createTime: string
    loading: boolean

    // Aggregated metrics across all nodes of this run (input/output tokens from LLM-typed nodes, total duration in ms)
    inputTokens?: number
    outputTokens?: number
    duration?: number

    wfUuid: string
    nodes: WfRuntimeNode[]
  }

  interface WfRuntimeNode {
    id: string
    uuid: string
    workflowRuntimeId: string
    nodeId: string
    //json object
    input: any
    //json object
    output: any
    status: number
    statusRemark: string
    createTime: string
    duration: number | null
    metadata: AnyNodeMetrics | null

    wfComponent: WorkflowComponent
    wfRuntimeUuid: string
    nodeUuid: string
    nodeTitle: string
  }

  // Runtime-level aggregated metrics snapshot (terminal): tokens from LLM-typed nodes, total duration
  interface RuntimeMetrics {
    inputTokens?: number
    outputTokens?: number
    duration?: number
  }

  // 节点执行可观测指标（基类） | Node execution observability metrics (base)
  interface NodeExecutionMetrics {
    type?: string
    durationMs?: number
  }

  // LLM 节点指标 | LLM node metrics
  interface LLMMetrics extends NodeExecutionMetrics {
    type: 'llm'
    inputTokens?: number
    outputTokens?: number
    modelName?: string
    modelPlatform?: string
  }

  // 图片生成节点指标 | Image generation node metrics
  interface ImageMetrics extends NodeExecutionMetrics {
    type: 'image'
    imageModelName?: string
    imageSize?: string
  }

  // HTTP 请求节点指标 | HTTP request node metrics
  interface HttpRequestMetrics extends NodeExecutionMetrics {
    type: 'http_request'
    httpStatusCode?: number
    httpMethod?: string
  }

  // 搜索节点指标 | Search node metrics
  interface SearchMetrics extends NodeExecutionMetrics {
    type: 'search'
    searchResultCount?: number
  }

  // 知识检索节点指标 | Knowledge retrieval node metrics
  interface KnowledgeRetrievalMetrics extends NodeExecutionMetrics {
    type: 'knowledge_retrieval'
    retrievalCount?: number
  }

  // 邮件发送节点指标 | Mail send node metrics
  interface MailMetrics extends NodeExecutionMetrics {
    type: 'mail'
    recipientCount?: number
    sendSuccess?: boolean
  }

  // 文档提取节点指标 | Document extractor node metrics
  interface DocumentMetrics extends NodeExecutionMetrics {
    type: 'document'
    fileCount?: number
    extractedCharCount?: number
  }

  // Agent 节点指标（在 LLMMetrics 字段之上扩展 RAG / Character 信息） | Agent node metrics
  interface AgentMetrics extends Omit<LLMMetrics, 'type'> {
    type: 'agent'
    retrievalCount?: number
    characterUuid?: string
  }

  // MCP 工具节点指标 | MCP tool node metrics
  interface McpMetrics extends NodeExecutionMetrics {
    type: 'mcp'
    mcpTitle?: string
    toolName?: string
    resultLength?: number
  }

  // 技能节点指标 | Skill node metrics
  interface SkillMetrics extends NodeExecutionMetrics {
    type: 'skill'
    skillName?: string
    mode?: string
    script?: string
    resultLength?: number
  }

  type AnyNodeMetrics = LLMMetrics | AgentMetrics | ImageMetrics | HttpRequestMetrics
    | SearchMetrics | KnowledgeRetrievalMetrics | MailMetrics | DocumentMetrics
    | McpMetrics | SkillMetrics

  interface WorkflowState {
    showCreateOrEditView: boolean
    createOrEditWfUuid: string
    selectedType: string
    activeWorkflowInfo: WorkflowInfo
    wfUuidToUIWorkflow: Map<string, UIWorkflow>
    activeUuid: string
    wfComponents: WorkflowComponent[]
    myWorkflows: WorkflowInfo[]
    publicWorkflows: WorkflowInfo[]
    loadingMyWorkflows: boolean,
    loadingPublicWorkflows: boolean,
    wfUuidToWfRuntimeLoading: Map<string, boolean>
    wfUuidToWfRuntimes: Map<string, WorkflowRuntime[]>
    operators: Operator[]
    submitting: boolean
  }

  interface InfoListResp {
    total: number,
    records: WorkflowInfo[]
  }

  interface WfRuntimesResp {
    total: number,
    records: WorkflowRuntime[]
  }

  interface NodeIODefinition {
    uuid: string
    type: number
    name: string
    title: string
    required: boolean

    //type === files
    limit: number
    //type === options
    multiple: boolean
  }

  //引用类型的输入输出定义
  interface NodeIORefDinition {
    uuid: string
    name: string
    node_param_name: string
    node_uuid: string
  }

  interface UserInput {
    uuid: string
    name: string
    content: UserInputContent

    required: boolean
  }

  interface UserInputContent {
    type: number
    value: any
    title: string
  }

  interface NodeIOData {
    title: string
    type: number
    value: any
  }

  interface NodeConfig {
  }

  interface NodeConfigStart implements NodeConfig {
  }

  interface NodeConfigAnswer implements NodeConfig {
    model_platform: string
    model_name: string
    prompt: string
  }

  //Classifier node
  interface NodeConfigClassifier implements NodeConfig {
    model_platform: string
    model_name: string
    categories: NodeConfigClassifierCategory[]
  }

  interface NodeConfigClassifierCategory {
    target_node_uuid: string
    category_uuid: string
    category_name: string
  }

  //Switcher node
  interface NodeConfigSwitcher implements NodeConfig {
    default_target_node_uuid: string
    cases: NodeConfigSwitcherCase[]
  }

  interface NodeConfigTemplate implements NodeConfig {
    template: string
  }

  interface NodeConfigEnd implements NodeConfig {
    result: string
  }

  interface NodeConfigKnowledgeRetrieval implements NodeConfig {
    knowledge_base_uuid: string
    knowledge_base_name: string
    /** T3-12：留空（键不存在）= 继承知识库级配置 */
    score?: number | null
    top_n?: number | null
    /** T3-12：重排开关，'' 继承 / 'true' / 'false' */
    rerank?: string
    /** T3-12：上下文扩窗开关，'' 继承 / 'true' / 'false' */
    expand_window?: string
    is_strict: boolean
    default_response: string
  }

  interface NodeConfigSwitcherCase {
    uuid: string
    operator: string
    target_node_uuid: string
    conditions: NOdeConfigCaseCondition[]
  }

  interface NodeConfigSwitcherCaseCondition {
    uuid: string
    node_uuid: string
    node_param_name: string
    operator: string
    value: string
  }

  interface NodeConfigKeywordExtractor implements NodeConfig {
    model_platform: string
    model_name: string
    top_n: number
  }

  interface NodeConfigFaqExtractor implements NodeConfig {
    model_platform: string
    model_name: string
    top_n: number
  }

  interface NodeConfigGoogleSearch implements NodeConfig {
    query: string
    country: string
    language: string
    top_n: number
  }

  interface NodeConfigOpenAiImage implements NodeConfig {
    prompt: string
    size: string
    quality: string
  }

  interface NodeConfigTongyiwanx implements NodeConfig {
    model_name: string
    prompt: string
    size: string
    seed: number
  }

  interface NodeConfigHumanFeedback implements NodeConfig {
    tip: string
  }

  interface NodeConfigMailSend implements NodeConfig {
    sender_type: number
    cc_mails: string
    to_mails: string
    subject: string
    content: string
    smtp: {
      host: string
      port: number
    }
    sender: {
      name: string
      mail: string
      password: string
    }
  }

  interface NodeConfigHttpRequestParam {
    name: string
    value: string
  }

  interface NodeConfigHttpRequest implements NodeConfig {
    method: string
    url: string
    headers: NodeConfigHttpRequestParam[]
    params: NodeConfigHttpRequestParam[]
    text_body: string
    json_body: object
    form_data_body: NodeConfigHttpRequestParam[]
    form_urlencoded_body: NodeConfigHttpRequestParam[]
    content_type: string
    timeout: number
    retry_times: number
    clear_html: boolean
  }

  // Agent node
  interface NodeConfigAgent implements NodeConfig {
    character_uuid: string
    model_platform?: string
    model_name?: string
    prompt?: string
    enable_rag?: boolean
    enable_mcp?: boolean
    enable_web_search?: boolean
  }

  // 数据库节点 | Database node
  interface NodeConfigDatabase implements NodeConfig {
    datasource_uuid: string
    sql: string
    // text | json
    output_format: string
    limit: number
    replace_input: boolean
  }

  // MCP 工具节点 | MCP tool node
  interface NodeConfigMcp implements NodeConfig {
    // 候选 MCP 服务 id；留空 = 当前用户全部已启用服务
    mcp_ids?: Array<string | number>
    tool_name: string
    // 工具入参 JSON 模板，支持 {name} 占位符
    arguments: string
    // text | json
    output_format: string
  }

  // 技能节点 | Skill node
  interface NodeConfigSkill implements NodeConfig {
    skill_uuid?: string
    skill_name?: string
    // instruction | script
    mode: string
    script?: string
    args?: string
  }

  // MCP 服务暴露的工具（/user/mcp/tools 返回） | Tool exposed by an MCP server
  interface McpToolInfo {
    mcpId: string
    mcpTitle: string
    toolName: string
    description: string
    parameterSchema: string
  }

  // 外部数据库数据源（Database MCP） | External database datasource
  interface DbDatasource {
    id: string
    uuid: string
    title: string
    dbType: string
    host: string
    port: number
    dbName: string
    username: string
    password: string
    jdbcParams: string
    remark: string
    isEnable: boolean
  }

  interface NodeIOData {
    name: string
    value: any
  }

  interface Operator {
    name: string
    desc: string
  }

  interface UIWorkflow {
    nodes: Node[]
    edges: Edge[]
  }

  interface InputLabel {
    label: string
    value: string
  }

  // ==================== T3-13 变量穿梭框 / 模板校验 ====================

  /** 指定节点可用变量（GET /workflow/available-vars），对象类型递归展开 children 2 层 */
  interface WfAvailableVar {
    name: string
    sourceNodeUuid?: string
    sourceNodeTitle?: string
    sourceParamName?: string
    /** text / number / bool / files / options / object / array / unknown */
    type: string
    example?: unknown
    children?: WfAvailableVar[]
  }

  /** 模板校验响应（POST /workflow/validate-template），errors 非空即校验失败 */
  interface WfTemplateValidateResp {
    valid: boolean
    errors: WfVarError[]
  }

  interface WfVarError {
    /** 出错的引用表达式，如 resp.data.id */
    ref: string
    /** 错误码：A0077 根变量不存在 / A0078 字段路径不存在 / A0079 导入知识包缺失 */
    code: string
    /** 人类可读原因（后端已 i18n） */
    message: string
  }

  // ==================== T3-14 导出 / 导入 ====================

  interface WfExportReq {
    version?: number
    title: string
    remark: string
    nodes: Array<Record<string, unknown>>
    edges: Array<Record<string, unknown>>
  }
}