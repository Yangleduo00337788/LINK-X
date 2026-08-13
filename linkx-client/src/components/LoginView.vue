<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NIcon, NCheckbox, NModal, NSelect, useMessage } from 'naive-ui'
import {
  RefreshOutline,
  MailOutline,
  ChevronDownOutline,
  PersonOutline,
  LockClosedOutline,
  ShieldCheckmarkOutline
} from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import BrandMarkIcon from './BrandMarkIcon.vue'
import SliderCaptcha from './SliderCaptcha.vue'
import { LxButton, LxIconButton } from './ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import * as authApi from '../api/auth'
import * as feedbackApi from '../api/feedback'
import { sendResetCode, verifyResetCode, resetPasswordByEmail } from '../api/account'
import { validateUsername, validatePassword } from '../utils/validation'
import { hasRefreshToken } from '../utils/tokenStorage'
import { useI18n } from '../i18n'

const message = useMessage()
const router = useRouter()
const appStore = useAppStore()
const { savedLogin, isLoading, authInitializing, userProfile } = storeToRefs(appStore)
const { login } = appStore
const { t } = useI18n()

const isElectron = !!window.electronAPI?.isElectron

/** quick：快速登录（头像+昵称）；password：账密登录 */
const loginMode = ref<'quick' | 'password'>('password')

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const autoLogin = ref(false)

/** 自动登录阶段：先检网络，再自动登录 */
const autoLoginPhase = ref<'idle' | 'checking' | 'logging-in'>('idle')

/** 自动登录进行中 */
const autoLogging = computed(
  () =>
    autoLoginPhase.value !== 'idle' ||
    authInitializing.value ||
    (isLoading.value && autoLogin.value)
)

const matchedSavedAccount = computed(() => {
  const user = username.value.trim()
  return !!user && user === (savedLogin.value.username || '').trim()
})

const displayNickname = computed(() => {
  if (matchedSavedAccount.value) {
    return (
      savedLogin.value.nickname ||
      userProfile.value.nickname ||
      username.value.trim() ||
      t('login.user')
    )
  }
  const user = username.value.trim()
  return user || (loginMode.value === 'password' ? t('login.account') : t('login.enterAccount'))
})

const displayAvatarUrl = computed(() => {
  if (!matchedSavedAccount.value) return undefined
  return savedLogin.value.avatar || userProfile.value.avatar || undefined
})

const displayAvatarText = computed(() => displayNickname.value.charAt(0) || '?')

const loginButtonText = computed(() => {
  if (autoLoginPhase.value === 'checking') return t('login.checkingNetwork')
  if (autoLoginPhase.value === 'logging-in' || authInitializing.value) return t('login.autoLogging')
  if (isLoading.value) return t('login.loggingIn')
  return t('login.login')
})

const captchaId = ref('')
const captchaImage = ref('')
const captchaCode = ref('')
const captchaType = ref<'image' | 'slider'>('image')
const puzzleImage = ref('')
const puzzleY = ref(0)
/** 与后端 CAPTCHA_ENABLED 对齐；默认 true，拉取 /auth/config 后再更新 */
const captchaEnabled = ref(true)
const registerEnabled = ref(true)
const forgotPasswordEmailEnabled = ref(true)
const passwordPolicy = ref({
  minLength: 8,
  maxLength: 64,
  requireUpperLower: false,
  requireDigit: true,
  requireSpecial: false,
})

