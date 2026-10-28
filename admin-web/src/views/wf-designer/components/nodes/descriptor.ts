/**
 * S2-07（ADR-005）步骤③：descriptor 单源下沉后的 admin-web 薄壳。
 *
 * 节点"定义源头"（类型、25 节点数据、内置模板、查找助手）在共享包
 * aideepin-wf-shared（packages/aideepin-wf-shared/src/descriptor/），本文件只保留：
 *   1. Vue 组件导入（画布节点 + 属性面板）——组件是端实现，不进共享包（零外部包依赖铁律）；
 *   2. 数据 × 组件绑定（COMPONENT_BINDINGS）——type → component/propertyComponent；
 *   3. 对外 API（wfNodeDescriptors / getWfNodeDescriptor / 类型重导出）。
 *
 * 与 user-web 薄壳的差异：admin 暂缺 Orchestrator/Loop/Parallel/Code/SubWorkflow
 * 五类节点的端侧组件（任务书本期范围外），故此处采用 fail-soft：无绑定的 type 直接
 * 过滤掉，不像 user 端 fail-fast 抛错导致模块加载即崩。五类组件补齐后应改为 fail-fast。
 */
import type { Component } from 'vue'
import { wfNodeDescriptorData } from 'aideepin-wf-shared'
import type {
  WfDescriptorField,
  WfDescriptorFieldOption,
  WfDescriptorFieldType,
  WfDescriptorShowWhen,
  WfLocalizedText,
  WfNodeCategory,
  WfNodeDescriptorData,
  WfNodeDoc,
  WfNodePreset,
  WfNodePresetNode,
} from 'aideepin-wf-shared'

// 类型 API 兼容重导出（消费方仍从本文件 import type）
export type {
  WfDescriptorField,
  WfDescriptorFieldOption,
  WfDescriptorFieldType,
  WfDescriptorShowWhen,
  WfLocalizedText,
  WfNodeCategory,
  WfNodeDoc,
  WfNodePreset,
  WfNodePresetNode,
}

import StartNode from './StartNode.vue'
import EndNode from './EndNode.vue'
import AnswerNode from './AnswerNode.vue'
import ClassifierNode from './ClassifierNode.vue'
import DocumentExtractorNode from './DocumentExtractorNode.vue'
import KeywordExtractorNode from './KeywordExtractorNode.vue'
import FaqExtractorNode from './FaqExtractorNode.vue'
import OpenAiImageNode from './OpenAiImageNode.vue'
import TongyiwanxNode from './TongyiwanxNode.vue'
import KnowledgeRetrievalNode from './KnowledgeRetrievalNode.vue'
import GoogleNode from './GoogleNode.vue'
import SwitcherNode from './SwitcherNode.vue'
import TemplateNode from './TemplateNode.vue'
import HumanFeedbackNode from './HumanFeedbackNode.vue'
import MailSendNode from './MailSendNode.vue'
import HttpRequestNode from './HttpRequestNode.vue'
import AgentNode from './AgentNode.vue'
import DatabaseNode from './DatabaseNode.vue'
import McpNode from './McpNode.vue'
import SkillNode from './SkillNode.vue'

import StartNodeProperty from 'aideepin-wf-shared/src/nodes/StartNodeProperty.vue'
import AnswerNodeProperty from './AnswerNodeProperty.vue'
import ClassifierNodeProperty from './ClassifierNodeProperty.vue'
import SwitcherNodeProperty from './SwitcherNodeProperty.vue'
import TemplateNodeProperty from './TemplateNodeProperty.vue'
import KeywordExtractorNodeProperty from './KeywordExtractorNodeProperty.vue'
import FaqExtractorNodeProperty from './FaqExtractorNodeProperty.vue'
import DocumentExtractorNodeProperty from './DocumentExtractorNodeProperty.vue'
import KnowledgeRetrievalNodeProperty from './KnowledgeRetrievalNodeProperty.vue'
import OpenAiImageNodeProperty from './OpenAiImageNodeProperty.vue'
import TongyiwanxNodeProperty from './TongyiwanxNodeProperty.vue'
import GoogleNodeProperty from './GoogleNodeProperty.vue'
import EndNodeProperty from './EndNodeProperty.vue'
import HumanFeedbackNodeProperty from './HumanFeedbackNodeProperty.vue'
import MailSendNodeProperty from './MailSendNodeProperty.vue'
import HttpRequestNodeProperty from './HttpRequestNodeProperty.vue'
import AgentNodeProperty from './AgentNodeProperty.vue'
import DatabaseNodeProperty from './DatabaseNodeProperty.vue'
import McpNodeProperty from './McpNodeProperty.vue'
import SkillNodeProperty from './SkillNodeProperty.vue'

