<script setup lang="ts">
// Vue 计算属性
import { computed } from 'vue'
// Naive UI 按钮与消息提示
import { NButton, useMessage } from 'naive-ui'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'

// 消息提示实例
const message = useMessage()
// 聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 应用 Store 实例
const appStore = useAppStore()
// 领红包弹窗是否打开、目标红包消息 ID
const { redPacketReceiveOpen, redPacketReceiveMsgId } = storeToRefs(chatModalsStore)
// 关闭领红包弹窗的方法
const { closeRedPacketReceive } = chatModalsStore
// 当前会话消息列表
const { currentMessages } = storeToRefs(appStore)
// 打开红包消息的方法
const { openRedPacketMessage } = appStore

// 根据消息 ID 查找对应的红包消息
const packetMsg = computed(() =>
  currentMessages.value.find(m => m.id === redPacketReceiveMsgId.value)
)

// 红包是否已被领取
const opened = computed(() => !!packetMsg.value?.redPacketOpened)

// 关闭领红包弹窗
function close() {
  closeRedPacketReceive()
}

// 拆开红包并领取金额
function openPacket() {
  const id = redPacketReceiveMsgId.value
  if (!id) return
  if (openRedPacketMessage(id)) {
    message.success(`已领取 ¥${packetMsg.value?.redPacketAmount || '0.01'}`)
  }
}
</script>

<template>
  <!-- 领红包弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="redPacketReceiveOpen && packetMsg" class="modal-root" @click.self="close">
      <div class="packet-card" @click.stop>
        <!-- 红包封面：祝福语与金额 -->
        <div class="packet-cover">
          <p class="from">{{ packetMsg.isSelf ? '你发出的红包' : '收到红包' }}</p>
          <p class="greeting">{{ packetMsg.redPacketGreeting || '恭喜发财' }}</p>
          <p v-if="opened" class="amount">¥{{ packetMsg.redPacketAmount || '0.01' }}</p>
          <p v-else class="hint">点击下方拆开红包</p>
        </div>
        <!-- 底部：拆开或关闭按钮 -->
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