async function loadAuthConfig() {
  try {
    const res = await authApi.fetchAuthConfig()
    if (res.code === 200 && res.data) {
      captchaEnabled.value = !!res.data.captchaEnabled
      captchaType.value = res.data.captchaType === 'slider' ? 'slider' : 'image'
      registerEnabled.value = res.data.registerEnabled !== false
      forgotPasswordEmailEnabled.value = res.data.forgotPasswordEmailEnabled !== false
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
    // 拉不到配置时保持展示验证码，避免误关
    captchaEnabled.value = true
    registerEnabled.value = true
    forgotPasswordEmailEnabled.value = true
  }
}

const showForgot = ref(false)
const forgotStep = ref<'input' | 'verify' | 'reset'>('input')
const forgotUser = ref('')
const forgotCode = ref('')
const forgotNewPassword = ref('')
const forgotConfirmPassword = ref('')
const forgotLoading = ref(false)
const forgotSendLoading = ref(false)
const forgotCountdown = ref(0)
let forgotCountdownTimer: ReturnType<typeof setInterval> | null = null

const compact = computed(() => isElectron)

/** 开启自动登录时，登录页至少展示时长（毫秒），避免秒进主界面 */
const AUTO_LOGIN_MIN_DISPLAY_MS = 2500
let autoLoginScheduleTimer: ReturnType<typeof setTimeout> | null = null

function cancelAutoLoginSchedule() {
  if (autoLoginScheduleTimer) {
    clearTimeout(autoLoginScheduleTimer)
    autoLoginScheduleTimer = null
  }
}

function scheduleAutoLoginAfterMinDisplay() {
  cancelAutoLoginSchedule()
  autoLoginScheduleTimer = setTimeout(() => {
    autoLoginScheduleTimer = null
    void runAutoLoginFlow()
  }, AUTO_LOGIN_MIN_DISPLAY_MS)
}

const showMenu = ref(false)
const showNetworkTip = ref(false)
const showFeedback = ref(false)
const feedbackText = ref('')
const feedbackType = ref<'bug' | 'suggestion' | 'other'>('suggestion')
const feedbackContact = ref('')
const feedbackLoading = ref(false)

const feedbackTypeOptions = computed(() => [
  { label: t('login.suggestion'), value: 'suggestion' },
  { label: t('login.feedback'), value: 'bug' },
  { label: t('login.other'), value: 'other' }
])

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function closeMenu() {
  showMenu.value = false
}

function onMenuAction(key: 'network' | 'forgot' | 'feedback') {
  closeMenu()
  if (key === 'network') {
    showNetworkTip.value = true
    return
  }
  if (key === 'forgot') {
    if (!forgotPasswordEmailEnabled.value) {
      message.warning(t('login.forgotDisabled'))
      return
    }
    void openForgot()
    return
  }
  showFeedback.value = true
  feedbackText.value = ''
  feedbackContact.value = ''
  feedbackType.value = 'suggestion'
}

async function submitFeedback() {
  const text = feedbackText.value.trim()
  if (!text) {
    message.warning(t('login.fillFeedback'))
    return
  }
  feedbackLoading.value = true
  try {
    const res = await feedbackApi.submitFeedback({
      type: feedbackType.value,
      content: text,
      contact: feedbackContact.value.trim() || undefined
    })
    if (res.code === 200) {
      message.success(t('login.thanksFeedback'))
      showFeedback.value = false
    } else {
      message.error(res.message || t('login.submitFail'))
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.submitFailHint'))
  } finally {
    feedbackLoading.value = false
  }
}

function onDocClick() {
  if (showMenu.value) closeMenu()
}

function onSliderSuccess(offset: number) {
  captchaCode.value = String(offset)
}

async function loadCaptcha(target: 'login' | 'register' = 'login') {
  if (target !== 'login' || !captchaEnabled.value) return
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      captchaId.value = res.data.captchaId
      captchaImage.value = res.data.imageBase64
      captchaType.value = res.data.type === 'slider' ? 'slider' : 'image'
      puzzleImage.value = res.data.puzzleImageBase64 || ''
      puzzleY.value = res.data.puzzleY ?? 0
      captchaCode.value = ''
    }
  } catch {
    message.error(t('login.captchaFail'))
  }
}

function switchToPasswordMode() {
  if (autoLogging.value) return
  loginMode.value = 'password'
  requestAnimationFrame(() => {
    void loadCaptcha('login')
  })
}

function switchToQuickMode() {
  if (autoLogging.value) return
  if (!username.value.trim() && savedLogin.value.username) {
    username.value = savedLogin.value.username
  }
  if (!username.value.trim()) {
    message.warning(t('login.enterUsernameFirst'))
    return
  }
  loginMode.value = 'quick'
}

function applyRegisteredUsername(): boolean {
  try {
    const pending = localStorage.getItem('linkx:registered-username')
    if (!pending) return false
    localStorage.removeItem('linkx:registered-username')
    username.value = pending
    loginMode.value = 'password'
    rememberMe.value = true
    requestAnimationFrame(() => {
      void loadCaptcha('login')
    })
    return true
  } catch {
    return false
  }
}

function onWindowFocus() {
  applyRegisteredUsername()
}

