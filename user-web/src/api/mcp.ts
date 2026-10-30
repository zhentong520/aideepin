import { get, post } from '@/utils/request'

function mcpSearch<T>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/mcp/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function userMcpList<T>(currentPage: number, pageSize: number) {
  return get<T>({
    url: `/user/mcp/list?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function userMcpSaveOrUpdate<T>(data: Mcp.UserMcpUpdateReq) {
  return post<T>({
    url: '/user/mcp/saveOrUpdate',
    data,
  })
}

// 当前用户已启用的 MCP 服务（含服务信息），供工作流 MCP 节点选择器使用
function userMcpEnabled<T>() {
  return get<T>({
    url: '/user/mcp/enabled',
  })
}

// 当前用户可用的 MCP 工具清单；mcpIds 为空表示查询全部已启用服务
function mcpTools<T>(mcpIds?: Array<string | number>) {
  const query = mcpIds && mcpIds.length > 0 ? `?mcpIds=${mcpIds.join(',')}` : ''
  return get<T>({
    url: `/user/mcp/tools${query}`,
  })
}

// 外部数据库数据源（Database MCP / Database 节点）
// 注意：数据源的新增/修改/删除已收归管理后台（/admin/db-datasource），用户端只保留只读接口
function datasourceList<T>() {
  return get<T>({
    url: '/db-datasource/list',
  })
}

function datasourceTables<T>(uuid: string) {
  return get<T>({
    url: `/db-datasource/tables?uuid=${uuid}`,
  })
}

function datasourceColumns<T>(uuid: string, table: string) {
  return get<T>({
    url: `/db-datasource/columns?uuid=${uuid}&table=${table}`,
  })
}

// 只读查询：body = { uuid, sql, limit }
function datasourceQuery<T>(data: { uuid: string; sql: string; limit?: number }) {
  return post<T>({
    url: '/db-datasource/query',
    data,
  })
}

export default {
  mcpSearch,
  userMcpList,
  userMcpSaveOrUpdate,
  userMcpEnabled,
  mcpTools,
  datasourceList,
  datasourceTables,
  datasourceColumns,
  datasourceQuery,
}
