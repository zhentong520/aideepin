/**
 * S2-07（ADR-005）步骤①：descriptor 单源下沉后的 user-web 薄壳。
 *
 * 节点"定义源头"（类型、25 节点数据、内置模板、查找助手）已迁入共享包
 * aideepin-wf-shared（packages/aideepin-wf-shared/src/descriptor/），本文件只保留：
 *   1. Vue 组件导入（画布节点 + 属性面板）——组件是端实现，不进共享包（零外部包依赖铁律）；
 *   2. 数据 × 组件绑定（COMPONENT_BINDINGS）——type → component/propertyComponent；
 *   3. 对外 API 兼容重导出（wfNodeDescriptors / getWfNodeDescriptor / presets / 类型），
 *      六处消费方（index.ts、WorkflowDefine.vue、WfDefineRightPanel.vue、workflow-util.ts、
 *      locales、RuntimeNodes.vue）零改动。
 *
 * 新增一种节点：先在 wf-shared 的 wfNodeDescriptorData 加数据，再在本文件
 * COMPONENT_BINDINGS 加一行绑定即可。
 */
import type { Component } from 'vue'
import {
  wfNodeDescriptorData,
  getWfNodeDescriptorData,
  getWfNodePreset as sharedGetWfNodePreset,
  wfNodePresets as sharedWfNodePresets,
} from 'aideepin-wf-shared'
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
import SwitcherNode from './SwitcherNode.vue'
import TemplateNode from './TemplateNode.vue'
import KeywordExtractorNode from './KeywordExtractorNode.vue'
import DocumentExtractorNode from './DocumentExtractorNode.vue'
import FaqExtractorNode from './FaqExtractorNode.vue'
import KnowledgeRetrievalNode from './KnowledgeRetrievalNode.vue'
import GoogleNode from './GoogleNode.vue'
import OpenAiImageNode from './OpenAiImageNode.vue'
import TongyiwanxNode from './TongyiwanxNode.vue'
import HumanFeedbackNode from './HumanFeedbackNode.vue'
import MailSendNode from './MailSendNode.vue'
import HttpRequestNode from './HttpRequestNode.vue'
import AgentNode from './AgentNode.vue'
import DatabaseNode from './DatabaseNode.vue'
import McpNode from './McpNode.vue'
import SkillNode from './SkillNode.vue'
import OrchestratorNode from './OrchestratorNode.vue'
import LoopNode from './LoopNode.vue'
import ParallelNode from './ParallelNode.vue'
import CodeNode from './CodeNode.vue'
import SubWorkflowNode from './SubWorkflowNode.vue'

import StartNodeProperty from 'aideepin-wf-shared/src/nodes/StartNodeProperty.vue'
import EndNodeProperty from './EndNodeProperty.vue'
import AnswerNodeProperty from './AnswerNodeProperty.vue'
import ClassifierNodeProperty from './ClassifierNodeProperty.vue'
import SwitcherNodeProperty from './SwitcherNodeProperty.vue'
import TemplateNodeProperty from './TemplateNodeProperty.vue'
import KeywordExtractorNodeProperty from './KeywordExtractorNodeProperty.vue'
import DocumentExtractorNodeProperty from './DocumentExtractorNodeProperty.vue'
import FaqExtractorNodeProperty from './FaqExtractorNodeProperty.vue'
import KnowledgeRetrievalNodeProperty from './KnowledgeRetrievalNodeProperty.vue'
import GoogleNodeProperty from './GoogleNodeProperty.vue'
import OpenAiImageNodeProperty from './OpenAiImageNodeProperty.vue'
import TongyiwanxNodeProperty from './TongyiwanxNodeProperty.vue'
import HumanFeedbackNodeProperty from './HumanFeedbackNodeProperty.vue'
import MailSendNodeProperty from './MailSendNodeProperty.vue'
import HttpRequestNodeProperty from './HttpRequestNodeProperty.vue'
import AgentNodeProperty from './AgentNodeProperty.vue'
import DatabaseNodeProperty from './DatabaseNodeProperty.vue'
import McpNodeProperty from './McpNodeProperty.vue'
import SkillNodeProperty from './SkillNodeProperty.vue'
import OrchestratorNodeProperty from './OrchestratorNodeProperty.vue'
import P3NodeProperty from './P3NodeProperty.vue'

/** 端侧完整节点描述 = 共享包纯数据 + Vue 组件引用 */
export interface WfNodeDescriptor extends WfNodeDescriptorData {
  /** 画布节点组件（WorkflowDefine.vue slot 动态渲染用） */
  component: Component
  /** 属性面板组件（T3-08：customPanel=true 时仍用它，其余走 configSchema 生成） */
  propertyComponent: Component
}

/** type → 组件绑定（与 wfNodeDescriptorData 的 25 个 type 一一对应） */
const COMPONENT_BINDINGS: Record<string, { component: Component, propertyComponent: Component }> = {
  Start: { component: StartNode, propertyComponent: StartNodeProperty },
  End: { component: EndNode, propertyComponent: EndNodeProperty },
  Answer: { component: AnswerNode, propertyComponent: AnswerNodeProperty },
  Classifier: { component: ClassifierNode, propertyComponent: ClassifierNodeProperty },
  Switcher: { component: SwitcherNode, propertyComponent: SwitcherNodeProperty },
  Template: { component: TemplateNode, propertyComponent: TemplateNodeProperty },
  KeywordExtractor: { component: KeywordExtractorNode, propertyComponent: KeywordExtractorNodeProperty },
  DocumentExtractor: { component: DocumentExtractorNode, propertyComponent: DocumentExtractorNodeProperty },
  FaqExtractor: { component: FaqExtractorNode, propertyComponent: FaqExtractorNodeProperty },
  KnowledgeRetrieval: { component: KnowledgeRetrievalNode, propertyComponent: KnowledgeRetrievalNodeProperty },
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
  Orchestrator: { component: OrchestratorNode, propertyComponent: OrchestratorNodeProperty },
  Loop: { component: LoopNode, propertyComponent: P3NodeProperty },
  Parallel: { component: ParallelNode, propertyComponent: P3NodeProperty },
  Code: { component: CodeNode, propertyComponent: P3NodeProperty },
  SubWorkflow: { component: SubWorkflowNode, propertyComponent: P3NodeProperty },
}

export const wfNodeDescriptors: WfNodeDescriptor[] = wfNodeDescriptorData.map((desc) => {
  const binding = COMPONENT_BINDINGS[desc.type]
  // fail-fast：共享包加了新节点而两端忘了绑定时，模块加载即报错，不留运行时悬空组件
  if (!binding)
    throw new Error(`[wf-descriptor] missing component binding for node type: ${desc.type}`)
  return { ...desc, ...binding }
})

export const wfNodePresets: WfNodePreset[] = sharedWfNodePresets

/** 按组件名取节点描述（大小写不敏感；无匹配返回 undefined） */
export function getWfNodeDescriptor(name?: string | null): WfNodeDescriptor | undefined {
  return getWfNodeDescriptorData(name) as WfNodeDescriptor | undefined
}

/** 按模板 key 取内置节点模板 */
export function getWfNodePreset(key: string): WfNodePreset | undefined {
  return sharedGetWfNodePreset(key)
}