async function runAutoLoginFlow() {
  if (autoLoginPhase.value !== 'idle') return
  cancelAutoLoginSchedule()

  // 1) 先扫描是否离线
  autoLoginPhase.value = 'checking'
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    autoLoginPhase.value = 'idle'
    autoLogin.value = false
    message.warning(t('login.offlineAutoLogin'))
    showNetworkTip.value = true
    return
  }

  // 2) 在线：进入自动登录
  autoLoginPhase.value = 'logging-in'
  try {
    const result = await appStore.tryAutoLogin()
    if (result === 'offline') {
      autoLogin.value = false
      message.warning(t('login.offlineAutoLogin'))
      showNetworkTip.value = true
    } else if (result === 'failed') {
      autoLogin.value = false
      message.error(t('login.autoLoginFail'))
      if (loginMode.value === 'quick') {
        switchToPasswordMode()
      }
    }
  } finally {
    autoLoginPhase.value = 'idle'
  }
}

onMounted(() => {
  username.value = savedLogin.value.username || ''
  rememberMe.value = savedLogin.value.rememberMe ?? true
  autoLogin.value = savedLogin.value.autoLogin ?? false
  document.addEventListener('click', onDocClick)
  window.addEventListener('focus', onWindowFocus)

  void loadAuthConfig().then(() => {
    const fromRegister = applyRegisteredUsername()
    if (!fromRegister) {
      if (username.value) {
        loginMode.value = 'quick'
      } else {
        loginMode.value = 'password'
        requestAnimationFrame(() => {
          void loadCaptcha('login')
        })
      }
    }

    // 自动登录：先展示登录页数秒，再检网络并登录
    if (!fromRegister && autoLogin.value && rememberMe.value && username.value) {
      loginMode.value = 'quick'
      void nextTick().then(() => {
        requestAnimationFrame(() => {
          scheduleAutoLoginAfterMinDisplay()
        })
      })
    }
  })
})

onUnmounted(() => {
  cancelAutoLoginSchedule()
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('focus', onWindowFocus)
})

watch(rememberMe, val => {
  if (!val) autoLogin.value = false
})

watch(autoLogin, val => {
  if (val) rememberMe.value = true
})

async function handleLogin() {
  if (autoLogging.value || isLoading.value) return

  // 快速登录：有 refreshToken 则走自动登录，否则切到账密
  if (loginMode.value === 'quick') {
    if (await hasRefreshToken()) {
      autoLogin.value = true
      rememberMe.value = true
      cancelAutoLoginSchedule()
      void runAutoLoginFlow()
      return
    }
    switchToPasswordMode()
    message.info(t('login.enterPassword'))
    return
  }

  const user = username.value.trim()
  const pass = password.value

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }
  const passErr = validatePassword(pass)
  if (passErr) {
    message.warning(passErr)
    return
  }
  if (captchaEnabled.value && !captchaCode.value.trim()) {
    message.warning(
      captchaType.value === 'slider' ? t('captcha.completeSlider') : t('login.enterCaptcha')
    )
    return
  }

  try {
    await login(user, pass, {
      rememberMe: rememberMe.value,
      autoLogin: autoLogin.value,
      ...(captchaEnabled.value
        ? { captchaId: captchaId.value, captchaCode: captchaCode.value.trim() }
        : {})
    })
    message.success(t('login.welcomeBack', { user }))
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.loginFail'))
    await loadCaptcha('login')
  }
}

function openRegister() {
  if (autoLogging.value) return
  if (!registerEnabled.value) {
    message.warning(t('login.registerDisabled'))
    return
  }
  if (window.electronAPI?.openRegister) {
    window.electronAPI.openRegister()
    return
  }
  // Web：新开标签/窗口，不替换当前登录页
  const url = `${window.location.origin}${window.location.pathname}${window.location.search}#/register`
  const popup = window.open(url, 'linkx-register', 'width=360,height=640,menubar=no,toolbar=no,location=no,status=no')
  if (!popup) {
    void router.push('/register')
  }
}

async function openForgot() {
  if (autoLogging.value) return
  showForgot.value = true
  forgotStep.value = 'input'
  forgotUser.value = username.value.trim() || ''
  forgotCode.value = ''
  forgotNewPassword.value = ''
  forgotConfirmPassword.value = ''
  forgotCountdown.value = 0
  if (forgotCountdownTimer) {
    clearInterval(forgotCountdownTimer)
    forgotCountdownTimer = null
  }
}

