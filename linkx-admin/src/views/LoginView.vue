<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
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
import QRCode from 'qrcode'
import { beginTotpSetupChallenge, fetchAuthConfig, fetchCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import PrefSwitcher from '@/components/PrefSwitcher.vue'
import AdminOpsBannerCarousel from '@/components/AdminOpsBannerCarousel.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const { t, locale } = useI18n()

type Step = 'password' | 'totp' | 'setup'

const step = ref<Step>('password')
const formRef = ref<FormInst | null>(null)
const totpFormRef = ref<FormInst | null>(null)
const loading = ref(false)
const captchaEnabled = ref(true)
const captchaId = ref('')
const captchaImg = ref('')
const captchaLoading = ref(false)
const loginBannerCount = ref<number | null>(null)
const challengeToken = ref('')
const setupSecret = ref('')
const setupUri = ref('')
const qrDataUrl = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const totpForm = reactive({
  code: '',
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

const totpRules = computed<FormRules>(() => {
  void locale.value
  return {
    code: {
      required: true,
      trigger: 'blur',
      validator: (_r, v: string) => {
        if (!v || !/^\d{6}$/.test(v.trim())) return new Error(t('login.totpCodeRequired'))
        return true
      },
    },
  }
})

function onLoginBannerLoaded(payload: { count: number }) {
  loginBannerCount.value = payload.count
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

async function finishLogin(loginResult?: { newLoginIp?: boolean; loginIp?: string }) {
  rememberUsername(form.username)
  message.success(t('login.success'))
  if (loginResult?.newLoginIp) {
    message.warning(
      loginResult.loginIp
        ? t('login.newIpWarnWithIp', { ip: loginResult.loginIp })
        : t('login.newIpWarn'),
      { duration: 6000 }
    )
  }
  const redirect = (route.query.redirect as string) || '/admin/dashboard'
  await router.replace(redirect)
}

async function renderQr(uri: string) {
  try {
    qrDataUrl.value = await QRCode.toDataURL(uri, { width: 180, margin: 1 })
  } catch {
    qrDataUrl.value = ''
  }
}

async function enterSetup(token: string) {
  challengeToken.value = token
  step.value = 'setup'
  loading.value = true
  try {
    const setup = await beginTotpSetupChallenge(token)
    setupSecret.value = setup.secret
    setupUri.value = setup.otpauthUri
    await renderQr(setup.otpauthUri)
    totpForm.code = ''
    await nextTick()
  } finally {
    loading.value = false
  }
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await auth.login({
      username: form.username.trim(),
      password: form.password,
      captchaId: captchaEnabled.value ? captchaId.value : undefined,
      captchaCode: captchaEnabled.value ? form.captchaCode.trim() : undefined,
    })
    if (data.requiresTotp && data.challengeToken) {
      challengeToken.value = data.challengeToken
      step.value = 'totp'
      totpForm.code = ''
      return
    }
    if (data.requiresTotpSetup && data.challengeToken) {
      await enterSetup(data.challengeToken)
      return
    }
    await finishLogin(data)
  } catch {
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function submitTotp() {
  await totpFormRef.value?.validate()
  loading.value = true
  try {
    const data =
      step.value === 'setup'
        ? await auth.completeTotpSetup(challengeToken.value, totpForm.code.trim())
        : await auth.completeTotpLogin(challengeToken.value, totpForm.code.trim())
    await finishLogin(data)
  } catch {
    totpForm.code = ''
  } finally {
    loading.value = false
  }
}

function backToPassword() {
  step.value = 'password'
  challengeToken.value = ''
  setupSecret.value = ''
  setupUri.value = ''
  qrDataUrl.value = ''
  totpForm.code = ''
  void loadCaptcha()
}

watch(
  () => step.value,
  (s) => {
    if (s === 'totp' || s === 'setup') totpForm.code = ''
  }
)

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

    <div class="login-split">
      <aside class="login-visual">
        <AdminOpsBannerCarousel
          class="login-ops-banner"
          position="login"
          height="100%"
          :radius="0"
          :show-arrow="(loginBannerCount || 0) > 1"
          @loaded="onLoginBannerLoaded"
        />
        <div v-if="loginBannerCount === 0" class="login-visual-fallback">
          <div class="login-visual-mark">L</div>
          <div class="login-visual-title">{{ t('app.brand') }}</div>
          <p class="login-visual-desc">{{ t('login.subtitle') }}</p>
        </div>
      </aside>

      <section class="login-panel">
        <NCard class="login-card" :bordered="false">
          <div class="login-brand">{{ t('app.brand') }}</div>
          <p class="login-sub">
            {{
              step === 'totp'
                ? t('login.totpSubtitle')
                : step === 'setup'
                  ? t('login.totpSetupSubtitle')
                  : t('login.subtitle')
            }}
          </p>

          <NForm
            v-if="step === 'password'"
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @keyup.enter="submit"
          >
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

          <div v-else>
            <div v-if="step === 'setup'" class="totp-setup">
              <img v-if="qrDataUrl" class="totp-qr" :src="qrDataUrl" alt="totp-qr" />
              <p class="totp-hint">{{ t('login.totpScanHint') }}</p>
              <code class="totp-secret">{{ setupSecret }}</code>
            </div>
            <NForm
              ref="totpFormRef"
              :model="totpForm"
              :rules="totpRules"
              size="large"
              @keyup.enter="submitTotp"
            >
              <NFormItem path="code" :label="t('login.totpCode')">
                <NInput
                  v-model:value="totpForm.code"
                  maxlength="6"
                  :placeholder="t('login.totpCodePlaceholder')"
                  autocomplete="one-time-code"
                />
              </NFormItem>
              <NSpace vertical style="width: 100%">
                <NButton type="primary" block :loading="loading" @click="submitTotp">
                  {{ step === 'setup' ? t('login.totpConfirmBind') : t('login.totpVerify') }}
                </NButton>
                <NButton block quaternary :disabled="loading" @click="backToPassword">
                  {{ t('login.back') }}
                </NButton>
              </NSpace>
            </NForm>
          </div>
        </NCard>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
  box-sizing: border-box;
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
.login-split {
  position: relative;
  z-index: 1;
  width: min(960px, calc(100vw - 48px));
  min-height: min(560px, calc(100vh - 48px));
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--lx-border);
  background: var(--lx-login-card);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.14);
  backdrop-filter: blur(10px);
}
.login-visual {
  position: relative;
  min-height: 280px;
  background:
    linear-gradient(145deg, rgba(18, 183, 245, 0.22), transparent 55%),
    linear-gradient(320deg, rgba(64, 128, 255, 0.18), transparent 50%), var(--lx-login-base);
}
.login-ops-banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.login-visual-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 36px;
  color: var(--lx-text);
}
.login-visual-mark {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #12b7f5, #3b82f6);
  box-shadow: 0 10px 24px rgba(18, 183, 245, 0.35);
  margin-bottom: 18px;
}
.login-visual-title {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.06em;
}
.login-visual-desc {
  margin: 10px 0 0;
  max-width: 280px;
  color: var(--lx-text-3);
  font-size: 14px;
  line-height: 1.6;
}
.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px 28px;
  background: var(--lx-login-card);
}
.login-card {
  width: 100%;
  max-width: 360px;
  background: transparent !important;
  box-shadow: none !important;
}
.login-brand {
  font-size: 28px;
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
.totp-setup {
  display: grid;
  justify-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.totp-qr {
  width: 180px;
  height: 180px;
  border-radius: 8px;
  background: #fff;
}
.totp-hint {
  margin: 0;
  color: var(--lx-text-3);
  font-size: 13px;
  text-align: center;
}
.totp-secret {
  font-size: 12px;
  word-break: break-all;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--lx-captcha-bg);
  max-width: 100%;
}

@media (max-width: 820px) {
  .login-page {
    padding: 16px;
    place-items: stretch;
  }
  .login-split {
    width: 100%;
    min-height: auto;
    grid-template-columns: 1fr;
  }
  .login-visual {
    min-height: 200px;
    aspect-ratio: 16 / 9;
  }
  .login-panel {
    padding: 24px 20px 28px;
  }
}
</style>
