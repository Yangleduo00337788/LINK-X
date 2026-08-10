<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAutoComplete,
  NButton,
  NCheckbox,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NSpace,
  NSpin,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { LockClosedOutline, PersonOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
import QRCode from 'qrcode'
import { beginTotpSetupChallenge, fetchAuthConfig, fetchCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useSecurityStore } from '@/stores/security'
import PrefSwitcher from '@/components/PrefSwitcher.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import { unlockSpeech } from '@/utils/voiceNotify'
import logoLinkx from '@/assets/logo-linkx.png'
import loginHeroRobot from '@/assets/login-hero-robot.png'

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
const captchaModalVisible = ref(false)
const sliderCaptchaRef = ref<InstanceType<typeof SliderCaptcha> | null>(null)
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
const rememberMeKey = 'linkx-admin-login-remember'
const usernameHistory = ref<string[]>(loadUsernameHistory())
const rememberMe = ref(localStorage.getItem(rememberMeKey) === '1')

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
  if (rememberMe.value) {
    localStorage.setItem(usernameHistoryKey + '-last', v)
  }
}

const rules = computed<FormRules>(() => {
  void locale.value
  return {
    username: { required: true, message: t('login.usernameRequired'), trigger: 'blur' },
    password: { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
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

function onForgotPassword() {
  message.info(t('login.forgotPasswordHint'))
}

function onSsoLogin() {
  message.info(t('login.ssoComingSoon'))
}

function onRememberMeChange(checked: boolean) {
  rememberMe.value = checked
  localStorage.setItem(rememberMeKey, checked ? '1' : '0')
  if (!checked) {
    form.username = ''
    form.password = ''
  }
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
    puzzleImg.value = data.puzzleImageBase64
      ? data.puzzleImageBase64.startsWith('data:')
        ? data.puzzleImageBase64
        : `data:image/png;base64,${data.puzzleImageBase64}`
      : ''
    puzzleY.value = data.puzzleY ?? 0
    form.captchaCode = ''
  } catch {
    /* request already toasts */
  } finally {
    captchaLoading.value = false
  }
}

function resetCaptchaModal() {
  form.captchaCode = ''
  sliderCaptchaRef.value?.reset?.()
}

function closeCaptchaModal() {
  if (loading.value) return
  captchaModalVisible.value = false
  resetCaptchaModal()
}

async function openCaptchaModal() {
  resetCaptchaModal()
  captchaModalVisible.value = true
  await loadCaptcha()
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

async function onModalSliderSuccess(offset: number) {
  form.captchaCode = String(offset)
  await doLogin()
}

async function onImageCaptchaConfirm() {
  if (!form.captchaCode.trim()) {
    message.warning(t('login.captchaRequired'))
    return
  }
  await doLogin()
}

async function doLogin() {
  if (captchaEnabled.value && !form.captchaCode.trim()) {
    message.warning(
      captchaType.value === 'slider' ? t('captcha.completeSlider') : t('login.captchaRequired')
    )
    return
  }

  loading.value = true
  securityStore.resetSession()
  try {
    const data = await auth.login({
      username: form.username.trim(),
      password: form.password,
      captchaId: captchaEnabled.value ? captchaId.value : undefined,
      captchaCode: captchaEnabled.value ? form.captchaCode.trim() : undefined,
    })
    closeCaptchaModal()
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
    if (captchaEnabled.value) {
      resetCaptchaModal()
      if (captchaModalVisible.value) {
        await loadCaptcha()
      }
    }
  } finally {
    loading.value = false
  }
}

async function submit() {
  unlockSpeech(locale.value)
  await formRef.value?.validate()
  if (captchaEnabled.value) {
    await openCaptchaModal()
    return
  }
  await doLogin()
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
  closeCaptchaModal()
}

watch(
  () => step.value,
  (s) => {
    if (s === 'totp' || s === 'setup') totpForm.code = ''
  }
)

function onCaptchaKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && captchaModalVisible.value && !loading.value) {
    closeCaptchaModal()
  }
}

onMounted(async () => {
  auth.resetLocalSession()
  securityStore.resetSession()
  if (rememberMe.value) {
    const saved = localStorage.getItem(usernameHistoryKey + '-last')
    if (saved) form.username = saved
  }
  try {
    const cfg = await fetchAuthConfig()
    captchaEnabled.value = !!cfg.captchaEnabled
    captchaType.value = cfg.captchaType === 'slider' ? 'slider' : 'image'
    securityStore.applyFromAuthConfig(cfg)
  } catch {
    captchaEnabled.value = true
  }
  window.addEventListener('keydown', onCaptchaKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onCaptchaKeydown)
})
</script>

<template>
  <div class="login-page">
    <div class="login-page__prefs">
      <PrefSwitcher />
    </div>

    <main class="login-split lx-appear-in">
      <aside class="login-visual">
        <div class="login-visual__glow" aria-hidden="true" />
        <div class="login-visual__brand">
          <div class="login-visual__logo-wrap">
            <img class="login-visual__logo" :src="logoLinkx" alt="LinkX" draggable="false" />
          </div>
          <p class="login-visual__slogan">{{ t('login.heroSlogan') }}</p>
          <p class="login-visual__tagline">{{ t('login.heroTagline') }}</p>
        </div>
        <div class="login-visual__arts" aria-hidden="true">
          <img
            class="login-visual__art"
            :src="loginHeroRobot"
            alt=""
            draggable="false"
          />
        </div>
      </aside>

      <section class="login-panel">
        <div class="login-card">
        <div class="login-card__inner">
          <header v-if="step === 'password'" class="login-card__head">
            <h1 class="login-card__title">{{ t('login.welcomeTitle') }}</h1>
            <p class="login-card__hint">{{ t('login.welcomeHint') }}</p>
          </header>

          <h1 v-else class="login-card__title login-card__title--solo">
            {{ step === 'totp' ? t('login.totpSubtitle') : t('login.totpSetupSubtitle') }}
          </h1>

          <div class="login-card__form">
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
                  >
                    <template #prefix>
                      <NIcon :component="PersonOutline" class="login-field-icon" />
                    </template>
                  </NAutoComplete>
                </NFormItem>
                <NFormItem path="password">
                  <NInput
                    v-model:value="form.password"
                    type="password"
                    show-password-on="click"
                    :placeholder="t('login.passwordPlaceholder')"
                    autocomplete="current-password"
                  >
                    <template #prefix>
                      <NIcon :component="LockClosedOutline" class="login-field-icon" />
                    </template>
                  </NInput>
                </NFormItem>
                <div class="login-card__options">
                  <NCheckbox :checked="rememberMe" @update:checked="onRememberMeChange">
                    {{ t('login.rememberMe') }}
                  </NCheckbox>
                  <button type="button" class="login-card__link" @click="onForgotPassword">
                    {{ t('login.forgotPassword') }}
                  </button>
                </div>
                <NButton class="login-card__submit" block :loading="loading" @click="submit">
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
                    <NButton class="login-card__submit" block :loading="loading" @click="submitTotp">
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

          <div v-if="step === 'password'" class="login-card__sso">
            <div class="login-card__divider">
              <span>{{ t('login.orLoginWith') }}</span>
            </div>
            <button type="button" class="login-card__sso-btn" @click="onSsoLogin">
              <NIcon :component="ShieldCheckmarkOutline" :size="18" />
              <span>{{ t('login.ssoLogin') }}</span>
            </button>
          </div>

          <footer v-if="step === 'password'" class="login-card__foot">
            <p>{{ t('login.copyright') }}</p>
            <p>{{ t('login.icp') }}</p>
          </footer>
        </div>
        </div>
      </section>
    </main>

    <Teleport to="body">
      <Transition name="login-captcha-fade">
        <div
          v-if="captchaModalVisible"
          class="login-captcha-overlay"
          @click.self="!loading && closeCaptchaModal()"
        >
          <div class="login-captcha-modal__card" role="dialog" aria-modal="true">
            <header class="login-captcha-modal__head">
              <div class="login-captcha-modal__title-wrap">
                <span class="login-captcha-modal__badge" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
                    <path
                      d="M12 3L4 7v5c0 4.42 3.58 8 8 8s8-3.58 8-8V7l-8-4Z"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M9 12l2 2 4-4"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
                <h3 class="login-captcha-modal__title">{{ t('login.captchaModalTitle') }}</h3>
              </div>
              <button
                type="button"
                class="login-captcha-modal__close"
                :disabled="loading"
                aria-label="close"
                @click="closeCaptchaModal"
              >
                ×
              </button>
            </header>

            <div class="login-captcha-modal__body">
              <NSpin :show="captchaLoading || loading">
                <SliderCaptcha
                  v-if="captchaType === 'slider'"
                  ref="sliderCaptchaRef"
                  :background="captchaImg"
                  :puzzle="puzzleImg"
                  :puzzle-y="puzzleY"
                  :disabled="captchaLoading || loading"
                  @success="onModalSliderSuccess"
                  @refresh="loadCaptcha"
                />
                <div v-else class="login-captcha-modal__image">
                  <NSpace class="login-captcha-row" :wrap="false">
                    <NInput
                      v-model:value="form.captchaCode"
                      :placeholder="t('login.captchaPlaceholder')"
                      class="login-captcha-row__input"
                      :disabled="loading"
                      @keyup.enter="onImageCaptchaConfirm"
                    />
                    <div class="captcha-box" @click="loadCaptcha">
                      <NSpin :show="captchaLoading" size="small">
                        <img v-if="captchaImg" :src="captchaImg" alt="captcha" />
                        <span v-else>{{ t('login.refreshCaptcha') }}</span>
                      </NSpin>
                    </div>
                  </NSpace>
                  <NButton
                    class="login-captcha-modal__confirm"
                    block
                    :loading="loading"
                    @click="onImageCaptchaConfirm"
                  >
                    {{ t('login.submit') }}
                  </NButton>
                </div>
              </NSpin>
            </div>

            <footer v-if="captchaType !== 'slider'" class="login-captcha-modal__foot">
              <button
                type="button"
                class="login-captcha-modal__link"
                :disabled="captchaLoading || loading"
                @click="loadCaptcha"
              >
                {{ t('login.refreshCaptcha') }}
              </button>
            </footer>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  box-sizing: border-box;
  background: #fff;
}

.login-page__prefs {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 5;
}

.login-page__prefs :deep(.n-button--primary-type) {
  background-color: var(--lx-oa-blue) !important;
  border-color: var(--lx-oa-blue) !important;
  color: #fff !important;
  --n-color: var(--lx-oa-blue) !important;
  --n-color-hover: var(--lx-accent-hover) !important;
  --n-color-pressed: var(--lx-accent-pressed) !important;
  --n-text-color: #fff !important;
}

.login-page__prefs :deep(.n-button--primary-type:not(.n-button--disabled):hover) {
  background-color: var(--lx-accent-hover) !important;
  border-color: var(--lx-accent-hover) !important;
}

/* ── 左右分栏（参考 Snowy 登录页） ── */
.login-split {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 0.95fr);
  min-height: 100vh;
}

