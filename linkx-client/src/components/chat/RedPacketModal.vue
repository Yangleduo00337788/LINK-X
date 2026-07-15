<script setup lang="ts">
// Vue 响应式 API
import { ref } from 'vue'
// Naive UI 输入框、按钮与消息提示
import { NInput, NButton, useMessage } from 'naive-ui'
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
// 发红包弹窗是否打开
const { redPacketOpen } = storeToRefs(chatModalsStore)
// 关闭发红包弹窗的方法
const { closeRedPacket } = chatModalsStore
// 发送消息的方法
const { sendMessage } = appStore
const { currentSession } = storeToRefs(appStore)

// 红包金额（元）
const amount = ref('8.88')
// 红包祝福语
const greeting = ref('恭喜发财，大吉大利')

// 关闭发红包弹窗
function close() {
  closeRedPacket()
}

// 发送红包消息到当前会话
async function send() {
  if (currentSession.value?.isReal) {
    message.warning('真实会话暂不支持红包')
    return
  }
  const amt = amount.value.trim() || '0.01'
  const text = greeting.value.trim() || '恭喜发财'
  try {
    await sendMessage(text, {
      type: 'redPacket',
      redPacketGreeting: text,
      redPacketAmount: amt
    })
    message.success('红包已发送')
    close()
  } catch {
    message.error('红包发送失败')
  }
}
</script>

<template>
  <!-- 发红包弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="redPacketOpen" class="modal-root" @click.self="close">
      <div class="packet-card" @click.stop>
        <!-- 红包头部标题 -->
        <div class="packet-head">发红包</div>
        <!-- 金额与祝福语表单 -->
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
        <!-- 底部操作按钮 -->
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
