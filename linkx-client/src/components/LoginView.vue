<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { NInput, NButton, NIcon, NCheckbox, NModal, useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline, RefreshOutline } from '@vicons/ionicons5'
import WindowControls from './WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import * as authApi from '../api/auth'
import { validateUsername, validatePassword, validateNickname } from '../utils/validation'

const message = useMessage()
const appStore = useAppStore()
const { savedLogin, isLoading } = storeToRefs(appStore)
const { login } = appStore

const isElectron = !!window.electronAPI?.isElectron

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const autoLogin = ref(false)

const captchaId = ref('')
const captchaImage = ref('')
const captchaCode = ref('')

const showRegister = ref(false)
const regUser = ref('')
const regPass = ref('')
const regNickname = ref('')
const regCaptchaCode = ref('')
const regCaptchaId = ref('')
const regCaptchaImage = ref('')

const showForgot = ref(false)
const forgotUser = ref('')

const compact = computed(() => isElectron)

async function loadCaptcha(target: 'login' | 'register' = 'login') {
  try {
    const res = await authApi.fetchCaptcha()
    if (res.code === 200 && res.data) {
      if (target === 'login') {
        captchaId.value = res.data.captchaId
        captchaImage.value = res.data.imageBase64
        captchaCode.value = ''
      } else {
        regCaptchaId.value = res.data.captchaId
        regCaptchaImage.value = res.data.imageBase64
        regCaptchaCode.value = ''
      }
    }
  } catch {
    message.error('验证码加载失败')
  }
}

onMounted(() => {
  username.value = savedLogin.value.username || ''
  rememberMe.value = savedLogin.value.rememberMe ?? true
  autoLogin.value = savedLogin.value.autoLogin ?? false
  void loadCaptcha('login')
})

watch(rememberMe, val => {
  if (!val) autoLogin.value = false
})

watch(autoLogin, val => {
  if (val) rememberMe.value = true
})

async function handleLogin() {
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
  if (!captchaCode.value.trim()) {
    message.warning('请输入验证码')
    return
  }

  try {
    await login(user, pass, {
      rememberMe: rememberMe.value,
      autoLogin: autoLogin.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim()
    })
    message.success(`欢迎回来，${user}`)
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '登录失败，请检查账号密码')
    await loadCaptcha('login')
  }
}

async function openRegister() {
  showRegister.value = true
  regNickname.value = regUser.value.trim()
  await loadCaptcha('register')
}

async function handleRegister() {
  const user = regUser.value.trim()
  const pass = regPass.value
  const nickname = regNickname.value.trim() || user

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
  if (!regCaptchaCode.value.trim()) {
    message.warning('请输入验证码')
    return
  }

  try {
    const res = await authApi.register({
      username: user,
      password: pass,
      nickname,
      captchaId: regCaptchaId.value,
      captchaCode: regCaptchaCode.value.trim()
    })
    if (res.code === 200) {
      message.success('注册成功，请登录')
      username.value = user
      password.value = ''
      showRegister.value = false
      await loadCaptcha('login')
    } else {
      message.error(res.message || '注册失败，请检查信息后重试')
      await loadCaptcha('register')
    }
  } catch (error: unknown) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '注册请求失败')
    await loadCaptcha('register')
  }
}

function handleForgot() {
  if (!forgotUser.value.trim()) {
    message.warning('请输入 LinkX ID')
    return
  }
  message.info('找回密码功能即将上线')
  showForgot.value = false
}
</script>

