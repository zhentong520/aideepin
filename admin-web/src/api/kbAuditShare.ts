import { http } from '@/utils/http/axios'

// 知识库「操作审计」与「细粒度共享」管理端接口（二开新增）。
// 后端权限层对管理员有旁路（isAdmin 直接放行），所以管理端可以查看/操作任意知识库的审计与共享，
// 不需要新增 admin 专属端点。http 客户端会自动拼接 /api 前缀并携带鉴权头。

// 操作审计流水（分页）
function listAudit(kbUuid: string, currentPage: number, pageSize: number) {
  return http.request({
    url: `/knowledge-base/audit/${kbUuid}?currentPage=${currentPage}&pageSize=${pageSize}`,
    method: 'get',
  })
}

// 已共享用户列表
function listShares(kbUuid: string) {
  return http.request({
    url: `/knowledge-base/share/list/${kbUuid}`,
    method: 'get',
  })
}

// 授权某用户访问（permission: READ / WRITE）
function grantShare(kbUuid: string, targetId: number, permission: string) {
  return http.request({
    url: `/knowledge-base/share/grant?kbUuid=${encodeURIComponent(kbUuid)}&targetId=${targetId}&permission=${permission}`,
    method: 'post',
  })
}

// 撤销对某用户的共享
function revokeShare(kbUuid: string, targetId: number) {
  return http.request({
    url: `/knowledge-base/share/revoke?kbUuid=${encodeURIComponent(kbUuid)}&targetId=${targetId}`,
    method: 'post',
  })
}

export default {
  listAudit,
  listShares,
  grantShare,
  revokeShare,
}