async function handleSendResetCode() {
  const user = forgotUser.value.trim()
  if (!user) {
    message.warning(t('login.enterLinkxId'))
    return
  }

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }

  forgotSendLoading.value = true
  try {
    await sendResetCode({ username: user })
    message.success(t('login.codeSentEmail'))
    forgotStep.value = 'verify'
    startCountdown()
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.sendCodeFail'))
  } finally {
    forgotSendLoading.value = false
  }
}

const verifyLoading = ref(false)
async function handleVerifyCode() {
  const code = forgotCode.value.trim()
  if (!code) {
    message.warning(t('login.enterCode'))
    return
  }
  if (code.length !== 6) {
    message.warning(t('login.codeSixDigits'))
    return
  }

  verifyLoading.value = true
  try {
    await verifyResetCode({ username: forgotUser.value.trim(), code })
    forgotStep.value = 'reset'
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.codeWrong'))
  } finally {
    verifyLoading.value = false
  }
}

async function handleResendCode() {
  forgotSendLoading.value = true
  try {
    await sendResetCode({ username: forgotUser.value.trim() })
    message.success(t('login.codeResent'))
    startCountdown()
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.sendCodeFail'))
  } finally {
    forgotSendLoading.value = false
  }
}

function startCountdown() {
  forgotCountdown.value = 60
  if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
  forgotCountdownTimer = setInterval(() => {
    forgotCountdown.value--
    if (forgotCountdown.value <= 0) {
      if (forgotCountdownTimer) clearInterval(forgotCountdownTimer)
      forgotCountdownTimer = null
    }
  }, 1000)
}

async function handleForgot() {
  const user = forgotUser.value.trim()
  const newPass = forgotNewPassword.value
  const confirmPass = forgotConfirmPassword.value

  const passErr = validatePassword(newPass, passwordPolicy.value)
  if (passErr) {
    message.warning(passErr)
    return
  }
  if (newPass !== confirmPass) {
    message.warning(t('login.passwordMismatch'))
    return
  }

  forgotLoading.value = true
  try {
    await resetPasswordByEmail({
      username: user,
      code: forgotCode.value.trim(),
      newPassword: newPass
    })
    message.success(t('login.resetOk'))
    showForgot.value = false
    username.value = user
    password.value = ''
    loginMode.value = 'password'
    await loadCaptcha('login')
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('login.resetFail'))
  } finally {
    forgotLoading.value = false
  }
}
</script>

