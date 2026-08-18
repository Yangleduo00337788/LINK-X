<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NInput, NSwitch, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'
import { LxGroupCard, LxButton } from '../ui'

const appSettingsStore = useAppSettingsStore()
const appStore = useAppStore()
const message = useMessage()
const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline,
  privacySendReadReceipt,
  retainChatCache
} = storeToRefs(appSettingsStore)
const { t } = useI18n()

const pinStorageReady = ref(false)
const pinConfigured = ref(appStore.hasLockPin())
const currentPin = ref('')
const newPin = ref('')
const confirmPin = ref('')
const savingPin = ref(false)

const canManageLockPin = computed(() => pinStorageReady.value)

onMounted(async () => {
  const api = window.electronAPI?.secureStorage
  if (!api) {
    pinStorageReady.value = false
    return
  }
  pinStorageReady.value = await api.isAvailable()
})

function onRetainChatCacheChange(value: boolean) {
  retainChatCache.value = value
  if (!value) {
    appStore.clearLocalChatCache()
  }
}

function resetPinForm() {
  currentPin.value = ''
  newPin.value = ''
  confirmPin.value = ''
}

async function saveLockPin() {
  if (!canManageLockPin.value || savingPin.value) return

  if (pinConfigured.value) {
    const ok = await appStore.verifyLockPin(currentPin.value)
    if (!ok) {
      message.error(t('privacy.lockPinCurrentWrong'))
      return
    }
  }

  if (newPin.value !== confirmPin.value) {
    message.error(t('privacy.lockPinMismatch'))
    return
  }

  savingPin.value = true
  try {
    await appStore.setLockPin(newPin.value)
    pinConfigured.value = true
    resetPinForm()
    message.success(t('privacy.lockPinSaved'))
  } catch (e) {
    const msg = e instanceof Error ? e.message : t('errors.pinStorageUnavailable')
    message.error(msg)
  } finally {
    savingPin.value = false
  }
}

async function clearLockPin() {
  if (!canManageLockPin.value || savingPin.value || !pinConfigured.value) return

  const ok = await appStore.verifyLockPin(currentPin.value)
  if (!ok) {
    message.error(t('privacy.lockPinCurrentWrong'))
    return
  }

  savingPin.value = true
  try {
    await appStore.clearLockPin()
    pinConfigured.value = false
    resetPinForm()
    message.success(t('privacy.lockPinCleared'))
  } catch (e) {
    const msg = e instanceof Error ? e.message : t('errors.pinStorageUnavailable')
    message.error(msg)
  } finally {
    savingPin.value = false
  }
}
</script>

<template>
  <div class="settings-scroll">
    <LxGroupCard tag="section" variant="settings">
      <div class="group-head"><span>{{ t('privacy.title') }}</span></div>
      <p class="privacy-note">{{ t('privacy.note') }}</p>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.verifyFriend') }}</span>
          <span class="setting-desc">{{ t('privacy.verifyFriendDesc') }}</span>
        </div>
        <n-switch
          v-model:value="privacyVerifyFriend"
          size="small"
          @update:value="appSettingsStore.scheduleSave('privacyVerifyFriend')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.allowStranger') }}</span>
          <span class="setting-desc">{{ t('privacy.allowStrangerDesc') }}</span>
        </div>
        <n-switch
          v-model:value="privacyAllowStranger"
          size="small"
          @update:value="appSettingsStore.scheduleSave('privacyAllowStranger')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.showOnline') }}</span>
          <span class="setting-desc">{{ t('privacy.showOnlineDesc') }}</span>
        </div>
        <n-switch
          v-model:value="privacyShowOnline"
          size="small"
          @update:value="appSettingsStore.scheduleSave('privacyShowOnline')"
        />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.sendReadReceipt') }}</span>
          <span class="setting-desc">{{ t('privacy.sendReadReceiptDesc') }}</span>
        </div>
        <n-switch
          v-model:value="privacySendReadReceipt"
          size="small"
          @update:value="appSettingsStore.scheduleSave('privacySendReadReceipt')"
        />
      </div>
    </LxGroupCard>

    <LxGroupCard tag="section" variant="settings" class="local-privacy-card">
      <div class="group-head"><span>{{ t('privacy.localTitle') }}</span></div>
      <p class="privacy-note">{{ t('privacy.localNote') }}</p>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.retainChatCache') }}</span>
          <span class="setting-desc">{{ t('privacy.retainChatCacheDesc') }}</span>
        </div>
        <n-switch
          v-model:value="retainChatCache"
          size="small"
          @update:value="onRetainChatCacheChange"
        />
      </div>

      <div class="lock-pin-section">
        <div class="setting-row lock-pin-head">
          <div class="setting-text">
            <span class="setting-name">{{ t('privacy.lockPin') }}</span>
            <span class="setting-desc">{{ t('privacy.lockPinDesc') }}</span>
          </div>
          <span class="lock-pin-status" :class="{ on: pinConfigured }">
            {{ pinConfigured ? t('privacy.lockPinConfigured') : t('privacy.lockPinNotConfigured') }}
          </span>
        </div>

        <template v-if="canManageLockPin">
          <div v-if="pinConfigured" class="pin-field">
            <label class="pin-label">{{ t('privacy.lockPinCurrent') }}</label>
            <n-input
              v-model:value="currentPin"
              type="password"
              maxlength="6"
              show-password-on="click"
              :placeholder="t('lock.pinPh')"
            />
          </div>
          <div class="pin-field">
            <label class="pin-label">{{ t('privacy.lockPinNew') }}</label>
            <n-input
              v-model:value="newPin"
              type="password"
              maxlength="6"
              show-password-on="click"
              :placeholder="t('lock.pinPh')"
            />
          </div>
          <div class="pin-field">
            <label class="pin-label">{{ t('privacy.lockPinConfirm') }}</label>
            <n-input
              v-model:value="confirmPin"
              type="password"
              maxlength="6"
              show-password-on="click"
              :placeholder="t('lock.pinPh')"
            />
          </div>
          <div class="pin-actions">
            <LxButton
              v-if="pinConfigured"
              variant="sm"
              :disabled="savingPin || !currentPin"
              @click="clearLockPin"
            >
              {{ t('privacy.lockPinClear') }}
            </LxButton>
            <LxButton
              variant="sm-primary"
              :disabled="savingPin || !newPin || !confirmPin || (pinConfigured && !currentPin)"
              @click="saveLockPin"
            >
              {{ t('privacy.lockPinSave') }}
            </LxButton>
          </div>
        </template>
        <p v-else class="privacy-note lock-pin-tip">{{ t('privacy.lockPinDesktopOnly') }}</p>
      </div>
    </LxGroupCard>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.privacy-note {
  margin: 0 var(--lx-space-2xl) var(--lx-space);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-muted);
}

.lock-pin-section {
  border-top: 1px solid var(--lx-border-light);
}

.lock-pin-head {
  border-top: none;
}

.lock-pin-status {
  flex-shrink: 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.lock-pin-status.on {
  color: var(--lx-success);
}

.lock-pin-tip {
  padding-top: 0;
}

.pin-field {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xs);
  padding: 0 var(--lx-space-2xl) var(--lx-space-lg);
}

.pin-label {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}

.pin-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lx-space);
  padding: 0 var(--lx-space-2xl) var(--lx-space-2xl);
}
</style>
