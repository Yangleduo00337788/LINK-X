<script setup lang="ts">
/**
 * 独立注册页（Electron 子窗口 / Web 路由）。
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NIcon, useMessage } from 'naive-ui'
import {
  RefreshOutline,
  PersonOutline,
  LockClosedOutline,
  HappyOutline,
  MailOutline,
  ShieldCheckmarkOutline
} from '@vicons/ionicons5'
import * as authApi from '../api/auth'
import { validateUsername, validatePassword, validateNickname } from '../utils/validation'
import { useI18n } from '../i18n'
import WindowCaptionButtons from './WindowCaptionButtons.vue'

const message = useMessage()
const router = useRouter()
const { t } = useI18n()
const isElectron = !!window.electronAPI?.isElectron

const regUser = ref('')
const regPass = ref('')
const regNickname = ref('')
const regEmail = ref('')
const regCaptchaCode = ref('')
const regCaptchaId = ref('')
const regCaptchaImage = ref('')
const captchaEnabled = ref(true)
const submitting = ref(false)

const compact = computed(() => isElectron)
const submitLabel = computed(() => (submitting.value ? t('register.submitting') : t('register.submit')))

async function loadAuthConfig() {
  try {
    const res = await authApi.fetchAuthConfig()
    if (res.code === 200 && res.data) {
      captchaEnabled.value = !!res.data.captchaEnabled
    }
  } catch {
    captchaEnabled.value = true
  }
}

async function loadCaptcha() {
  if (!captchaEnabled.value) return
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      regCaptchaId.value = res.data.captchaId
      regCaptchaImage.value = res.data.imageBase64
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

async function handleRegister() {
  const user = regUser.value.trim()
  const pass = regPass.value
  const nickname = regNickname.value.trim() || user
  const email = regEmail.value.trim()

  const userErr = validateUsername(user)
  if (userErr) {
    message.warning(userErr)
    return
  }
  const passErr = validatePassword(pass, true)
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
  if (captchaEnabled.value && !regCaptchaCode.value.trim()) {
    message.warning(t('register.enterCaptcha'))
    return
  }

  submitting.value = true
  try {
    const res = await authApi.register({
      username: user,
      password: pass,
      nickname,
      email,
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
</script>

<template>
  <div class="register-page" :class="{ 'register-page--compact': compact }">
    <div class="reg-atmosphere" aria-hidden="true">
      <span class="orb orb-a" />
      <span class="orb orb-b" />
      <span class="orb orb-c" />
      <span class="mesh" />
    </div>

    <div class="reg-win-bar">
      <div class="reg-title">{{ t('register.title') }}</div>
      <div class="drag-area" />
      <WindowCaptionButtons v-if="isElectron" :show-maximize="false" />
      <button v-else type="button" class="web-close" :title="t('common.back')" @click="closeOrBack">×</button>
    </div>

    <div class="reg-body">
      <div class="brand-title" aria-label="LinkX">
        <span class="brand-mark">L</span>
        <span class="brand-text">LinkX</span>
      </div>
      <p class="reg-desc">{{ t('register.subtitle') }}</p>

      <div class="reg-form">
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

        <div v-if="captchaEnabled" class="captcha-row">
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
          <n-button quaternary circle class="captcha-refresh" @click="loadCaptcha">
            <template #icon>
              <n-icon :component="RefreshOutline" />
            </template>
          </n-button>
        </div>

        <button
          type="button"
          class="lx-login-btn"
          :class="{ loading: submitting }"
          :disabled="submitting"
          @click="handleRegister"
        >
          <span v-if="submitting" class="btn-spinner" aria-hidden="true" />
          <span>{{ submitLabel }}</span>
        </button>
      </div>

      <div class="footer">
        <a href="#" class="footer-link" @click.prevent="closeOrBack">{{ t('register.backLogin') }}</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  --lx-login-accent: #12b7f5;
  --lx-login-accent-deep: #0aa6e0;
  --lx-login-ink: #1a2332;
  --lx-login-muted: #7a8494;
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background: linear-gradient(165deg, #d8ecfb 0%, #e8f3fc 38%, #f4f7fb 72%, #fafbfd 100%);
  overflow: hidden;
  color: var(--lx-login-ink);
  font-family: 'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.reg-atmosphere {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
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
  border-radius: var(--lx-window-radius, 20px);
  overflow: hidden;
  clip-path: inset(0 round var(--lx-window-radius, 20px));
}

.reg-win-bar {
  flex-shrink: 0;
  height: 40px;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
  -webkit-app-region: no-drag;
  position: relative;
  z-index: 20;
}

.reg-title {
  padding-left: 14px;
  font-size: 13px;
  font-weight: 500;
  color: #5c6370;
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

.web-close {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: #5c6370;
  cursor: pointer;
  line-height: 1;
  border-radius: 8px;
}

.web-close:hover {
  background: #e81123;
  color: #fff;
}

.reg-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 28px 18px;
  box-sizing: border-box;
  overflow: auto;
  position: relative;
  z-index: 1;
}

.brand-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
  margin-bottom: 8px;
  user-select: none;
  animation: rise-in 0.45s ease both;
}

.brand-mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(145deg, #39c2f6 0%, #12b7f5 48%, #5b8cff 100%);
  box-shadow:
    0 6px 16px rgba(18, 183, 245, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.brand-text {
  font-size: 28px;
  font-weight: 720;
  letter-spacing: 0.5px;
  line-height: 1;
  background: linear-gradient(100deg, #0ea5e0 0%, #12b7f5 35%, #5b8cff 70%, #a855f7 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.reg-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--lx-login-muted);
  animation: rise-in 0.5s ease 0.05s both;
}

.reg-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 9px;
  animation: rise-in 0.55s ease 0.1s both;
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
  color: #9aa3b2;
}

.lx-field :deep(.n-input-wrapper) {
  background: rgba(255, 255, 255, 0.88);
  border-radius: 12px;
  box-shadow:
    0 1px 2px rgba(26, 35, 50, 0.04),
    inset 0 0 0 1px rgba(255, 255, 255, 0.9);
  padding-left: 12px;
  padding-right: 12px;
  min-height: 40px;
  transition: box-shadow 0.18s ease, background 0.18s ease;
}

.lx-field :deep(.n-input--focus .n-input-wrapper) {
  background: #fff;
  box-shadow:
    0 0 0 3px rgba(18, 183, 245, 0.16),
    inset 0 0 0 1px rgba(18, 183, 245, 0.45);
}

.lx-field :deep(.n-input__input-el) {
  font-size: 13px;
  height: 40px;
}

.lx-field :deep(.n-input__prefix) {
  margin-right: 6px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  width: 102px;
  height: 40px;
  border-radius: 12px;
  cursor: pointer;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(18, 183, 245, 0.12);
  flex-shrink: 0;
  object-fit: contain;
  transition: transform 0.15s ease;
}

.captcha-img:hover {
  transform: scale(1.02);
}

.captcha-img--placeholder {
  background: linear-gradient(135deg, #e8eef5 0%, #f5f7fa 100%);
  border: 1px dashed #c5d0dc;
  box-shadow: none;
}

.captcha-input {
  flex: 1;
  min-width: 0;
}

.captcha-refresh {
  color: var(--lx-login-muted) !important;
}

.lx-login-btn {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #39c2f6 0%, #12b7f5 48%, #0aa6e0 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow:
    0 8px 20px rgba(18, 183, 245, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.28);
  transition: transform 0.15s ease, box-shadow 0.15s ease, filter 0.15s ease, opacity 0.15s ease;
}

.lx-login-btn:hover:not(:disabled) {
  filter: brightness(1.04);
  transform: translateY(-1px);
  box-shadow:
    0 10px 24px rgba(18, 183, 245, 0.38),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.lx-login-btn:active:not(:disabled) {
  transform: translateY(0);
  filter: brightness(0.98);
}

.lx-login-btn:disabled {
  opacity: 0.9;
  cursor: default;
  transform: none;
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
  padding-top: 16px;
  display: flex;
  justify-content: center;
  animation: rise-in 0.55s ease 0.16s both;
}

.footer-link {
  color: var(--lx-login-accent-deep);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
}

.footer-link:hover {
  color: #088fc4;
  text-decoration: none;
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .brand-title,
  .reg-desc,
  .reg-form,
  .footer {
    animation: none !important;
  }
}
</style>