<template>
  <div class="login-page" :class="{ 'login-page--compact': compact }">
    <div class="login-atmosphere" aria-hidden="true">
      <span class="orb orb-a" />
      <span class="orb orb-b" />
      <span class="orb orb-c" />
      <span class="mesh" />
    </div>

    <div class="login-win-bar">
      <div class="drag-area" />
      <div class="login-win-actions" @click.stop>
        <button
          type="button"
          class="lx-win-caption-btn lx-win-caption-btn--login-menu"
          :title="t('login.menu')"
          @click="toggleMenu"
        >
          <span class="web-menu-ico" />
        </button>
        <div v-if="showMenu" class="login-menu" role="menu">
          <button type="button" class="login-menu-item" role="menuitem" @click="onMenuAction('network')">
            {{ t('login.network') }}
          </button>
          <button
            v-if="forgotPasswordEmailEnabled"
            type="button"
            class="login-menu-item"
            role="menuitem"
            @click="onMenuAction('forgot')"
          >
            {{ t('login.forgot') }}
          </button>
          <button type="button" class="login-menu-item" role="menuitem" @click="onMenuAction('feedback')">
            {{ t('login.feedback') }}
          </button>
        </div>
      </div>
      <WindowCaptionButtons :show-maximize="false" />
    </div>

    <div class="login-body" :class="{ 'login-body--password': loginMode === 'password' }">
      <div
        class="brand-title"
        :class="{ 'brand-title--compact': loginMode === 'password' }"
        aria-label="LinkX"
      >
        <BrandMarkIcon :size="loginMode === 'password' ? 34 : 40" />
        <span class="brand-text">LinkX</span>
      </div>

      <div class="profile-block" :class="{ 'profile-block--password': loginMode === 'password' }">
        <div class="avatar-ring" :class="{ 'avatar-ring--lg': loginMode === 'quick' }">
          <div class="avatar-glow" aria-hidden="true" />
          <Avatar
            :text="displayAvatarText"
            color="var(--lx-accent)"
            :size="loginMode === 'quick' ? 88 : 68"
            :image-url="displayAvatarUrl"
          />
        </div>
        <!-- 快速登录：头像下方昵称 + 居中自动登录 -->
        <template v-if="loginMode === 'quick'">
          <div class="profile-nickname">
            <span class="nickname-text">{{ displayNickname }}</span>
            <n-icon
              :component="ChevronDownOutline"
              :size="16"
              class="nickname-chevron"
              :title="t('login.passwordLogin')"
              @click="switchToPasswordMode"
            />
          </div>
          <div class="options options--quick">
            <n-checkbox v-model:checked="autoLogin" size="small" :disabled="autoLogging">
              {{ t('login.autoLogin') }}
            </n-checkbox>
          </div>
        </template>
      </div>

      <!-- 快速登录 -->
      <div v-if="loginMode === 'quick'" class="quick-panel lx-panel">
        <LxButton
          variant="login"
          :class="{ 'is-loading': isLoading || autoLogging }"
          :disabled="isLoading || autoLogging"
          @click="handleLogin"
        >
          <span v-if="autoLogging || isLoading" class="lx-btn-spinner" aria-hidden="true" />
          <span>{{ loginButtonText }}</span>
        </LxButton>
      </div>

      <!-- 账密登录 -->
      <div v-else class="password-panel lx-panel">
        <n-input
          v-model:value="username"
          size="large"
          :placeholder="t('login.accountPh')"
          class="lx-field"
          :bordered="false"
          :disabled="autoLogging"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="PersonOutline" :size="16" class="field-ico" />
          </template>
        </n-input>
        <n-input
          v-model:value="password"
          type="password"
          show-password-on="click"
          size="large"
          :placeholder="t('login.passwordPh')"
          class="lx-field"
          :bordered="false"
          :disabled="autoLogging"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" :size="16" class="field-ico" />
          </template>
        </n-input>

        <div v-if="captchaEnabled" class="captcha-row">
          <SliderCaptcha
            v-if="captchaType === 'slider'"
            :background="captchaImage"
            :puzzle="puzzleImage"
            :puzzle-y="puzzleY"
            :disabled="autoLogging"
            @success="onSliderSuccess"
            @refresh="loadCaptcha('login')"
          />
          <template v-else>
            <div
              v-if="!captchaImage"
              class="captcha-img captcha-img--placeholder"
              :title="t('login.refreshCaptcha')"
              @click="!autoLogging && loadCaptcha('login')"
            />
            <img
              v-else
              :src="captchaImage"
              :alt="t('login.captcha')"
              class="captcha-img"
              :title="t('login.refreshCaptcha')"
              @click="!autoLogging && loadCaptcha('login')"
            />
            <n-input
              v-model:value="captchaCode"
              size="large"
              :placeholder="t('login.captcha')"
              class="lx-field captcha-input"
              :bordered="false"
              maxlength="4"
              :disabled="autoLogging"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <n-icon :component="ShieldCheckmarkOutline" :size="15" class="field-ico" />
              </template>
            </n-input>
            <LxIconButton
              class="captcha-refresh"
              :title="t('login.refreshCaptcha')"
              :disabled="autoLogging"
              @click="loadCaptcha('login')"
            >
              <n-icon :component="RefreshOutline" />
            </LxIconButton>
          </template>
        </div>

        <div class="options">
          <n-checkbox v-model:checked="autoLogin" size="small" :disabled="autoLogging">
            {{ t('login.autoLogin') }}
          </n-checkbox>
          <n-checkbox v-model:checked="rememberMe" size="small" :disabled="autoLogging">
            {{ t('login.rememberAccount') }}
          </n-checkbox>
        </div>

        <LxButton
          variant="login"
          :class="{ 'is-loading': isLoading }"
          :disabled="isLoading"
          @click="handleLogin"
        >
          <span v-if="isLoading" class="lx-btn-spinner" aria-hidden="true" />
          <span>{{ loginButtonText }}</span>
        </LxButton>
      </div>

      <div class="footer">
        <template v-if="loginMode === 'quick'">
          <a
            href="#"
            class="footer-link"
            :class="{ 'is-disabled': autoLogging }"
            @click.prevent="switchToPasswordMode"
          >{{ t('login.passwordLogin') }}</a>
          <template v-if="registerEnabled">
            <span class="footer-sep" />
            <a
              href="#"
              class="footer-link"
              :class="{ 'is-disabled': autoLogging }"
              @click.prevent="openRegister"
            >{{ t('login.register') }}</a>
          </template>
        </template>
        <template v-else>
          <a
            v-if="username.trim()"
            href="#"
            class="footer-link"
            @click.prevent="switchToQuickMode"
          >{{ t('login.quickLogin') }}</a>
          <span v-if="username.trim() && registerEnabled" class="footer-sep" />
          <a v-if="registerEnabled" href="#" class="footer-link" @click.prevent="openRegister">{{ t('login.register') }}</a>
        </template>
      </div>
    </div>

    <n-modal v-model:show="showNetworkTip" preset="dialog" :title="t('login.network')" :positive-text="t('common.know')" @positive-click="showNetworkTip = false">
      <p class="dialog-tip">{{ t('login.networkTip') }}</p>
    </n-modal>

    <n-modal v-model:show="showFeedback" preset="dialog" :title="t('login.feedback')" style="max-width: 400px">
      <div class="feedback-form">
        <n-select v-model:value="feedbackType" :options="feedbackTypeOptions" />
        <n-input
          v-model:value="feedbackText"
          type="textarea"
          :placeholder="t('login.feedbackPh')"
          :rows="4"
          style="margin-top: 12px"
        />
        <n-input
          v-model:value="feedbackContact"
          :placeholder="t('login.contactPh')"
          style="margin-top: 12px"
        />
      </div>
      <template #action>
        <LxButton variant="modal" @click="showFeedback = false">{{ t('common.cancel') }}</LxButton>
        <LxButton variant="modal-primary" :disabled="feedbackLoading" @click="submitFeedback">
          {{ t('common.submit') }}
        </LxButton>
      </template>
    </n-modal>

    <n-modal v-model:show="showForgot" preset="dialog" :title="t('login.forgotTitle')" style="max-width: 400px">
      <div class="forgot-form">
        <template v-if="forgotStep === 'input'">
          <div class="forgot-tip">
            <n-icon :component="MailOutline" :size="20" class="forgot-tip-icon" />
            <p>{{ t('login.forgotStep1') }}</p>
          </div>
          <div class="form-item">
            <label>LinkX ID</label>
            <n-input
              v-model:value="forgotUser"
              :placeholder="t('login.usernamePh')"
              @keyup.enter="handleSendResetCode"
            />
          </div>
        </template>

        <template v-else-if="forgotStep === 'verify'">
          <div class="forgot-tip">
            <p>{{ t('login.forgotStep2') }}</p>
          </div>
          <div class="form-item">
            <label>{{ t('login.codeLabel') }}</label>
            <n-input
              v-model:value="forgotCode"
              :placeholder="t('login.code6Ph')"
              maxlength="6"
              @keyup.enter="handleVerifyCode"
            />
          </div>
          <div class="resend-row">
            <span v-if="forgotCountdown > 0" class="resend-tips">
              {{ t('login.resendIn', { n: forgotCountdown }) }}
            </span>
            <a
              v-else
              href="#"
              class="resend-link"
              :class="{ 'is-disabled': forgotSendLoading }"
              @click.prevent="handleResendCode"
            >
              {{ t('login.resendCode') }}
            </a>
            <span class="resend-sep">|</span>
            <a href="#" class="resend-link" @click.prevent="forgotStep = 'input'">{{ t('login.backPrev') }}</a>
          </div>
        </template>

        <template v-else>
          <div class="forgot-tip">
            <p>{{ t('login.forgotStep3') }}</p>
          </div>
          <div class="form-item">
            <label>{{ t('login.newPassword') }}</label>
            <n-input
              v-model:value="forgotNewPassword"
              type="password"
              show-password-on="click"
              :placeholder="t('login.newPasswordPh')"
            />
          </div>
          <div class="form-item">
            <label>{{ t('login.confirmPassword') }}</label>
            <n-input
              v-model:value="forgotConfirmPassword"
              type="password"
              show-password-on="click"
              :placeholder="t('login.confirmPasswordPh')"
              @keyup.enter="handleForgot"
            />
          </div>
        </template>
      </div>
      <template #action>
        <LxButton variant="modal" @click="showForgot = false">{{ t('common.cancel') }}</LxButton>
        <LxButton
          v-if="forgotStep === 'input'"
          variant="modal-primary"
          :disabled="forgotSendLoading"
          @click="handleSendResetCode"
        >
          {{ t('login.sendCode') }}
        </LxButton>
        <LxButton
          v-else-if="forgotStep === 'verify'"
          variant="modal-primary"
          :disabled="verifyLoading"
          @click="handleVerifyCode"
        >
          {{ t('login.next') }}
        </LxButton>
        <LxButton
          v-else
          variant="modal-primary"
          :disabled="forgotLoading"
          @click="handleForgot"
        >
          {{ t('login.resetPassword') }}
        </LxButton>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.login-page {
  --lx-login-accent: var(--lx-accent);
  --lx-login-accent-deep: var(--lx-accent-deep);
  
  
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background:
    var(--lx-login-bg-gradient);
  overflow: hidden;
  color: var(--lx-login-ink);
  font-family: 'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.login-atmosphere {
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
  top: -12%;
  left: -18%;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(18, 183, 245, 0.34) 0%, transparent 68%);
}

