<script setup lang="ts">
/**
 * 发红包弹窗。
 * <p>
 * 真实接入后端 {@code POST /red-packet}：
 * 服务端会冻结发送者余额、写入红包表，并异步以 {@code msgType=redPacket} 推送一条消息到会话。
 * 前端不再发送 WS 红包帧，仅展示乐观消息，等待服务端通过 WS 推送覆盖。
 * </p>
 */
import { ref, computed } from 'vue'
import { NInput, NButton, NRadioGroup, NRadio, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import * as redPacketApi from '../../api/redPacket'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketOpen } = storeToRefs(chatModalsStore)
const { closeRedPacket } = chatModalsStore
const { currentSession, userProfile } = storeToRefs(appStore)

const amount = ref('8.88')
const greeting = ref('恭喜发财，大吉大利')
const totalCount = ref(1)
// 红包类型：normal 普通 / lucky 拼手气
const packetType = ref<'normal' | 'lucky'>('normal')
const submitting = ref(false)

const sessionIdNum = computed(() => {
  const id = currentSession.value?.id
  if (!id) return null
  const n = Number(id)
  return Number.isFinite(n) ? n : null
})

const canSubmit = computed(() => {
  const amt = Number(amount.value)
  return (
    !submitting.value &&
    sessionIdNum.value !== null &&
    Number.isFinite(amt) &&
    amt >= 0.01 &&
    totalCount.value >= 1 &&
    totalCount.value <= 100
  )
})

function close() {
  if (submitting.value) return
  closeRedPacket()
}

async function send() {
  if (!currentSession.value || sessionIdNum.value === null) {
    message.warning('请先选择会话')
    return
  }
  if (!canSubmit.value) {
    message.warning('请检查红包金额与个数')
    return
  }
  const amt = Number(amount.value)
  if (totalCount.value > Math.floor(amt / 0.01)) {
    message.warning('每个红包金额不能少于 0.01 元')
    return
  }
  submitting.value = true
  try {
    const res = await redPacketApi.sendRedPacket({
      conversationId: String(sessionIdNum.value),
      type: packetType.value,
      totalAmount: amt,
      totalCount: totalCount.value,
      greeting: greeting.value.trim() || '恭喜发财'
    })
    if (res.code === 200 && res.data) {
      message.success(
        `${packetType.value === 'lucky' ? '拼手气' : '普通'}红包已发送，金额 ¥${amt.toFixed(2)}`
      )
      // 不再写入乐观消息：后端已通过 WS 推送，等待 appStore.handleIncomingWsMessage 自动补齐
      // 当前用户也能收到自己 push 的 message 帧，因此不需要手动 unshift
      void userProfile.value // 静默引用以保留类型推断
      close()
    } else {
      message.error(res.message || '红包发送失败')
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '红包发送失败')
  } finally {
    submitting.value = false
  }
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
            <n-input v-model:value="amount" placeholder="0.01" :disabled="submitting" />
          </label>
          <label class="field">
            <span>个数</span>
            <n-input
              v-model:value="totalCount"
              type="number"
              :min="1"
              :max="100"
              :disabled="submitting"
            />
          </label>
          <label class="field">
            <span>类型</span>
            <n-radio-group v-model:value="packetType" :disabled="submitting">
              <n-radio value="normal">普通红包</n-radio>
              <n-radio value="lucky">拼手气红包</n-radio>
            </n-radio-group>
          </label>
          <label class="field">
            <span>祝福语</span>
            <n-input v-model:value="greeting" placeholder="恭喜发财" :disabled="submitting" />
          </label>
        </div>
        <div class="packet-foot">
          <n-button :disabled="submitting" @click="close">取消</n-button>
          <n-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="send">
            塞钱进红包
          </n-button>
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