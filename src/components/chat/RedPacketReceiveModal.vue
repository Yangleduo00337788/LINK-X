<script setup lang="ts">
import { computed } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketReceiveOpen, redPacketReceiveMsgId } = storeToRefs(chatModalsStore)
const { closeRedPacketReceive } = chatModalsStore
const { currentMessages } = storeToRefs(appStore)
const { openRedPacketMessage } = appStore

const packetMsg = computed(() =>
  currentMessages.value.find(m => m.id === redPacketReceiveMsgId.value)
)

const opened = computed(() => !!packetMsg.value?.redPacketOpened)

function close() {
  closeRedPacketReceive()
}

function openPacket() {
  const id = redPacketReceiveMsgId.value
  if (!id) return
  if (openRedPacketMessage(id)) {
    message.success(`已领取 ¥${packetMsg.value?.redPacketAmount || '0.01'}`)
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="redPacketReceiveOpen && packetMsg" class="modal-root" @click.self="close">
      <div class="packet-card" @click.stop>
        <div class="packet-cover">
          <p class="from">{{ packetMsg.isSelf ? '你发出的红包' : '收到红包' }}</p>
          <p class="greeting">{{ packetMsg.redPacketGreeting || '恭喜发财' }}</p>
          <p v-if="opened" class="amount">¥{{ packetMsg.redPacketAmount || '0.01' }}</p>
          <p v-else class="hint">点击下方拆开红包</p>
        </div>
        <div class="packet-foot">
          <n-button v-if="!opened && !packetMsg.isSelf" type="primary" @click="openPacket">
            开
          </n-button>
          <n-button v-else @click="close">关闭</n-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2350;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.packet-card {
  width: min(300px, 90vw);
  border-radius: var(--lx-radius);
  overflow: hidden;
  background: var(--lx-bg-card);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.packet-cover {
  background: linear-gradient(180deg, #e84c3d 0%, #c0392b 100%);
  color: #fff;
  text-align: center;
  padding: 36px 24px 32px;
}

.from {
  margin: 0 0 8px;
  font-size: 13px;
  opacity: 0.9;
}

.greeting {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.amount {
  margin: 16px 0 0;
  font-size: 28px;
  font-weight: 700;
}

.hint {
  margin: 16px 0 0;
  font-size: 13px;
  opacity: 0.85;
}

.packet-foot {
  padding: 16px;
  display: flex;
  justify-content: center;
  background: var(--lx-bg-card);
}
</style>
