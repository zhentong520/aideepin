import { http } from '@/utils/http/axios'

/** T6-14 按 traceId 查全链路时间线（span 按 start_time 升序） */
function spans(traceId: string) {
  return http.request({
    url: `/admin/agent-trace/spans`,
    method: 'get',
    params: { traceId },
  })
}

/** 最近的 trace 列表（每个 trace 一条入口 span），不输 traceId 也能点开 */
function recent(limit = 20) {
  return http.request({
    url: `/admin/agent-trace/recent`,
    method: 'get',
    params: { limit },
  })
}

export default {
  spans,
  recent,
}
