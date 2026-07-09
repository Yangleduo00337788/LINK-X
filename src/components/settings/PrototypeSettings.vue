<script setup lang="ts">
import { NSwitch, NButton, NIcon } from 'naive-ui'
import { FlaskOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'

const appStore = useAppStore()
const { isOffline } = storeToRefs(appStore)
const { setOffline, simulateIncomingMessage } = appStore
</script>

<template>
  <div class="settings-scroll">
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
