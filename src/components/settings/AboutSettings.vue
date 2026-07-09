<script setup lang="ts">
import { NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useAppSettingsStore } from '../../stores/appSettings'

const message = useMessage()
const appStore = useAppStore()
const appSettingsStore = useAppSettingsStore()

const { theme } = storeToRefs(appStore)
const { chatBackground } = storeToRefs(appSettingsStore)

function checkUpdate() {
  message.success('当前已是最新版本 1.0.0')
}

function exportLogs() {
  console.info('[LinkX] 系统日志导出', { theme: theme.value, chatBackground: chatBackground.value })
  message.success('日志已输出到开发者控制台')
}
</script>

<template>
  <div class="settings-scroll about-scroll">
    <section class="about-card">
      <div class="about-glow" />
      <img src="../../assets/logo-linkx.svg" alt="LinkX" class="about-logo" />
      <h3 class="about-name">LinkX</h3>
      <p class="about-ver">Version 1.0.0 · Beta</p>
      <p class="about-desc">极简、高效、安全的企业级即时通讯工具</p>
      <div class="about-actions">
        <n-button type="primary" @click="checkUpdate">检查更新</n-button>
        <n-button secondary @click="exportLogs">系统日志</n-button>
      </div>
      <p class="about-copy">© 2026 LinkX Team</p>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.about-scroll {
  justify-content: center;
}

.about-card {
  position: relative;
  text-align: center;
  padding: 36px 24px 28px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
}

.about-glow {
  position: absolute;
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 200px;
  height: 120px;
  background: radial-gradient(ellipse, var(--lx-accent-soft) 0%, transparent 70%);
  pointer-events: none;
}

.about-logo {
  width: 72px;
  height: 72px;
  position: relative;
  z-index: 1;
}

.about-name {
  margin: 16px 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.about-ver {
  margin: 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.about-desc {
  margin: 12px 0 24px;
  font-size: 14px;
  color: var(--lx-text-secondary);
  line-height: 1.5;
}

.about-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
}

.about-copy {
  margin: 0;
  font-size: 11px;
  color: var(--lx-text-muted);
}
</style>
