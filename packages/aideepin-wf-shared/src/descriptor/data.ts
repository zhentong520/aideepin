/**
 * S2-07（ADR-005）步骤①：节点描述单源下沉（自 user-web descriptor.ts 机械平移）。
 *
 * 本文件是两端共用的节点"定义源头"：25 个节点描述数据（不含画布/属性
 * 组件引用——组件是 Vue 端实现，由两端薄壳各自绑定）、内置模板、查找助手。
 * 新增一种节点：在 wfNodeDescriptorData 加一条数据 + 两端组件映射各加一行。
 *
 * 冲突处理约定（任务卡）：两端 diff 逐项比对，冲突处取 user-web 版（本文件即 user-web 版）。
 * ⭐ 零外部包依赖铁律沿用（见 src/index.ts 头注）：本文件只依赖同包 types.ts。
 */
import type { WfDescriptorFieldOption, WfLocalizedText, WfNodeDescriptorData, WfNodePreset } from './types'

// ==================== T3-02 七类色条（与 workflow-util.getNodeColorByComponentName 对齐） ====================
const COLOR_TRIGGER = '#3b82f6' // 开始：蓝
const COLOR_LLM = '#8b5cf6' // LLM：紫
const COLOR_RETRIEVAL = '#14b8a6' // 知识库：青
const COLOR_FLOW = '#f59e0b' // 条件：琥珀
const COLOR_OUTPUT = '#f43f5e' // 输出/结束：玫红
const COLOR_ACTION = '#64748b' // 插件/工具：石板灰

const METRIC_TOKENS = ['inputTokens', 'outputTokens', 'modelName']

const TIP_EMPTY_USE_PREVIOUS: WfLocalizedText = {
  zh: '为空则使用上一个节点的输出',
  en: 'Leave empty to use the previous node output',
}

// T9-02/03 专用提示
const TIP_EMPTY_USE_PREVIOUS_ITEM: WfLocalizedText = {
  zh: '支持 {item}（当前项）、{index}（下标）与上游变量；留空则逐项输出数组元素',
  en: 'Supports {item}, {index} and upstream vars; empty = output each item verbatim',
}
const TIP_INHERIT_ARRAY_PARAM: WfLocalizedText = {
  zh: '承载 JSON 数组的输入参数名，留空使用默认输入',
  en: 'Input param holding the JSON array; empty = default input',
}
const TIP_BRANCHES_JSON: WfLocalizedText = {
  zh: 'JSON 数组，如 [{{"name":"a","template":"处理:{input}","delay_ms":0}}]',
  en: 'JSON array, e.g. [{{"name":"a","template":"process:{input}","delay_ms":0}}]',
}

const RERANK_OPTIONS: WfDescriptorFieldOption[] = [
  { label: { zh: '继承知识库级', en: 'Inherit KB level' }, value: '' },
  { label: { zh: '开启', en: 'On' }, value: 'true' },
  { label: { zh: '关闭', en: 'Off' }, value: 'false' },
]

const TIP_INHERIT_KB: WfLocalizedText = {
  zh: '留空则继承知识库级配置',
  en: 'Leave empty to inherit knowledge-base level config',
}

