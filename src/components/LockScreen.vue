<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, NAvatar, NIcon, useMessage } from 'naive-ui'
import { LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'

const message = useMessage()
const appStore = useAppStore()
const { userProfile } = storeToRefs(appStore)
const { unlock, verifyLockPassword } = appStore

const password = ref('')
const errorMsg = ref('')

function handleUnlock() {
  if (verifyLockPassword(password.value)) {
    unlock()
    password.value = ''
    errorMsg.value = ''
  } else {
    errorMsg.value = '密码错误，请重试'
    message.error('解锁密码不正确')
  }
}
</script>

<template>
  <div class="lock-screen">
    <div class="lock-glass"></div>
    <div class="lock-content">
      <div class="lock-icon-wrapper">
        <n-icon :component="LockClosedOutline" :size="32" class="lock-icon" />
      </div>

      <n-avatar :size="80" src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user" round class="avatar" />

      <h2 class="nickname">{{ userProfile.nickname }}</h2>
      <p class="status">LinkX 已锁定</p>

      <div class="unlock-form">
        <n-input
          v-model:value="password"
          type="password"
          placeholder="输入登录密码解锁"
          class="password-input"
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
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.lock-glass {
  position: absolute;
  top: -20px;
  left: -20px;
  right: -20px;
  bottom: -20px;
  background: var(--lx-bg-overlay);
  backdrop-filter: blur(20px);
  z-index: 1;
}

.lock-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--lx-bg-card);
  padding: 40px 60px;
  border-radius: 16px;
  box-shadow: var(--lx-shadow-modal);
}

.lock-icon-wrapper {
  margin-bottom: 24px;
  color: var(--lx-text-nav);
}

.avatar {
  margin-bottom: 16px;
  border: 2px solid var(--lx-bg-card);
  box-shadow: var(--lx-shadow-soft);
}

.nickname {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.status {
  margin: 0 0 32px;
  font-size: 14px;
  color: var(--lx-text-secondary);
}

.unlock-form {
  width: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.password-input {
  border-radius: var(--lx-radius);
}

.error-msg {
  margin-top: 12px;
  color: var(--lx-danger);
  font-size: 12px;
  text-align: center;
}
</style>
