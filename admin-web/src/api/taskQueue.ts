import { http } from '@/utils/http/axios'
import { TaskQueueSearchReq } from '/#/taskQueue'

/** 任务分页（update_time 倒序），按类型/状态可选过滤 */
function page(data: TaskQueueSearchReq, params: { current: number; size: number }) {
  return http.request({
    url: '/admin/task-queue/page',
    method: 'get',
    params: { ...data, current: params.current, size: params.size },
  })
}

/** 手工重试：DEAD/FAILED → PENDING，重试计数清零 */
function retry(id: number) {
  return http.request({
    url: `/admin/task-queue/${id}/retry`,
    method: 'post',
  })
}

/** 终止任务：PENDING/RUNNING → DEAD */
function terminate(id: number) {
  return http.request({
    url: `/admin/task-queue/${id}/terminate`,
    method: 'post',
  })
}

export default {
  page,
  retry,
  terminate,
}
