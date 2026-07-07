<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, NAvatar, NIcon } from 'naive-ui'
import { LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { useAppState } from '../composables/useAppState'

const { userProfile, unlock } = useAppState()

const password = ref('')
const errorMsg = ref('')

function handleUnlock() {
  if (password.value === '123456' || password.value === '') { // 简单模拟，任意密码或空密码都能解锁
    unlock()
    password.value = ''
    errorMsg.value = ''
  } else {
    errorMsg.value = '密码错误，请重试 (提示: 可以直接按回车解锁)'
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
      
      <n-avatar :size="80" src="https://07akioni.oss-cn-beijing.aliyuncs.com/07akioni.jpeg" round class="avatar" />
      
      <h2 class="nickname">{{ userProfile.nickname }}</h2>
      <p class="status">LinkX 已锁定</p>
      
      <div class="unlock-form">
        <n-input
          v-model:value="password"
          type="password"
          placeholder="输入密码解锁"
          class="password-input"
          @keyup.enter="handleUnlock"
        >
          <template #suffix>
            <n-button text @click="handleUnlock">
              <n-icon :component="ArrowForwardOutline" />
            </n-button>
          </template>
        </n-input>
        <div class="error-msg" v-if="errorMsg">{{ errorMsg }}</div>
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
  background: var(--lx-bg-window, #e8e8e8);
  filter: blur(20px);
  z-index: 1;
}

.lock-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.85);
  padding: 40px 60px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(20px);
}

.lock-icon-wrapper {
  margin-bottom: 24px;
  color: #555;
}

.avatar {
  margin-bottom: 16px;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.nickname {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 500;
  color: #333;
}

.status {
  margin: 0 0 32px;
  font-size: 14px;
  color: #666;
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
  color: #fa5151;
  font-size: 12px;
  text-align: center;
}
</style>