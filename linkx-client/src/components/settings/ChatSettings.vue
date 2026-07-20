<script setup lang="ts">
import { computed } from 'vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import type { ChatBackgroundId } from '../../types'
import { useI18n } from '../../i18n'

const message = useMessage()
const appSettingsStore = useAppSettingsStore()
const { chatBackground } = storeToRefs(appSettingsStore)
const { setChatBackground, scheduleSave } = appSettingsStore
const { t } = useI18n()

const chatBackgrounds = computed(() => [
  {
    id: 'default' as ChatBackgroundId,
    label: t('chat.bgDefault'),
    style: 'linear-gradient(180deg, #f5f6f7 0%, #eceff1 100%)'
  },
  {
    id: 'purple' as ChatBackgroundId,
    label: t('chat.bgPurple'),
    style: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)'
  },
  {
    id: 'orange' as ChatBackgroundId,
    label: t('chat.bgOrange'),
    style: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)'
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
    <section class="group-card">
      <div class="group-head"><span>{{ t('chat.background') }}</span></div>
      <p class="group-tip">{{ t('chat.tip') }}</p>
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
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.bg-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 0 16px 16px;
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
  border-radius: 8px;
  border: 2px solid var(--lx-border-light);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  transition: border-color 0.2s;
}

.bg-tile.active .bg-preview,
.bg-tile:hover .bg-preview {
  border-color: var(--lx-accent);
}

.bg-preview-bubble {
  height: 10px;
  border-radius: 999px;
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
  margin-top: 8px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}
</style>
