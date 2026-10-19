import { t } from '@/locales'
import { getWfNodeDescriptor } from '@/views/workflow/components/nodes/descriptor'
import type { WfDescriptorField } from '@/views/workflow/components/nodes/descriptor'
import i18n from '@/locales'

/**
 * T3-06：保存前校验 + 连线合法性
 * T3-11：节点级实时校验（getNodeValidationIssues）与保存校验共用同一套 configSchema 驱动逻辑，
 *        保证「节点上的 ⚠ 徽标」与「保存拦截」口径一致。
 */

export type WfValidationErrorKind = 'required' | 'selfLoop' | 'duplicateEdge'

export interface WfValidationError {
  nodeUuid: string
  nodeTitle: string
  kind: WfValidationErrorKind
  /** 出错的字段/边说明 */
  fieldLabel: string
}

/** T3-11：节点徽标用的校验问题（只含字段缺失） */
export interface WfNodeIssue {
  fieldLabel: string
}

function isEmptyValue(value: unknown): boolean {
  if (value === null || value === undefined)
    return true
  if (typeof value === 'string')
    return value.trim() === ''
  if (Array.isArray(value))
    return value.length === 0
  return false
}

function fieldLabelText(field: WfDescriptorField): string {
  const locale = (i18n.global.locale as unknown as { value: string })?.value || 'zh-CN'
  return locale.startsWith('en') ? field.label.en : field.label.zh
}

/** showWhen 条件是否满足（与 SchemaForm 渲染条件一致） */
function showWhenSatisfied(field: WfDescriptorField, cfg: Record<string, unknown>): boolean {
  if (!field.showWhen || field.showWhen.length === 0)
    return true
  return field.showWhen.every((cond) => {
    const value = cond.field.split('.').reduce<unknown>((obj, key) => {
      if (obj && typeof obj === 'object')
        return (obj as Record<string, unknown>)[key]
      return undefined
    }, cfg)
    return value === cond.equals
  })
}

/** 点路径取值（MailSend smtp.host 等） */
function getPathValue(cfg: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((obj, key) => {
    if (obj && typeof obj === 'object')
      return (obj as Record<string, unknown>)[key]
    return undefined
  }, cfg)
}

/**
 * T3-11：单个节点的未配置项（configSchema 必填字段驱动，实时可调用）
 * - Start 豁免：入参按需配置，空入参是合法流程
 * - prompt/query 类「留空沿用上一节点输出」的字段在 descriptor 中 required=false，不会误报
 */
export function getNodeValidationIssues(node: Workflow.WorkflowNode): WfNodeIssue[] {
  const desc = getWfNodeDescriptor(node.wfComponent?.name)
  if (!desc || desc.type === 'Start')
    return []

  const cfg = (node.nodeConfig || {}) as Record<string, unknown>
  const issues: WfNodeIssue[] = []
  for (const field of desc.configSchema) {
    if (!field.required)
      continue
    if (!showWhenSatisfied(field, cfg))
      continue
    // 结构化编辑器字段（分类项/分支条件/入参表）由各自面板管理，顶层存在性检查即可
    if (isEmptyValue(getPathValue(cfg, field.name)))
      issues.push({ fieldLabel: fieldLabelText(field) })
  }
  return issues
}

/**
 * 校验工作流：空必填字段（T3-11 同源逻辑）+ 自环连线 + 重复连线
 */
export function validateWorkflow(workflow: Workflow.WorkflowInfo): WfValidationError[] {
  const errors: WfValidationError[] = []

  // 1. 空必填字段（与节点 ⚠ 徽标共用 getNodeValidationIssues）
  for (const node of workflow.nodes) {
    for (const issue of getNodeValidationIssues(node))
      errors.push({ nodeUuid: node.uuid, nodeTitle: node.title, kind: 'required', fieldLabel: issue.fieldLabel })
  }

  // 2. 自环连线
  for (const edge of workflow.edges) {
    if (edge.sourceNodeUuid && edge.sourceNodeUuid === edge.targetNodeUuid) {
      const node = workflow.nodes.find(n => n.uuid === edge.sourceNodeUuid)
      errors.push({
        nodeUuid: edge.sourceNodeUuid,
        nodeTitle: node?.title || edge.sourceNodeUuid,
        kind: 'selfLoop',
        fieldLabel: t('workflow.validationSelfLoop'),
      })
    }
  }

  // 3. 重复连线（同 source + sourceHandle + target）
  const seen = new Set<string>()
  for (const edge of workflow.edges) {
    const key = `${edge.sourceNodeUuid}|${edge.sourceHandle || ''}|${edge.targetNodeUuid}`
    if (seen.has(key)) {
      const node = workflow.nodes.find(n => n.uuid === edge.sourceNodeUuid)
      errors.push({
        nodeUuid: edge.sourceNodeUuid,
        nodeTitle: node?.title || edge.sourceNodeUuid,
        kind: 'duplicateEdge',
        fieldLabel: t('workflow.validationDuplicateEdge'),
      })
    }
    seen.add(key)
  }

  return errors
}
