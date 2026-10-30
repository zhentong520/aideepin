import { http } from '@/utils/http/axios'

/**
 * 数据源管理（平台级共享资源，仅管理员维护）：
 * 增删改在 /admin/db-datasource；用户端 /db-datasource/* 只读。
 */
function datasourceList<T = any>() {
  return http.request<T>({
    url: '/admin/db-datasource/list',
    method: 'get',
  })
}

function datasourceSaveOrUpdate<T = any>(data: Workflow.DbDatasource) {
  return http.request<T>({
    url: '/admin/db-datasource/saveOrUpdate',
    method: 'post',
    data,
  })
}

function datasourceDelete<T = any>(uuid: string) {
  return http.request<T>({
    url: `/admin/db-datasource/delete?uuid=${uuid}`,
    method: 'post',
  })
}

function datasourceTest<T = any>(data: Workflow.DbDatasource) {
  return http.request<T>({
    url: '/admin/db-datasource/test',
    method: 'post',
    data,
  })
}

function datasourceTestByUuid<T = any>(uuid: string) {
  return http.request<T>({
    url: `/admin/db-datasource/test?uuid=${uuid}`,
    method: 'get',
  })
}

export default {
  datasourceList,
  datasourceSaveOrUpdate,
  datasourceDelete,
  datasourceTest,
  datasourceTestByUuid,
}
