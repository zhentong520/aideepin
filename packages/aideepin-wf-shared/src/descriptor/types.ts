/**
 * S2-07（ADR-005）步骤①：工作流节点描述类型单源（自 user-web descriptor.ts 机械平移）。
 *
 * `WfNodeDescriptorData` 是节点描述的纯数据形态（不含 Vue 组件引用）；
 * 两端薄壳各自声明 `WfNodeDescriptor extends WfNodeDescriptorData` 补上
 * `component` / `propertyComponent` 两个 Vue 端字段。
 */

export type WfNodeCategory = 'trigger' | 'llm' | 'retrieval' | 'transform' | 'action' | 'flow'

export interface WfLocalizedText {
  zh: string
  en: string
}

/** T3-14 节点文档（节点卡 hover 展示） */
export interface WfNodeDoc {
  /** 用途 */
  purpose: string
  /** 必填项 */
  required: string
  /** 输出结构 */
  output: string
  /** 常见错误 */
  errors: string
}

/** configSchema 字段类型（供 T3-08 生成三段式表单） */
export type WfDescriptorFieldType =
  | 'input'
  | 'textarea'
  | 'number'
  | 'switch'
  | 'select'
  // 选择器（复用已实现的资源选择器，禁止退化回手填 ID）
  | 'modelSelector'
  | 'imageModelSelector'
  | 'kbSelector'
  | 'agentSelector'
  | 'skillSelector'
  | 'datasourceSelector'
  | 'mcpSelector'
  // 结构化编辑器
  | 'kvList'
  | 'userInputs'
  | 'categoryList'
  | 'conditionCases'

export interface WfDescriptorFieldOption {
  label: WfLocalizedText
  value: string | number
}

/** 字段显示条件：全部满足才渲染（MailSend 自定义 SMTP、Skill 脚本模式等） */
export interface WfDescriptorShowWhen {
  field: string
  equals: string | number | boolean
}

export interface WfDescriptorField {
  name: string
  label: WfLocalizedText
  type: WfDescriptorFieldType
  required?: boolean
  /** T3-08 三段式分组：required=必填 / resource=资源选择 / advanced=高级设置（默认收起） */
  group?: 'required' | 'resource' | 'advanced'
  options?: WfDescriptorFieldOption[]
  placeholder?: WfLocalizedText
  tip?: WfLocalizedText
  /** selector 类字段：随主键一起写入的伴生配置键（如 model_platform / knowledge_base_name） */
  pairsTo?: string
  /** 条件显示（T3-08：替代 MailSend v-show 硬编码） */
  showWhen?: WfDescriptorShowWhen[]
  /** number 字段留空 = 继承上级默认（T3-12：置空时从 nodeConfig 删除该键） */
  inheritable?: boolean
}

export interface WfNodeDescriptorData {
  /** 组件名，与后端 WfComponentNameEnum / workflow_component.name 一致 */
  type: string
  /** iconify 图标名（原 workflow-util.getIconByComponentName 的 switch 映射） */
  icon: string
  /** 图标颜色 class（原 getIconClassByComponentName 的 switch 映射） */
  iconClass: string
  category: WfNodeCategory
  /** T3-02 七类色条颜色（与 getNodeColorByComponentName 对齐） */
  color: string
  /** T3-08：true = configSchema 表达不了，保留原自定义面板（原因见各节点注释） */
  customPanel?: boolean
  /** 组件标题（locales componentTitle 的单一来源，locales key 保留作兜底） */
  componentTitle: WfLocalizedText
  /** RuntimeNodes.vue 指标 chip 声明：节点执行 metadata 中要展示的字段名列表 */
  metrics: string[]
  /** 配置表单 schema（供 T3-08 生成 n8n 三段式表单） */
  configSchema: WfDescriptorField[]
  /** T3-14 节点文档（节点卡 hover 展示） */
  doc: { zh: WfNodeDoc, en: WfNodeDoc }
  hasInput: boolean
  hasOutput: boolean
}

export interface WfNodePresetNode {
  /** 节点类型（WfNodeDescriptor.type） */
  type: string
  /** 节点标题覆盖 */
  title?: WfLocalizedText
  /** 相对落点的偏移 */
  dx: number
  dy: number
  /** nodeConfig 补丁（数组按索引浅合并，如 Classifier 的 categories） */
  config?: Record<string, unknown>
}

/** edges: 源节点下标 + 目标节点下标（可选第三位为 sourceHandle；Classifier 分支边的 sourceHandle 为分类项下标数字） */
export interface WfNodePreset {
  key: string
  title: WfLocalizedText
  remark: WfLocalizedText
  nodes: WfNodePresetNode[]
  edges: Array<[number, number] | [number, number, string | number]>
}
