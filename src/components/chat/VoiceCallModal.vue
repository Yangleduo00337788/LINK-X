<script setup lang="ts">
import { ref } from 'vue'
import { NIcon } from 'naive-ui'
import {
  MicOutline,
  MicOffOutline,
  VideocamOutline,
  DesktopOutline,
  CallOutline
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { voiceCallOpen } = storeToRefs(chatModalsStore)
const { closeVoiceCall } = chatModalsStore
const { currentSession } = storeToRefs(appStore)

const micOn = ref(true)

function hangUp() {
  closeVoiceCall()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="voiceCallOpen" class="call-root">
      <div class="call-window">
        <div class="call-top">
          <span class="status">等待对方接听...</span>
        </div>
        <div class="call-center">
          <Avatar
            v-if="currentSession"
            :text="currentSession.avatarText"
            :color="currentSession.avatarColor"
            :image-url="currentSession.avatarUrl"
            :size="88"
          />
          <p class="peer">{{ currentSession?.name || '好友' }}</p>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl" @click="micOn = !micOn">
            <n-icon :component="micOn ? MicOutline : MicOffOutline" :size="26" />
            <span>{{ micOn ? '关闭麦克风' : '开启麦克风' }}</span>
          </button>
          <button type="button" class="ctl muted">
            <n-icon :component="VideocamOutline" :size="26" />
            <span>开启视频</span>
          </button>
          <button type="button" class="ctl muted">
            <n-icon :component="DesktopOutline" :size="26" />
            <span>屏幕共享</span>
          </button>
          <button type="button" class="ctl hangup" @click="hangUp">
            <n-icon :component="CallOutline" :size="28" />
            <span>挂断</span>
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.call-root {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(420px, 90vw);
  background: linear-gradient(180deg, #3a3a3a 0%, #2a2a2a 100%);
  border-radius: var(--lx-radius);
  padding: 24px 20px 28px;
  color: #fff;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.4);
}

.call-top {
  text-align: center;
  margin-bottom: 24px;
}

.status {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
}

.call-center {
  text-align: center;
  margin-bottom: 32px;
}

.call-center :deep(.avatar) {
  margin: 0 auto 12px;
}

.peer {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.call-controls {
  display: flex;
  justify-content: space-around;
  align-items: flex-start;
  gap: 8px;
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  cursor: pointer;
  max-width: 72px;
}

.ctl span {
  line-height: 1.2;
}

.ctl.muted {
  opacity: 0.55;
}

.ctl.hangup {
  color: #fff;
}

.ctl.hangup :deep(svg) {
  background: #e34d59;
  border-radius: 50%;
  padding: 10px;
  width: 48px !important;
  height: 48px !important;
}
</style>