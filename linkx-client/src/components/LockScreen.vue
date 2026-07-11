<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, NAvatar, NIcon, useMessage } from 'naive-ui'
import { LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'

const message = useMessage()
const appStore = useAppStore()
const { userProfile } = storeToRefs(appStore)
const { unlock, verifyLockPin, hasLockPin } = appStore

const pin = ref('')
const errorMsg = ref('')

async function handleUnlock() {
  if (!hasLockPin()) {
    message.warning('请先在设置中设置锁屏 PIN')
    return
  }

  const ok = await verifyLockPin(pin.value)
  if (ok) {
    unlock()
    pin.value = ''
    errorMsg.value = ''
  } else {
    errorMsg.value = 'PIN 错误，请重试'
    message.error('锁屏 PIN 不正确')
  }
}
</script>

<template>
  <div class="lock-screen" role="dialog" aria-modal="true" aria-label="LinkX 已锁定">
    <div class="lock-content">
      <div class="lock-icon-wrapper">
        <n-icon :component="LockClosedOutline" :size="32" class="lock-icon" />
      </div>

      <n-avatar :size="80" class="avatar">
        {{ userProfile.nickname?.charAt(0) || 'U' }}
      </n-avatar>

      <h2 class="nickname">{{ userProfile.nickname }}</h2>
      <p class="status">LinkX 已锁定</p>

      <div class="unlock-form">
        <n-input
          v-model:value="pin"
          type="password"
          placeholder="输入 4-6 位锁屏 PIN"
          class="password-input"
          maxlength="6"
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
        <p v-if="!hasLockPin()" class="hint">未设置锁屏 PIN 时，请前往设置进行配置</p>
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
  background: var(--lx-accent, #12b7f5);
  color: #fff;
  font-size: 28px;
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

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--lx-text-secondary, #666666);
  text-align: center;
}
</style>
