<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NInput, NButton, NIcon, NCheckbox, useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'

const message = useMessage()
const appStore = useAppStore()
const { savedLogin } = storeToRefs(appStore)
const { login } = appStore

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const autoLogin = ref(false)
const isLoading = ref(false)

onMounted(() => {
  username.value = savedLogin.value.username || 'linkx_888888'
  rememberMe.value = savedLogin.value.rememberMe
  autoLogin.value = savedLogin.value.autoLogin
})

function handleLogin() {
  const user = username.value.trim()
  const pass = password.value.trim()

  if (!user) {
    message.warning('请输入 LinkX ID 或手机号')
    return
  }
  if (!pass) {
    message.warning('请输入密码')
    return
  }

  isLoading.value = true
  setTimeout(() => {
    isLoading.value = false
    login(user, pass, {
      rememberMe: rememberMe.value,
      autoLogin: autoLogin.value
    })
    message.success(`欢迎回来，${user}`)
  }, 500)
}
</script>

<template>
  <div class="login-container">
    <div class="drag-bar" style="-webkit-app-region: drag"></div>

    <div class="login-box">
      <div class="logo-area">
        <img src="../assets/logo-linkx.svg" alt="LinkX" class="logo-img" />
        <h1 class="app-title">LinkX</h1>
        <p class="app-subtitle">企业级即时通讯与协同平台</p>
      </div>

      <div class="form-area">
        <n-input
          v-model:value="username"
          size="large"
          placeholder="请输入 LinkX ID 或手机号"
          class="login-input"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="PersonOutline" />
          </template>
        </n-input>

        <n-input
          v-model:value="password"
          type="password"
          show-password-on="click"
          size="large"
          placeholder="请输入密码"
          class="login-input"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" />
          </template>
        </n-input>

        <div class="form-options">
          <n-checkbox v-model:checked="rememberMe">记住账号</n-checkbox>
          <n-checkbox v-model:checked="autoLogin">自动登录</n-checkbox>
        </div>

        <n-button
          type="primary"
          size="large"
          block
          :loading="isLoading"
          class="login-btn"
          @click="handleLogin"
        >
          登 录
        </n-button>
      </div>

      <div class="login-footer">
        <a href="#" class="footer-link" @click.prevent="message.info('注册功能开发中')">注册账号</a>
        <span class="divider">|</span>
        <a href="#" class="footer-link" @click.prevent="message.info('找回密码功能开发中')">找回密码</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lx-bg-panel, var(--lx-bg-panel-deep));
  position: relative;
  overflow: hidden;
}

.drag-bar {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 40px;
  z-index: 10;
}

.login-box {
  width: 320px;
  height: 460px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-shadow-modal);
  padding: 36px 24px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 2;
  box-sizing: border-box;
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
}

.logo-img {
  width: 64px;
  height: 64px;
  margin-bottom: 12px;
}

.app-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin: 0 0 6px;
  letter-spacing: 1px;
}

.app-subtitle {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin: 0;
}

.form-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.login-input {
  --n-border-radius: var(--lx-radius) !important;
  --n-height: 42px !important;
  background: #f7f7f7;
}

:deep(.n-input) {
  background-color: #f7f7f7 !important;
}
:deep(.n-input:focus-within) {
  background-color: var(--lx-bg-card) !important;
}

.form-options {
  display: flex;
  justify-content: space-between;
  padding: 0 4px;
  margin-top: -4px;
  margin-bottom: 8px;
}

.login-btn {
  height: 42px;
  border-radius: var(--lx-radius);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 4px;
  background: var(--lx-accent);
  border-color: var(--lx-accent);
  margin-top: 4px;
}

.login-btn:hover {
  background: var(--lx-accent-hover);
  border-color: var(--lx-accent-hover);
}

.login-footer {
  margin-top: auto;
  padding-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.footer-link {
  color: var(--lx-text-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.footer-link:hover {
  color: var(--lx-accent);
}

.divider {
  color: #ddd;
}
</style>
