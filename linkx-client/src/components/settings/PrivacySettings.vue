<!-- 作者：yangleduo -->
<script setup lang="ts">
import { NSwitch } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useI18n } from '../../i18n'

const appSettingsStore = useAppSettingsStore()
const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline,
  privacySendReadReceipt
} = storeToRefs(appSettingsStore)
const { t } = useI18n()
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
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
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.privacy-note {
  margin: 0 16px 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--lx-text-muted);
}
</style>
