<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NInput, NButton, NIcon, NCheckbox, NModal, useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import WindowControls from './WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'

const message = useMessage()
const appStore = useAppStore()
const { savedLogin } = storeToRefs(appStore)
const { login } = appStore

const isElectron = !!window.electronAPI?.isElectron

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const autoLogin = ref(false)
const isLoading = ref(false)
const showRegister = ref(false)
const showForgot = ref(false)
const regUser = ref('')
const regPass = ref('')
const forgotUser = ref('')

const compact = computed(() => isElectron)

onMounted(() => {
  username.value = savedLogin.value.username || 'linkx_888888'
  rememberMe.value = savedLogin.value.rememberMe ?? true
  autoLogin.value = savedLogin.value.autoLogin ?? false
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
  }, 400)
}

function handleRegister() {
  if (!regUser.value.trim() || !regPass.value.trim()) {
    message.warning('请填写完整注册信息')
    return
  }
  message.success(`账号「${regUser.value.trim()}」注册成功，请登录`)
  username.value = regUser.value.trim()
  showRegister.value = false
}

function handleForgot() {
  if (!forgotUser.value.trim()) {
    message.warning('请输入 LinkX ID 或手机号')
    return
  }
  message.success('重置链接已发送到绑定邮箱（本地模拟）')
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
          placeholder="linkx_888888"
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
          placeholder="请输入密码"
          class="field"
          :bordered="true"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <n-icon :component="LockClosedOutline" :size="18" class="field-ico" />
          </template>
        </n-input>

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
        <a href="#" class="footer-link" @click.prevent="showRegister = true">注册账号</a>
        <span class="footer-sep">|</span>
        <a href="#" class="footer-link" @click.prevent="showForgot = true">找回密码</a>
      </div>
    </div>

    <n-modal v-model:show="showRegister" preset="dialog" title="注册 LinkX 账号">
      <n-input v-model:value="regUser" placeholder="LinkX ID / 手机号" />
      <n-input
        v-model:value="regPass"
        type="password"
        placeholder="设置密码"
        style="margin-top: 12px"
      />
      <template #action>
        <n-button @click="showRegister = false">取消</n-button>
        <n-button type="primary" @click="handleRegister">注册</n-button>
      </template>
    </n-modal>

    <n-modal v-model:show="showForgot" preset="dialog" title="找回密码">
      <n-input v-model:value="forgotUser" placeholder="LinkX ID / 手机号" />
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
  min-height: 480px;
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
