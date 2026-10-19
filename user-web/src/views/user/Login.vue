<script setup lang='ts'>
import { v4 as uuidv4 } from 'uuid'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { NButton, NIcon, NImage, NInput, NModal, NSpace, NTabPane, NTabs, NTag, useMessage } from 'naive-ui'
// import { useRouter } from 'vue-router'
import { CheckmarkCircle } from '@vicons/ionicons5'
import api from '@/api'
import { t } from '@/locales'
import { useAppStore, useAuthStore, useUserStore } from '@/store'

import Icon403 from '@/icons/403.vue'

interface LoginResp {
  token: string
  name: string
  email: string
  uuid: string
  locale: string
  captchaId?: string
}

// const router = useRouter()
const authStore = useAuthStore()
const userStore = useUserStore()
const appStore = useAppStore()
const ms = useMessage()
const loading = ref(false)
const email = ref('')
const password = ref('')
const loginCaptchaId = ref('')
const loginCaptchaCode = ref('')
const registerCaptchaId = ref(uuidv4().replace(/-/g, ''))
const registerCaptchaCode = ref('')
const captchaTimestamp = ref(new Date().getTime())
const activeTab = ref('login')
const confirmPassword = ref('')
const disabled = computed(() => !email.value.trim() || loading.value)
const registerReturnSuccess = ref<boolean>(false)
const registerReturnMsg = ref<string>('')
const resetPasswordReturnMsg = ref<string>('')

/**
 * 注册邮箱验证码开关 | Feature switch for email-code registration
 *
 * 默认 false：注册走「图形验证码 + 激活邮件链接」的原流程。
 * 置为 true 后：注册改为「邮箱验证码」，表单出现「获取验证码」按钮与验证码输入框，
 * 校验通过即激活账号并直接登录。
 * 注意：后端 adi.register.email-code.enable 必须同步打开，否则接口会返回「未开启邮箱验证码注册」。
 */
const registerEmailCodeEnabled = import.meta.env.VITE_REGISTER_EMAIL_CODE === 'true'

const emailCode = ref('')
const emailCodeCountdown = ref(0)
const emailCodeSending = ref(false)
let emailCodeTimer: ReturnType<typeof setInterval> | undefined

const emailCodeButtonText = computed(() => {
  if (emailCodeCountdown.value > 0)
    return `${emailCodeCountdown.value}${t('common.secondsShort')}`
  return t('common.sendEmailCode')
})

const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function startEmailCodeCountdown(seconds: number) {
  stopEmailCodeCountdown()
  emailCodeCountdown.value = seconds
  emailCodeTimer = setInterval(() => {
    emailCodeCountdown.value -= 1
    if (emailCodeCountdown.value <= 0)
      stopEmailCodeCountdown()
  }, 1000)
}

function stopEmailCodeCountdown() {
  if (emailCodeTimer) {
    clearInterval(emailCodeTimer)
    emailCodeTimer = undefined
  }
  emailCodeCountdown.value = 0
}

async function handleSendEmailCode() {
  const mail = email.value.trim()
  if (!mail) {
    ms.error(t('common.pleaseInputEmailFirst'))
    return
  }
  if (!emailReg.test(mail)) {
    ms.error(t('common.emailInvalid'))
    return
  }
  try {
    emailCodeSending.value = true
    await api.sendRegisterEmailCode(mail)
    ms.success(t('common.emailCodeSent'))
    registerReturnSuccess.value = true
    registerReturnMsg.value = t('common.emailCodeSent')
    startEmailCodeCountdown(60)
  } catch (error: any) {
    registerReturnSuccess.value = false
    registerReturnMsg.value = error.message ?? 'error'
    ms.error(error.message ?? 'error')
  } finally {
    emailCodeSending.value = false
  }
}

onUnmounted(() => stopEmailCodeCountdown())

function handlePress(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleLogin()
  }
}

