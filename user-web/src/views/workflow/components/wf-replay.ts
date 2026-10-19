/**
 * T3-09 执行链路回放：RunDetail → WorkflowDefine 的事件负载
 */
export interface WfReplayEvent {
  type: 'reset' | 'node' | 'finish' | 'error'
  nodeUuid?: string
  status?: number
  remark?: string
}
