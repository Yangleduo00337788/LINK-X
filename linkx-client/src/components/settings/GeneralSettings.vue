<script setup lang="ts">
// Naive UI 开关与图标
import { NSwitch, NIcon } from 'naive-ui'
// Ionicons5 桌面与通知图标
import { DesktopOutline, NotificationsOutline } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用设置 Store
import { useAppSettingsStore } from '../../stores/appSettings'

// 应用设置 Store 实例
const appSettingsStore = useAppSettingsStore()
// 解构通用与通知相关设置项
const {
  autoStart,
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound
} = storeToRefs(appSettingsStore)

// 同步开机自启设置到 Electron
function applyAutoStart() {
  window.electronAPI?.setAutoStart?.(autoStart.value)
}
</script>

<template>
  <!-- 通用设置页 -->
  <div class="settings-scroll">
    <!-- 系统设置分组 -->
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

    <!-- 消息与通知设置分组 -->
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
