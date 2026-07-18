<script setup lang="ts">
/**
 * 领红包弹窗。
 * <p>
 * 调用真实后端 {@code POST /red-packet/{id}/receive} 领取红包；
 * 服务端会做乐观锁扣减、生成领取记录、转移余额并返回最新 RedPacketVO；
 * 领取完成后，前端用返回的 VO 更新本地消息的红包状态。
 * </p>
 */
import { computed, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import * as redPacketApi from '../../api/redPacket'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketReceiveOpen, redPacketReceiveMsgId } = storeToRefs(chatModalsStore)
const { closeRedPacketReceive } = chatModalsStore
const { currentMessages } = storeToRefs(appStore)

const packetMsg = computed(() =>
  currentMessages.value.find(m => m.id === redPacketReceiveMsgId.value)
)
const opened = computed(() => !!packetMsg.value?.redPacketOpened)
const remaining = computed(() => {
  const total = packetMsg.value?.redPacketTotalCount ?? 0
  const recv = packetMsg.value?.redPacketReceived ? 1 : 0
  return Math.max(0, total - recv)
})
const statusText = computed(() => {
  const s = packetMsg.value?.redPacketStatus
  if (s === 'finished') return '已领完'
  if (s === 'expired') return '已过期'
  if (opened.value) return '已领取'
  return '未领取'
})

async function syncRedPacketDetail() {
  const msg = packetMsg.value
  const redPacketId = msg?.redPacketId
  if (!msg || !redPacketId) return
  try {
    const res = await redPacketApi.getRedPacketDetail(redPacketId)
    if (res.code === 200 && res.data) {
      const rp = res.data
      msg.redPacketGreeting = rp.greeting
      msg.redPacketRemainingCount = rp.remainingCount
      msg.redPacketStatus = rp.status
      msg.redPacketReceived = rp.received
      if (rp.receivedAmount != null) {
        msg.redPacketReceivedAmount = String(rp.receivedAmount)
        msg.redPacketOpened = true
      }
    }
  } catch {
    /* 详情拉取失败时沿用消息内嵌字段 */
  }
}

watch(redPacketReceiveOpen, open => {
  if (open) {
    void syncRedPacketDetail()
  }
})

function close() {
  closeRedPacketReceive()
}

async function openPacket() {
  const msg = packetMsg.value
  const redPacketId = msg?.redPacketId
  if (!msg || !redPacketId) {
    message.warning('红包信息缺失')
    return
  }
  if (msg.isSelf) {
    message.info('这是你发出的红包')
    return
  }
  if (msg.redPacketReceived || msg.redPacketOpened) {
    message.info('已领取过该红包')
    return
  }
  if (msg.redPacketStatus === 'finished') {
    message.warning('红包已领完')
    return
  }
  if (msg.redPacketStatus === 'expired') {
    message.warning('红包已过期')
    return
  }
  try {
    const res = await redPacketApi.receiveRedPacket(redPacketId)
    if (res.code === 200 && res.data) {
      const rp = res.data
      msg.redPacketOpened = true
      msg.redPacketReceived = true
      msg.redPacketReceivedAmount = String(rp.receivedAmount ?? '')
      msg.redPacketRemainingCount = rp.remainingCount
      msg.redPacketStatus = rp.status as 'active' | 'finished' | 'expired'
      message.success(
        rp.receivedAmount != null
          ? `已领取 ¥${Number(rp.receivedAmount).toFixed(2)}`
          : '已领取红包'
      )
    } else {
      message.warning(res.message || '领取失败')
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '领取失败')
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
          <p v-if="opened" class="amount">
            ¥{{ packetMsg.redPacketReceivedAmount || packetMsg.redPacketAmount || '0.00' }}
          </p>
          <p v-else-if="packetMsg.redPacketStatus === 'finished'" class="hint">红包已领完</p>
          <p v-else-if="packetMsg.redPacketStatus === 'expired'" class="hint">红包已过期</p>
          <p v-else class="hint">点击下方拆开红包</p>
          <p class="status">{{ statusText }} · 剩余 {{ remaining }} 个</p>
        </div>
        <div class="packet-foot">
          <n-button
            v-if="!opened && !packetMsg.isSelf && packetMsg.redPacketStatus === 'active'"
            type="primary"
            @click="openPacket"
          >
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

.status {
  margin: 12px 0 0;
  font-size: 12px;
  opacity: 0.85;
}

.packet-foot {
  padding: 16px;
  display: flex;
  justify-content: center;
  background: var(--lx-bg-card);
}
</style>