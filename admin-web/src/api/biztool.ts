import { http } from '@/utils/http/axios'

/**
 * 业务插件目录 API（管理端）。
 *
 * 后端动态枚举容器内所有 BizToolProvider Spring bean——
 * 新增业务插件只需实现 Provider 并注册为 bean，重启后自动出现在这里，
 * 智能体配置页据此渲染「业务插件」勾选组。这正是拔插式的另一半：
 * 不需要维护任何注册表文件。
 */
function fetchBizTools() {
  return http.request({ url: '/admin/biztool/list', method: 'get' })
}

export default {
  fetchBizTools,
}
