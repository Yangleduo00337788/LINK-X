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
import AuthParticleBackground from '@/components/AuthParticleBackground.vue'
import { unlockSpeech } from '@/utils/voiceNotify'
import linkxLogoMark from '@linkx-client/assets/logo-mark-transparent.png'
import loginHeroArt from '@/assets/login-hero-3d-lx.png'

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
    <div class="login-page__bg" aria-hidden="true">
      <span class="login-page__blob login-page__blob--purple" />
      <span class="login-page__blob login-page__blob--yellow" />
      <span class="login-page__blob login-page__blob--pink" />
      <span class="login-page__dots" />
      <span class="login-page__arc login-page__arc--left" />
      <span class="login-page__arc login-page__arc--right" />
      <AuthParticleBackground palette="colorful" always-animate />
    </div>

    <div class="login-page__prefs">
      <PrefSwitcher />
    </div>

    <main class="login-layout lx-appear-in">
      <aside class="login-hero">
        <div class="login-hero__brand">
          <div class="login-hero__title-row">
            <span class="login-hero__mark-wrap">
              <img class="login-hero__mark" :src="linkxLogoMark" alt="LinkX" draggable="false" />
            </span>
            <span class="login-hero__name">LinkX</span>
          </div>
          <p class="login-hero__slogan">{{ t('login.heroSlogan') }}</p>
          <p class="login-hero__tagline">{{ t('login.heroTagline') }}</p>
        </div>
        <div class="login-hero__art-wrap" aria-hidden="true">
          <img class="login-hero__art" :src="loginHeroArt" alt="" draggable="false" />
        </div>
      </aside>

      <section class="login-card">
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
              <h3 class="login-captcha-modal__title">{{ t('login.captchaModalTitle') }}</h3>
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

            <footer class="login-captcha-modal__foot">
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
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  padding: 16px clamp(48px, 6vw, 96px) 24px;
  overflow: hidden;
  box-sizing: border-box;
}

/* ── 装饰背景 ── */
.login-page__bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.login-page__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.login-page__blob--purple {
  top: -8%;
  left: -6%;
  width: 38%;
  height: 42%;
  background: rgba(167, 139, 250, 0.45);
}

.login-page__blob--yellow {
  top: 4%;
  right: -4%;
  width: 28%;
  height: 32%;
  background: rgba(253, 224, 71, 0.5);
}

.login-page__blob--pink {
  bottom: -6%;
  right: 8%;
  width: 32%;
  height: 36%;
  background: rgba(251, 146, 160, 0.35);
}

