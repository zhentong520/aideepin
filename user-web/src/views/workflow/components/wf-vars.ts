import api from '@/api'
import { t } from '@/locales'
import i18n from '@/locales'

/**
 * T3-13 字段级变量穿梭框支撑工具
 * - available-vars 拉取与缓存（按 wfUuid+nodeUuid，30s TTL）
 * - 变量树展平 / 分组
 * - validate-template 调用与错误码文案映射（A0077/A0078）
 */

export interface WfVarFlatItem {
  /** 完整引用路径，如 question.title */
  path: string
  /** 根变量名（refInput.name） */
  root: string
  type: string
  example?: unknown
  /** 来源节点标题（用于分组展示） */
  sourceTitle: string
}

const varsCache = new Map<string, { at: number, list: Workflow.WfAvailableVar[] }>()
const VARS_CACHE_TTL = 30_000

/** 拉取指定节点的可用变量树（带 30s 缓存，失败返回空数组不打断输入） */
export async function fetchAvailableVars(wfUuid: string, nodeUuid: string): Promise<Workflow.WfAvailableVar[]> {
  const key = `${wfUuid}|${nodeUuid}`
  const hit = varsCache.get(key)
  if (hit && Date.now() - hit.at < VARS_CACHE_TTL)
    return hit.list

  try {
    const { data } = await api.workflowAvailableVars<Workflow.WfAvailableVar[]>(wfUuid, nodeUuid)
    const list = Array.isArray(data) ? data : []
    varsCache.set(key, { at: Date.now(), list })
    return list
  } catch {
    return []
  }
}

export function clearAvailableVarsCache(wfUuid?: string) {
  if (!wfUuid) {
    varsCache.clear()
    return
  }
  for (const key of varsCache.keys()) {
    if (key.startsWith(`${wfUuid}|`))
      varsCache.delete(key)
  }
}

/** 变量树展平：根变量 + 子字段（叶子才可插入；对象类型无 children 时也可整体插入） */
export function flattenVarTree(list: Workflow.WfAvailableVar[]): WfVarFlatItem[] {
  const items: WfVarFlatItem[] = []
  for (const root of list) {
    const sourceTitle = root.sourceNodeTitle || root.name
    items.push({ path: root.name, root: root.name, type: root.type, example: root.example, sourceTitle })
    for (const child of root.children || []) {
      items.push({ path: `${root.name}.${child.name}`, root: root.name, type: child.type, example: child.example, sourceTitle })
      // 仅展开到第 2 层（与后端 maxDepth 一致）
      for (const grand of child.children || [])
        items.push({ path: `${root.name}.${child.name}.${grand.name}`, root: root.name, type: grand.type, example: grand.example, sourceTitle })
    }
  }
  return items
}

/** 按来源节点分组（穿梭框分组渲染） */
export function groupVarItems(items: WfVarFlatItem[]): Array<{ key: string, title: string, items: WfVarFlatItem[] }> {
  const groups = new Map<string, WfVarFlatItem[]>()
  for (const item of items) {
    const list = groups.get(item.sourceTitle) || []
    list.push(item)
    groups.set(item.sourceTitle, list)
  }
  return Array.from(groups.entries()).map(([title, groupItems]) => ({
    key: title,
    title,
    items: groupItems,
  }))
}

/** 错误码 → 文案（后端 message 为空时的兜底） */
export function varErrorText(code: string): string {
  if (code === 'A0077')
    return t('workflow.varErrorRootNotFound')
  if (code === 'A0078')
    return t('workflow.varErrorPathNotFound')
  return t('workflow.varErrorFallback')
}

export interface WfTemplateIssue {
  ref: string
  text: string
}

/** 调 validate-template 校验模板，返回可直接展示的错误列表（接口失败时静默放行） */
export async function validateTemplate(wfUuid: string, nodeUuid: string, template: string): Promise<WfTemplateIssue[]> {
  if (!template || !template.includes('{'))
    return []

  try {
    const { data } = await api.workflowValidateTemplate<Workflow.WfTemplateValidateResp>({
      wfUuid,
      nodeUuid,
      template,
    })
    if (!data || data.valid)
      return []

    return (data.errors || []).map(err => ({
      ref: err.ref,
      text: err.message || varErrorText(err.code),
    }))
  } catch {
    return []
  }
}

/** 取当前界面语言对应的描述文本（WfLocalizedText） */
export function localizedText(value: { zh: string, en: string }): string {
  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  return locale.startsWith('en') ? value.en : value.zh
}
