import { http } from '@/utils/http/axios'

function componentSearch(data: { isEnable?: boolean }, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/workflow/component/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

function componentAddOrUpdate(data: {
  uuid: string
  title: string
  remark: string
  displayOrder: number
  isEnable: boolean
}) {
  return http.request({
    url: '/admin/workflow/component/addOrUpdate',
    method: 'post',
    data,
  })
}

function componentEnable(params: { uuid: string; isEnable: boolean }) {
  return http.request({
    url: `/admin/workflow/component/enable?uuid=${params.uuid}&isEnable=${params.isEnable}`,
    method: 'post',
  })
}

function componentDel(data: { uuid: string }) {
  return http.request({
    url: `/admin/workflow/component/del/${data.uuid}`,
    method: 'post',
  })
}

function search(
  data: { title?: string; isPublic?: boolean },
  params: { current: number; size: number }
) {
  return http.request({
    url: `/admin/workflow/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

function add(data: { title: string; remark?: string; isPublic?: boolean }) {
  return http.request({
    url: '/workflow/add',
    method: 'post',
    data,
  })
}

function updateBaseInfo(data: { uuid: string; title: string; remark: string }) {
  return http.request({
    url: '/workflow/base-info/update',
    method: 'post',
    data,
  })
}

function del(uuid: string) {
  return http.request({
    url: `/workflow/del/${uuid}`,
    method: 'post',
  })
}

function setPublic(uuid: string, isPublic: boolean) {
  return http.request({
    url: `/workflow/set-public/${uuid}?isPublic=${isPublic}`,
    method: 'post',
  })
}

function setEnable(params: { uuid: string; isEnable: boolean }) {
  return http.request({
    url: '/admin/workflow/enable',
    method: 'post',
    params,
  })
}

/**
 * 存量凭据回填（S-05 配套运维）：扫描工作流节点配置 jsonb 中的敏感键值，
 * 加密写入 adi_credential，原字段置空 + 写 credential_uuid；幂等，返回迁移节点数。
 */
function credentialBackfill() {
  return http.request({
    url: '/admin/credential/backfill',
    method: 'post',
  })
}

export default {
  componentSearch,
  componentAddOrUpdate,
  componentEnable,
  componentDel,
  search,
  add,
  updateBaseInfo,
  del,
  setPublic,
  setEnable,
  credentialBackfill,
}
