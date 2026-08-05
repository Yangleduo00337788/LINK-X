<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAutoComplete,
  NButton,
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
import { useSecurityStore } from '@/stores/security'
import AuthPageShell from '@/components/AuthPageShell.vue'
import AdminOpsBannerCarousel from '@/components/AdminOpsBannerCarousel.vue'
import LoginHeroIllustration from '@/components/LoginHeroIllustration.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import { unlockSpeech } from '@/utils/voiceNotify'

const auth = useAuthStore()
const securityStore = useSecurityStore()
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
const captchaType = ref<'image' | 'slider'>('image')
const captchaId = ref('')
const captchaImg = ref('')
const puzzleImg = ref('')
const puzzleY = ref(0)
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
    captchaType.value = data.type === 'slider' ? 'slider' : 'image'
    captchaImg.value = data.imageBase64.startsWith('data:')
      ? data.imageBase64
      : `data:image/png;base64,${data.imageBase64}`
    puzzleImg.value = data.puzzleImageBase64 || ''
    puzzleY.value = data.puzzleY ?? 0
    form.captchaCode = ''
  } catch {
    /* request already toasts */
  } finally {
    captchaLoading.value = false
  }
}

function onSliderSuccess(offset: number) {
  form.captchaCode = String(offset)
}

async function finishLogin(loginResult?: { newLoginIp?: boolean; loginIp?: string }) {
  unlockSpeech()
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
  unlockSpeech(locale.value)
  await formRef.value?.validate()
  loading.value = true
  securityStore.resetSession()
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
  } catch (error) {
    auth.resetLocalSession()
    const msg = error instanceof Error ? error.message : t('common.requestFailed')
    if (msg) message.error(msg)
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function submitTotp() {
  unlockSpeech(locale.value)
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
  auth.resetLocalSession()
  securityStore.resetSession()
  try {
    const cfg = await fetchAuthConfig()
    captchaEnabled.value = !!cfg.captchaEnabled
    captchaType.value = cfg.captchaType === 'slider' ? 'slider' : 'image'
    securityStore.applyFromAuthConfig(cfg)
  } catch {
    captchaEnabled.value = true
  }
  await loadCaptcha()
})
</script>

<template>
  <AuthPageShell immersive>
    <template #visual>
      <AdminOpsBannerCarousel
        class="login-ops-banner"
        position="login"
        height="100%"
        :radius="0"
        :show-arrow="(loginBannerCount || 0) > 1"
        @loaded="onLoginBannerLoaded"
      />
    </template>
    <template v-if="loginBannerCount === 0" #visual-fallback>
      <LoginHeroIllustration />
    </template>

    <div class="login-form-card">
      <header class="login-form-head">
        <h1 class="login-form-title">
          {{
            step === 'totp'
              ? t('login.totpSubtitle')
              : step === 'setup'
                ? t('login.totpSetupSubtitle')
                : t('login.welcomeTitle')
          }}
        </h1>
        <p v-if="step === 'password'" class="login-form-hint">{{ t('login.welcomeHint') }}</p>
      </header>

      <div class="login-step-wrap">
        <Transition name="lx-auth-step">
        <NForm
          v-if="step === 'password'"
          key="password"
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          :show-label="false"
          @keyup.enter="submit"
        >
          <NFormItem path="username">
            <NAutoComplete
              v-model:value="form.username"
              :options="usernameOptions"
              :placeholder="t('login.usernamePlaceholder')"
              clearable
            />
          </NFormItem>
          <NFormItem path="password">
            <NInput
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              :placeholder="t('login.passwordPlaceholder')"
              autocomplete="current-password"
            />
          </NFormItem>
          <NFormItem v-if="captchaEnabled" path="captchaCode">
            <SliderCaptcha
              v-if="captchaType === 'slider'"
              :background="captchaImg"
              :puzzle="puzzleImg"
              :puzzle-y="puzzleY"
              :disabled="captchaLoading"
              @success="onSliderSuccess"
              @refresh="loadCaptcha"
            />
            <NSpace v-else style="width: 100%" :wrap="false">
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
          <NButton
            class="login-submit-btn"
            color="#2b7fff"
            block
            :loading="loading"
            @click="submit"
          >
            {{ t('login.submit') }}
          </NButton>
        </NForm>

        <div v-else key="totp">
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
        </Transition>
      </div>

      <footer v-if="step === 'password'" class="login-form-footer">
        {{ t('login.copyright') }}
      </footer>
    </div>
  </AuthPageShell>
</template>

<style scoped>
.login-ops-banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.login-form-card {
  width: 100%;
  padding: 36px 40px 24px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 10px 40px rgba(15, 23, 42, 0.06);
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.login-form-head {
  margin-bottom: 32px;
}

.login-form-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
  color: #1a1a1a;
}

.login-form-hint {
  margin: 10px 0 0;
  font-size: 14px;
  color: #bfbfbf;
}

.login-step-wrap :deep(.n-form-item) {
  margin-bottom: 20px;
}

.login-step-wrap :deep(.n-input) {
  --n-height: 44px;
}

.login-step-wrap {
  position: relative;
  min-height: 220px;
}

.login-step-wrap :deep(.slider-captcha) {
  width: 100%;
  max-width: 100%;
}

.login-step-wrap :deep(.slider-captcha__panel),
.login-step-wrap :deep(.slider-captcha__track) {
  width: 100%;
  max-width: 100%;
}

.login-step-wrap :deep(.slider-captcha__bg) {
  width: 100%;
  height: auto;
  aspect-ratio: 2 / 1;
}

.login-submit-btn {
  height: 44px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  border-radius: 8px !important;
}

.login-step-wrap :deep(.slider-captcha__thumb) {
  color: #2b7fff;
}

.login-step-wrap :deep(.slider-captcha__track-fill) {
  background: rgba(43, 127, 255, 0.15);
}

.login-step-wrap :deep(.slider-captcha__track--dragging .slider-captcha__track-fill) {
  background: rgba(43, 127, 255, 0.25);
}

.login-step-wrap :deep(.slider-captcha__thumb--dragging) {
  background: #eef5ff;
  box-shadow: 0 2px 10px rgba(43, 127, 255, 0.25);
}

.login-form-footer {
  margin-top: 32px;
  padding-top: 0;
  border-top: none;
  font-size: 12px;
  line-height: 1.6;
  color: #bfbfbf;
  text-align: center;
}

[data-theme='dark'] .login-form-card {
  background: #1a1f2e;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

[data-theme='dark'] .login-form-title {
  color: #e5eaf3;
}

[data-theme='dark'] .login-form-hint,
[data-theme='dark'] .login-form-footer {
  color: #8b95a5;
}

[data-theme='dark'] .login-form-footer {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.captcha-box {
  width: 120px;
  height: 40px;
  border-radius: var(--lx-radius);
  border: 1px solid var(--lx-border);
  background: var(--lx-captcha-bg);
  cursor: pointer;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex-shrink: 0;
  color: var(--lx-text-3);
  font-size: 12px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.captcha-box:hover {
  border-color: var(--lx-oa-blue);
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.12);
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
  border-radius: var(--lx-radius);
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
  border-radius: var(--lx-radius);
  background: var(--lx-captcha-bg);
  max-width: 100%;
}
</style>