.orb-b {
  top: 8%;
  right: -22%;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(255, 176, 210, 0.32) 0%, transparent 70%);
  animation-delay: -4s;
}

.orb-c {
  bottom: -18%;
  left: 18%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(91, 140, 255, 0.22) 0%, transparent 70%);
  animation-delay: -8s;
}

.mesh {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image:
    radial-gradient(rgba(255, 255, 255, 0.55) 0.8px, transparent 0.8px);
  background-size: 18px 18px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 75%);
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

.login-page--compact {
  min-height: 461px;
  padding: 0;
  border-radius: var(--lx-window-radius);
  overflow: hidden;
  clip-path: inset(0 round var(--lx-window-radius));
}

.login-win-bar {
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

.drag-area {
  flex: 1;
  height: 100%;
  -webkit-app-region: drag;
}

.login-win-actions {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--lx-space-xs);
  -webkit-app-region: no-drag;
  padding-right: var(--lx-space-xs);
}

.lx-win-caption-btn--login-menu {
  width: 36px;
}

.lx-win-caption-btn--login-menu::before {
  background: rgba(255, 255, 255, 0.55);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);
}

.lx-win-caption-btn--login-menu:hover::before {
  background: rgba(255, 255, 255, 0.85);
}

.web-menu-ico,
.web-menu-ico::before,
.web-menu-ico::after {
  display: block;
  width: 12px;
  height: 1.5px;
  background: var(--lx-ink-soft);
  border-radius: var(--lx-radius-hair);
}

