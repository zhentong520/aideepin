import { http } from '@/utils/http/axios'

export function skillSearch(params: { current: number; size: number }, keyword?: string) {
  const query = `currentPage=${params.current}&pageSize=${params.size}${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}`
  return http.request({
    url: `/admin/skill/search?${query}`,
    method: 'post',
  })
}

export function skillDetail(uuid: string) {
  return http.request({
    url: `/admin/skill/${uuid}`,
    method: 'get',
  })
}

export function skillAdd(data: { name: string; title?: string; description: string; version?: string; content: string }) {
  const qs = `name=${encodeURIComponent(data.name)}&title=${encodeURIComponent(data.title || '')}&description=${encodeURIComponent(data.description)}&version=${encodeURIComponent(data.version || '')}&content=${encodeURIComponent(data.content)}`
  return http.request({
    url: `/admin/skill/add?${qs}`,
    method: 'post',
  })
}

export function skillEdit(data: { id: number; name: string; title?: string; description: string; version?: string; content: string }) {
  const qs = `id=${data.id}&name=${encodeURIComponent(data.name)}&title=${encodeURIComponent(data.title || '')}&description=${encodeURIComponent(data.description)}&version=${encodeURIComponent(data.version || '')}&content=${encodeURIComponent(data.content)}`
  return http.request({
    url: `/admin/skill/edit?${qs}`,
    method: 'post',
  })
}

export function skillImportZip(formData: FormData) {
  return http.request({
    url: '/admin/skill/importZip',
    method: 'post',
    data: formData,
  })
}

export function skillEnable(id: number, isEnable: boolean) {
  return http.request({
    url: `/admin/skill/enable?id=${id}&isEnable=${isEnable}`,
    method: 'post',
  })
}

export function skillDel(id: number) {
  return http.request({
    url: `/admin/skill/del/${id}`,
    method: 'post',
  })
}

/** 技能包文件清单（SKILL.md 置顶；手工创建的技能会带一个虚拟 SKILL.md） */
export function skillFiles(uuid: string) {
  return http.request({
    url: `/admin/skill/${uuid}/files`,
    method: 'get',
  })
}

/** 读取技能包内某个文件的内容（相对路径，如 scripts/count.py） */
export function skillFileContent(uuid: string, path: string) {
  return http.request({
    url: `/admin/skill/${uuid}/file?path=${encodeURIComponent(path)}`,
    method: 'get',
  })
}

/**
 * 保存技能包内文件（在线编辑 / 新建）。
 * 走 JSON body 而非 query：指令正文动辄数千字符，塞进 URL 会撞长度上限。
 */
export function skillSaveFile(uuid: string, path: string, content: string) {
  return http.request({
    url: `/admin/skill/${uuid}/file`,
    method: 'post',
    data: { path, content },
  })
}

/** 删除技能包内文件（虚拟 SKILL.md 不可删，后端会拒绝） */
export function skillDeleteFile(uuid: string, path: string) {
  return http.request({
    url: `/admin/skill/${uuid}/file?path=${encodeURIComponent(path)}`,
    method: 'delete',
  })
}

/** 重命名 / 移动技能包内文件；newPath 带目录即等同移动（目标已存在时后端拒绝） */
export function skillRenameFile(uuid: string, path: string, newPath: string) {
  return http.request({
    url: `/admin/skill/${uuid}/file/rename`,
    method: 'post',
    data: { path, newPath },
  })
}

/** 批量启停（一条 SQL，避免逐个请求出现一半成功一半失败） */
export function skillEnableBatch(ids: number[], isEnable: boolean) {
  return http.request({
    url: '/admin/skill/enableBatch',
    method: 'post',
    data: { ids, isEnable },
  })
}

/** 批量删除（软删除，磁盘上的技能包不动） */
export function skillDelBatch(ids: number[]) {
  return http.request({
    url: '/admin/skill/delBatch',
    method: 'post',
    data: { ids },
  })
}

/**
 * 导出技能包 zip。
 * 走原生响应（isReturnNativeResponse）以拿到 Blob 与 Content-Disposition；
 * 默认的 transformRequestData 会按 {code,success,data} 解析，对二进制响应不适用。
 */
export function skillExportZip(uuid: string) {
  return http.request(
    {
      url: `/admin/skill/${uuid}/export`,
      method: 'get',
      responseType: 'blob',
    },
    { isReturnNativeResponse: true, isShowMessage: false }
  )
}

/**
 * 批量导出：勾选的技能各放进一个以技能名命名的子目录，打成一个 zip。
 * 走 POST + JSON body 而非 GET query：uuid 列表可能很长，塞进 URL 有长度上限风险。
 */
export function skillExportBatch(uuids: string[]) {
  return http.request(
    {
      url: '/admin/skill/exportBatch',
      method: 'post',
      data: { uuids },
      responseType: 'blob',
    },
    { isReturnNativeResponse: true, isShowMessage: false }
  )
}
