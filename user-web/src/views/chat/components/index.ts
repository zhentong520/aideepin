import Message from './Message/index.vue'
import AudioMessage from './Message/AudioMessage.vue'
// M1 业务编排：对话触发的业务工作流卡片（按 question 消息的 wfRuntimeUuid 渲染，3s 轮询）
import WorkflowCard from './Message/WorkflowCard.vue'
export { Message, AudioMessage, WorkflowCard }
