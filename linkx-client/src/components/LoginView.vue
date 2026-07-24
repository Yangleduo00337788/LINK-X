<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NIcon, NCheckbox, NModal, NSelect, useMessage } from 'naive-ui'
import { RefreshOutline, MailOutline, ChevronDownOutline } from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
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
/** 与后端 CAPTCHA_ENABLED 对齐；默认 true，拉取 /auth/config 后再更新 */
const captchaEnabled = ref(true)

async function loadAuthConfig() {
  try {
    const res = await authApi.fetchAuthConfig()
    if (res.code === 200 && res.data) {
      captchaEnabled.value = !!res.data.captchaEnabled
    }
  } catch {
    // 拉不到配置时保持展示验证码，避免误关
    captchaEnabled.value = true
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

async function loadCaptcha(target: 'login' | 'register' = 'login') {
  if (target !== 'login' || !captchaEnabled.value) return
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      captchaId.value = res.data.captchaId
      captchaImage.value = res.data.imageBase64
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

  // 1) 先扫描是否离线
  autoLoginPhase.value = 'checking'
  await new Promise<void>(resolve => setTimeout(resolve, 280))
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

    // 自动登录：先检网络，再登录（文案：检测网络中 → 自动登录中）
    if (!fromRegister && autoLogin.value && rememberMe.value && username.value) {
      loginMode.value = 'quick'
      void nextTick().then(() => {
        requestAnimationFrame(() => {
          void runAutoLoginFlow()
        })
      })
    }
  })
})

