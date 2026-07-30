<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NSpin,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { fetchAuthConfig, fetchCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const captchaEnabled = ref(true)
const captchaId = ref('')
const captchaImg = ref('')
const captchaLoading = ref(false)

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules: FormRules = {
  username: { required: true, message: '请输入用户名', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' },
  captchaCode: {
    required: true,
    trigger: 'blur',
    validator: (_r, v: string) => {
      if (!captchaEnabled.value) return true
      if (!v) return new Error('请输入验证码')
      return true
    },
  },
}

async function loadCaptcha() {
  if (!captchaEnabled.value) return
  captchaLoading.value = true
  try {
    const data = await fetchCaptcha()
    captchaId.value = data.captchaId
    captchaImg.value = data.imageBase64.startsWith('data:')
      ? data.imageBase64
      : `data:image/png;base64,${data.imageBase64}`
    form.captchaCode = ''
  } catch {
    /* request already toasts */
  } finally {
    captchaLoading.value = false
  }
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.login({
      username: form.username.trim(),
      password: form.password,
      captchaId: captchaEnabled.value ? captchaId.value : undefined,
      captchaCode: captchaEnabled.value ? form.captchaCode.trim() : undefined,
    })
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/admin/dashboard'
    router.replace(redirect)
  } catch {
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const cfg = await fetchAuthConfig()
    captchaEnabled.value = !!cfg.captchaEnabled
  } catch {
    captchaEnabled.value = true
  }
  await loadCaptcha()
})
</script>

<template>
  <div class="login-page">
    <div class="login-bg" />
    <NCard class="login-card" :bordered="false">
      <div class="login-brand">LinkX</div>
      <p class="login-sub">管理后台</p>
      <NForm ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <NFormItem path="username" label="用户名">
          <NInput v-model:value="form.username" placeholder="请输入管理员账号" autocomplete="username" />
        </NFormItem>
        <NFormItem path="password" label="密码">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </NFormItem>
        <NFormItem v-if="captchaEnabled" path="captchaCode" label="验证码">
          <NSpace style="width: 100%" :wrap="false">
            <NInput v-model:value="form.captchaCode" placeholder="验证码" style="flex: 1" />
            <div class="captcha-box" @click="loadCaptcha">
              <NSpin :show="captchaLoading" size="small">
                <img v-if="captchaImg" :src="captchaImg" alt="captcha" />
                <span v-else>点击刷新</span>
              </NSpin>
            </div>
          </NSpace>
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="submit">登录</NButton>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 20% 20%, rgba(91, 141, 239, 0.18), transparent 55%),
    radial-gradient(ellipse 70% 50% at 80% 80%, rgba(60, 80, 110, 0.25), transparent 50%),
    linear-gradient(160deg, #0b0d11 0%, #141820 45%, #0f1218 100%);
}
.login-card {
  position: relative;
  width: min(400px, calc(100vw - 32px));
  background: rgba(23, 26, 33, 0.92) !important;
  border: 1px solid #2a2f3a !important;
  border-radius: 12px !important;
  backdrop-filter: blur(8px);
}
.login-brand {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #e8eaed;
}
.login-sub {
  margin: 4px 0 24px;
  color: #7a8494;
  font-size: 14px;
}
.captcha-box {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  border: 1px solid #2a2f3a;
  background: #12151b;
  cursor: pointer;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex-shrink: 0;
}
.captcha-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