/* ── 左侧品牌视觉区 ── */
.login-visual {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: clamp(32px, 5vh, 56px) clamp(28px, 4vw, 56px) 0;
  box-sizing: border-box;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(var(--lx-primary-rgb), 0.45) 0%, transparent 62%),
    radial-gradient(ellipse 70% 55% at 85% 85%, rgba(var(--lx-primary-rgb), 0.28) 0%, transparent 58%),
    linear-gradient(
      145deg,
      var(--lx-oa-blue) 0%,
      var(--lx-accent-pressed) 52%,
      color-mix(in srgb, var(--lx-accent-pressed) 75%, #000) 100%
    );
}

.login-visual__glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.14) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.35;
}

.login-visual__brand {
  position: relative;
  z-index: 2;
  max-width: 520px;
}

.login-visual__logo-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.2);
  max-width: 100%;
}

.login-visual__logo {
  display: block;
  width: min(100%, 420px);
  height: auto;
  max-height: clamp(96px, 12vh, 116px);
  object-fit: contain;
  object-position: left center;
}

.login-visual__slogan {
  margin: 24px 0 0;
  padding: 0;
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 700;
  color: #fff;
  line-height: 1.35;
  letter-spacing: 0.01em;
}

.login-visual__tagline {
  margin: 10px 0 0;
  padding: 0;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.65;
  max-width: 420px;
}