onUnmounted(() => {
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
    message.warning(t('login.enterCaptcha'))
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
  if (window.electronAPI?.openRegister) {
    window.electronAPI.openRegister()
    return
  }
  // Web：新开标签/窗口，不替换当前登录页
  const url = `${window.location.origin}${window.location.pathname}${window.location.search}#/register`
  const popup = window.open(url, 'linkx-register', 'width=360,height=560,menubar=no,toolbar=no,location=no,status=no')
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

  const passErr = validatePassword(newPass)
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
    <div class="login-win-bar">
      <div class="drag-area" />
      <div class="login-win-actions" @click.stop>
        <button
          type="button"
          class="web-menu-btn"
          :title="t('login.menu')"
          @click="toggleMenu"
        >
          <span class="web-menu-ico" />
        </button>
        <div v-if="showMenu" class="login-menu" role="menu">
          <button type="button" class="login-menu-item" role="menuitem" @click="onMenuAction('network')">
            {{ t('login.network') }}
          </button>
          <button type="button" class="login-menu-item" role="menuitem" @click="onMenuAction('forgot')">
            {{ t('login.forgot') }}
          </button>
          <button type="button" class="login-menu-item" role="menuitem" @click="onMenuAction('feedback')">
            {{ t('login.feedback') }}
          </button>
        </div>
      </div>
    </div>

    <div class="login-body" :class="{ 'login-body--password': loginMode === 'password' }">
      <div class="avatar-glow" aria-hidden="true" />

      <!-- 彩色品牌名仅快速登录展示；账密页只留头像，腾出表单空间 -->
      <div v-if="loginMode === 'quick'" class="brand-title" aria-label="LinkX">LinkX</div>

      <div class="profile-block" :class="{ 'profile-block--password': loginMode === 'password' }">
        <div class="avatar-ring">
          <Avatar
            :text="displayAvatarText"
            color="#12b7f5"
            :size="loginMode === 'quick' ? 88 : 72"
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
      <div v-if="loginMode === 'quick'" class="quick-panel">
        <button
          type="button"
          class="lx-login-btn"
          :class="{ loading: isLoading || autoLogging }"
          :disabled="isLoading || autoLogging"
          @click="handleLogin"
        >
          <span v-if="autoLogging || isLoading" class="btn-spinner" aria-hidden="true" />
          <span>{{ loginButtonText }}</span>
        </button>
      </div>

      <!-- 账密登录 -->
      <div v-else class="password-panel">
        <n-input
          v-model:value="username"
          size="large"
          :placeholder="t('login.accountPh')"
          class="lx-field"
          :bordered="false"
          :disabled="autoLogging"
          @keyup.enter="handleLogin"
        />
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
        />

        <div v-if="captchaEnabled" class="captcha-row">
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
          />
          <n-button quaternary circle :disabled="autoLogging" @click="loadCaptcha('login')">
            <template #icon>
              <n-icon :component="RefreshOutline" />
            </template>
          </n-button>
        </div>

        <div class="options">
          <n-checkbox v-model:checked="autoLogin" size="small" :disabled="autoLogging">
            {{ t('login.autoLogin') }}
          </n-checkbox>
          <n-checkbox v-model:checked="rememberMe" size="small" :disabled="autoLogging">
            {{ t('login.rememberAccount') }}
          </n-checkbox>
        </div>

        <button
          type="button"
          class="lx-login-btn"
          :class="{ loading: isLoading }"
          :disabled="isLoading"
          @click="handleLogin"
        >
          <span v-if="isLoading" class="btn-spinner" aria-hidden="true" />
          <span>{{ loginButtonText }}</span>
        </button>
      </div>

      <div class="footer">
        <template v-if="loginMode === 'quick'">
          <a
            href="#"
            class="footer-link"
            :class="{ disabled: autoLogging }"
            @click.prevent="switchToPasswordMode"
          >{{ t('login.passwordLogin') }}</a>
          <span class="footer-sep">|</span>
          <a
            href="#"
            class="footer-link"
            :class="{ disabled: autoLogging }"
            @click.prevent="openRegister"
          >{{ t('login.register') }}</a>
        </template>
        <template v-else>
          <a
            v-if="username.trim()"
            href="#"
            class="footer-link"
            @click.prevent="switchToQuickMode"
          >{{ t('login.quickLogin') }}</a>
          <span v-if="username.trim()" class="footer-sep">|</span>
          <a href="#" class="footer-link" @click.prevent="openRegister">{{ t('login.register') }}</a>
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
        <n-button @click="showFeedback = false">{{ t('common.cancel') }}</n-button>
        <n-button type="primary" :loading="feedbackLoading" @click="submitFeedback">{{ t('common.submit') }}</n-button>
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
              :class="{ disabled: forgotSendLoading }"
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
        <n-button @click="showForgot = false">{{ t('common.cancel') }}</n-button>
        <n-button
          v-if="forgotStep === 'input'"
          type="primary"
          :loading="forgotSendLoading"
          @click="handleSendResetCode"
        >
          {{ t('login.sendCode') }}
        </n-button>
        <n-button
          v-else-if="forgotStep === 'verify'"
          type="primary"
          :loading="verifyLoading"
          @click="handleVerifyCode"
        >
          {{ t('login.next') }}
        </n-button>
        <n-button
          v-else
          type="primary"
          :loading="forgotLoading"
          @click="handleForgot"
        >
          {{ t('login.resetPassword') }}
        </n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.login-page {
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background:
    radial-gradient(ellipse 70% 45% at 50% 32%, rgba(255, 210, 230, 0.42) 0%, transparent 70%),
    linear-gradient(180deg, #dceefc 0%, #eef5fb 42%, #f7f9fc 100%);
  overflow: hidden;
}

.login-page--compact {
  min-height: 461px;
  padding: 0;
}

.login-win-bar {
  flex-shrink: 0;
  height: env(titlebar-area-height, 36px);
  width: env(titlebar-area-width, 100%);
  margin-left: env(titlebar-area-x, 0px);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 20;
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
  -webkit-app-region: no-drag;
}

.web-menu-btn {
  width: 28px;
  height: 28px;
  margin-right: 8px;
  border: none;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.web-menu-ico,
.web-menu-ico::before,
.web-menu-ico::after {
  display: block;
  width: 12px;
  height: 1.5px;
  background: #5c6370;
  border-radius: 1px;
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
  min-width: 120px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  z-index: 30;
}

.login-menu-item {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px 16px;
  font-size: 13px;
  color: #1f2329;
  cursor: pointer;
}

.login-menu-item:hover {
  background: #f5f7fa;
}

.login-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 28px 18px;
  position: relative;
  box-sizing: border-box;
}

.login-body--password {
  padding: 2px 24px 14px;
}

.avatar-glow {
  position: absolute;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.75) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.brand-title {
  position: relative;
  z-index: 1;
  margin-top: 12px;
  margin-bottom: 32px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 1px;
  line-height: 1;
  background: linear-gradient(90deg, #12b7f5 0%, #5b8cff 45%, #c45dff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  user-select: none;
}

.profile-block {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  gap: 20px;
}

.profile-block--password {
  margin-top: 10px;
  margin-bottom: 36px;
  gap: 0;
}

.avatar-ring {
  padding: 3px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 12px rgba(18, 183, 245, 0.18);
}

.profile-nickname {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  max-width: 220px;
  margin-top: 2px;
}

.nickname-text {
  font-size: 16px;
  font-weight: 500;
  color: #1a1a1a;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nickname-chevron {
  color: #8f959e;
  cursor: pointer;
  flex-shrink: 0;
}

.nickname-chevron:hover {
  color: #12b7f5;
}

.password-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
}

.quick-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 2px;
}

.options--quick {
  justify-content: center;
  width: 100%;
  margin: 0;
  padding-top: 2px;
}

.options :deep(.n-checkbox .n-checkbox__label) {
  font-size: 13px;
  color: #6b7280;
}

.options :deep(.n-checkbox.n-checkbox--checked .n-checkbox-box) {
  background-color: #12b7f5;
  border-color: #12b7f5;
}

.lx-field {
  width: 100%;
}

.lx-field :deep(.n-input-wrapper) {
  background: #ffffff;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  padding-left: 14px;
  padding-right: 14px;
  min-height: 38px;
}

.lx-field :deep(.n-input__input-el) {
  font-size: 13px;
  height: 38px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  width: 110px;
  height: 38px;
  border-radius: 10px;
  cursor: pointer;
  border: none;
  background: #fff;
  flex-shrink: 0;
  object-fit: contain;
}

.captcha-img--placeholder {
  background: linear-gradient(135deg, #e8eef5 0%, #f5f7fa 100%);
  border: 1px dashed #c5d0dc;
}

.captcha-input {
  flex: 1;
}

.lx-login-btn {
  width: 100%;
  height: 40px;
  margin-top: 10px;
  border: none;
  border-radius: 20px;
  background: #12b7f5;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 2px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.15s ease, opacity 0.15s ease;
  -webkit-app-region: no-drag;
}

.lx-login-btn:hover:not(:disabled) {
  background: #0aa6e0;
}

.lx-login-btn:disabled,
.lx-login-btn.loading {
  cursor: default;
  opacity: 0.92;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.footer {
  margin-top: auto;
  padding-top: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13px;
  position: relative;
  z-index: 1;
}

.footer-link {
  color: #12b7f5;
  text-decoration: none;
}

.footer-link:hover {
  text-decoration: underline;
}

.footer-link.disabled {
  color: #b0b8c0;
  pointer-events: none;
  text-decoration: none;
}

.footer-sep {
  color: #c5ccd3;
  user-select: none;
}

.dialog-tip {
  margin: 0;
  font-size: 13px;
  color: #555;
  line-height: 1.6;
}

.feedback-form {
  display: flex;
  flex-direction: column;
}

.forgot-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.forgot-form .form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.forgot-form .form-item label {
  font-size: 14px;
  color: #666;
}

.forgot-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f0f8ff;
  border-left: 3px solid #12b7f5;
  border-radius: 4px;
  margin-bottom: 8px;
}

.forgot-tip-icon {
  color: #12b7f5;
  flex-shrink: 0;
}

.forgot-tip p {
  margin: 0;
  color: #555;
  font-size: 13px;
  line-height: 1.5;
}

.resend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.resend-tips {
  color: #999;
}

.resend-link {
  color: #12b7f5;
  text-decoration: none;
  cursor: pointer;
}

.resend-link:hover {
  text-decoration: underline;
}

.resend-link.disabled {
  color: #ccc;
  cursor: not-allowed;
  pointer-events: none;
}

.resend-sep {
  color: #ddd;
}
</style>
