<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 独立注册页（Electron 子窗口 / Web 路由）。
 */
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NIcon, useMessage } from 'naive-ui'
import {
  RefreshOutline,
  PersonOutline,
  LockClosedOutline,
  HappyOutline,
  MailOutline,
  KeyOutline,
  ShieldCheckmarkOutline
} from '@vicons/ionicons5'
import * as authApi from '../api/auth'
import { validateUsername, validatePassword, validateNickname } from '../utils/validation'
import { useI18n } from '../i18n'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import SliderCaptcha from './SliderCaptcha.vue'
import { LxButton, LxIconButton } from './ui'
import { openLegalPageInBrowser } from '../utils/legalPage'
import { useNativeWindowFrame } from '../utils/electronChrome'

const message = useMessage()
const router = useRouter()
const { t } = useI18n()
const isElectron = !!window.electronAPI?.isElectron
const showCustomCaption = computed(() => !!window.electronAPI?.showCustomCaptionButtons)
const useNativeFrame = computed(() => isElectron && useNativeWindowFrame())

const regUser = ref('')
const regPass = ref('')
const regNickname = ref('')
const regEmail = ref('')
const regEmailCode = ref('')
const regCaptchaCode = ref('')
const regCaptchaId = ref('')
const regCaptchaImage = ref('')
const regCaptchaType = ref<'image' | 'slider'>('image')
const regPuzzleImage = ref('')
const regPuzzleY = ref(0)
const captchaEnabled = ref(true)
const registerEnabled = ref(true)
const passwordPolicy = ref({
  minLength: 8,
  maxLength: 64,
  requireUpperLower: false,
  requireDigit: true,
  requireSpecial: false,
})
const configLoaded = ref(false)
const submitting = ref(false)
const acceptedTerms = ref(false)
const emailCodeSending = ref(false)
const emailCodeCooldown = ref(0)
let emailCooldownTimer: ReturnType<typeof setInterval> | null = null

const compact = computed(() => isElectron)
const submitLabel = computed(() => (submitting.value ? t('register.submitting') : t('register.submit')))

async function loadAuthConfig() {
  try {
    const res = await authApi.fetchAuthConfig()
    if (res.code === 200 && res.data) {
      captchaEnabled.value = !!res.data.captchaEnabled
      regCaptchaType.value = res.data.captchaType === 'slider' ? 'slider' : 'image'
      registerEnabled.value = res.data.registerEnabled !== false
      const p = res.data.passwordPolicy
      if (p) {
        passwordPolicy.value = {
          minLength: p.minLength ?? 8,
          maxLength: p.maxLength ?? 64,
          requireUpperLower: p.requireUpperLower === true,
          requireDigit: p.requireDigit !== false,
          requireSpecial: p.requireSpecial === true,
        }
      }
    }
  } catch {
    captchaEnabled.value = true
    registerEnabled.value = true
  } finally {
    configLoaded.value = true
  }
}

function onRegSliderSuccess(offset: number) {
  regCaptchaCode.value = String(offset)
}

async function loadCaptcha() {
  if (!captchaEnabled.value) return
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      regCaptchaId.value = res.data.captchaId
      regCaptchaImage.value = res.data.imageBase64
      regCaptchaType.value = res.data.type === 'slider' ? 'slider' : 'image'
      regPuzzleImage.value = res.data.puzzleImageBase64 || ''
      regPuzzleY.value = res.data.puzzleY ?? 0
      regCaptchaCode.value = ''
    }
  } catch {
    message.error(t('register.captchaFail'))
  }
}

function closeOrBack() {
  if (isElectron && window.electronAPI?.close) {
    void window.electronAPI.close()
    return
  }
  void router.replace('/')
}

function startEmailCooldown(seconds = 60) {
  emailCodeCooldown.value = seconds
  if (emailCooldownTimer) clearInterval(emailCooldownTimer)
  emailCooldownTimer = setInterval(() => {
    emailCodeCooldown.value -= 1
    if (emailCodeCooldown.value <= 0 && emailCooldownTimer) {
      clearInterval(emailCooldownTimer)
      emailCooldownTimer = null
    }
  }, 1000)
}

async function sendEmailCode() {
  if (!registerEnabled.value) {
    message.warning(t('register.disabled'))
    return
  }
  const email = regEmail.value.trim()
  if (!email) {
    message.warning(t('register.enterEmail'))
    return
  }
  if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)) {
    message.warning(t('register.invalidEmail'))
    return
  }
  emailCodeSending.value = true
  try {
    const res = await authApi.sendRegisterCode({
      email,
      username: regUser.value.trim() || undefined
    })
    if (res.code === 200) {
      message.success(t('register.codeSent'))
      startEmailCooldown(60)
    } else {
      message.error(res.message || t('register.sendCodeFail'))
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('register.sendCodeFail'))
  } finally {
    emailCodeSending.value = false
  }
}

