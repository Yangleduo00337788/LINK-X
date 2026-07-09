<script setup lang="ts">
import { NSwitch, NIcon } from 'naive-ui'
import { DesktopOutline, NotificationsOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'

const appSettingsStore = useAppSettingsStore()
const {
  autoStart,
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound
} = storeToRefs(appSettingsStore)

function applyAutoStart() {
  window.electronAPI?.setAutoStart?.(autoStart.value)
}
</script>

<template>
  <div class="settings-scroll">
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="DesktopOutline" :size="18" class="group-ico" />
        <span>系统</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">开机自动启动</span>
          <span class="setting-desc">登录 Windows 后自动打开 LinkX</span>
        </div>
        <n-switch v-model:value="autoStart" size="small" @update:value="applyAutoStart" />
      </div>
    </section>

    <section class="group-card">
      <div class="group-head">
        <n-icon :component="NotificationsOutline" :size="18" class="group-ico" />
        <span>消息与通知</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">新消息声音提示</span>
        </div>
        <n-switch v-model:value="soundNotify" size="small" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">通知显示消息详情</span>
        </div>
        <n-switch v-model:value="messageDetail" size="small" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">群聊 @ 我</span>
        </div>
        <n-switch v-model:value="notifyAtMe" size="small" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">通知声音</span>
        </div>
        <n-switch v-model:value="notifySound" size="small" />
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';
</style>
