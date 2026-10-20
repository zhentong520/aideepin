import { http } from '@/utils/http/axios'

function search(data: any, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/token-monitor/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

/**
 * T6-13 模型成本看板聚合。
 * 返回 status: OK / NO_DATA / UNAVAILABLE —— 前端据此区分「暂无数据」与「数据不可用」。
 */
function costDashboard(beginTime: string, endTime: string) {
  return http.request({
    url: `/admin/token-monitor/cost-dashboard`,
    method: 'post',
    params: { beginTime, endTime },
  })
}

export default {
  search,
  costDashboard,
}