async function handleRegister() {
  if (!registerEnabled.value) {
    message.warning(t('register.disabled'))
    return
  }
  if (!acceptedTerms.value) {
    message.warning(t('register.mustAgree'))
    return
  }
  const user = regUser.value.trim()
  const pass = regPass.value
  const nickname = regNickname.value.trim() || user
  const email = regEmail.value.trim()
  const emailCode = regEmailCode.value.trim()

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }
  const passErr = validatePassword(pass, passwordPolicy.value)
  if (passErr) {
    message.warning(passErr)
    return
  }
  const nickErr = validateNickname(nickname)
  if (nickErr) {
    message.warning(nickErr)
    return
  }
  if (!email) {
    message.warning(t('register.enterEmail'))
    return
  }
  if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)) {
    message.warning(t('register.invalidEmail'))
    return
  }
  if (!emailCode) {
    message.warning(t('register.enterEmailCode'))
    return
  }
  if (captchaEnabled.value && !regCaptchaCode.value.trim()) {
    message.warning(
      regCaptchaType.value === 'slider' ? t('captcha.completeSlider') : t('register.enterCaptcha')
    )
    return
  }

  submitting.value = true
  try {
    const res = await authApi.register({
      username: user,
      password: pass,
      nickname,
      email,
      emailCode,
      ...(captchaEnabled.value
        ? { captchaId: regCaptchaId.value, captchaCode: regCaptchaCode.value.trim() }
        : {})
    })
    if (res.code === 200) {
      message.success(t('register.success'))
      try {
        localStorage.setItem('linkx:registered-username', user)
      } catch {
        /* ignore */
      }
      setTimeout(() => closeOrBack(), 400)
    } else {
      message.error(res.message || t('register.fail'))
      await loadCaptcha()
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('register.fail'))
    await loadCaptcha()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadAuthConfig().then(() => {
    requestAnimationFrame(() => {
      void loadCaptcha()
    })
  })
})

onUnmounted(() => {
  if (emailCooldownTimer) {
    clearInterval(emailCooldownTimer)
    emailCooldownTimer = null
  }
})
</script>