.login-visual__arts {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(168px, 26vh, 220px) clamp(20px, 4vw, 48px) clamp(20px, 3vh, 36px);
  box-sizing: border-box;
  pointer-events: none;
}

.login-visual__art {
  display: block;
  width: min(100%, 560px);
  height: auto;
  max-height: 100%;
  object-fit: contain;
  object-position: center center;
  user-select: none;
}

/* ── 右侧登录区 ── */
.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px clamp(24px, 4vw, 64px);
  background: #fff;
  box-sizing: border-box;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  min-height: auto;
}

.login-card__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: auto;
  padding: 0;
  box-sizing: border-box;
}

.login-card__head {
  margin-bottom: 24px;
}

.login-card__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.4;
}

.login-card__title--solo {
  margin-bottom: 20px;
  text-align: center;
}

.login-card__hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.5;
}

.login-card__form {
  position: relative;
  z-index: 1;
}

.login-card__form :deep(.n-form-item) {
  margin-bottom: 16px;
}

.login-card__form :deep(.n-input) {
  --n-height: 44px;
  --n-border-radius: 8px;
}

.login-card__form :deep(.n-input--focus) {
  --n-border: 1px solid var(--lx-oa-blue);
  --n-border-hover: 1px solid var(--lx-oa-blue);
  --n-box-shadow-focus: 0 0 0 2px var(--lx-accent-hover-bg);
}

