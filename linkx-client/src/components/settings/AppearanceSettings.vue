<script setup lang="ts">
// Naive UI 图标与消息提示
import { NIcon, useMessage } from 'naive-ui'
// Ionicons5 主题与选中图标
import { MoonOutline, SunnyOutline, CheckmarkCircle } from '@vicons/ionicons5'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 应用设置 Store
import { useAppSettingsStore } from '../../stores/appSettings'
// 聊天背景 ID 类型
import type { ChatBackgroundId } from '../../types'

// 消息提示实例
const message = useMessage()
// 应用 Store 实例
const appStore = useAppStore()
// 应用设置 Store 实例
const appSettingsStore = useAppSettingsStore()

// 当前主题
const { theme } = storeToRefs(appStore)
// 当前聊天背景
const { chatBackground } = storeToRefs(appSettingsStore)
// 切换主题的方法
const { toggleTheme } = appStore
// 设置聊天背景的方法
const { setChatBackground } = appSettingsStore

// 可选聊天背景列表
const chatBackgrounds: { id: ChatBackgroundId; label: string; style: string }[] = [
  { id: 'default', label: '默认纯色', style: 'linear-gradient(180deg, #f5f6f7 0%, #eceff1 100%)' },
  { id: 'purple', label: '梦幻紫', style: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)' },
  { id: 'orange', label: '落日橘', style: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' }
]

// 选择聊天背景并提示
function pickChatBackground(id: ChatBackgroundId) {
  setChatBackground(id)
  appSettingsStore.scheduleSave('chatBackground')
  message.success('聊天背景已更新')
}
</script>

<template>
  <!-- 外观与显示设置页 -->
  <div class="settings-scroll">
    <!-- 主题切换分组 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="theme === 'dark' ? MoonOutline : SunnyOutline" :size="18" class="group-ico" />
        <span>主题</span>
      </div>
      <div class="theme-mode-row">
        <button
          type="button"
          class="theme-mode"
          :class="{ active: theme === 'light' }"
          @click="theme !== 'light' && toggleTheme()"
        >
          <n-icon :component="SunnyOutline" :size="22" />
          <span>浅色</span>
        </button>
        <button
          type="button"
          class="theme-mode"
          :class="{ active: theme === 'dark' }"
          @click="theme !== 'dark' && toggleTheme()"
        >
          <n-icon :component="MoonOutline" :size="22" />
          <span>深色</span>
        </button>
      </div>
    </section>

    <!-- 聊天背景选择分组 -->
    <section class="group-card">
      <div class="group-head">
        <span>聊天背景</span>
      </div>
      <p class="group-tip">选择会话区域的背景样式</p>
      <div class="bg-grid">
        <button
          v-for="bg in chatBackgrounds"
          :key="bg.id"
          type="button"
          class="bg-tile"
          :class="{ active: chatBackground === bg.id }"
          @click="pickChatBackground(bg.id)"
        >
          <div class="bg-preview" :style="{ background: bg.style }">
            <div class="bg-preview-bubble left" />
            <div class="bg-preview-bubble right" />
          </div>
          <span class="bg-label">{{ bg.label }}</span>
          <n-icon
            v-if="chatBackground === bg.id"
            :component="CheckmarkCircle"
            :size="18"
            class="bg-check"
          />
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

/* ---- 主题切换 ---- */
.theme-mode-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 8px 16px 16px;
}

.theme-mode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border: 2px solid var(--lx-border-light);
  border-radius: 10px;
  background: var(--lx-bg-card);
  color: var(--lx-text-secondary);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, box-shadow 0.2s;
}

.theme-mode:hover {
  border-color: var(--lx-accent);
  color: var(--lx-text-body);
}

.theme-mode.active {
  border-color: var(--lx-accent);
  color: var(--lx-accent);
  box-shadow: 0 0 0 3px var(--lx-accent-soft);
}

.theme-mode span {
  font-size: 13px;
  font-weight: 500;
}

/* ---- 聊天背景 ---- */
.bg-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 0 16px 16px;
}

.bg-tile {
  position: relative;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: center;
}

.bg-preview {
  height: 88px;
  border-radius: 8px;
  border: 2px solid var(--lx-border-light);
  overflow: hidden;
  position: relative;
  transition: border-color 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 12px;
}

.bg-tile:hover .bg-preview {
  transform: translateY(-2px);
  border-color: var(--lx-accent);
}

.bg-tile.active .bg-preview {
  border-color: var(--lx-accent);
  box-shadow: 0 0 0 3px var(--lx-accent-soft);
}

.bg-preview-bubble {
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.75);
}

.bg-preview-bubble.left {
  width: 55%;
  align-self: flex-start;
}

.bg-preview-bubble.right {
  width: 40%;
  align-self: flex-end;
  background: rgba(18, 183, 245, 0.35);
}

.bg-label {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.bg-check {
  position: absolute;
  top: 6px;
  right: 6px;
  color: var(--lx-accent);
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
}
</style>
