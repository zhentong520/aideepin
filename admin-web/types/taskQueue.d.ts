export interface TaskQueueInfo {
  id: number
  taskType: string
  payload: Record<string, unknown> | null
  status: string
  priority: number
  retryCount: number
  maxRetry: number
  nextRunAt: string
  ownerInstance: string
  lastError: string
  createdBy: number
  createTime: string
  updateTime: string
}

export interface TaskQueueSearchReq {
  taskType?: string
  status?: string
}