.login-field-icon {
  color: #bfbfbf;
}

.login-card__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 20px;
}

.login-card__options :deep(.n-checkbox .n-checkbox__label) {
  font-size: 13px;
  color: #8c8c8c;
}

.login-card__link {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--lx-oa-blue);
  cursor: pointer;
}

.login-card__link:hover {
  color: var(--lx-accent-hover);
}

.login-card__submit {
  height: 44px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: 8px !important;
}

.login-page :deep(.login-card__submit.n-button) {
  background-color: var(--lx-oa-blue) !important;
  border-color: var(--lx-oa-blue) !important;
  color: #ffffff !important;
  --n-text-color: #ffffff !important;
}

.login-page :deep(.login-card__submit.n-button:not(.n-button--disabled):hover) {
  background-color: var(--lx-accent-hover) !important;
  border-color: var(--lx-accent-hover) !important;
}

.login-page :deep(.login-card__submit.n-button:not(.n-button--disabled):active) {
  background-color: var(--lx-accent-pressed) !important;
  border-color: var(--lx-accent-pressed) !important;
}

.login-card__sso {
  margin-top: 22px;
}

.login-card__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: #bfbfbf;
  font-size: 12px;
}

.login-card__divider::before,
.login-card__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #f0f0f0;
}

.login-card__sso-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 42px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  color: var(--lx-oa-blue);
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.login-card__sso-btn:hover {
  border-color: #d6e4ff;
  background: #f5f8ff;
}

.login-card__foot {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #bfbfbf;
  line-height: 1.8;
}

.login-card__foot p {
  margin: 0;
}

.login-captcha-row {
  width: 100%;
}

.login-captcha-row__input {
  flex: 1;
}

