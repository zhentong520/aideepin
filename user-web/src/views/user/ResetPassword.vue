<script setup lang='ts'>
import { computed, ref } from 'vue'
import { NButton, NCard, NForm, NFormItem, NInput, NResult, NSpace } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import api from '@/api'

/**
 * S-07：找回密码「设置新密码」页。
 * 邮件里的重置链接先打到后端 GET /auth/password/reset 预检，
 * 有效则 302 到本页（携带 code），由用户自设新密码后 POST 提交。
 * 原「后端把口令重置为固定值 123456」的流程已废弃。
 */
const route = useRoute()
const router = useRouter()
const ms = useMessage()

const code = computed(() => (route.query.code as string) || '')
const invalidCode = computed(() => route.query.invalid === '1')

const password = ref<string>('')
const confirmPassword = ref<string>('')
const loading = ref<boolean>(false)
const success = ref<boolean>(false)
const countdown = ref<number>(5)

async function handleSubmit() {
  if (!password.value || password.value.length < 6 || password.value.length > 64) {
    ms.error('密码长度需为 6-64 个字符')
    return
  }
  if (password.value !== confirmPassword.value) {
    ms.error('两次输入的密码不一致')
    return
  }
  try {
    loading.value = true
    await api.passwordReset(code.value, password.value)
    success.value = true
    const intervalId = setInterval(() => {
      if (countdown.value < 1) {
        clearInterval(intervalId)
        // 未登录访问 '/' 会自然弹出全局登录弹层（App.vue）
        router.replace('/')
      }
      else {
        countdown.value--
      }
    }, 1000)
  }
  catch (error: any) {
    ms.error(error?.message ?? '重置失败，链接可能已失效，请重新申请')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <NSpace justify="center" class="h-full items-center">
    <NResult
      v-if="invalidCode" class="m-0 p-0" status="error"
      title="链接无效" description="重置链接已失效或已被使用，请重新申请找回密码"
    >
      <template #footer>
        <NButton type="primary" @click="router.replace('/')">返回首页</NButton>
      </template>
    </NResult>

    <NResult
      v-else-if="success" class="m-0 p-0" status="success"
      title="密码已更新" :description="`请使用新密码登录，倒计时：${countdown}秒`"
    />

    <NCard v-else style="width: 380px" title="设置新密码" embedded>
      <NForm label-placement="top">
        <NFormItem label="新密码">
          <NInput
            v-model:value="password" type="password" show-password-on="click"
            placeholder="6-64 个字符" @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NFormItem label="确认新密码">
          <NInput
            v-model:value="confirmPassword" type="password" show-password-on="click"
            placeholder="再次输入新密码" @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NButton block type="primary" :loading="loading" @click="handleSubmit">
          提交
        </NButton>
      </NForm>
    </NCard>
  </NSpace>
</template>
