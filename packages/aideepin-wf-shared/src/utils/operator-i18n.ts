/**
 * 条件分支操作符本地化标签。
 *
 * 后端 OperatorEnum 的 desc 固定英文（Contains / Greater Than …），
 * 前端按 operator.name 映射宿主词条 workflow.operator.*；
 * 词条缺失时回退后端英文 desc，保证两端（zh/en）都不出现原始 key。
 */
import { t } from '#wf-shared-vendor'

const OPERATOR_KEY_MAP: Record<string, string> = {
  'contains': 'contains',
  'not contains': 'notContains',
  'start with': 'startWith',
  'end with': 'endWith',
  'empty': 'empty',
  'not empty': 'notEmpty',
  '=': 'equal',
  '!=': 'notEqual',
  '>': 'greater',
  '>=': 'greaterOrEqual',
  '<': 'less',
  '<=': 'lessOrEqual',
}

/** 取操作符的本地化标签（name=后端 OperatorEnum.name，desc=后端英文描述） */
export function operatorLabel(name: string, desc: string): string {
  const key = OPERATOR_KEY_MAP[String(name || '').trim()]
  if (!key)
    return desc
  const i18nKey = `workflow.operator.${key}`
  const text = t(i18nKey)
  // vue-i18n 词条缺失时返回 key 本身 —— 此时回退英文 desc
  return text === i18nKey ? desc : text
}