<template>
  <div class="login-page" :class="{ 'login-page--compact': compact }">
    <div v-if="isElectron" class="login-win-bar">
      <div class="drag-area" />
      <WindowControls />
    </div>

    <div class="login-card">
      <div class="brand">
        <img src="../assets/logo-linkx.svg" alt="" class="brand-logo" width="56" height="56" />
        <h1 class="brand-name">LinkX</h1>
        <p class="brand-desc">企业级即时通讯与协同平台</p>
      </div>

      <div class="form">
        <n-input
          v-model:value="username"
          size="large"
          placeholder="用户名（4-32位字母数字下划线）"
          class="field"
          :bordered="true"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="PersonOutline" :size="18" class="field-ico" />
          </template>
        </n-input>

        <n-input
          v-model:value="password"
          type="password"
          show-password-on="click"
          size="large"
          placeholder="密码（8-64位）"
          class="field"
          :bordered="true"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" :size="18" class="field-ico" />
          </template>
        </n-input>

        <div class="captcha-row">
          <img
            v-if="captchaImage"
            :src="captchaImage"
            alt="验证码"
            class="captcha-img"
            title="点击刷新"
            @click="loadCaptcha('login')"
          />
          <n-input
            v-model:value="captchaCode"
            size="large"
            placeholder="验证码"
            class="captcha-input"
            maxlength="6"
            @keyup.enter="handleLogin"
          />
          <n-button quaternary circle @click="loadCaptcha('login')">
            <template #icon>
              <n-icon :component="RefreshOutline" />
            </template>
          </n-button>
        </div>

        <div class="options">
          <n-checkbox v-model:checked="rememberMe" size="small">记住账号</n-checkbox>
          <n-checkbox v-model:checked="autoLogin" size="small">自动登录</n-checkbox>
        </div>

        <n-button
          type="primary"
          size="large"
          block
          :loading="isLoading"
          class="submit-btn"
          @click="handleLogin"
        >
          登 录
        </n-button>
      </div>

      <div class="footer">
        <a href="#" class="footer-link" @click.prevent="openRegister">注册账号</a>
        <span class="footer-sep">|</span>
        <a href="#" class="footer-link" @click.prevent="showForgot = true">找回密码</a>
      </div>
    </div>

    <n-modal v-model:show="showRegister" preset="dialog" title="注册 LinkX 账号">
      <n-input v-model:value="regUser" placeholder="用户名" />
      <n-input
        v-model:value="regPass"
        type="password"
        placeholder="密码（8位以上，含字母和数字）"
        style="margin-top: 12px"
      />
      <n-input
        v-model:value="regNickname"
        placeholder="昵称"
        style="margin-top: 12px"
      />
      <div class="captcha-row" style="margin-top: 12px">
        <img
          v-if="regCaptchaImage"
          :src="regCaptchaImage"
          alt="验证码"
          class="captcha-img"
          @click="loadCaptcha('register')"
        />
        <n-input v-model:value="regCaptchaCode" placeholder="验证码" maxlength="6" />
        <n-button quaternary circle @click="loadCaptcha('register')">
          <template #icon>
            <n-icon :component="RefreshOutline" />
          </template>
        </n-button>
      </div>
      <template #action>
        <n-button @click="showRegister = false">取消</n-button>
        <n-button type="primary" @click="handleRegister">注册</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="showForgot" preset="dialog" title="找回密码">
      <n-input v-model:value="forgotUser" placeholder="LinkX ID" />
      <template #action>
        <n-button @click="showForgot = false">取消</n-button>
        <n-button type="primary" @click="handleForgot">发送重置链接</n-button>
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
  align-items: center;
  justify-content: center;
  background: #e8e8e8;
  box-sizing: border-box;
  padding: 16px;
}

.login-page--compact {
  background: #ffffff;
  padding: 0;
  flex-direction: column;
  align-items: stretch;
  min-height: 520px;
}

.login-win-bar {
  flex-shrink: 0;
  height: 32px;
  display: flex;
  align-items: stretch;
  -webkit-app-region: no-drag;
}

.drag-area {
  flex: 1;
  -webkit-app-region: drag;
}

.login-card {
  width: 360px;
  min-height: 520px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 40px 36px 28px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login-page--compact .login-card {
  width: 100%;
  min-height: 0;
  flex: 1;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 36px 40px 24px;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
}

.brand-logo {
  display: block;
  margin-bottom: 10px;
}

.brand-name {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: #1a1a1a;
  letter-spacing: 0.5px;
}

.brand-desc {
  margin: 0;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.form {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.captcha-input {
  flex: 1;
}

.field :deep(.n-input-wrapper) {
  background: #f5f5f5;
  border-radius: 6px;
}

.field :deep(.n-input__input-el) {
  font-size: 14px;
}

.field-ico {
  color: #b0b0b0;
}

.options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 2px;
  margin-top: 2px;
}

.options :deep(.n-checkbox__label) {
  font-size: 13px;
  color: #666;
}

.submit-btn {
  height: 40px;
  margin-top: 6px;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 6px;
  text-indent: 6px;
}

.footer {
  margin-top: auto;
  padding-top: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 12px;
}

.footer-link {
  color: #888;
  text-decoration: none;
}

.footer-link:hover {
  color: var(--lx-accent, #12b7f5);
}

.footer-sep {
  color: #ddd;
  user-select: none;
}
</style>
