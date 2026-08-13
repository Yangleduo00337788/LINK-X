<!-- 作者：yangleduo -->
<script setup lang="ts">
import { NSwitch } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useAppStore } from '../../stores/app'
import { useI18n } from '../../i18n'
import { LxGroupCard } from '../ui'

const appSettingsStore = useAppSettingsStore()
const appStore = useAppStore()
const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline,
  privacySendReadReceipt,
  retainChatCache
} = storeToRefs(appSettingsStore)
const { t } = useI18n()

function onRetainChatCacheChange(value: boolean) {
  retainChatCache.value = value
  if (!value) {
    appStore.clearLocalChatCache()
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
</style>
