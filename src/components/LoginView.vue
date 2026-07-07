<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, NIcon, NCheckbox } from 'naive-ui'
import { LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import { useAppState } from '../composables/useAppState'

const { login } = useAppState()

const username = ref('linkx_888888')
const password = ref('123456')
const rememberMe = ref(true)
const autoLogin = ref(false)
const isLoading = ref(false)

function handleLogin() {
  if (!username.value || !password.value) return
  isLoading.value = true
  // 模拟网络请求延迟
  setTimeout(() => {
    isLoading.value = false
    login()
  }, 800)
}
</script>

<template>
  <div class="login-container">
    <!-- 顶部控制栏区域占位，用于拖拽 -->
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
          <n-checkbox v-model:checked="rememberMe">记住密码</n-checkbox>
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
        <a href="#" class="footer-link">注册账号</a>
        <span class="divider">|</span>
        <a href="#" class="footer-link">找回密码</a>
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
  background: var(--lx-bg-panel, #e8e8e8);
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
  background: #ffffff;
  border-radius: var(--lx-radius);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
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
  color: #333;
  margin: 0 0 6px;
  letter-spacing: 1px;
}

.app-subtitle {
  font-size: 12px;
  color: #999;
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
  background-color: #fff !important;
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
  background: #12b7f5;
  border-color: #12b7f5;
  margin-top: 4px;
}

.login-btn:hover {
  background: #39c2f6;
  border-color: #39c2f6;
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
  color: #666;
  text-decoration: none;
  transition: color 0.2s;
}

.footer-link:hover {
  color: #12b7f5;
}

.divider {
  color: #ddd;
}
</style>
