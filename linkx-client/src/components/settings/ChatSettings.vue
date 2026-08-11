<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed } from 'vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import type { ChatBackgroundId } from '../../types'
import { useI18n } from '../../i18n'
import { lxChatWallpaperBg } from '../../theme/vars'
import { LxGroupCard } from '../ui'

const message = useMessage()
const appSettingsStore = useAppSettingsStore()
const { chatBackground } = storeToRefs(appSettingsStore)
const { setChatBackground, scheduleSave } = appSettingsStore
const { t } = useI18n()

const chatBackgrounds = computed(() => [
  {
    id: 'default' as ChatBackgroundId,
    label: t('chat.bgDefault'),
    style: lxChatWallpaperBg.default
  },
  {
    id: 'purple' as ChatBackgroundId,
    label: t('chat.bgPurple'),
    style: lxChatWallpaperBg.purple
  },
  {
    id: 'orange' as ChatBackgroundId,
    label: t('chat.bgOrange'),
    style: lxChatWallpaperBg.orange
  }
])

function pickChatBackground(id: ChatBackgroundId) {
  setChatBackground(id)
  scheduleSave('chatBackground')
  message.success(t('chat.updated'))
}
</script>

<template>
  <div class="settings-scroll">
    <LxGroupCard tag="section" variant="settings">
      <div class="group-head"><span>{{ t('chat.background') }}</span></div>
      <p class="group-tip">{{ t('chat.tip') }}</p>
      <div class="bg-grid">
        <button
          v-for="bg in chatBackgrounds"
          :key="bg.id"
          type="button"
          class="bg-tile"
          :class="{ 'is-active': chatBackground === bg.id }"
          @click="pickChatBackground(bg.id)"
        >
          <div class="bg-preview" :style="{ background: bg.style }">
            <div class="bg-preview-bubble left" />
            <div class="bg-preview-bubble right" />
          </div>
          <span class="bg-label">{{ bg.label }}</span>
        </button>
      </div>
    </LxGroupCard>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.bg-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lx-space-lg);
  padding: 0 var(--lx-space-2xl) var(--lx-space-2xl);
}

.bg-tile {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: center;
}

.bg-preview {
  height: 72px;
  border-radius: var(--lx-radius-sm);
  border: 2px solid var(--lx-border-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--lx-space-sm);
  padding: var(--lx-space-lg);
  transition: border-color var(--lx-duration-md);
}

.bg-tile.is-active .bg-preview,
.bg-tile:hover .bg-preview {
  border-color: var(--lx-accent);
}

.bg-preview-bubble {
  height: 10px;
  border-radius: var(--lx-bubble-radius);
  background: rgba(255, 255, 255, 0.7);
}

.bg-preview-bubble.left {
  width: 55%;
  align-self: flex-start;
}

.bg-preview-bubble.right {
  width: 40%;
  align-self: flex-end;
}

.bg-label {
  display: block;
  margin-top: var(--lx-space);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}
</style>