export const wfNodeDescriptorData: WfNodeDescriptorData[] = [
  {
    type: 'Start',
    icon: 'carbon:play-outline',
    iconClass: 'text-blue-900',
    category: 'trigger',
    color: COLOR_TRIGGER,
    // 保留自定义：入参表格编辑器（增删改查 + 4 种类型），configSchema 表达成本高于收益
    customPanel: true,
    componentTitle: { zh: '开始', en: 'Start' },
    metrics: [],
    configSchema: [
      { name: 'user_inputs', label: { zh: '自定义入参', en: 'User inputs' }, type: 'userInputs', group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '接收用户输入，作为整个流程的数据入口。',
        required: '按需配置入参变量（文本/数字/文件/布尔），部分流程可不放任何入参。',
        output: '各入参变量按变量名供下游节点以 {变量名} / {{变量名}} 引用。',
        errors: '下游引用了未定义的入参变量；文件类型入参未设置数量上限导致上传被拒。',
      },
      en: {
        purpose: 'Receives user input as the data entry of the whole workflow.',
        required: 'Configure input variables (text/number/file/boolean) as needed; some flows need none.',
        output: 'Each input variable is referenced by downstream nodes via {name} / {{name}}.',
        errors: 'Downstream references an undefined input variable; file upload rejected due to missing limit.',
      },
    },
    hasInput: false,
    hasOutput: true,
  },
  {
    type: 'End',
    icon: 'carbon:closed-caption',
    iconClass: 'text-orange-800',
    category: 'flow',
    color: COLOR_OUTPUT,
    componentTitle: { zh: '结束', en: 'End' },
    metrics: [],
    configSchema: [
      { name: 'result', label: { zh: '输出内容', en: 'Result' }, type: 'textarea', required: true, group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '汇总并输出流程最终结果，结束执行。',
        required: '输出内容（支持 {{变量}} 引用，留空自动填充默认文案）。',
        output: '以默认参数 output 输出渲染后的最终文本。',
        errors: '{{变量}} 根变量或字段路径不存在（A0077/A0078）导致渲染报错。',
      },
      en: {
        purpose: 'Assembles the final result and ends the workflow.',
        required: 'Result template (supports {{var}} refs; auto-fills a default text when empty).',
        output: 'Outputs the rendered text via the default param "output".',
        errors: 'Template rendering fails when a {{var}} root or field path does not exist (A0077/A0078).',
      },
    },
    hasInput: true,
    hasOutput: false,
  },
  {
    type: 'Answer',
    icon: 'carbon:question-answering',
    iconClass: 'text-green-800',
    category: 'llm',
    color: COLOR_LLM,
    componentTitle: { zh: '生成回答', en: 'Generate Answer' },
    metrics: METRIC_TOKENS,
    configSchema: [
      { name: 'prompt', label: { zh: '提示词', en: 'Prompt' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS },
      { name: 'model_name', label: { zh: '模型', en: 'Model' }, type: 'modelSelector', group: 'resource', pairsTo: 'model_platform' },
    ],
    doc: {
      zh: {
        purpose: '用大模型按提示词生成回答。',
        required: '模型；提示词可留空（=沿用上一节点输出）。',
        output: '默认参数 output：模型回答文本。',
        errors: '模型未配置或不可用；提示词中的变量引用错误；输入超出模型上下文限制。',
      },
      en: {
        purpose: 'Generates an answer with an LLM from the prompt.',
        required: 'Model; prompt may be empty (= use previous node output).',
        output: 'Default param "output": the model answer text.',
        errors: 'Model missing/unavailable; wrong variable refs in prompt; input exceeds context window.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Classifier',
    icon: 'carbon:type-pattern',
    iconClass: 'text-violet-900',
    category: 'llm',
    color: COLOR_LLM,
    // 保留自定义：分类项与分支连线一一对应（sourceHandle=category_uuid），面板需管理连线状态
    customPanel: true,
    componentTitle: { zh: '内容归类', en: 'Content Classifier' },
    metrics: METRIC_TOKENS,
    configSchema: [
      { name: 'model_name', label: { zh: '模型', en: 'Model' }, type: 'modelSelector', group: 'resource', pairsTo: 'model_platform' },
      { name: 'categories', label: { zh: '分类项', en: 'Categories' }, type: 'categoryList', group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '用大模型把输入归类到预定义分类，并按分类路由到不同分支。',
        required: '模型；至少两个分类项，且每个分类需连出分支线。',
        output: '沿命中的分类分支连线流动，输出为分类名文本。',
        errors: '分类名留空；某分类未连线导致流程中断；模型输出无法匹配任何分类。',
      },
      en: {
        purpose: 'Classifies input into predefined categories via LLM and routes per category.',
        required: 'Model; at least two categories, each wired to a branch.',
        output: 'Flows along the matched category branch; output is the category name.',
        errors: 'Empty category name; a category without a branch breaks the flow; model output matches nothing.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Switcher',
    icon: 'oui:logstash-if',
    iconClass: 'text-yellow-900',
    category: 'flow',
    color: COLOR_FLOW,
    // 保留自定义：多分支条件组合编辑器（与/或、引用变量、分支连线），结构化程度高
    customPanel: true,
    componentTitle: { zh: '条件分支', en: 'Conditional Branch' },
    metrics: [],
    configSchema: [
      { name: 'cases', label: { zh: '分支条件', en: 'Branch cases' }, type: 'conditionCases', group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '按条件表达式把流程路由到不同分支。',
        required: '至少一个分支条件并连线；建议配置保底分支。',
        output: '沿命中分支（或保底 default_handle）连线流动。',
        errors: '条件引用的变量不存在；所有分支都不匹配且未配置保底分支导致流程结束。',
      },
      en: {
        purpose: 'Routes the flow by conditional expressions.',
        required: 'At least one wired condition branch; a fallback branch is recommended.',
        output: 'Flows along the matched branch (or default_handle fallback).',
        errors: 'Condition references a missing variable; nothing matches and no fallback configured.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Template',
    icon: 'carbon:prompt-template',
    iconClass: 'text-sky-800',
    category: 'transform',
    color: COLOR_LLM,
    componentTitle: { zh: '模板转换', en: 'Template Transform' },
    metrics: [],
    configSchema: [
      { name: 'template', label: { zh: '模板内容', en: 'Template' }, type: 'textarea', required: true, group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '用模板把上游变量拼装成一段文本（不调用模型）。',
        required: '模板内容，支持 {{变量}} 与 {变量} 引用。',
        output: '默认参数 output：渲染后的文本。',
        errors: '{{变量}} 根变量不存在（A0077）或字段路径不存在（A0078）；渲染结果非文本。',
      },
      en: {
        purpose: 'Assembles upstream variables into a text via template (no model call).',
        required: 'Template content supporting {{var}} and {var} refs.',
        output: 'Default param "output": the rendered text.',
        errors: '{{var}} root not found (A0077) or field path not found (A0078).',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'KeywordExtractor',
    icon: 'carbon:api-key',
    iconClass: 'text-cyan-900',
    category: 'llm',
    color: COLOR_LLM,
    componentTitle: { zh: '关键词提取', en: 'Keyword Extractor' },
    metrics: METRIC_TOKENS,
    configSchema: [
      { name: 'top_n', label: { zh: '提取数量', en: 'Top N' }, type: 'number', required: true, group: 'required' },
      { name: 'model_name', label: { zh: '模型', en: 'Model' }, type: 'modelSelector', group: 'resource', pairsTo: 'model_platform' },
    ],
    doc: {
      zh: {
        purpose: '从输入文本中提取指定数量的关键词。',
        required: '模型与提取数量（1-30）。',
        output: '默认参数 output：逗号分隔的关键词文本。',
        errors: '模型未配置或调用失败；输入为空时输出为空。',
      },
      en: {
        purpose: 'Extracts N keywords from the input text.',
        required: 'Model and Top N (1-30).',
        output: 'Default param "output": comma-separated keywords.',
        errors: 'Model missing or call failed; empty input yields empty output.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'DocumentExtractor',
    icon: 'carbon:ibm-knowledge-catalog-standard',
    iconClass: 'text-rose-900',
    category: 'retrieval',
    color: COLOR_RETRIEVAL,
    // 保留自定义：面板内容取决于开始节点是否存在文件入参（动态提示），非纯配置表单
    customPanel: true,
    componentTitle: { zh: '文档提取', en: 'Document Extractor' },
    metrics: ['fileCount', 'extractedCharCount'],
    configSchema: [],
    doc: {
      zh: {
        purpose: '提取开始节点上传文件（TXT/PDF/DOC 等）中的文本内容。',
        required: '开始节点需配置文件类型的入参。',
        output: '每个文件对应一段提取后的文本。',
        errors: '开始节点没有文件入参；文件格式不支持或文件损坏。',
      },
      en: {
        purpose: 'Extracts text from files uploaded at the Start node (TXT/PDF/DOC etc.).',
        required: 'The Start node must have a file-type input.',
        output: 'One extracted text per file.',
        errors: 'No file input at Start; unsupported format or corrupted file.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'FaqExtractor',
    icon: 'fluent-mdl2:book-answers',
    iconClass: 'text-teal-600',
    category: 'llm',
    color: COLOR_LLM,
    componentTitle: { zh: '常见问题提取', en: 'FAQ Extractor' },
    metrics: METRIC_TOKENS,
    configSchema: [
      { name: 'top_n', label: { zh: '提取数量', en: 'Top N' }, type: 'number', required: true, group: 'required' },
      { name: 'model_name', label: { zh: '模型', en: 'Model' }, type: 'modelSelector', group: 'resource', pairsTo: 'model_platform' },
    ],
    doc: {
      zh: {
        purpose: '从输入文本中提取常见问答对（FAQ）。',
        required: '模型与提取数量（1-30）。',
        output: '默认参数 output：FAQ 问答对列表文本。',
        errors: '模型未配置或调用失败；输入为空时输出为空。',
      },
      en: {
        purpose: 'Extracts FAQ pairs from the input text.',
        required: 'Model and Top N (1-30).',
        output: 'Default param "output": FAQ list text.',
        errors: 'Model missing or call failed; empty input yields empty output.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'KnowledgeRetrieval',
    icon: 'carbon:connect-target',
    iconClass: 'text-stone-900',
    category: 'retrieval',
    color: COLOR_RETRIEVAL,
    componentTitle: { zh: '知识检索', en: 'Knowledge Retrieval' },
    metrics: ['retrievalCount'],
    configSchema: [
      { name: 'knowledge_base_uuid', label: { zh: '知识库', en: 'Knowledge base' }, type: 'kbSelector', required: true, group: 'resource', pairsTo: 'knowledge_base_name' },
      { name: 'top_n', label: { zh: '返回条数 (topK)', en: 'Top K' }, type: 'number', group: 'advanced', inheritable: true, tip: TIP_INHERIT_KB },
      { name: 'score', label: { zh: '最低分数 (minScore)', en: 'Min score' }, type: 'number', group: 'advanced', inheritable: true, tip: TIP_INHERIT_KB },
      {
        name: 'rerank',
        label: { zh: '重排', en: 'Rerank' },
        type: 'select',
        group: 'advanced',
        options: RERANK_OPTIONS,
        tip: TIP_INHERIT_KB,
      },
      {
        name: 'expand_window',
        label: { zh: '上下文扩窗', en: 'Context window expansion' },
        type: 'select',
        group: 'advanced',
        options: RERANK_OPTIONS,
        tip: TIP_INHERIT_KB,
      },
      { name: 'is_strict', label: { zh: '严格模式', en: 'Strict mode' }, type: 'switch', group: 'advanced' },
      { name: 'default_response', label: { zh: '兜底回复', en: 'Default response' }, type: 'textarea', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '从知识库召回与输入相关的片段，供下游节点引用。',
        required: '知识库；其余参数留空时继承知识库级配置。',
        output: '默认参数 output：拼接后的召回文本。',
        errors: '知识库不存在或已被删除；严格模式下无召回直接失败；分数过高导致召回为空。',
      },
      en: {
        purpose: 'Retrieves relevant chunks from a knowledge base for downstream nodes.',
        required: 'Knowledge base; other params inherit KB-level config when empty.',
        output: 'Default param "output": concatenated retrieved text.',
        errors: 'KB missing/deleted; strict mode fails when nothing retrieved; min score too high.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Google',
    icon: 'ri:google-line',
    iconClass: 'text-emerald-900',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: 'Google 搜索', en: 'Google Search' },
    metrics: ['searchResultCount'],
    configSchema: [
      { name: 'query', label: { zh: '搜索词', en: 'Query' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS },
      { name: 'top_n', label: { zh: '结果数量', en: 'Top N' }, type: 'number', group: 'required' },
      { name: 'country', label: { zh: '国家', en: 'Country' }, type: 'input', group: 'advanced' },
      { name: 'language', label: { zh: '语言', en: 'Language' }, type: 'input', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '调用 Google 搜索获取外部资料。',
        required: '搜索词可留空（=沿用上一节点输出）。',
        output: '默认参数 output：搜索结果列表文本。',
        errors: '系统未配置 Google API Key；搜索词为空且上游输出也为空。',
      },
      en: {
        purpose: 'Fetches external information via Google search.',
        required: 'Query may be empty (= use previous node output).',
        output: 'Default param "output": search result list text.',
        errors: 'Google API key not configured; empty query and empty upstream output.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'OpenAiImage',
    icon: 'solar:pallete-2-linear',
    iconClass: 'text-fuchsia-700',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: 'OpenAI 画图', en: 'OpenAI Image' },
    metrics: ['imageModelName', 'imageSize'],
    configSchema: [
      { name: 'prompt', label: { zh: '画图描述', en: 'Prompt' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS },
      {
        name: 'size',
        label: { zh: '尺寸', en: 'Size' },
        type: 'select',
        group: 'advanced',
        options: [
          { label: { zh: '1024x1024', en: '1024x1024' }, value: '1024x1024' },
          { label: { zh: '1024x1792', en: '1024x1792' }, value: '1024x1792' },
          { label: { zh: '1792x1024', en: '1792x1024' }, value: '1792x1024' },
        ],
      },
      {
        name: 'quality',
        label: { zh: '画质', en: 'Quality' },
        type: 'select',
        group: 'advanced',
        options: [
          { label: { zh: '标准', en: 'Standard' }, value: 'standard' },
          { label: { zh: '高清', en: 'HD' }, value: 'hd' },
        ],
      },
    ],
    doc: {
      zh: {
        purpose: '调用 OpenAI DALL·E 按描述生成图片。',
        required: '画图描述可留空（=沿用上一节点输出）。',
        output: '图片文件列表（可在运行详情预览）。',
        errors: '描述触发内容政策拦截；所选尺寸不被模型支持；API 额度不足。',
      },
      en: {
        purpose: 'Generates an image via OpenAI DALL·E from the prompt.',
        required: 'Prompt may be empty (= use previous node output).',
        output: 'Image file list (previewable in run details).',
        errors: 'Prompt blocked by content policy; unsupported size; API quota exceeded.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Tongyiwanx',
    icon: 'solar:pallete-2-linear',
    iconClass: 'text-fuchsia-700',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: '通义万相-画图', en: 'Wanx Image' },
    metrics: ['imageModelName', 'imageSize'],
    configSchema: [
      { name: 'model_name', label: { zh: '画图模型', en: 'Image model' }, type: 'imageModelSelector', group: 'resource' },
      { name: 'prompt', label: { zh: '画图描述', en: 'Prompt' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS },
      { name: 'size', label: { zh: '尺寸', en: 'Size' }, type: 'input', group: 'advanced' },
      { name: 'seed', label: { zh: '种子', en: 'Seed' }, type: 'number', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '调用通义万相（dashscope）按描述生成图片。',
        required: '画图模型与画图描述。',
        output: '图片文件列表（可在运行详情预览）。',
        errors: '未配置 dashscope 画图模型；seed 非法；内容触发平台审核。',
      },
      en: {
        purpose: 'Generates an image via Tongyi Wanx (dashscope).',
        required: 'Image model and prompt.',
        output: 'Image file list (previewable in run details).',
        errors: 'No dashscope image model; invalid seed; content blocked by platform review.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'HumanFeedback',
    icon: 'covid:transmission-virus-human-transmit-2',
    iconClass: 'text-zinc-800',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：唯一字段 tip 且无输入引用编辑器，走 schema 反而多渲染无关节
    customPanel: true,
    componentTitle: { zh: '人机交互', en: 'Human Feedback' },
    metrics: [],
    configSchema: [
      { name: 'tip', label: { zh: '提示文案', en: 'Tip' }, type: 'textarea', required: true, group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '暂停流程，等待人工输入后继续执行。',
        required: '提示文案（展示给处理人）。',
        output: '人工输入的文本。',
        errors: '长时间无人处理导致流程一直挂起。',
      },
      en: {
        purpose: 'Pauses the flow until a human provides input.',
        required: 'Tip text shown to the reviewer.',
        output: 'The human-provided text.',
        errors: 'Flow hangs indefinitely when nobody responds.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'MailSend',
    icon: 'carbon:mail-all',
    iconClass: 'text-amber-800',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: '邮件发送', en: 'Email Send' },
    metrics: ['sendSuccess'],
    configSchema: [
      { name: 'to_mails', label: { zh: '收件人', en: 'To' }, type: 'input', required: true, group: 'required', placeholder: { zh: '多个邮箱以逗号隔开', en: 'Comma separated' } },
      { name: 'subject', label: { zh: '主题', en: 'Subject' }, type: 'textarea', required: true, group: 'required' },
      { name: 'content', label: { zh: '正文', en: 'Content' }, type: 'textarea', required: true, group: 'required' },
      {
        name: 'sender_type',
        label: { zh: '发件类型', en: 'Sender type' },
        type: 'select',
        group: 'advanced',
        options: [
          { label: { zh: '系统邮箱', en: 'System mailbox' }, value: 1 },
          { label: { zh: '自定义 SMTP', en: 'Custom SMTP' }, value: 2 },
        ],
      },
      { name: 'cc_mails', label: { zh: '抄送', en: 'Cc' }, type: 'input', group: 'advanced' },
      { name: 'smtp.host', label: { zh: 'SMTP 主机', en: 'SMTP host' }, type: 'input', group: 'advanced', showWhen: [{ field: 'sender_type', equals: 2 }] },
      { name: 'smtp.port', label: { zh: 'SMTP 端口', en: 'SMTP port' }, type: 'number', group: 'advanced', showWhen: [{ field: 'sender_type', equals: 2 }] },
      { name: 'sender.name', label: { zh: '发件人名称', en: 'Sender name' }, type: 'input', group: 'advanced', showWhen: [{ field: 'sender_type', equals: 2 }] },
      { name: 'sender.mail', label: { zh: '发件人邮箱', en: 'Sender mail' }, type: 'input', group: 'advanced', showWhen: [{ field: 'sender_type', equals: 2 }] },
      { name: 'sender.password', label: { zh: '发件人密码', en: 'Sender password' }, type: 'input', group: 'advanced', showWhen: [{ field: 'sender_type', equals: 2 }] },
    ],
    doc: {
      zh: {
        purpose: '把内容以邮件形式发送给指定收件人。',
        required: '收件人、主题、正文；自定义 SMTP 时需填全 SMTP 配置。',
        output: '发送结果（成功标记与收件人数）。',
        errors: '收件人格式不合法；自定义 SMTP 服务器/密码错误导致发送失败。',
      },
      en: {
        purpose: 'Sends content to recipients via email.',
        required: 'To, subject and content; full SMTP config when using custom SMTP.',
        output: 'Send result (success flag and recipient count).',
        errors: 'Invalid recipient address; wrong SMTP server/password causes send failure.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'HttpRequest',
    icon: 'carbon:http',
    iconClass: 'text-slate-800',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：四种 content_type 对应四套 body 编辑器（text/json/form-data/urlencoded），schema 无法表达
    customPanel: true,
    componentTitle: { zh: 'HTTP 请求', en: 'HTTP Request' },
    metrics: ['httpStatusCode'],
    configSchema: [
      {
        name: 'method',
        label: { zh: '请求方法', en: 'Method' },
        type: 'select',
        required: true,
        group: 'required',
        options: [
          { label: { zh: 'GET', en: 'GET' }, value: 'GET' },
          { label: { zh: 'POST', en: 'POST' }, value: 'POST' },
          { label: { zh: 'PUT', en: 'PUT' }, value: 'PUT' },
          { label: { zh: 'DELETE', en: 'DELETE' }, value: 'DELETE' },
        ],
      },
      { name: 'url', label: { zh: '请求地址', en: 'URL' }, type: 'input', required: true, group: 'required' },
      { name: 'headers', label: { zh: '请求头', en: 'Headers' }, type: 'kvList', group: 'advanced' },
      { name: 'params', label: { zh: '查询参数', en: 'Params' }, type: 'kvList', group: 'advanced' },
      { name: 'timeout', label: { zh: '超时(秒)', en: 'Timeout (s)' }, type: 'number', group: 'advanced' },
      { name: 'retry_times', label: { zh: '重试次数', en: 'Retry times' }, type: 'number', group: 'advanced' },
      { name: 'clear_html', label: { zh: '清除 HTML', en: 'Clear HTML' }, type: 'switch', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '调用外部 HTTP 接口并取回响应。',
        required: '请求方法与请求地址。',
        output: '响应体文本与 HTTP 状态码。',
        errors: 'URL 不可达或超时；非 2xx 状态码（按配置重试）；body 与 content_type 不匹配。',
      },
      en: {
        purpose: 'Calls an external HTTP API and returns the response.',
        required: 'Method and URL.',
        output: 'Response body text and HTTP status code.',
        errors: 'Unreachable URL or timeout; non-2xx status (retry per config); body mismatched with content type.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Agent',
    icon: 'carbon:bot',
    iconClass: 'text-indigo-800',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: 'Agent 节点', en: 'Agent' },
    metrics: [...METRIC_TOKENS, 'retrievalCount'],
    configSchema: [
      { name: 'character_uuid', label: { zh: 'Agent', en: 'Agent' }, type: 'agentSelector', required: true, group: 'resource' },
      { name: 'prompt', label: { zh: '附加提示词', en: 'Extra prompt' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS },
      { name: 'model_name', label: { zh: '模型', en: 'Model' }, type: 'modelSelector', group: 'advanced', pairsTo: 'model_platform' },
      { name: 'enable_rag', label: { zh: '启用知识库', en: 'Enable RAG' }, type: 'switch', group: 'advanced' },
      { name: 'enable_mcp', label: { zh: '启用 MCP', en: 'Enable MCP' }, type: 'switch', group: 'advanced' },
      { name: 'enable_web_search', label: { zh: '启用联网搜索', en: 'Enable web search' }, type: 'switch', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '让已配置的 Agent（自带知识库/MCP/联网能力）处理输入。',
        required: '选择 Agent；附加提示词可留空。',
        output: 'Agent 的回答文本。',
        errors: 'Agent 已被删除；所选模型不支持联网搜索时开关自动关闭。',
      },
      en: {
        purpose: 'Delegates the input to a configured Agent (RAG/MCP/web capabilities).',
        required: 'Pick an Agent; extra prompt optional.',
        output: "The Agent's answer text.",
        errors: 'Agent deleted; web-search switch auto-disabled when the model lacks support.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Database',
    icon: 'carbon:data-base',
    iconClass: 'text-indigo-800',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：表结构浏览器（加载表/字段辅助写 SQL）为独立交互，schema 无法表达
    customPanel: true,
    componentTitle: { zh: '数据库查询', en: 'Database Query' },
    metrics: [],
    configSchema: [
      { name: 'datasource_uuid', label: { zh: '数据源', en: 'Datasource' }, type: 'datasourceSelector', required: true, group: 'resource' },
      { name: 'sql', label: { zh: 'SQL 语句', en: 'SQL' }, type: 'textarea', required: true, group: 'required' },
      {
        name: 'output_format',
        label: { zh: '输出格式', en: 'Output format' },
        type: 'select',
        group: 'advanced',
        options: [
          { label: { zh: '文本', en: 'Text' }, value: 'text' },
          { label: { zh: 'JSON', en: 'JSON' }, value: 'json' },
        ],
      },
      { name: 'limit', label: { zh: '最大行数', en: 'Row limit' }, type: 'number', group: 'advanced' },
      { name: 'replace_input', label: { zh: '替换占位符', en: 'Replace placeholders' }, type: 'switch', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '在外部数据源上执行只读 SQL 并返回结果。',
        required: '数据源与 SQL（仅允许 select/with）。',
        output: '查询结果（文本表格或 JSON）。',
        errors: '非只读语句被拦截；数据源连接失败；表或字段不存在。',
      },
      en: {
        purpose: 'Runs read-only SQL on an external datasource and returns results.',
        required: 'Datasource and SQL (select/with only).',
        output: 'Query result (text table or JSON).',
        errors: 'Non-read-only statement blocked; connection failed; table/column missing.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Mcp',
    icon: 'carbon:tool-kit',
    iconClass: 'text-teal-700',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：工具清单需按所选 MCP 服务动态拉取并展示描述
    customPanel: true,
    componentTitle: { zh: 'MCP 工具', en: 'MCP Tool' },
    metrics: ['toolName', 'mcpTitle', 'resultLength'],
    configSchema: [
      { name: 'mcp_ids', label: { zh: 'MCP 服务', en: 'MCP services' }, type: 'mcpSelector', group: 'resource' },
      { name: 'tool_name', label: { zh: '工具名', en: 'Tool name' }, type: 'input', required: true, group: 'required' },
      { name: 'arguments', label: { zh: '入参 JSON', en: 'Arguments JSON' }, type: 'textarea', group: 'advanced' },
      {
        name: 'output_format',
        label: { zh: '输出格式', en: 'Output format' },
        type: 'select',
        group: 'advanced',
        options: [
          { label: { zh: '文本', en: 'Text' }, value: 'text' },
          { label: { zh: 'JSON', en: 'JSON' }, value: 'json' },
        ],
      },
    ],
    doc: {
      zh: {
        purpose: '调用 MCP 服务提供的工具并返回执行结果。',
        required: '工具名；MCP 服务留空 = 在全部已启用服务中查找。',
        output: '工具执行结果文本。',
        errors: 'MCP 服务未启用或不可连通；工具名不存在；入参 JSON 非法。',
      },
      en: {
        purpose: 'Invokes a tool provided by MCP services.',
        required: 'Tool name; empty service list = search all enabled services.',
        output: 'Tool execution result text.',
        errors: 'MCP service disabled/unreachable; unknown tool name; invalid arguments JSON.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Skill',
    icon: 'tabler:puzzle',
    iconClass: 'text-orange-700',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：脚本模式需动态拉取技能内 .py 文件清单并做模式联动
    customPanel: true,
    componentTitle: { zh: '技能', en: 'Skill' },
    metrics: ['skillName', 'mode', 'script', 'resultLength'],
    configSchema: [
      { name: 'skill_uuid', label: { zh: '技能', en: 'Skill' }, type: 'skillSelector', required: true, group: 'resource', pairsTo: 'skill_name' },
      {
        name: 'mode',
        label: { zh: '执行方式', en: 'Mode' },
        type: 'select',
        required: true,
        group: 'required',
        options: [
          { label: { zh: '读取指令', en: 'Instruction' }, value: 'instruction' },
          { label: { zh: '执行脚本', en: 'Script' }, value: 'script' },
        ],
      },
      { name: 'script', label: { zh: '脚本路径', en: 'Script path' }, type: 'input', group: 'advanced', showWhen: [{ field: 'mode', equals: 'script' }] },
      { name: 'args', label: { zh: '脚本参数', en: 'Script args' }, type: 'input', group: 'advanced', showWhen: [{ field: 'mode', equals: 'script' }] },
    ],
    doc: {
      zh: {
        purpose: '执行技能：读取技能指令正文或运行技能脚本。',
        required: '选择技能与执行方式；脚本模式需填脚本路径。',
        output: '技能指令正文或脚本标准输出。',
        errors: '技能未安装；无脚本的技能选了脚本模式；脚本执行超时（60 秒）。',
      },
      en: {
        purpose: 'Runs a skill: read its instruction text or execute its script.',
        required: 'Skill and mode; script path required in script mode.',
        output: 'Instruction text or script stdout.',
        errors: 'Skill not installed; script mode on a script-less skill; script timeout (60s).',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    type: 'Orchestrator',
    icon: 'carbon:network-4',
    iconClass: 'text-purple-700',
    category: 'action',
    color: COLOR_ACTION,
    // 保留自定义：sub_agents 是数组结构（多行 Agent + 任务 id + 提示词），三段式 SchemaForm 无法表达
    customPanel: true,
    componentTitle: { zh: '多 Agent 编排', en: 'Multi-Agent Orchestrator' },
    metrics: ['subTaskCount', 'successCount', 'failCount', 'summaryLength'],
    configSchema: [],
    doc: {
      zh: {
        purpose: '把子任务分派给多个子 Agent 并行执行，通过共享黑板回写结果并汇总。',
        required: '至少一个子 Agent；任务 id 缺省用 Agent 唯一标识。',
        output: '汇总文本（默认含每个子任务的成败记录）。',
        errors: '子 Agent 列表为空；编排开关被停用；fail_fast 开启时存在失败子任务。',
      },
      en: {
        purpose: 'Dispatches subtasks to multiple sub-agents in parallel, writes results back through a shared blackboard, then summarizes.',
        required: 'At least one sub-agent; task id defaults to the agent uuid.',
        output: 'Summary text (with each subtask success/failure record by default).',
        errors: 'Empty sub-agent list; orchestration switch off; fail_fast with failed subtasks.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    // T9-02 循环节点：metrics 与后端 LoopMetrics 字段一一对应（第六处指标 chip 自动生效）
    type: 'Loop',
    icon: 'carbon:repeat',
    iconClass: 'text-amber-800',
    category: 'flow',
    color: COLOR_FLOW,
    componentTitle: { zh: '循环节点', en: 'Loop' },
    metrics: ['iterations', 'itemCount', 'resultCount', 'stopReason'],
    configSchema: [
      {
        name: 'mode',
        label: { zh: '执行模式', en: 'Mode' },
        type: 'select',
        required: true,
        group: 'required',
        options: [
          { label: { zh: '数组逐项 (Each)', en: 'Each (array)' }, value: 'each' },
          { label: { zh: '条件循环 (While)', en: 'While (condition)' }, value: 'while' },
        ],
      },
      { name: 'body_template', label: { zh: '子链体模板', en: 'Body template' }, type: 'textarea', group: 'required', tip: TIP_EMPTY_USE_PREVIOUS_ITEM },
      { name: 'condition_template', label: { zh: '条件模板', en: 'Condition template' }, type: 'textarea', group: 'advanced', showWhen: [{ field: 'mode', equals: 'while' }] },
      { name: 'items_param', label: { zh: '数组入参名', en: 'Array param' }, type: 'input', group: 'advanced', showWhen: [{ field: 'mode', equals: 'each' }], tip: TIP_INHERIT_ARRAY_PARAM },
      { name: 'item_param_name', label: { zh: '当前项变量名', en: 'Item variable' }, type: 'input', group: 'advanced', showWhen: [{ field: 'mode', equals: 'each' }] },
      { name: 'max_iterations', label: { zh: '最大次数', en: 'Max iterations' }, type: 'number', group: 'advanced' },
      { name: 'parallel', label: { zh: '并行逐项', en: 'Parallel items' }, type: 'switch', group: 'advanced', showWhen: [{ field: 'mode', equals: 'each' }] },
      { name: 'max_parallel', label: { zh: '并行度', en: 'Max parallel' }, type: 'number', group: 'advanced', showWhen: [{ field: 'mode', equals: 'each' }] },
    ],
    doc: {
      zh: {
        purpose: '对数组输入逐项执行子链（Each，可选并行）或按条件循环（Loop），结束后聚合输出。',
        required: '执行模式；each 需要数组入参（缺省用默认输入），while 需要条件模板。',
        output: '默认参数 output 为聚合 JSON 数组；另有 results_text / stop_reason / iterations。',
        errors: '数组入参不存在或不可解析；while 条件恒真触达 max_iterations 熔断（部分结果仍输出）。',
      },
      en: {
        purpose: 'Iterates an array input item by item (Each, parallel optional) or loops by condition (Loop), then aggregates.',
        required: 'Mode; Each needs an array param (default input fallback), While needs a condition template.',
        output: 'Default param "output": aggregated JSON array; plus results_text / stop_reason / iterations.',
        errors: 'Array param missing or unparseable; always-true while condition hits the max-iteration fuse (partial results still emitted).',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    // T9-03 并行节点：metrics 与后端 ParallelMetrics 字段一一对应
    type: 'Parallel',
    icon: 'carbon:branch',
    iconClass: 'text-amber-800',
    category: 'flow',
    color: COLOR_FLOW,
    componentTitle: { zh: '并行节点', en: 'Parallel' },
    metrics: ['branchCount', 'successCount', 'failCount', 'parallelElapsedMs'],
    configSchema: [
      { name: 'branches', label: { zh: '并行分支', en: 'Branches' }, type: 'textarea', required: true, group: 'required', tip: TIP_BRANCHES_JSON },
      {
        name: 'join',
        label: { zh: '汇合语义', en: 'Join semantics' },
        type: 'select',
        group: 'required',
        options: [
          { label: { zh: '全成功 (all)', en: 'All succeed' }, value: 'all' },
          { label: { zh: '任一成功 (any)', en: 'Any succeeds' }, value: 'any' },
        ],
      },
      { name: 'timeout_seconds', label: { zh: '分支超时(秒)', en: 'Timeout (s)' }, type: 'number', group: 'advanced' },
      { name: 'max_parallel', label: { zh: '并行度', en: 'Max parallel' }, type: 'number', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '多分支并行执行，全部完成后按 join 语义汇合（all=全成功 / any=任一成功）。',
        required: '至少一个分支（branches JSON 数组：name/template，可选 delay_ms 模拟慢分支）。',
        output: '每个成功分支一条同名输出；默认参数 output 为各分支结果拼接。',
        errors: 'join=all 下存在失败/超时分支；join=any 下全部分支失败。',
      },
      en: {
        purpose: 'Runs branches in parallel and joins with configurable semantics (all / any).',
        required: 'At least one branch (branches JSON array: name/template, optional delay_ms to simulate slow ones).',
        output: 'One output per succeeded branch (by name); default param "output" joins them.',
        errors: 'join=all with a failed/timed-out branch; join=any with all branches failed.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    // T9-04 代码节点：metrics 与后端 CodeMetrics 字段一一对应（复用 Skill 沙箱）
    type: 'Code',
    icon: 'carbon:code',
    iconClass: 'text-slate-700',
    category: 'action',
    color: COLOR_ACTION,
    componentTitle: { zh: '代码节点', en: 'Code' },
    metrics: ['language', 'timeoutSeconds', 'resultLength', 'exitCode'],
    configSchema: [
      { name: 'code', label: { zh: '代码', en: 'Code' }, type: 'textarea', required: true, group: 'required' },
      { name: 'timeout_seconds', label: { zh: '超时(秒)', en: 'Timeout (s)' }, type: 'number', group: 'advanced' },
    ],
    doc: {
      zh: {
        purpose: '把一段代码作为 Skill 沙箱任务执行（python，固定参数列表、超时强杀、输出截断）。',
        required: '代码正文；上游输入经 SKILL_SCRIPT_ARGS 环境变量（JSON）注入。',
        output: '脚本 stdout 作为默认参数 output；另有 exit_code。',
        errors: '语言非 python 被拒；脚本超时；非零退出码视为节点失败。',
      },
      en: {
        purpose: 'Runs a code snippet as a Skill sandbox task (python, fixed arg list, timeout kill, output truncation).',
        required: 'Code body; upstream inputs are injected as the SKILL_SCRIPT_ARGS env var (JSON).',
        output: 'Script stdout as default param "output"; plus exit_code.',
        errors: 'Non-python language rejected; script timeout; non-zero exit code fails the node.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
  {
    // T9-07 子工作流节点：metrics 与后端 SubWorkflowMetrics 字段一一对应
    type: 'SubWorkflow',
    icon: 'carbon:flow-connection',
    iconClass: 'text-amber-800',
    category: 'flow',
    color: COLOR_FLOW,
    componentTitle: { zh: '子工作流', en: 'SubWorkflow' },
    metrics: ['subWorkflowUuid', 'chainDepth', 'chainPath'],
    configSchema: [
      { name: 'workflow_uuid', label: { zh: '子工作流', en: 'Sub-workflow' }, type: 'input', required: true, group: 'required' },
    ],
    doc: {
      zh: {
        purpose: '引用另一个工作流作为子流程执行（子流程有自己的运行记录）。',
        required: '目标工作流 uuid（须启用且公开或归属运行用户）。',
        output: '子工作流最终 outputs（JSON 文本）；另有 sub_workflow_uuid。',
        errors: '递归引用（链上已存在）被拒；嵌套深度超过 3 被拒；目标工作流不存在或已停用。',
      },
      en: {
        purpose: 'Runs another workflow as a sub-process (with its own run record).',
        required: 'Target workflow uuid (enabled, public or owned by the runner).',
        output: 'The sub-workflow final outputs (JSON text); plus sub_workflow_uuid.',
        errors: 'Recursive reference rejected; nesting depth over 3 rejected; target missing or disabled.',
      },
    },
    hasInput: true,
    hasOutput: true,
  },
]

// ==================== T3-14 内置节点模板（可整体拖入画布） ====================
// （WfNodePresetNode / WfNodePreset 类型定义在 ./types，此处只有数据）

const PRESET_PRE_SALES_PROMPT = '你是售前客服。请根据用户问题礼貌、简洁地介绍产品与报价，并引导用户留下联系方式。'
const PRESET_AFTER_SALES_PROMPT = '你是售后客服。请耐心解答用户关于使用、退换货与报修的问题；无法解决时提示用户提交工单。'

export const wfNodePresets: WfNodePreset[] = [
  {
    key: 'customer-service',
    title: { zh: '客服问答', en: 'Customer Service' },
    remark: { zh: '按售前/售后分类，分别生成回答', en: 'Classify pre/after-sales, then answer' },
    nodes: [
      { type: 'Start', dx: 0, dy: 0 },
      {
        type: 'Classifier',
        title: { zh: '问题分类', en: 'Question Classifier' },
        dx: 360, dy: 0,
        config: { categories: [{ category_name: { zh: '售前咨询', en: 'Pre-sales' } }, { category_name: { zh: '售后支持', en: 'After-sales' } }] },
      },
      { type: 'Answer', title: { zh: '售前回答', en: 'Pre-sales Answer' }, dx: 720, dy: -100, config: { prompt: { zh: PRESET_PRE_SALES_PROMPT, en: PRESET_PRE_SALES_PROMPT } } },
      { type: 'Answer', title: { zh: '售后回答', en: 'After-sales Answer' }, dx: 720, dy: 140, config: { prompt: { zh: PRESET_AFTER_SALES_PROMPT, en: PRESET_AFTER_SALES_PROMPT } } },
      { type: 'End', dx: 1080, dy: 20 },
    ],
    edges: [[0, 1], [1, 2, 0], [1, 3, 1], [2, 4], [3, 4]],
  },
  {
    key: 'kb-qa',
    title: { zh: '知识库问答', en: 'Knowledge QA' },
    remark: { zh: '先检索知识库，再让模型作答', en: 'Retrieve from KB, then answer' },
    nodes: [
      { type: 'Start', dx: 0, dy: 0 },
      { type: 'KnowledgeRetrieval', dx: 360, dy: 0 },
      {
        type: 'Answer',
        dx: 720, dy: 0,
        config: { prompt: { zh: '请根据以下参考资料回答用户问题，资料中没有的信息请如实说明。\n\n参考资料：', en: 'Answer the user question based on the reference material below. Say so honestly if not covered.\n\nReference material:' } },
      },
      { type: 'End', dx: 1080, dy: 0 },
    ],
    edges: [[0, 1], [1, 2], [2, 3]],
  },
  {
    key: 'content-gen',
    title: { zh: '内容生成', en: 'Content Generation' },
    remark: { zh: '模板拼装大纲，再让模型撰写成文', en: 'Assemble an outline, then write' },
    nodes: [
      { type: 'Start', dx: 0, dy: 0 },
      {
        type: 'Template',
        title: { zh: '生成大纲', en: 'Build Outline' },
        dx: 360, dy: 0,
        config: { template: { zh: '请围绕「{input}」整理一份内容大纲，包含 3-5 个要点。', en: 'Build a content outline about "{input}" with 3-5 key points.' } },
      },
      {
        type: 'Answer',
        title: { zh: '撰写正文', en: 'Write Content' },
        dx: 720, dy: 0,
        config: { prompt: { zh: '请根据以下大纲撰写一篇结构完整、语言流畅的文章。\n\n大纲：', en: 'Write a well-structured, fluent article from the outline below.\n\nOutline:' } },
      },
      { type: 'End', dx: 1080, dy: 0 },
    ],
    edges: [[0, 1], [1, 2], [2, 3]],
  },
]

const descriptorByName = new Map(wfNodeDescriptorData.map(desc => [desc.type.toLowerCase(), desc]))
const presetByKey = new Map(wfNodePresets.map(preset => [preset.key, preset]))

/** 按组件名取节点描述（大小写不敏感；无匹配返回 undefined） */
export function getWfNodeDescriptorData(name?: string | null): WfNodeDescriptorData | undefined {
  if (!name)
    return undefined

  return descriptorByName.get(name.toLowerCase())
}

/** 按模板 key 取内置节点模板 */
export function getWfNodePreset(key: string): WfNodePreset | undefined {
  return presetByKey.get(key)
}
