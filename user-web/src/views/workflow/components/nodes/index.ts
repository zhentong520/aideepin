import SpecialNode from './SpecialNode.vue'
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
import OrchestratorNode from './OrchestratorNode.vue'

import StartNodeProperty from 'aideepin-wf-shared/src/nodes/StartNodeProperty.vue'
import AnswerNodeProperty from './AnswerNodeProperty.vue'
import ClassifierNodeProperty from 'aideepin-wf-shared/src/components/nodes/ClassifierNodeProperty.vue'
import SwticherNodeProperty from 'aideepin-wf-shared/src/components/nodes/SwitcherNodeProperty.vue'
import TemplateNodeProperty from './TemplateNodeProperty.vue'
import KeywordExtractorNodeProperty from './KeywordExtractorNodeProperty.vue'
import FaqExtractorNodeProperty from './FaqExtractorNodeProperty.vue'
import DocumentExtractorNodeProperty from './DocumentExtractorNodeProperty.vue'
import KnowledgeRetrievalNodeProperty from 'aideepin-wf-shared/src/components/nodes/KnowledgeRetrievalNodeProperty.vue'
import OpenAiImageNodeProperty from './OpenAiImageNodeProperty.vue'
import TongyiwanxNodeProperty from './TongyiwanxNodeProperty.vue'
import GoogleNodeProperty from './GoogleNodeProperty.vue'
import EndNodeProperty from './EndNodeProperty.vue'
import HumanFeedbackNodeProperty from './HumanFeedbackNodeProperty.vue'
import MailSendNodeProperty from 'aideepin-wf-shared/src/components/nodes/MailSendNodeProperty.vue'
import HttpRequestNodeProperty from 'aideepin-wf-shared/src/components/nodes/HttpRequestNodeProperty.vue'
import AgentNodeProperty from 'aideepin-wf-shared/src/components/nodes/AgentNodeProperty.vue'
import DatabaseNodeProperty from 'aideepin-wf-shared/src/components/nodes/DatabaseNodeProperty.vue'
import McpNodeProperty from 'aideepin-wf-shared/src/components/nodes/McpNodeProperty.vue'
import SkillNodeProperty from 'aideepin-wf-shared/src/components/nodes/SkillNodeProperty.vue'
import OrchestratorNodeProperty from './OrchestratorNodeProperty.vue'

// T5-03：节点描述化 —— 六处硬编码收敛后的"定义源头"（新增节点只需在 descriptor.ts 加一条描述）
export {
  wfNodeDescriptors,
  getWfNodeDescriptor,
  wfNodePresets,
  getWfNodePreset,
} from './descriptor'
export type {
  WfNodeDescriptor,
  WfNodeCategory,
  WfLocalizedText,
  WfNodeDoc,
  WfDescriptorField,
  WfDescriptorFieldType,
  WfDescriptorShowWhen,
  WfNodePreset,
  WfNodePresetNode,
} from './descriptor'

export {
  SpecialNode,
  StartNode,
  EndNode,
  AnswerNode,
  ClassifierNode,
  DocumentExtractorNode,
  KeywordExtractorNode,
  FaqExtractorNode,
  OpenAiImageNode,
  TongyiwanxNode,
  KnowledgeRetrievalNode,
  GoogleNode,
  SwitcherNode,
  TemplateNode,
  AgentNode,
  DatabaseNode,
  McpNode,
  SkillNode,
  OrchestratorNode,
  StartNodeProperty,
  AnswerNodeProperty,
  ClassifierNodeProperty,
  SwticherNodeProperty,
  TemplateNodeProperty,
  KeywordExtractorNodeProperty,
  FaqExtractorNodeProperty,
  DocumentExtractorNodeProperty,
  KnowledgeRetrievalNodeProperty,
  OpenAiImageNodeProperty,
  TongyiwanxNodeProperty,
  GoogleNodeProperty,
  EndNodeProperty,
  HumanFeedbackNode,
  HumanFeedbackNodeProperty,
  MailSendNode,
  MailSendNodeProperty,
  HttpRequestNode,
  HttpRequestNodeProperty,
  AgentNodeProperty,
  DatabaseNodeProperty,
  McpNodeProperty,
  SkillNodeProperty,
  OrchestratorNodeProperty,
}
