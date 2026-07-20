<script setup lang="ts">
import { NSwitch } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useI18n } from '../../i18n'

const appSettingsStore = useAppSettingsStore()
const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline
} = storeToRefs(appSettingsStore)
const { t } = useI18n()
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
      <div class="group-head"><span>{{ t('privacy.title') }}</span></div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">{{ t('privacy.verifyFriend') }}</span>
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
        </div>
        <n-switch
          v-model:value="privacyShowOnline"
          size="small"
          @update:value="appSettingsStore.scheduleSave('privacyShowOnline')"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';
</style>