.web-menu-ico {
  position: relative;
}

.web-menu-ico::before,
.web-menu-ico::after {
  content: '';
  position: absolute;
  left: 0;
}

.web-menu-ico::before {
  top: -4px;
}

.web-menu-ico::after {
  top: 4px;
}

.login-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 6px;
  min-width: 128px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  border-radius: var(--lx-radius-xl);
  box-shadow: 0 8px 28px rgba(26, 35, 50, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.8);
  padding: var(--lx-space-sm);
  z-index: var(--lx-z-dock);
  animation: menu-in var(--lx-duration) ease;
}

@keyframes menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-menu-item {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: var(--lx-space) var(--lx-space-lg);
  font-size: var(--lx-font-md);
  color: var(--lx-login-ink);
  cursor: pointer;
  border-radius: var(--lx-radius-xs);
}

.login-menu-item:hover {
  background: rgba(18, 183, 245, 0.1);
  color: var(--lx-login-accent-deep);
}

.login-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--lx-space-xs) var(--lx-space-4xl-plus) var(--lx-space-2xl);
  position: relative;
  z-index: var(--lx-z-raised);
  box-sizing: border-box;
}

.login-body--password {
  padding: 0 var(--lx-space-3xl-plus) var(--lx-space-lg);
}

.brand-title {
  position: relative;
  z-index: var(--lx-z-raised);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space);
  margin-top: var(--lx-space-xs);
  margin-bottom: var(--lx-space-2xl);
  user-select: none;
  animation: rise-in var(--lx-duration-slower) ease both;
}

.brand-title--compact {
  margin-top: 0;
  margin-bottom: var(--lx-space);
  gap: var(--lx-space-sm);
}

