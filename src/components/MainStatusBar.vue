<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NIcon } from 'naive-ui'
import { PushOutline } from '@vicons/ionicons5'
import WindowControls from './WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import { useOverlayStore } from '../stores/overlay'

withDefaults(
  defineProps<{
    variant?: 'profile' | 'chat' | 'module'
    title?: string
    subtitle?: string
    showTheme?: boolean
  }>(),
  {
    variant: 'profile',
    title: '',
    subtitle: '',
    showTheme: true
  }
)

const appStore = useAppStore()
const overlayStore = useOverlayStore()
const { userProfile, navKey, currentSession } = storeToRefs(appStore)
const { open: openOverlay } = overlayStore

const displayName = computed(() => userProfile.value.nickname)

const centerTitle = computed(() => {
  if (navKey.value === 'chat' && currentSession.value) {
    return currentSession.value.name
  }
  return ''
})

const isPinned = ref(false)

onMounted(async () => {
  if (window.electronAPI && window.electronAPI.isPinned) {
    isPinned.value = await window.electronAPI.isPinned()
  }
})

async function togglePin() {
  if (window.electronAPI && window.electronAPI.togglePin) {
    isPinned.value = await window.electronAPI.togglePin()
  }
}

function openProfile() {
  openOverlay('profile')
}
</script>

<template>
  <header class="main-status-bar">
    <div class="status-left">
      <div class="brand-block" title="LinkX">
        <svg class="brand-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="24" height="24" rx="6" fill="var(--lx-text)"/>
          <path d="M8 8L16 16M16 8L8 16" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="brand-text">LinkX</span>
      </div>
      <button type="button" class="status-avatar" title="个人资料" @click="openProfile">
        <img
          src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
          alt=""
          class="status-avatar-img"
        />
      </button>

      <div v-if="variant === 'profile'" class="profile-col">
        <span class="nickname">{{ displayName }}</span>
      </div>

      <template v-else-if="variant === 'chat'">
        <div class="profile-col">
          <span class="nickname single">{{ title || '请选择会话' }}</span>
          <span v-if="subtitle" class="signature-link static">{{ subtitle }}</span>
        </div>
      </template>

      <template v-else>
        <div class="profile-col">
          <span class="nickname single">{{ title }}</span>
          <span v-if="subtitle" class="signature-link static">{{ subtitle }}</span>
        </div>
      </template>
    </div>

    <div class="status-center title-bar-drag">
      <span v-if="centerTitle" class="session-title">{{ centerTitle }}</span>
    </div>

    <div class="status-right">
      <button type="button" class="pin-btn" :class="{ active: isPinned }" title="置顶窗口" @click="togglePin">
        <n-icon :component="PushOutline" :size="16" />
      </button>
    </div>

    <div class="win-controls-slot">
      <WindowControls />
    </div>
  </header>
</template>

<style scoped>
.main-status-bar {
  flex-shrink: 0;
  height: 40px;
  min-height: 40px;
  display: flex;
  align-items: stretch;
  padding: 0 0 0 10px;
  background: transparent;
  border-bottom: none;
  position: relative;
  z-index: 50;
}

.win-controls-slot {
  flex-shrink: 0;
  position: relative;
  z-index: 10001;
  -webkit-app-region: no-drag;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
  flex-shrink: 0;
  height: 40px;
  -webkit-app-region: no-drag;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-right: 33px;
  user-select: none;
}

.brand-icon {
  flex-shrink: 0;
}

.brand-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--lx-text);
  letter-spacing: -0.01em;
  line-height: 1;
}

.status-avatar {
  border: none;
  padding: 0;
  margin: 0 8px 0 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  background: var(--lx-bg-panel-deep);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.8),
    0 0 0 2px var(--lx-border-light);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.status-avatar:hover {
  transform: scale(1.04);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.9),
    0 0 0 2px rgba(18, 183, 245, 0.35);
}

.status-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.profile-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  min-width: 0;
  max-width: 200px;
  line-height: 1.2;
  padding: 2px 0;
}

.nickname {
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nickname.single {
  font-size: 14px;
  font-weight: 500;
}

.signature-link {
  border: none;
  background: none;
  padding: 0;
  margin: 0;
  font-size: 11px;
  color: #a8a8a8;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  text-align: left;
  line-height: 1.3;
}

.signature-link:hover {
  color: var(--lx-accent);
}

.signature-link.static {
  cursor: default;
  pointer-events: none;
}

.status-center {
  flex: 1;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  -webkit-app-region: drag;
  cursor: default;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--lx-text-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  pointer-events: none;
  user-select: none;
}

.status-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-right: 4px;
  -webkit-app-region: no-drag;
}

.pin-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
  cursor: pointer;
  border: none;
  background: none;
  width: 28px;
  height: 28px;
  border-radius: var(--lx-radius);
  transition: all 0.2s;
}

.pin-btn:hover {
  color: var(--lx-text-secondary);
  background: var(--lx-bg-hover);
}

.pin-btn.active {
  color: var(--lx-accent);
  background: rgba(18, 183, 245, 0.1);
}
</style>