<script setup lang="ts">
// Naive UI 开关、按钮与图标
import { NSwitch, NButton, NIcon } from 'naive-ui'
// Ionicons5 实验图标
import { FlaskOutline } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'

// 应用 Store 实例
const appStore = useAppStore()
// 是否模拟断网
const { isOffline } = storeToRefs(appStore)
// 设置断网状态、模拟新消息的方法
const { setOffline, simulateIncomingMessage } = appStore
</script>

<template>
  <!-- 原型演示工具设置页 -->
  <div class="settings-scroll">
    <!-- 边缘状态触发分组 -->
    <section class="group-card group-card--warn">
      <div class="group-head">
        <n-icon :component="FlaskOutline" :size="18" class="group-ico" />
        <span>边缘状态触发</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">模拟网络断开</span>
          <span class="setting-desc">开启后将在聊天列表顶部显示断网提示</span>
        </div>
        <n-switch :value="isOffline" size="small" @update:value="setOffline" />
      </div>
    </section>

    <!-- 系统通知模拟分组 -->
    <section class="group-card">
      <div class="group-head">
        <span>系统通知</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">模拟接收新消息</span>
          <span class="setting-desc">触发桌面通知与未读角标（Electron）</span>
        </div>
        <n-button size="small" type="primary" @click="simulateIncomingMessage">
          触发
        </n-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.group-card--warn {
  border-color: rgba(250, 173, 20, 0.25);
}
</style>
