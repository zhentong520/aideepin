import { post } from '@/utils/request'

function login<T>(email: string, password: string, captchaId: string, captchaCode: string) {
  return post<T>({
    url: '/auth/login',
    data: { email, password, captchaId, captchaCode },
  })
}

function register<T>(email: string, password: string, captchaId: string, captchaCode: string) {
  return post<T>({
    url: '/auth/register',
    data: { email, password, captchaId, captchaCode },
  })
}

// 注册邮箱验证码开关打开后的两个接口 | Used only when VITE_REGISTER_EMAIL_CODE=true
function sendRegisterEmailCode<T>(email: string) {
  return post<T>({
    url: '/auth/register/email-code',
    data: { email },
  })
}

function registerByEmailCode<T>(email: string, password: string, emailCode: string) {
  return post<T>({
    url: '/auth/register/by-email-code',
    data: { email, password, emailCode },
  })
}

function logout<T>() {
  return post<T>({
    url: '/user/logout',
  })
}

function userEdit<T>(config: User.Config) {
  return post<T>({
    url: '/user/edit',
    data: config,
  })
}

/**
 * S-07：通过找回密码 code 设置新密码（原实现为后端把口令重置为固定值，前端无此调用）
 */
function passwordReset<T>(code: string, newPassword: string) {
  return post<T>({
    url: '/auth/password/reset',
    data: { code, newPassword },
  })
}

function passwordFind<T>(email: string) {
  return post<T>({
    url: `/auth/password/forgot?email=${email}`,
  })
}

function modifyPassword<T>(oldPassword: string, newPassword: string) {
  return post<T>({
    url: '/user/password/modify',
    data: { oldPassword, newPassword },
  })
}

export default {
  login,
  register,
  sendRegisterEmailCode,
  registerByEmailCode,
  logout,
  passwordReset,
  passwordFind,
  modifyPassword,
  userEdit,
}
