import { t } from '@/locales'

/**
 * T3-06：保存前校验 + 连线合法性
 * 校验规则与属性面板的「红星必填标记」保持一致（MailSend 的三个红星字段等）。
 */

export type WfValidationErrorKind = 'required' | 'selfLoop' | 'duplicateEdge'

export interface WfValidationError {
  nodeUuid: string
  nodeTitle: string
  kind: WfValidationErrorKind
  /** 出错的字段/边说明 */
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

/**
 * 校验工作流：空必填字段 + 自环连线 + 重复连线
 */
export function validateWorkflow(workflow: Workflow.WorkflowInfo): WfValidationError[] {
  const errors: WfValidationError[] = []

  // 1. 空必填字段（与属性面板红星标记一致）
  for (const node of workflow.nodes) {
    const cfg = (node.nodeConfig || {}) as Record<string, unknown>
    const pushRequired = (value: unknown, label: string) => {
      if (isEmptyValue(value))
        errors.push({ nodeUuid: node.uuid, nodeTitle: node.title, kind: 'required', fieldLabel: label })
    }

    switch (node.wfComponent?.name) {
      case 'MailSend':
        pushRequired(cfg.to_mails, t('workflow.recipientEmail'))
        pushRequired(cfg.subject, t('workflow.emailSubject'))
        pushRequired(cfg.content, t('workflow.emailContent'))
        break
      case 'KnowledgeRetrieval':
        pushRequired(cfg.knowledge_base_uuid, t('workflow.knowledgeBaseLabel'))
        break
      case 'Database':
        pushRequired(cfg.datasource_uuid, t('workflow.datasourceLabel'))
        pushRequired(cfg.sql, 'SQL')
        break
      case 'HttpRequest':
        pushRequired(cfg.url, 'URL')
        break
      case 'Agent':
        pushRequired(cfg.character_uuid, t('workflow.characterLabel'))
        break
      case 'Skill':
        pushRequired(cfg.skill_uuid, t('workflow.componentTitle.Skill'))
        break
      default:
        break
    }
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
