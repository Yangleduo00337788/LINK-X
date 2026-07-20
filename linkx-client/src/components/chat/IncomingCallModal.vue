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
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
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
    message.error((e as Error).message || t('extra.acceptFail'))
  }
}

async function reject() {
  await callStore.rejectIncoming()
}

function avatarText(name: string) {
  return name?.charAt(0) || t('extra.friendChar')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showIncomingUi" class="call-root">
      <div class="call-window">
        <p class="label">{{ callType === 'video' ? t('extra.videoIncoming') : t('extra.voiceIncoming') }}</p>
        <div class="call-center">
          <Avatar
            :text="avatarText(peerName)"
            color="#07c160"
            :image-url="peerAvatar || undefined"
            :size="88"
          />
          <p class="peer">{{ peerName || t('extra.friend') }}</p>
          <p class="hint">
            {{ callType === 'video' ? t('extra.inviteVideoCall') : t('extra.inviteVoiceCall') }}
          </p>
        </div>
        <div class="call-controls">
          <button type="button" class="ctl reject" @click="reject">
            <n-icon :component="CloseOutline" :size="28" />
            <span>{{ t('extra.reject') }}</span>
          </button>
          <button type="button" class="ctl accept" @click="accept">
            <n-icon :component="CallOutline" :size="28" />
            <span>{{ t('extra.accept') }}</span>
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
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}

.call-controls {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  gap: 48px;
}

.ctl {
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.ctl :deep(.n-icon) {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ctl.reject :deep(.n-icon) {
  background: #fa5151;
}

.ctl.accept :deep(.n-icon) {
  background: #07c160;
}
</style>
