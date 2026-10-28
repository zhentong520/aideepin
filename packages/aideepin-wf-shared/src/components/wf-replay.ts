/**
 * T3-09 执行链路回放：RunDetail → WorkflowDefine 的事件负载。
 * S2-07#4 下沉：类型单源于共享包，宿主 wf-replay.ts 仅重导出保持 API 兼容。
 */
export interface WfReplayEvent {
  type: 'reset' | 'node' | 'finish' | 'error'
  nodeUuid?: string
  status?: number
  remark?: string
}
