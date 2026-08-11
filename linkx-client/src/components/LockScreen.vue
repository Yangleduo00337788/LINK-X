<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NAvatar, NIcon, useMessage } from 'naive-ui'
import { LockClosedOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import { LxIconButton } from './ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useI18n } from '../i18n'

const message = useMessage()
const appStore = useAppStore()
const { userProfile } = storeToRefs(appStore)
const { unlock, verifyLockPin, hasLockPin } = appStore
const { t } = useI18n()

const pin = ref('')
const errorMsg = ref('')

async function handleUnlock() {
  if (!hasLockPin()) {
    message.warning(t('lock.setPinFirst'))
    return
  }

  const ok = await verifyLockPin(pin.value)
  if (ok) {
    unlock()
    pin.value = ''
    errorMsg.value = ''
  } else {
    errorMsg.value = t('lock.pinWrong')
    message.error(t('lock.pinIncorrect'))
  }
}
</script>

<template>
  <div class="lock-screen" role="dialog" aria-modal="true" :aria-label="t('lock.title')">
    <div class="lock-content">
      <div class="lock-icon-wrapper">
        <n-icon :component="LockClosedOutline" :size="32" class="lock-icon" />
      </div>

      <n-avatar :size="80" class="avatar">
        {{ userProfile.nickname?.charAt(0) || 'U' }}
      </n-avatar>

      <h2 class="nickname">{{ userProfile.nickname }}</h2>
      <p class="status">{{ t('lock.title') }}</p>

      <div class="unlock-form">
        <n-input
          v-model:value="pin"
          type="password"
          :placeholder="t('lock.pinPh')"
          class="password-input"
          maxlength="6"
          autofocus
          @keyup.enter="handleUnlock"
        >
          <template #suffix>
            <LxIconButton :title="t('lock.title')" @click="handleUnlock">
              <n-icon :component="ArrowForwardOutline" />
            </LxIconButton>
          </template>
        </n-input>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <p v-if="!hasLockPin()" class="hint">{{ t('lock.noPinHint') }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-lock);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(18, 18, 22, 0.92);
  -webkit-app-region: no-drag;
}

.lock-content {
  position: relative;
  z-index: var(--lx-z-raised);
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--lx-bg-card);
  padding: var(--lx-space-section) var(--lx-space-block-lg);
  border-radius: var(--lx-radius-2xl);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.35);
  min-width: 320px;
}

[data-theme='dark'] .lock-content {
  background: var(--lx-bg-card);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.55);
}

.lock-icon-wrapper {
  margin-bottom: var(--lx-space-4xl);
  color: var(--lx-accent, var(--lx-accent));
}

.avatar {
  margin-bottom: var(--lx-space-2xl);
  border: 2px solid var(--lx-text-on-accent)fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  background: var(--lx-accent, var(--lx-accent));
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-6xl);
}

[data-theme='dark'] .avatar {
  border-color: var(--lx-lock-border);
}

.nickname {
  margin: 0 0 var(--lx-space);
  font-size: var(--lx-font-4xl);
  font-weight: 500;
  color: var(--lx-text-body);
}

.status {
  margin: 0 0 var(--lx-space-5xl);
  font-size: var(--lx-font);
  color: var(--lx-text-secondary);
}

.unlock-form {
  width: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.password-input {
  border-radius: var(--lx-radius);
}

.error-msg {
  margin-top: var(--lx-space-lg);
  color: var(--lx-danger, var(--lx-danger-hover));
  font-size: var(--lx-font-sm);
  text-align: center;
}

.hint {
  margin-top: var(--lx-space-lg);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
  text-align: center;
}
</style>
