<script setup lang="ts">
/**
 * 来电弹窗：被叫端接听 / 拒绝。
 */
import { watch } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import { CallOutline, CloseOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useCallStore } from '../../stores/call'

const message = useMessage()
const callStore = useCallStore()
const { showIncomingUi, peerName, peerAvatar, callType, errorMessage } = storeToRefs(callStore)

watch(errorMessage, msg => {
  if (msg) {
    message.info(msg)
    callStore.clearError()
  }
})

async function accept() {
  try {
    await callStore.acceptIncoming()
  } catch (e) {
    message.error((e as Error).message || '接听失败')
  }
}

async function reject() {
  await callStore.rejectIncoming()
}

function avatarText(name: string) {
  return name?.charAt(0) || '友'
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showIncomingUi" class="call-root">
      <div class="call-window">
        <p class="label">{{ callType === 'video' ? '视频来电' : '语音来电' }}</p>
        <div class="call-center">
          <Avatar
            :text="avatarText(peerName)"
            color="#07c160"
            :image-url="peerAvatar || undefined"
            :size="88"
          />
          <p class="peer">{{ peerName || '好友' }}</p>
          <p class="hint">邀请你进行{{ callType === 'video' ? '视频' : '语音' }}通话</p>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl reject" @click="reject">
            <n-icon :component="CloseOutline" :size="28" />
            <span>拒绝</span>
          </button>
          <button type="button" class="ctl accept" @click="accept">
            <n-icon :component="CallOutline" :size="28" />
            <span>接听</span>
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
  z-index: 2210;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(380px, 90vw);
  background: linear-gradient(180deg, #3a3a3a 0%, #2a2a2a 100%);
  border-radius: var(--lx-radius);
  padding: 28px 20px 32px;
  color: #fff;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  text-align: center;
}

.label {
  margin: 0 0 20px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
}

.call-center :deep(.avatar) {
  margin: 0 auto 12px;
}

.peer {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.hint {
  margin: 0 0 28px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}

.call-controls {
  display: flex;
  justify-content: space-around;
  gap: 24px;
}

.ctl {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.ctl.reject :deep(svg),
.ctl.accept :deep(svg) {
  border-radius: 50%;
  padding: 12px;
  width: 52px !important;
  height: 52px !important;
}

.ctl.reject :deep(svg) {
  background: #fa5151;
}

.ctl.accept :deep(svg) {
  background: #07c160;
}
</style>