.brand-text {
  font-size: var(--lx-font-6xl);
  font-weight: 720;
  letter-spacing: 0.5px;
  line-height: var(--lx-leading-none);
  background: linear-gradient(100deg, var(--lx-accent-sky) 0%, var(--lx-accent) 35%, var(--lx-brand-blue-mid) 70%, var(--lx-brand-purple) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-title--compact .brand-text {
  font-size: var(--lx-font-4xl);
}

.profile-block {
  position: relative;
  z-index: var(--lx-z-raised);
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: var(--lx-space-2xl);
  gap: var(--lx-space-xl);
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration-instant) both;
}

.profile-block--password {
  margin-top: var(--lx-space-2xs);
  margin-bottom: var(--lx-space-xl);
  gap: 0;
}

.avatar-ring {
  position: relative;
  padding: var(--lx-space-2xs);
  border-radius: var(--lx-avatar-radius);
  background: var(--lx-login-card-gradient);
  box-shadow:
    0 8px 24px rgba(18, 183, 245, 0.2),
    0 0 0 1px rgba(255, 255, 255, 0.8);
}

.avatar-ring--lg {
  padding: var(--lx-space-xs);
}

.avatar-glow {
  position: absolute;
  inset: -18px;
  border-radius: var(--lx-avatar-radius);
  background: radial-gradient(circle, rgba(18, 183, 245, 0.22) 0%, transparent 68%);
  pointer-events: none;
  z-index: -1;
}

.profile-nickname {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space-2xs);
  max-width: 220px;
  margin-top: var(--lx-space-2xs);
}

.nickname-text {
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-login-ink);
  line-height: var(--lx-leading-snug);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nickname-chevron {
  color: var(--lx-login-text-muted-alt);
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--lx-duration) ease, transform var(--lx-duration) ease;
}

.nickname-chevron:hover {
  color: var(--lx-login-accent);
  transform: translateY(1px);
}

.lx-panel {
  width: 100%;
  position: relative;
  z-index: var(--lx-z-raised);
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration-faster) both;
}

.password-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--lx-space);
  flex: 1;
  min-height: 0;
}

.quick-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--lx-space-md);
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

.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lx-space-2xs) var(--lx-space-2xs) 0;
}

.options--quick {
  justify-content: center;
  width: 100%;
  margin: 0;
  padding-top: var(--lx-space-2xs);
}

.options :deep(.n-checkbox .n-checkbox__label) {
  font-size: var(--lx-font-sm);
  color: var(--lx-login-muted);
}

.options :deep(.n-checkbox.n-checkbox--checked .n-checkbox-box) {
  background-color: var(--lx-login-accent);
  border-color: var(--lx-login-accent);
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
  padding-top: var(--lx-space-md);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space-lg);
  font-size: var(--lx-font-md);
  position: relative;
  z-index: var(--lx-z-raised);
  animation: rise-in var(--lx-duration-slowest) ease var(--lx-duration) both;
}

.footer-link {
  color: var(--lx-login-accent-deep);
  text-decoration: none;
  font-weight: 500;
  transition: color var(--lx-duration) ease;
}

.footer-link:hover {
  color: var(--lx-login-link-hover);
  text-decoration: none;
}

.footer-link.disabled {
  color: var(--lx-login-text-faint);
  pointer-events: none;
  text-decoration: none;
}

.footer-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--lx-login-track);
  user-select: none;
}

.dialog-tip {
  margin: 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
  line-height: var(--lx-leading-relaxed);
}

.feedback-form {
  display: flex;
  flex-direction: column;
}

.forgot-form {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xl);
}

.forgot-form .form-item {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-sm);
}

.forgot-form .form-item label {
  font-size: var(--lx-font);
  color: var(--lx-text-secondary);
}

.forgot-tip {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space-lg) var(--lx-space-2xl);
  background: var(--lx-login-panel-gradient);
  border-left: 3px solid var(--lx-login-accent);
  border-radius: var(--lx-radius-sm);
  margin-bottom: var(--lx-space);
}

.forgot-tip-icon {
  color: var(--lx-login-accent);
  flex-shrink: 0;
}

.forgot-tip p {
  margin: 0;
  color: var(--lx-text-secondary);
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
}

.resend-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  font-size: var(--lx-font-md);
}

.resend-tips {
  color: var(--lx-text-muted);
}

.resend-link {
  color: var(--lx-login-accent);
  text-decoration: none;
  cursor: pointer;
}

.resend-link:hover {
  text-decoration: underline;
}

.resend-link.disabled {
  color: var(--lx-border-strong);
  cursor: not-allowed;
  pointer-events: none;
}

.resend-sep {
  color: var(--lx-border-strong);
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .brand-title,
  .profile-block,
  .lx-panel,
  .footer,
  .login-menu {
    animation: none !important;
  }
}
</style>