<template>
  <div class="register-page" :class="{ 'register-page--compact': compact }">
    <div class="reg-atmosphere" aria-hidden="true">
      <span class="orb orb-a" />
      <span class="orb orb-b" />
      <span class="orb orb-c" />
      <span class="mesh" />
    </div>

    <div v-if="!useNativeFrame" class="reg-win-bar">
      <div class="reg-title">{{ t('register.title') }}</div>
      <div class="drag-area" />
      <WindowCaptionButtons v-if="showCustomCaption" :show-maximize="false" />
      <button
        v-else
        type="button"
        class="lx-win-caption-btn lx-win-caption-btn--close"
        :title="t('common.back')"
        @click="closeOrBack"
      >
        ×
      </button>
    </div>

    <div class="reg-body">
      <p class="reg-desc">{{ registerEnabled ? t('register.subtitle') : t('register.disabled') }}</p>

      <div v-if="configLoaded && !registerEnabled" class="reg-disabled">
        <LxButton variant="login" @click="closeOrBack">
          {{ t('common.back') }}
        </LxButton>
      </div>

      <div v-else class="reg-form">
        <n-input
          v-model:value="regUser"
          size="large"
          :placeholder="t('register.usernamePh')"
          class="lx-field"
          :bordered="false"
        >
          <template #prefix>
            <n-icon :component="PersonOutline" :size="16" class="field-ico" />
          </template>
        </n-input>
        <n-input
          v-model:value="regPass"
          type="password"
          show-password-on="click"
          size="large"
          :placeholder="t('register.passwordPh')"
          class="lx-field"
          :bordered="false"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" :size="16" class="field-ico" />
          </template>
        </n-input>
        <n-input
          v-model:value="regNickname"
          size="large"
          :placeholder="t('register.nicknamePh')"
          class="lx-field"
          :bordered="false"
        >
          <template #prefix>
            <n-icon :component="HappyOutline" :size="16" class="field-ico" />
          </template>
        </n-input>
        <n-input
          v-model:value="regEmail"
          size="large"
          :placeholder="t('register.emailPh')"
          class="lx-field"
          :bordered="false"
        >
          <template #prefix>
            <n-icon :component="MailOutline" :size="16" class="field-ico" />
          </template>
        </n-input>

        <div class="email-code-row">
          <n-input
            v-model:value="regEmailCode"
            size="large"
            :placeholder="t('register.emailCodePh')"
            class="lx-field email-code-input"
            :bordered="false"
            maxlength="8"
          >
            <template #prefix>
              <n-icon :component="KeyOutline" :size="15" class="field-ico" />
            </template>
          </n-input>
          <LxButton
            variant="ghost"
            class="email-code-btn"
            :disabled="emailCodeSending || emailCodeCooldown > 0"
            @click="sendEmailCode"
          >
            {{
              emailCodeCooldown > 0
                ? t('register.resendIn', { n: emailCodeCooldown })
                : t('register.sendCode')
            }}
          </LxButton>
        </div>

        <div v-if="captchaEnabled" class="captcha-row">
          <SliderCaptcha
            v-if="regCaptchaType === 'slider'"
            :background="regCaptchaImage"
            :puzzle="regPuzzleImage"
            :puzzle-y="regPuzzleY"
            @success="onRegSliderSuccess"
            @refresh="loadCaptcha"
          />
          <template v-else>
            <div
              v-if="!regCaptchaImage"
              class="captcha-img captcha-img--placeholder"
              :title="t('login.refreshCaptcha')"
              @click="loadCaptcha"
            />
            <img
              v-else
              :src="regCaptchaImage"
              :alt="t('register.captcha')"
              class="captcha-img"
              :title="t('login.refreshCaptcha')"
              @click="loadCaptcha"
            />
            <n-input
              v-model:value="regCaptchaCode"
              size="large"
              :placeholder="t('register.captchaPh')"
              class="lx-field captcha-input"
              :bordered="false"
              maxlength="4"
              @keyup.enter="handleRegister"
            >
              <template #prefix>
                <n-icon :component="ShieldCheckmarkOutline" :size="15" class="field-ico" />
              </template>
            </n-input>
            <LxIconButton
              class="captcha-refresh"
              :title="t('login.refreshCaptcha')"
              @click="loadCaptcha"
            >
              <n-icon :component="RefreshOutline" />
            </LxIconButton>
          </template>
        </div>

        <label class="agreement-row">
          <input v-model="acceptedTerms" type="checkbox" class="agreement-row__checkbox" />
          <span class="agreement-row__text">
            {{ t('register.agreePrefix') }}
            <button type="button" class="agreement-link" @click.stop="openLegalPageInBrowser('service')">
              {{ t('register.serviceAgreement') }}
            </button>
            <button type="button" class="agreement-link" @click.stop="openLegalPageInBrowser('privacy')">
              {{ t('register.privacyPolicy') }}
            </button>
          </span>
        </label>

        <LxButton
          variant="login"
          :class="{ 'is-loading': submitting }"
          :disabled="submitting"
          @click="handleRegister"
        >
          <span v-if="submitting" class="lx-btn-spinner" aria-hidden="true" />
          <span>{{ submitLabel }}</span>
        </LxButton>
      </div>

      <div class="footer">
        <a href="#" class="footer-link" @click.prevent="closeOrBack">{{ t('register.backLogin') }}</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  --lx-login-accent: var(--lx-accent);
  --lx-login-accent-deep: var(--lx-accent-deep);
  
  
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background: var(--lx-login-bg-gradient);
  overflow: hidden;
  color: var(--lx-login-ink);
  font-family: 'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.reg-atmosphere {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: var(--lx-z-base);
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  animation: orb-drift 14s ease-in-out infinite;
}

.orb-a {
  top: -10%;
  left: -16%;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(18, 183, 245, 0.34) 0%, transparent 68%);
}

.orb-b {
  top: 6%;
  right: -20%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(255, 176, 210, 0.3) 0%, transparent 70%);
  animation-delay: -4s;
}

.orb-c {
  bottom: -16%;
  left: 20%;
  width: 190px;
  height: 190px;
  background: radial-gradient(circle, rgba(91, 140, 255, 0.2) 0%, transparent 70%);
  animation-delay: -8s;
}

.mesh {
  position: absolute;
  inset: 0;
  opacity: 0.32;
  background-image: radial-gradient(rgba(255, 255, 255, 0.55) 0.8px, transparent 0.8px);
  background-size: 18px 18px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.5), transparent 78%);
}

@keyframes orb-drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(10px, -14px, 0) scale(1.06);
  }
}

.register-page--compact {
  min-height: 520px;
  border-radius: var(--lx-window-radius);
  overflow: hidden;
  clip-path: inset(0 round var(--lx-window-radius));
}