.captcha-box {
  width: 120px;
  height: 40px;
  border-radius: 8px;
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

/* ── 主题 ── */
[data-theme='dark'] .login-page {
  background: #0f172a;
}

[data-theme='dark'] .login-panel {
  background: #0f172a;
}

[data-theme='dark'] .login-card {
  background: transparent;
  box-shadow: none;
}

[data-theme='dark'] .login-card__title {
  color: #e5eaf3;
}

[data-theme='dark'] .login-card__hint,
[data-theme='dark'] .login-card__foot {
  color: #8b95a5;
}

[data-theme='dark'] .login-visual {
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(var(--lx-primary-rgb), 0.32) 0%, transparent 62%),
    radial-gradient(ellipse 70% 55% at 85% 85%, rgba(var(--lx-primary-rgb), 0.2) 0%, transparent 58%),
    linear-gradient(
      145deg,
      color-mix(in srgb, var(--lx-oa-blue) 70%, #000) 0%,
      color-mix(in srgb, var(--lx-accent-pressed) 80%, #000) 52%,
      color-mix(in srgb, var(--lx-accent-pressed) 55%, #000) 100%
    );
}

[data-theme='dark'] .login-card__sso-btn {
  background: rgba(15, 23, 42, 0.5);
  border-color: rgba(255, 255, 255, 0.08);
  color: #60a5fa;
}

[data-theme='dark'] .login-card__divider::before,
[data-theme='dark'] .login-card__divider::after {
  background: rgba(255, 255, 255, 0.08);
}

/* ── 响应式 ── */
@media (max-width: 960px) {
  .login-split {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .login-visual {
    min-height: auto;
    padding: 40px 24px 48px;
  }

  .login-visual__arts {
    position: relative;
    inset: auto;
    padding: 24px 12px 0;
  }

  .login-visual__art {
    width: min(100%, 400px);
    max-height: 320px;
  }

  .login-visual__logo-wrap {
    padding: 10px 16px;
  }

  .login-visual__logo {
    width: min(100%, 300px);
    max-height: 88px;
  }

  .login-panel {
    padding: 28px 24px 40px;
  }

  .login-page__prefs {
    top: 16px;
    right: 16px;
  }
}
</style>

<style>
.login-captcha-overlay {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(8px);
}

.login-captcha-fade-enter-active,
.login-captcha-fade-leave-active {
  transition: opacity 0.22s ease;
}

.login-captcha-fade-enter-active .login-captcha-modal__card,
.login-captcha-fade-leave-active .login-captcha-modal__card {
  transition: transform 0.22s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.22s ease;
}

.login-captcha-fade-enter-from,
.login-captcha-fade-leave-to {
  opacity: 0;
}

.login-captcha-fade-enter-from .login-captcha-modal__card,
.login-captcha-fade-leave-to .login-captcha-modal__card {
  opacity: 0;
  transform: scale(0.94) translateY(12px);
}

.login-captcha-modal__card {
  width: min(100%, 380px);
  background: var(--lx-card);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--lx-border);
  box-shadow:
    0 24px 48px rgba(15, 23, 42, 0.18),
    0 8px 16px rgba(15, 23, 42, 0.08);
}

.login-captcha-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--lx-border);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--lx-oa-blue) 6%, var(--lx-card)) 0%,
    var(--lx-card) 100%
  );
}

.login-captcha-modal__title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.login-captcha-modal__badge {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: var(--lx-oa-blue);
  background: var(--lx-accent-soft-bg);
  border: 1px solid var(--lx-accent-soft-border);
}

.login-captcha-modal__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
  line-height: 1.45;
}

.login-captcha-modal__close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-3);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.login-captcha-modal__close:hover:not(:disabled) {
  background: var(--lx-accent-hover-bg);
  color: var(--lx-text);
}

.login-captcha-modal__close:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.login-captcha-modal__body {
  padding: 20px 24px 24px;
  display: flex;
  justify-content: center;
}

.login-captcha-modal__body .slider-captcha {
  width: 300px;
  max-width: 100%;
  margin: 0 auto;
}

.login-captcha-modal__image {
  display: grid;
  gap: 14px;
}

.login-captcha-modal__confirm {
  height: 40px !important;
}

.login-captcha-modal__foot {
  display: flex;
  align-items: center;
  padding: 8px 20px 16px;
}

.login-captcha-modal__link {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--lx-oa-blue);
  cursor: pointer;
}

.login-captcha-modal__link:hover:not(:disabled) {
  color: var(--lx-accent-hover);
}

.login-captcha-modal__link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

[data-theme='dark'] .login-captcha-modal__card {
  box-shadow:
    0 24px 48px rgba(0, 0, 0, 0.42),
    0 8px 16px rgba(0, 0, 0, 0.24);
}

[data-theme='dark'] .login-captcha-modal__head {
  border-bottom-color: var(--lx-border);
}
</style>
