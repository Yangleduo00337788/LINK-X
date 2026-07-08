<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketOpen } = storeToRefs(chatModalsStore)
const { closeRedPacket } = chatModalsStore
const { sendMessage } = appStore

const amount = ref('8.88')
const greeting = ref('恭喜发财，大吉大利')

function close() {
  closeRedPacket()
}

function send() {
  const amt = amount.value.trim() || '0.01'
  const text = greeting.value.trim() || '恭喜发财'
  sendMessage(text, {
    type: 'redPacket',
    redPacketGreeting: text,
    redPacketAmount: amt
  })
  message.success('红包已发送')
  close()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="redPacketOpen" class="modal-root" @click.self="close">
      <div class="packet-card" @click.stop>
        <div class="packet-head">发红包</div>
        <div class="packet-body">
          <label class="field">
            <span>金额（元）</span>
            <n-input v-model:value="amount" placeholder="0.01" />
          </label>
          <label class="field">
            <span>祝福语</span>
            <n-input v-model:value="greeting" placeholder="恭喜发财" />
          </label>
        </div>
        <div class="packet-foot">
          <n-button @click="close">取消</n-button>
          <n-button type="primary" @click="send">塞钱进红包</n-button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2300;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.packet-card {
  width: min(360px, 92vw);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
  box-shadow: var(--lx-shadow-modal);
}

.packet-head {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  color: #fff;
  text-align: center;
  padding: 24px;
  font-size: 18px;
  font-weight: 600;
}

.packet-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.packet-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 0 20px 20px;
}
</style>