.reg-win-bar {
  flex-shrink: 0;
  height: var(--lx-size-win-bar);
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: var(--lx-z-sticky);
}

.reg-title {
  padding-left: var(--lx-space-xl);
  font-size: var(--lx-font-md);
  font-weight: 500;
  color: var(--lx-ink-soft);
  user-select: none;
  -webkit-app-region: no-drag;
  display: flex;
  align-items: center;
}

.drag-area {
  flex: 1;
  height: 100%;
  -webkit-app-region: drag;
}

.reg-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--lx-space-lg) var(--lx-space-5xl-minus) var(--lx-space-2xl);
  box-sizing: border-box;
  overflow: auto;
  position: relative;
  z-index: var(--lx-z-raised);
}

.reg-desc {
  margin: 0 0 var(--lx-space-2xl);
  font-size: var(--lx-font-md);
  color: var(--lx-login-muted);
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration-instant) both;
}

.reg-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration-faster) both;
}

.reg-disabled {
  width: 100%;
  margin-top: var(--lx-space-lg);
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.lx-field {
  width: 100%;
}

.field-ico {
  color: var(--lx-login-icon);
}

.lx-field :deep(.n-input-wrapper) {
  background: rgba(255, 255, 255, 0.88);
  border-radius: var(--lx-radius-lg);
  box-shadow:
    0 1px 2px rgba(26, 35, 50, 0.04),
    inset 0 0 0 1px rgba(255, 255, 255, 0.9);
  padding-left: var(--lx-space-lg);
  padding-right: var(--lx-space-lg);
  min-height: var(--lx-size-control);
  transition: box-shadow var(--lx-duration-md) ease, background var(--lx-duration-md) ease;
}

.lx-field :deep(.n-input--focus .n-input-wrapper) {
  background: var(--lx-bg-card);
  box-shadow:
    0 0 0 3px rgba(18, 183, 245, 0.16),
    inset 0 0 0 1px rgba(18, 183, 245, 0.45);
}

.lx-field :deep(.n-input__input-el) {
  font-size: var(--lx-font-md);
  height: var(--lx-size-control);
}

.lx-field :deep(.n-input__prefix) {
  margin-right: var(--lx-space-sm);
}

.email-code-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.email-code-input {
  flex: 1;
  min-width: 0;
}

.email-code-btn {
  flex-shrink: 0;
  height: var(--lx-size-control);
  border-radius: var(--lx-radius-lg);
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.captcha-img {
  width: 102px;
  height: var(--lx-size-control);
  border-radius: var(--lx-radius-lg);
  cursor: pointer;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(18, 183, 245, 0.12);
  flex-shrink: 0;
  object-fit: contain;
  transition: transform var(--lx-duration) ease;
}

.captcha-img:hover {
  transform: scale(1.02);
}

.captcha-img--placeholder {
  background: linear-gradient(135deg, var(--lx-bg-mist) 0%, var(--lx-bg-soft) 100%);
  border: 1px dashed var(--lx-login-border);
  box-shadow: none;
}

.captcha-input {
  flex: 1;
  min-width: 0;
}

.captcha-refresh {
  color: var(--lx-login-muted) !important;
}

.footer {
  margin-top: auto;
  padding-top: var(--lx-space-2xl);
  display: flex;
  justify-content: center;
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration) both;
}

.footer-link {
  color: var(--lx-login-accent-deep);
  text-decoration: none;
  font-size: var(--lx-font-md);
  font-weight: 500;
}

.footer-link:hover {
  color: var(--lx-login-link-hover);
  text-decoration: none;
}

.agreement-row {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space);
  margin-top: var(--lx-space-xs);
  cursor: pointer;
}

.agreement-row__checkbox {
  width: 15px;
  height: 15px;
  margin-top: var(--lx-space-2xs);
  flex-shrink: 0;
  accent-color: var(--lx-login-accent);
}

.agreement-row__text {
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-relaxed);
  color: var(--lx-login-muted);
}

.agreement-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--lx-login-accent-deep);
  font: inherit;
  cursor: pointer;
}

.agreement-link:hover {
  color: var(--lx-login-link-hover);
  text-decoration: underline;
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .reg-desc,
  .reg-form,
  .footer {
    animation: none !important;
  }
}

.lx-win-caption-btn--close {
  font-size: var(--lx-font-4xl);
  line-height: var(--lx-leading-none);
  color: var(--lx-ink-soft);
}

.lx-win-caption-btn--close:hover,
.lx-win-caption-btn--close:active {
  color: var(--lx-text-on-accent);
}
</style>
