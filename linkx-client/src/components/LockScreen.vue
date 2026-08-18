<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { NInput, useMessage } from 'naive-ui'
import WindowCaptionButtons from './WindowCaptionButtons.vue'
import BrandMarkIcon from './BrandMarkIcon.vue'
import { LxButton } from './ui'
import { useAppStore } from '../stores/app'
import { useSettingsStore } from '../stores/settings'
import { useI18n } from '../i18n'

const message = useMessage()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const { unlock, verifyLockPin, hasLockPin } = appStore
const { t } = useI18n()

const showUnlockForm = ref(false)
const pin = ref('')
const errorMsg = ref('')
const unlocking = ref(false)
const pinInputRef = ref<{ focus: () => void } | null>(null)

const isElectron = computed(() => !!window.electronAPI?.isElectron)
const pinConfigured = computed(() => hasLockPin())

async function openUnlockForm() {
  if (!pinConfigured.value) {
    message.warning(t('lock.setPinFirst'))
    return
  }
  pin.value = ''
  errorMsg.value = ''
  showUnlockForm.value = true
  await nextTick()
  pinInputRef.value?.focus()
}

function closeUnlockForm() {
  showUnlockForm.value = false
  pin.value = ''
  errorMsg.value = ''
}

async function handleUnlock() {
  if (!pinConfigured.value) {
    message.warning(t('lock.setPinFirst'))
    return
  }
  if (!pin.value.trim()) {
    errorMsg.value = t('lock.pinPh')
    return
  }

  unlocking.value = true
  try {
    const ok = await verifyLockPin(pin.value)
    if (ok) {
      closeUnlockForm()
      void unlock()
    } else {
      errorMsg.value = t('lock.pinWrong')
      message.error(t('lock.pinIncorrect'))
    }
  } finally {
    unlocking.value = false
  }
}

function goSettings() {
  closeUnlockForm()
  void unlock()
  settingsStore.openSettings('privacy')
}
</script>

<template>
  <div class="lock-screen" role="dialog" aria-modal="true" :aria-label="t('lock.lockedTitle')">
    <header v-if="isElectron" class="lock-caption">
      <WindowCaptionButtons show-pin />
    </header>

    <div class="lock-body">
      <div class="lock-logo" aria-hidden="true">
        <BrandMarkIcon :size="72" />
      </div>

      <h1 class="lock-title">{{ t('lock.lockedTitle') }}</h1>
      <p class="lock-hint">
        {{ pinConfigured ? t('lock.unlockHint') : t('lock.noPinHint') }}
      </p>

      <div v-if="showUnlockForm" class="unlock-form">
        <n-input
          ref="pinInputRef"
          v-model:value="pin"
          type="password"
          class="unlock-input"
          :placeholder="t('lock.pinPh')"
          maxlength="6"
          show-password-on="click"
          @keyup.enter="handleUnlock"
        />
        <p v-if="errorMsg" class="unlock-error">{{ errorMsg }}</p>
        <div class="unlock-actions">
          <LxButton variant="ghost" @click="closeUnlockForm">{{ t('common.cancel') }}</LxButton>
          <LxButton variant="primary-comfortable" :disabled="unlocking" @click="handleUnlock">
            {{ t('lock.unlockConfirm') }}
          </LxButton>
        </div>
      </div>

      <template v-else>
        <LxButton
          v-if="pinConfigured"
          variant="primary-comfortable"
          class="unlock-main-btn"
          @click="openUnlockForm"
        >
          {{ t('lock.unlockButton') }}
        </LxButton>
        <LxButton v-else variant="primary-comfortable" class="unlock-main-btn" @click="goSettings">
          {{ t('lock.goSettings') }}
        </LxButton>
      </template>
    </div>
  </div>
</template>

<style scoped>
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-lock);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--lx-bg-window);
  color: var(--lx-text-body);
  -webkit-app-region: no-drag;
}

.lock-caption {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  -webkit-app-region: no-drag;
}

.lock-body {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-6xl) var(--lx-space-4xl);
  box-sizing: border-box;
}

.lock-logo {
  margin-bottom: var(--lx-space-4xl);
}

.lock-title {
  margin: 0;
  font-size: var(--lx-font-4xl);
  font-weight: 600;
  line-height: var(--lx-leading-tight);
  color: var(--lx-text-body);
  text-align: center;
}

.lock-hint {
  margin: var(--lx-space-xl) 0 var(--lx-space-5xl);
  max-width: 360px;
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
  text-align: center;
}

.unlock-main-btn {
  min-width: 200px;
}

.unlock-form {
  width: min(320px, 100%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--lx-space-lg);
}

.unlock-input {
  width: 100%;
}

.unlock-error {
  margin: 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-danger);
  text-align: center;
}

.unlock-actions {
  display: flex;
  justify-content: center;
  gap: var(--lx-space);
}
</style>
