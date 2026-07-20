<script setup lang="ts">
/**
 * 独立注册页（Electron 子窗口 / Web 路由）。
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NIcon, useMessage } from 'naive-ui'
import { RefreshOutline } from '@vicons/ionicons5'
import * as authApi from '../api/auth'
import { validateUsername, validatePassword, validateNickname } from '../utils/validation'
import { useI18n } from '../i18n'

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
const submitting = ref(false)

const compact = computed(() => isElectron)
const submitLabel = computed(() => (submitting.value ? t('register.submitting') : t('register.submit')))

async function loadCaptcha() {
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
  if (!regCaptchaCode.value.trim()) {
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
      captchaId: regCaptchaId.value,
      captchaCode: regCaptchaCode.value.trim()
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
  requestAnimationFrame(() => {
    void loadCaptcha()
  })
})
</script>

<template>
  <div class="register-page" :class="{ 'register-page--compact': compact }">
    <div class="reg-win-bar">
      <div class="reg-title">{{ t('register.title') }}</div>
      <div class="drag-area" />
      <button v-if="!isElectron" type="button" class="web-close" :title="t('common.back')" @click="closeOrBack">×</button>
    </div>

    <div class="reg-body">
      <div class="brand-title" aria-label="LinkX">LinkX</div>
      <p class="reg-desc">{{ t('register.subtitle') }}</p>

      <div class="reg-form">
        <n-input
          v-model:value="regUser"
          size="large"
          :placeholder="t('register.usernamePh')"
          class="lx-field"
          :bordered="false"
        />
        <n-input
          v-model:value="regPass"
          type="password"
          show-password-on="click"
          size="large"
          :placeholder="t('register.passwordPh')"
          class="lx-field"
          :bordered="false"
        />
        <n-input
          v-model:value="regNickname"
          size="large"
          :placeholder="t('register.nicknamePh')"
          class="lx-field"
          :bordered="false"
        />
        <n-input
          v-model:value="regEmail"
          size="large"
          :placeholder="t('register.emailPh')"
          class="lx-field"
          :bordered="false"
        />

        <div class="captcha-row">
          <img
            v-if="regCaptchaImage"
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
            maxlength="6"
            @keyup.enter="handleRegister"
          />
          <n-button quaternary circle @click="loadCaptcha">
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
  width: 100%;
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background:
    radial-gradient(ellipse 70% 45% at 50% 28%, rgba(255, 210, 230, 0.42) 0%, transparent 70%),
    linear-gradient(180deg, #dceefc 0%, #eef5fb 42%, #f7f9fc 100%);
  overflow: hidden;
}

.register-page--compact {
  min-height: 520px;
}

.reg-win-bar {
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

.reg-title {
  padding-left: 14px;
  font-size: 13px;
  font-weight: 500;
  color: #5c6370;
  user-select: none;
  -webkit-app-region: no-drag;
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
  padding: 8px 28px 18px;
  box-sizing: border-box;
  overflow: auto;
}

.brand-title {
  margin-top: 8px;
  margin-bottom: 6px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
  line-height: 1;
  background: linear-gradient(90deg, #12b7f5 0%, #5b8cff 45%, #c45dff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  user-select: none;
}

.reg-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: #8f959e;
}

.reg-form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
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
  min-height: 40px;
}

.lx-field :deep(.n-input__input-el) {
  font-size: 13px;
  height: 40px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  width: 96px;
  height: 40px;
  border-radius: 10px;
  cursor: pointer;
  border: none;
  background: #fff;
  flex-shrink: 0;
  object-fit: cover;
}

.captcha-input {
  flex: 1;
}

.lx-login-btn {
  width: 100%;
  height: 40px;
  margin-top: 8px;
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
}

.lx-login-btn:hover:not(:disabled) {
  background: #0aa6e0;
}

.lx-login-btn:disabled {
  opacity: 0.92;
  cursor: default;
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
}

.footer-link {
  color: #12b7f5;
  text-decoration: none;
  font-size: 13px;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
