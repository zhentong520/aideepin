import type { FormItemRule } from 'naive-ui'
import { t } from '@/locales'

/**
 * 表单校验规则集中管理（T5-08）
 *
 * 与后端校验语义对齐（server/adi-common/.../dto/RegisterReq.java 等）：
 * - email   → @Email 格式校验
 * - password→ @Size(min = 6)
 * - captcha → @Length(min = 4, max = 4)
 * - 邮箱验证码 → @Length(min = 4, max = 10)
 * - 标题类字段 → @NotBlank（必填），长度上限为前端防御性约束
 */

/** 邮箱格式（与后端 @Email 语义对齐的宽松校验） */
export const EMAIL_REG = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 密码最小长度（后端 @Size(min = 6)） */
export const PASSWORD_MIN = 6

const INPUT_TRIGGER: FormItemRule['trigger'] = ['blur', 'input']

/** 必填 */
export function requiredRule(field: string): FormItemRule {
  return {
    required: true,
    message: t('common.fieldRequired', { field }),
    trigger: INPUT_TRIGGER,
  }
}

/** 最大长度 */
export function maxLengthRule(field: string, max: number): FormItemRule {
  return {
    validator: (_rule: FormItemRule, value: string) =>
      !value || value.length <= max ? true : new Error(t('common.fieldMaxLength', { field, max })),
    trigger: INPUT_TRIGGER,
  }
}

/** 邮箱：必填 + 格式 */
export function emailRules(field = 'Email'): FormItemRule[] {
  return [
    requiredRule(field),
    {
      validator: (_rule: FormItemRule, value: string) =>
        !value || EMAIL_REG.test(value) ? true : new Error(t('common.emailInvalid')),
      trigger: INPUT_TRIGGER,
    },
  ]
}

/** 密码：必填 + 最小长度（后端 @Size(min=6)） */
export function passwordRules(field = 'Password'): FormItemRule[] {
  return [
    requiredRule(field),
    {
      validator: (_rule: FormItemRule, value: string) =>
        !value || value.length >= PASSWORD_MIN
          ? true
          : new Error(t('common.passwordMinLength', { min: PASSWORD_MIN })),
      trigger: INPUT_TRIGGER,
    },
  ]
}

/** 图形验证码：必填 + 4 位（后端 @Length(min=4, max=4)） */
export function captchaRules(field = 'Captcha'): FormItemRule[] {
  return [
    requiredRule(field),
    {
      validator: (_rule: FormItemRule, value: string) =>
        !value || value.length === 4
          ? true
          : new Error(t('common.fieldLengthRange', { field, min: 4, max: 4 })),
      trigger: INPUT_TRIGGER,
    },
  ]
}

/** 邮箱验证码：必填 + 4-10 位（后端 @Length(min=4, max=10)） */
export function emailCodeRules(field = 'EmailCode'): FormItemRule[] {
  return [
    requiredRule(field),
    {
      validator: (_rule: FormItemRule, value: string) =>
        !value || (value.length >= 4 && value.length <= 10)
          ? true
          : new Error(t('common.fieldLengthRange', { field, min: 4, max: 10 })),
      trigger: INPUT_TRIGGER,
    },
  ]
}

/** 确认密码：与密码字段一致 */
export function confirmPasswordRule(getPassword: () => string, field = 'ConfirmPassword'): FormItemRule {
  return {
    validator: (_rule: FormItemRule, value: string) =>
      !value || value === getPassword() ? true : new Error(t('common.passwordNotMatch')),
    trigger: INPUT_TRIGGER,
  }
}

/** 标题类字段：必填 + 最大长度（默认 100，后端多为 @NotBlank） */
export function titleRules(field: string, max = 100): FormItemRule[] {
  return [requiredRule(field), maxLengthRule(field, max)]
}
