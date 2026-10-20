import { get, post } from '@/utils/request'
import { commonSseProcess } from './sse'

function workflowAdd<T>(data: { title: string; remark: string; isPublic: boolean }) {
  return post<T>({
    url: '/workflow/add',
    data,
  })
}

function workflowCopy<T>(wfUuid: string) {
  return post<T>({
    url: `/workflow/copy/${wfUuid}`,
  })
}

function workflowUpdate<T>(data: Workflow.WorkflowUpdateReq) {
  return post<T>({
    url: '/workflow/update',
    data,
  })
}

function workflowDel<T>(uuid: string) {
  return post<T>({
    url: `/workflow/del/${uuid}`,
  })
}

function workflowSetPublic<T>(uuid: string, isPublic?: boolean) {
  return post<T>({
    url: `/workflow/set-public/${uuid}?isPublic=${isPublic}`,
  })
}

function workflowBaseInfoUpdate<T>(data: { uuid: string; title: string; remark: string }) {
  return post<T>({
    url: '/workflow/base-info/update',
    data,
  })
}

function workflowRun(params: {
  options: { uuid: string; inputs: Workflow.UserInput[] }
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  messageReceived: (chunk: string, eventName: string) => void
  thinkingDataReceived: (chunk: string) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  commonSseProcess(`/api/workflow/run/${params.options.uuid}`, params)
}

function workflowRuntimeResume<T>(params: {
  runtimeUuid: string
  feedbackContent: string
}) {
  return post<T>({
    url: `/workflow/runtime/resume/${params.runtimeUuid}`,
    data: {
      ...params,
    },
  })
}

function workflowComponents<T>() {
  return get<T>({
    url: '/workflow/public/component/list',
  })
}

function workflowSearchMine<T>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/workflow/mine/search?${search}currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowSearchPublic<T>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/workflow/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowRuntimes<T>(wfUuid: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/workflow/runtime/page?wfUuid=${wfUuid}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowRuntimeNodes<T>(wfRuntimeUuid: string) {
  return get<T>({
    url: `/workflow/runtime/nodes/${wfRuntimeUuid}`,
  })
}

function workflowRuntimesClear<T>() {
  return post<T>({
    url: '/workflow/runtime/clear',
  })
}

function workflowOperators<T>() {
  return get<T>({
    url: '/workflow/public/operators',
  })
}

function workflowRuntimeDelete<T>(wfRuntimeUuid: string) {
  return get<T>({
    url: `/workflow/runtime/del/${wfRuntimeUuid}`,
  })
}

// T3-13：指定节点的可用变量树（字段级展开 2 层）
function workflowAvailableVars<T>(wfUuid: string, nodeUuid: string) {
  return get<T>({
    url: `/workflow/available-vars?wfUuid=${wfUuid}&nodeUuid=${nodeUuid}`,
  })
}

// T3-13：模板表达式校验（{{var.path}} 引用写错立即提示）
function workflowValidateTemplate<T>(data: { wfUuid: string; nodeUuid: string; template: string }) {
  return post<T>({
    url: '/workflow/validate-template',
    data,
  })
}

// T3-14：导出工作流 JSON
function workflowExport<T>(wfUuid: string) {
  return get<T>({
    url: `/workflow/${wfUuid}/export`,
  })
}

// T3-14：导入工作流 JSON（缺失知识包报 A0079）；body 为用户上传的任意 JSON
function workflowImport<T>(data: unknown) {
  return post<T>({
    url: '/workflow/import',
    data,
  })
}

// T9-05：版本历史（versionNo 降序 + diff 概要）
function workflowVersionList<T>(wfUuid: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/workflow/version/list?wfUuid=${wfUuid}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

// T9-05：一键回滚（回滚本身生成新版本）
function workflowVersionRollback<T>(wfUuid: string, versionNo: number) {
  return post<T>({
    url: `/workflow/version/rollback/${wfUuid}?versionNo=${versionNo}`,
  })
}

// T9-01：触发器列表（归属校验）
function workflowTriggerList<T>(wfUuid: string) {
  return get<T>({
    url: `/workflow/trigger/list?wfUuid=${wfUuid}`,
  })
}

// T9-01：保存触发器（新建/更新；schedule 校验 cron）
function workflowTriggerSave<T>(data: Workflow.WfTriggerSaveReq) {
  return post<T>({
    url: '/workflow/trigger/save',
    data,
  })
}

// T9-01：启用/停用触发器
function workflowTriggerEnable<T>(uuid: string, enable: boolean) {
  return post<T>({
    url: `/workflow/trigger/enable/${uuid}?enable=${enable}`,
  })
}

// T9-01：删除触发器（软删）
function workflowTriggerDel<T>(uuid: string) {
  return post<T>({
    url: `/workflow/trigger/del/${uuid}`,
  })
}

// T9-06：内置官方模板清单
function workflowTemplateList<T>() {
  return get<T>({
    url: '/workflow/template/list',
  })
}

// T9-06：从模板新建工作流（名字冲突自动加后缀）
function workflowTemplateCreate<T>(data: { templateId: string; title: string; remark?: string }) {
  return post<T>({
    url: '/workflow/template/create',
    data,
  })
}

export default {
  workflowAdd,
  workflowCopy,
  workflowUpdate,
  workflowDel,
  workflowSetPublic,
  workflowBaseInfoUpdate,
  workflowRun,
  workflowComponents,
  workflowSearchMine,
  workflowSearchPublic,
  workflowRuntimes,
  workflowRuntimeNodes,
  workflowRuntimesClear,
  workflowOperators,
  workflowRuntimeDelete,
  workflowRuntimeResume,
  workflowAvailableVars,
  workflowValidateTemplate,
  workflowExport,
  workflowImport,
  workflowVersionList,
  workflowVersionRollback,
  workflowTriggerList,
  workflowTriggerSave,
  workflowTriggerEnable,
  workflowTriggerDel,
  workflowTemplateList,
  workflowTemplateCreate,
}
