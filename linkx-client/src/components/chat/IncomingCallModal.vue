<!-- 作者：yangleduo -->
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
            color="var(--lx-success-strong)"
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
  z-index: var(--lx-z-dialog-call-in);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}

.call-window {
  width: min(380px, 90vw);
  background: var(--lx-call-gradient);
  border-radius: var(--lx-radius);
  padding: var(--lx-space-5xl-minus) var(--lx-space-3xl) var(--lx-space-5xl);
  color: var(--lx-text-on-accent);
  box-shadow: var(--lx-shadow-popover);
  text-align: center;
}

.label {
  margin: 0 0 var(--lx-space-3xl);
  font-size: var(--lx-font);
  color: rgba(255, 255, 255, 0.75);
}

.call-center :deep(.avatar) {
  margin: 0 auto var(--lx-space-lg);
}

.peer {
  margin: 0 0 var(--lx-space-sm);
  font-size: var(--lx-font-4xl);
  font-weight: 600;
}

.hint {
  margin: 0;
  font-size: var(--lx-font-md);
  color: rgba(255, 255, 255, 0.65);
}

.call-controls {
  margin-top: var(--lx-space-5xl-minus);
  display: flex;
  justify-content: center;
  gap: var(--lx-space-6xl);
}

.ctl {
  border: none;
  background: transparent;
  color: var(--lx-text-on-accent);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space);
  font-size: var(--lx-font-sm);
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
  background: var(--lx-danger);
}

.ctl.accept :deep(.n-icon) {
  background: var(--lx-success-strong);
}
</style>