const confirmPasswordStatus = computed(() => {
  if (!password.value || !confirmPassword.value)
    return undefined
  return password.value !== confirmPassword.value ? 'error' : 'success'
})

async function handleLogin() {
  const name = email.value.trim()
  const pwd = password.value.trim()
  if (!name || !pwd) {
    ms.error(t('common.emailOrPasswordEmpty'))
    return
  }

  if (loginCaptchaId.value && !loginCaptchaCode.value) {
    ms.error(t('common.pleaseInputCaptcha'))
    return
  }
  try {
    loading.value = true
    const result = await api.login<LoginResp>(email.value, pwd, loginCaptchaId.value, loginCaptchaCode.value)
    await authStore.setToken(result.data.token)
    await userStore.updateUserInfo(result.data)
    appStore.applyUserLocale(result.data.locale)
    ms.success('success')
  } catch (error: any) {
    console.error('login error', error)
    ms.error(error.message ?? 'error')
    if (error.data?.captchaId) {
      // 显示验证码
      loginCaptchaId.value = error.data.captchaId
    }
    password.value = ''
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const mail = email.value.trim()
  const pwd = password.value.trim()
  const confirmPwd = confirmPassword.value.trim()

  if (!mail || !pwd || !confirmPwd || pwd !== confirmPwd) {
    ms.error(t('common.passwordNotMatch'))
    return
  }

  // 开关打开时改走邮箱验证码：不校验图形验证码，邮箱验证码通过即激活账号并直接登录
  if (registerEmailCodeEnabled) {
    const code = emailCode.value.trim()
    if (!code) {
      ms.error(t('common.pleaseInputEmailCode'))
      return
    }
    try {
      loading.value = true
      const result = await api.registerByEmailCode<LoginResp>(mail, pwd, code)
      registerReturnSuccess.value = true
      registerReturnMsg.value = ''
      await authStore.setToken(result.data.token)
      await userStore.updateUserInfo(result.data)
      appStore.applyUserLocale(result.data.locale)
      stopEmailCodeCountdown()
      ms.success(t('common.success'))
    } catch (error: any) {
      registerReturnSuccess.value = false
      registerReturnMsg.value = error.message ?? 'error'
      ms.error(error.message ?? 'error')
    } finally {
      loading.value = false
    }
    return
  }

  try {
    loading.value = true
    const result = await api.register(mail, pwd, registerCaptchaId.value, registerCaptchaCode.value)
    registerReturnSuccess.value = true
    registerReturnMsg.value = result.data as string
    ms.success(registerReturnMsg.value)
  } catch (error: any) {
    registerReturnSuccess.value = false
    ms.error(error.message ?? 'error')
    registerReturnMsg.value = error.message ?? 'error'
  } finally {
    loading.value = false
  }
}

async function handleForgotPassword() {
  const name = email.value.trim()

  if (!name)
    return

  try {
    loading.value = true
    const result = await api.passwordFind(name)
    ms.success(result.data as string)
    resetPasswordReturnMsg.value = result.data as string
  } catch (error: any) {
    ms.error(error.message ?? 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  console.info('login,onmounted')
})
</script>

<template>
  <NModal v-model:show="authStore.showLoginModal" style="width: 90%; max-width: 640px">
    <div class="p-10 bg-white rounded dark:bg-slate-800">
      <div class="space-y-4">
        <header class="space-y-2">
          <!-- <p class="text-base text-center text-slate-500 dark:text-slate-500">
            {{ t('common.unauthorizedTips') }}
          </p> -->
          <Icon403 class="w-[200px] m-auto" />
        </header>

        <NTabs v-model:value="activeTab" default-value="login" type="line">
          <NTabPane name="login" :tab="t('common.login')">
            <NSpace vertical>
              <NInput v-model:value="email" type="text" :placeholder="t('common.email')" :input-props="{ autocomplete: 'on' }" />
              <NInput
                v-model:value="password" type="password" show-password-on="click"
                :placeholder="t('common.password')" @keypress="handlePress"
              />
              <NSpace :wrap-item="false">
                <NInput
                  v-if="loginCaptchaId" v-model:value="loginCaptchaCode" style="flex:1;height:40px;"
                  :placeholder="t('common.captcha')"
                />
                <NImage
                  v-if="loginCaptchaId"
                  :src="`/api/auth/login/captcha?captchaId=${loginCaptchaId}&t_${captchaTimestamp}`"
                  @click="captchaTimestamp = new Date().getTime()"
                />
              </NSpace>
              <NSpace justify="space-between">
                <NButton type="primary" size="medium" :disabled="disabled" :loading="loading" @click="handleLogin">
                  {{ t('common.login') }}
                </NButton>
                <NButton text type="primary" @click="activeTab = 'forgotPassword'">
                  {{ t('common.forgotPassword') }}
                </NButton>
              </NSpace>
            </NSpace>
          </NTabPane>

          <NTabPane name="register" :tab="t('common.register')">
            <NSpace vertical>
              <NInput v-model:value="email" type="text" size="large" :placeholder="t('common.email')" />
              <NInput
                v-model:value="password" type="password" size="large" show-password-on="click"
                :placeholder="t('common.password')"
              />
              <NInput
                v-model:value="confirmPassword" type="password" size="large" show-password-on="click"
                :placeholder="t('common.confirmPassword')" :status="confirmPasswordStatus"
              />
              <NSpace v-if="registerEmailCodeEnabled" :wrap-item="false">
                <NInput
                  v-model:value="emailCode" size="large" style="flex:1;height:40px;"
                  :placeholder="t('common.emailCode')" :maxlength="10"
                />
                <NButton
                  size="large" style="height:40px;"
                  :disabled="emailCodeCountdown > 0 || emailCodeSending"
                  :loading="emailCodeSending" @click="handleSendEmailCode"
                >
                  {{ emailCodeButtonText }}
                </NButton>
              </NSpace>
              <NSpace v-else :wrap-item="false">
                <NInput
                  v-if="registerCaptchaId" v-model:value="registerCaptchaCode" size="large"
                  style="flex:1;height:40px;" :placeholder="t('common.captcha')"
                />
                <NImage
                  v-if="registerCaptchaId" object-fit="fill"
                  :src="`/api/auth/register/captcha?captchaId=${registerCaptchaId}&_t=${captchaTimestamp}`"
                  @click="captchaTimestamp = new Date().getTime()"
                />
              </NSpace>
              <NSpace v-if="registerReturnMsg">
                <NTag :type="registerReturnSuccess ? 'success' : 'error'">
                  {{ registerReturnMsg }}
                  <template #icon>
                    <NIcon :component="CheckmarkCircle" />
                  </template>
                </NTag>
              </NSpace>
              <NButton
                type="primary" size="medium"
                :disabled="disabled || password !== confirmPassword || (registerEmailCodeEnabled && !emailCode.trim())"
                :loading="loading" @click="handleRegister"
              >
                {{ t('common.register') }}
              </NButton>
            </NSpace>
          </NTabPane>

          <NTabPane name="forgotPassword" tab="">
            <NSpace vertical>
              <NInput v-model:value="email" type="text" size="large" :placeholder="t('common.email')" />
              <NTag v-if="resetPasswordReturnMsg" :type="resetPasswordReturnMsg ? 'success' : 'error'">
                {{ resetPasswordReturnMsg }}
                <template #icon>
                  <NIcon :component="CheckmarkCircle" />
                </template>
              </NTag>
              <NButton type="primary" :disabled="email.length <= 0" :loading="loading" @click="handleForgotPassword">
                {{ t('common.resetPassword') }}
              </NButton>
            </NSpace>
          </NTabPane>
        </NTabs>
      </div>
    </div>
  </NModal>
</template>
