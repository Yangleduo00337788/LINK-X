<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAutoComplete,
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
import PrefSwitcher from '@/components/PrefSwitcher.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const { t, locale } = useI18n()

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

const usernameHistoryKey = 'linkx-admin-login-users'
const usernameHistory = ref<string[]>(loadUsernameHistory())

function loadUsernameHistory(): string[] {
  try {
    const raw = localStorage.getItem(usernameHistoryKey)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string').slice(0, 8) : []
  } catch {
    return []
  }
}

const usernameOptions = computed(() => {
  const q = form.username.trim().toLowerCase()
  const pool = usernameHistory.value
  return (q ? pool.filter((x) => x.toLowerCase().includes(q)) : pool).map((value) => ({
    label: value,
    value,
  }))
})

function rememberUsername(name: string) {
  const v = name.trim()
  if (!v) return
  const next = [v, ...usernameHistory.value.filter((x) => x !== v)].slice(0, 8)
  usernameHistory.value = next
  localStorage.setItem(usernameHistoryKey, JSON.stringify(next))
}

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    username: { required: true, message: t('login.usernameRequired'), trigger: 'blur' },
    password: { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
    captchaCode: {
      required: true,
      trigger: 'blur',
      validator: (_r, v: string) => {
        if (!captchaEnabled.value) return true
        if (!v) return new Error(t('login.captchaRequired'))
        return true
      },
    },
  }
})

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
    rememberUsername(form.username)
    message.success(t('login.success'))
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
    <div class="login-prefs">
      <PrefSwitcher />
    </div>
    <NCard class="login-card" :bordered="false">
      <div class="login-brand">{{ t('app.brand') }}</div>
      <p class="login-sub">{{ t('login.subtitle') }}</p>
      <NForm ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <NFormItem path="username" :label="t('login.username')">
          <NAutoComplete
            v-model:value="form.username"
            :options="usernameOptions"
            :placeholder="t('login.usernamePlaceholder')"
            clearable
          />
        </NFormItem>
        <NFormItem path="password" :label="t('login.password')">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            :placeholder="t('login.passwordPlaceholder')"
            autocomplete="current-password"
          />
        </NFormItem>
        <NFormItem v-if="captchaEnabled" path="captchaCode" :label="t('login.captcha')">
          <NSpace style="width: 100%" :wrap="false">
            <NInput
              v-model:value="form.captchaCode"
              :placeholder="t('login.captchaPlaceholder')"
              style="flex: 1"
            />
            <div class="captcha-box" @click="loadCaptcha">
              <NSpin :show="captchaLoading" size="small">
                <img v-if="captchaImg" :src="captchaImg" alt="captcha" />
                <span v-else>{{ t('login.refreshCaptcha') }}</span>
              </NSpin>
            </div>
          </NSpace>
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="submit">
          {{ t('login.submit') }}
        </NButton>
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
    radial-gradient(ellipse 80% 60% at 20% 20%, var(--lx-login-grad-1), transparent 55%),
    radial-gradient(ellipse 70% 50% at 80% 80%, var(--lx-login-grad-2), transparent 50%),
    var(--lx-login-base);
}
.login-prefs {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 2;
}
.login-card {
  position: relative;
  width: min(400px, calc(100vw - 32px));
  background: var(--lx-login-card) !important;
  border: 1px solid var(--lx-border) !important;
  border-radius: 16px !important;
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
}
.login-brand {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--lx-text);
}
.login-sub {
  margin: 4px 0 24px;
  color: var(--lx-text-3);
  font-size: 14px;
}
.captcha-box {
  width: 120px;
  height: 40px;
  border-radius: 16px;
  border: 1px solid var(--lx-border);
  background: var(--lx-captcha-bg);
  cursor: pointer;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex-shrink: 0;
  color: var(--lx-text-3);
  font-size: 12px;
}
.captcha-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
