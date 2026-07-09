<script setup lang="ts">
/**
 * 锁屏组件。
 * <p>
 * 用户手动锁定或超时锁定后全屏覆盖显示，
 * 输入正确密码后解锁恢复主界面。
 * </p>
 */
// Vue 响应式引用
import { ref } from 'vue'
// Naive UI 输入框、按钮、头像、图标及消息提示
import { NInput, NButton, NAvatar, NIcon, useMessage } from 'naive-ui'
// Ionicons5 锁与箭头图标
import { LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'

// 获取 Naive UI 消息提示实例
const message = useMessage()
// 获取应用 Store 实例
const appStore = useAppStore()
// 解构用户资料响应式引用
const { userProfile } = storeToRefs(appStore)
// 解构解锁与密码校验方法
const { unlock, verifyLockPassword } = appStore

// 解锁密码输入框的值
const password = ref('')
// 密码错误时的提示文案
const errorMsg = ref('')

// 处理解锁：校验密码通过后解除锁屏
function handleUnlock() {
  if (verifyLockPassword(password.value)) {
    unlock() // 校验通过，解除锁屏状态
    password.value = '' // 清空密码输入
    errorMsg.value = '' // 清空错误提示
  } else {
    errorMsg.value = '密码错误，请重试' // 显示内联错误提示
    message.error('解锁密码不正确') // 弹出错误消息
  }
}
</script>

<template>
  <!-- 全屏锁屏遮罩 -->
  <div class="lock-screen" role="dialog" aria-modal="true" aria-label="LinkX 已锁定">
    <div class="lock-content">
      <!-- 锁图标 -->
      <div class="lock-icon-wrapper">
        <n-icon :component="LockClosedOutline" :size="32" class="lock-icon" />
      </div>

      <!-- 用户头像 -->
      <n-avatar :size="80" src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user" class="avatar" />

      <!-- 用户昵称与锁定状态 -->
      <h2 class="nickname">{{ userProfile.nickname }}</h2>
      <p class="status">LinkX 已锁定</p>

      <!-- 密码输入与解锁 -->
      <div class="unlock-form">
        <n-input
          v-model:value="password"
          type="password"
          placeholder="输入登录密码解锁（未设置密码时任意 4 位以上）"
          class="password-input"
          autofocus
          @keyup.enter="handleUnlock"
        >
          <template #suffix>
            <n-button text @click="handleUnlock">
              <n-icon :component="ArrowForwardOutline" />
            </n-button>
          </template>
        </n-input>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: 30000;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(18, 18, 22, 0.92);
  -webkit-app-region: no-drag;
}

.lock-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #ffffff;
  padding: 40px 60px;
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.35);
  min-width: 320px;
}

[data-theme='dark'] .lock-content {
  background: #2c2c2c;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.55);
}

.lock-icon-wrapper {
  margin-bottom: 24px;
  color: var(--lx-accent, #12b7f5);
}

.avatar {
  margin-bottom: 16px;
  border: 2px solid #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

[data-theme='dark'] .avatar {
  border-color: #2c2c2c;
}

.nickname {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 500;
  color: var(--lx-text-body, #333333);
}

.status {
  margin: 0 0 32px;
  font-size: 14px;
  color: var(--lx-text-secondary, #666666);
}

.unlock-form {
  width: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.password-input {
  border-radius: var(--lx-radius, 8px);
}

.error-msg {
  margin-top: 12px;
  color: var(--lx-danger, #ff4d4f);
  font-size: 12px;
  text-align: center;
}
</style>