.login-page__dots {
  position: absolute;
  inset: 0;
  opacity: 0.22;
  z-index: 0;
  background-image:
    radial-gradient(circle, #a78bfa 1px, transparent 1px),
    radial-gradient(circle, #fbbf24 1px, transparent 1px),
    radial-gradient(circle, #60a5fa 1px, transparent 1px),
    radial-gradient(circle, #f472b6 1px, transparent 1px);
  background-size: 120px 120px, 180px 180px, 150px 150px, 200px 200px;
  background-position: 0 0, 40px 60px, 80px 20px, 120px 100px;
}

.login-page__arc {
  position: absolute;
  border: 2px solid rgba(255, 255, 255, 0.55);
  border-radius: 50%;
}

.login-page__arc--left {
  top: 18%;
  left: 6%;
  width: 180px;
  height: 180px;
  border-color: rgba(255, 255, 255, 0.35);
}

.login-page__arc--right {
  bottom: 22%;
  right: 10%;
  width: 120px;
  height: 120px;
}

/* ── 右上角偏好切换 ── */
.login-page__prefs {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 5;
}

.login-page__prefs :deep(.n-button--primary-type) {
  background-color: #1677ff !important;
  border-color: #1677ff !important;
  color: #fff !important;
  --n-color: #1677ff !important;
  --n-color-hover: #4096ff !important;
  --n-color-pressed: #0958d9 !important;
  --n-text-color: #fff !important;
}

.login-page__prefs :deep(.n-button--primary-type:not(.n-button--disabled):hover) {
  background-color: #4096ff !important;
  border-color: #4096ff !important;
}

/* ── 主布局：左品牌 + 右独立卡片 ── */
.login-layout {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  align-items: center;
  gap: 20px clamp(24px, 4vw, 56px);
  width: 100%;
  max-width: 1080px;
  min-height: auto;
  margin: 0 auto;
}

/* ── 左侧品牌 + 插图 ── */
.login-hero {
  position: relative;
  min-height: auto;
  padding: 0;
  overflow: hidden;
  isolation: isolate;
  align-self: center;
  transform: translateY(-8px);
}

.login-hero__brand {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  --hero-align: 12px;
  padding-left: var(--hero-align);
}

.login-hero__title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.login-hero__mark-wrap {
  flex-shrink: 0;
  width: 52px;
  height: 76px;
  margin-left: calc(-1 * var(--hero-align));
  display: flex;
  align-items: center;
  justify-content: flex-start;
  overflow: hidden;
}

.login-hero__mark {
  width: 80px;
  height: 80px;
  margin: 0 -12px 0 0;
  object-fit: contain;
  flex-shrink: 0;
}

.login-hero__name {
  display: block;
  font-size: 36px;
  font-weight: 800;
  color: #1f2937;
  letter-spacing: -0.02em;
  line-height: 1;
}

.login-hero__slogan {
  margin: 6px 0 0;
  padding: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.35;
}

.login-hero__tagline {
  margin: 6px 0 0;
  padding: 0;
  font-size: 15px;
  color: #8c8c8c;
  line-height: 1.6;
}

.login-hero__art-wrap {
  position: relative;
  width: 122%;
  margin: 0 0 0 -12%;
  height: clamp(360px, 50vh, 480px);
  pointer-events: none;
  -webkit-mask-image:
    radial-gradient(
      ellipse 112% 108% at 38% 48%,
      #000 0%,
      #000 46%,
      rgba(0, 0, 0, 0.96) 62%,
      rgba(0, 0, 0, 0.72) 76%,
      rgba(0, 0, 0, 0.28) 90%,
      transparent 98%
    ),
    linear-gradient(
      to right,
      transparent 0%,
      #000 5%,
      #000 95%,
      transparent 100%
    ),
    linear-gradient(
      to top,
      transparent 0%,
      #000 4%,
      #000 92%,
      transparent 100%
    );
  -webkit-mask-composite: source-in, source-in;
  mask-image:
    radial-gradient(
      ellipse 112% 108% at 38% 48%,
      #000 0%,
      #000 46%,
      rgba(0, 0, 0, 0.96) 62%,
      rgba(0, 0, 0, 0.72) 76%,
      rgba(0, 0, 0, 0.28) 90%,
      transparent 98%
    ),
    linear-gradient(
      to right,
      transparent 0%,
      #000 5%,
      #000 95%,
      transparent 100%
    ),
    linear-gradient(
      to top,
      transparent 0%,
      #000 4%,
      #000 92%,
      transparent 100%
    );
  mask-composite: intersect, intersect;
}

.login-hero__art-wrap::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background:
    linear-gradient(
      to right,
      rgba(232, 237, 248, 0.72) 0%,
      rgba(238, 242, 251, 0.28) 5%,
      transparent 12%
    ),
    linear-gradient(
      to top,
      rgba(238, 242, 251, 0.72) 0%,
      rgba(240, 244, 252, 0.28) 5%,
      transparent 11%
    );
}

.login-hero__art {
  display: block;
  width: 124%;
  height: 124%;
  object-fit: contain;
  object-position: 8% 94%;
  transform: translateY(1%);
  user-select: none;
  mix-blend-mode: multiply;
}

/* ── 右侧登录卡片 ── */
.login-card {
  width: 100%;
  max-width: 360px;
  justify-self: start;
  margin-left: 0;
  background: #fff;
  border-radius: 16px;
  box-shadow:
    0 20px 60px rgba(15, 23, 42, 0.1),
    0 0 0 1px rgba(255, 255, 255, 0.85) inset;
  min-height: 580px;
}

.login-card__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 580px;
  padding: 36px 32px 24px;
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
  --n-border: 1px solid #1677ff;
  --n-border-hover: 1px solid #1677ff;
  --n-box-shadow-focus: 0 0 0 2px rgba(22, 119, 255, 0.15);
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
  color: #1677ff;
  cursor: pointer;
}

.login-card__link:hover {
  color: #4096ff;
}

.login-card__submit {
  height: 44px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: 8px !important;
}

.login-page :deep(.login-card__submit.n-button) {
  background-color: #1677ff !important;
  border-color: #1677ff !important;
  color: #ffffff !important;
  --n-text-color: #ffffff !important;
}

.login-page :deep(.login-card__submit.n-button:not(.n-button--disabled):hover) {
  background-color: #4096ff !important;
  border-color: #4096ff !important;
}

.login-page :deep(.login-card__submit.n-button:not(.n-button--disabled):active) {
  background-color: #0958d9 !important;
  border-color: #0958d9 !important;
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
  color: #1677ff;
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
[data-theme='light'] .login-page {
  background: linear-gradient(160deg, #e8edf8 0%, #eef2fb 50%, #f0f4fc 100%);
}

[data-theme='dark'] .login-page {
  background: linear-gradient(160deg, #0b1220 0%, #111827 50%, #0f172a 100%);
}

[data-theme='dark'] .login-card {
  background: rgba(30, 41, 59, 0.96);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

[data-theme='dark'] .login-hero__name,
[data-theme='dark'] .login-hero__slogan,
[data-theme='dark'] .login-card__title {
  color: #e5eaf3;
}

[data-theme='dark'] .login-hero__tagline,
[data-theme='dark'] .login-card__hint,
[data-theme='dark'] .login-card__foot {
  color: #8b95a5;
}

[data-theme='dark'] .login-hero__art {
  mix-blend-mode: normal;
  opacity: 0.94;
}

[data-theme='dark'] .login-hero__art-wrap {
  -webkit-mask-image:
    radial-gradient(
      ellipse 112% 108% at 38% 48%,
      #000 0%,
      #000 42%,
      rgba(0, 0, 0, 0.94) 60%,
      rgba(0, 0, 0, 0.68) 76%,
      rgba(0, 0, 0, 0.22) 90%,
      transparent 98%
    ),
    linear-gradient(
      to right,
      transparent 0%,
      #000 5%,
      #000 95%,
      transparent 100%
    ),
    linear-gradient(
      to top,
      transparent 0%,
      #000 4%,
      #000 92%,
      transparent 100%
    );
  -webkit-mask-composite: source-in, source-in;
  mask-image:
    radial-gradient(
      ellipse 112% 108% at 38% 48%,
      #000 0%,
      #000 42%,
      rgba(0, 0, 0, 0.94) 60%,
      rgba(0, 0, 0, 0.68) 76%,
      rgba(0, 0, 0, 0.22) 90%,
      transparent 98%
    ),
    linear-gradient(
      to right,
      transparent 0%,
      #000 5%,
      #000 95%,
      transparent 100%
    ),
    linear-gradient(
      to top,
      transparent 0%,
      #000 4%,
      #000 92%,
      transparent 100%
    );
  mask-composite: intersect, intersect;
}

[data-theme='dark'] .login-hero__art-wrap::after {
  background:
    linear-gradient(
      to right,
      rgba(11, 18, 32, 0.72) 0%,
      rgba(17, 24, 39, 0.28) 5%,
      transparent 12%
    ),
    linear-gradient(
      to top,
      rgba(17, 24, 39, 0.72) 0%,
      rgba(15, 23, 42, 0.28) 5%,
      transparent 11%
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
  .login-layout {
    grid-template-columns: 1fr;
    gap: 32px;
    width: min(100%, 400px);
    min-height: auto;
    margin: 0 auto;
  }

  .login-card {
    max-width: none;
    justify-self: stretch;
    margin-left: 0;
  }

  .login-hero {
    min-height: auto;
    transform: none;
  }

  .login-hero__art-wrap {
    width: 100%;
    margin-left: 0;
    height: 260px;
  }

  .login-hero__brand {
    --hero-align: 9px;
  }

  .login-hero__mark-wrap {
    width: 42px;
    height: 54px;
  }

  .login-hero__mark {
    width: 62px;
    height: 62px;
    margin-right: -10px;
  }

  .login-hero__name {
    font-size: 28px;
  }

  .login-hero__slogan {
    font-size: 18px;
  }

  .login-card,
  .login-card__inner {
    min-height: auto;
  }

  .login-card__inner {
    padding: 32px 28px 24px;
  }

  .login-page {
    align-items: center;
    padding: 48px 24px 16px;
    justify-content: flex-start;
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
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

.login-captcha-fade-enter-active,
.login-captcha-fade-leave-active {
  transition: opacity 0.2s ease;
}

.login-captcha-fade-enter-active .login-captcha-modal__card,
.login-captcha-fade-leave-active .login-captcha-modal__card {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.login-captcha-fade-enter-from,
.login-captcha-fade-leave-to {
  opacity: 0;
}

.login-captcha-fade-enter-from .login-captcha-modal__card,
.login-captcha-fade-leave-to .login-captcha-modal__card {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}

.login-captcha-modal__card {
  width: min(100%, 400px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.28);
}

.login-captcha-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.login-captcha-modal__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.5;
}

.login-captcha-modal__close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #8c8c8c;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.login-captcha-modal__close:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.05);
  color: #595959;
}

.login-captcha-modal__close:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.login-captcha-modal__body {
  padding: 20px 24px 10px;
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
  color: #1677ff;
  cursor: pointer;
}

.login-captcha-modal__link:hover:not(:disabled) {
  color: #4096ff;
}

.login-captcha-modal__link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

[data-theme='dark'] .login-captcha-modal__card {
  background: #1e293b;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

[data-theme='dark'] .login-captcha-modal__title {
  color: #e5eaf3;
}

[data-theme='dark'] .login-captcha-modal__head {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
</style>