/** 端侧完整节点描述 = 共享包纯数据 + Vue 组件引用 */
export interface WfNodeDescriptor extends WfNodeDescriptorData {
  /** 画布节点组件（WorkflowDefine.vue slot 动态渲染用） */
  component: Component
  /** 属性面板组件（WfDefineRightPanel.vue 动态渲染用） */
  propertyComponent: Component
}

/** type → 组件绑定（与 wfNodeDescriptorData 中 admin 已实现组件的 type 对应） */
const COMPONENT_BINDINGS: Record<string, { component: Component; propertyComponent: Component }> = {
  Start: { component: StartNode, propertyComponent: StartNodeProperty },
  End: { component: EndNode, propertyComponent: EndNodeProperty },
  Answer: { component: AnswerNode, propertyComponent: AnswerNodeProperty },
  Classifier: { component: ClassifierNode, propertyComponent: ClassifierNodeProperty },
  Switcher: { component: SwitcherNode, propertyComponent: SwitcherNodeProperty },
  Template: { component: TemplateNode, propertyComponent: TemplateNodeProperty },
  KeywordExtractor: {
    component: KeywordExtractorNode,
    propertyComponent: KeywordExtractorNodeProperty,
  },
  DocumentExtractor: {
    component: DocumentExtractorNode,
    propertyComponent: DocumentExtractorNodeProperty,
  },
  FaqExtractor: { component: FaqExtractorNode, propertyComponent: FaqExtractorNodeProperty },
  KnowledgeRetrieval: {
    component: KnowledgeRetrievalNode,
    propertyComponent: KnowledgeRetrievalNodeProperty,
  },
  Google: { component: GoogleNode, propertyComponent: GoogleNodeProperty },
  OpenAiImage: { component: OpenAiImageNode, propertyComponent: OpenAiImageNodeProperty },
  Tongyiwanx: { component: TongyiwanxNode, propertyComponent: TongyiwanxNodeProperty },
  HumanFeedback: { component: HumanFeedbackNode, propertyComponent: HumanFeedbackNodeProperty },
  MailSend: { component: MailSendNode, propertyComponent: MailSendNodeProperty },
  HttpRequest: { component: HttpRequestNode, propertyComponent: HttpRequestNodeProperty },
  Agent: { component: AgentNode, propertyComponent: AgentNodeProperty },
  Database: { component: DatabaseNode, propertyComponent: DatabaseNodeProperty },
  Mcp: { component: McpNode, propertyComponent: McpNodeProperty },
  Skill: { component: SkillNode, propertyComponent: SkillNodeProperty },
}

export const wfNodeDescriptors: WfNodeDescriptor[] = wfNodeDescriptorData.flatMap((desc) => {
  const binding = COMPONENT_BINDINGS[desc.type]
  // fail-soft：admin 暂缺 5 类节点（Orchestrator/Loop/Parallel/Code/SubWorkflow）的端侧组件，
  // 无绑定即过滤；五类组件补齐后应改回 user 端同款 fail-fast（模块加载即报错）
  if (!binding) return []
  return [{ ...desc, ...binding }]
})

/** 按组件名取节点描述（大小写不敏感；无匹配返回 undefined） */
export function getWfNodeDescriptor(name?: string | null): WfNodeDescriptor | undefined {
  if (!name) return undefined
  const lower = name.toLowerCase()
  return wfNodeDescriptors.find((desc) => desc.type.toLowerCase() === lower)
}
