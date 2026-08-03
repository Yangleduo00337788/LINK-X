<script setup lang="ts">
/**
 * 领红包弹窗。
 * <p>
 * 调用真实后端 {@code POST /red-packet/{id}/receive} 领取红包；
 * 拆开时带旋转翻盖动画，金额弹出展示。
 * 自己发出的红包也可点击查看详情与领取记录。
 * </p>
 */
import { computed, watch, ref, nextTick, reactive } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import * as redPacketApi from '../../api/redPacket'
import type { RedPacket, RedPacketRecord } from '../../api/redPacket'
import { useI18n } from '../../i18n'

const message = useMessage()
const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketReceiveOpen, redPacketReceiveMsgId, redPacketReceivePacketId } =
  storeToRefs(chatModalsStore)
const { closeRedPacketReceive } = chatModalsStore
const { currentMessages } = storeToRefs(appStore)

const packetMsg = computed(() =>
  redPacketReceiveMsgId.value
    ? currentMessages.value.find(m => m.id === redPacketReceiveMsgId.value)
    : undefined
)

const standalone = reactive({
  redPacketId: '',
  greeting: '',
  remainingCount: 0,
  totalCount: 0,
  status: 'active' as 'active' | 'expired' | 'finished',
  received: false,
  receivedAmount: '',
  amount: '',
  type: 'normal' as 'normal' | 'lucky',
  opened: false
})

const redPacketRecords = ref<RedPacketRecord[]>([])
const detailIsSelf = ref<boolean | null>(null)
const detailType = ref<'normal' | 'lucky' | null>(null)
const animPhase = ref<'idle' | 'spinning' | 'revealed'>('idle')
const opening = ref(false)

const viewReady = computed(() => !!packetMsg.value || !!standalone.redPacketId)

const isSelfPacket = computed(() => detailIsSelf.value ?? packetMsg.value?.isSelf ?? false)
const isLucky = computed(
  () =>
    detailType.value === 'lucky' ||
    packetMsg.value?.redPacketType === 'lucky' ||
    standalone.type === 'lucky'
)
const typeLabel = computed(() => (isLucky.value ? t('modals.luckyPacket') : t('modals.normalPacket')))
const opened = computed(
  () =>
    !!packetMsg.value?.redPacketOpened ||
    standalone.opened ||
    animPhase.value === 'revealed'
)
const remaining = computed(() => {
  if (packetMsg.value?.redPacketRemainingCount != null) {
    return packetMsg.value.redPacketRemainingCount
  }
  if (standalone.redPacketId) return standalone.remainingCount
  const total = packetMsg.value?.redPacketTotalCount ?? 0
  if (redPacketRecords.value.length > 0) {
    return Math.max(0, total - redPacketRecords.value.length)
  }
  const recv = packetMsg.value?.redPacketReceived ? 1 : 0
  return Math.max(0, total - recv)
})
const packetStatus = computed(() => packetMsg.value?.redPacketStatus || standalone.status)
const greeting = computed(
  () => packetMsg.value?.redPacketGreeting || standalone.greeting || t('modals.greetingFallback')
)
const statusText = computed(() => {
  const s = packetStatus.value
  if (s === 'finished') return t('modals.rpStatusFinished')
  if (s === 'expired') return t('modals.rpStatusExpired')
  if (isSelfPacket.value) return t('modals.rpStatusSent')
  if (opened.value) return t('modals.rpStatusClaimed')
  return t('modals.rpStatusPending')
})
const displayAmount = computed(() => {
  const raw =
    packetMsg.value?.redPacketReceivedAmount ||
    standalone.receivedAmount ||
    (isSelfPacket.value ? packetMsg.value?.redPacketAmount || standalone.amount : '') ||
    '0.00'
  const n = Number(raw)
  return Number.isFinite(n) ? n.toFixed(2) : String(raw)
})

function applyDetail(rp: RedPacket) {
  const msg = packetMsg.value
  if (msg) {
    msg.redPacketId = rp.id
    msg.redPacketGreeting = rp.greeting
    msg.redPacketRemainingCount = rp.remainingCount
    msg.redPacketTotalCount = rp.totalCount
    msg.redPacketStatus = rp.status
    msg.redPacketReceived = rp.received
    msg.redPacketAmount = String(rp.totalAmount ?? '')
    msg.redPacketType = rp.type
    if (rp.receivedAmount != null) {
      msg.redPacketReceivedAmount = String(rp.receivedAmount)
      msg.redPacketOpened = true
      animPhase.value = 'revealed'
    }
  } else {
    standalone.redPacketId = rp.id
    standalone.greeting = rp.greeting || ''
    standalone.remainingCount = rp.remainingCount
    standalone.totalCount = rp.totalCount
    standalone.status = rp.status
    standalone.received = rp.received
    standalone.amount = String(rp.totalAmount ?? '')
    standalone.type = rp.type
    if (rp.receivedAmount != null) {
      standalone.receivedAmount = String(rp.receivedAmount)
      standalone.opened = true
      standalone.received = true
      animPhase.value = 'revealed'
    }
  }
  detailType.value = rp.type
  if (rp.isSelf != null) detailIsSelf.value = rp.isSelf
  redPacketRecords.value = rp.records ?? []
}

async function syncRedPacketDetail() {
  const msg = packetMsg.value
  const redPacketId =
    msg?.redPacketId || msg?.fileUrl || redPacketReceivePacketId.value || ''
  if (!redPacketId) return
  try {
    const res = await redPacketApi.getRedPacketDetail(redPacketId)
    if (res.code === 200 && res.data) applyDetail(res.data)
  } catch {
    /* keep embedded fields */
  }
}

watch(redPacketReceiveOpen, open => {
  if (open) {
    redPacketRecords.value = []
    detailIsSelf.value = null
    detailType.value = null
    animPhase.value = 'idle'
    opening.value = false
    Object.assign(standalone, {
      redPacketId: redPacketReceivePacketId.value || '',
      greeting: '',
      remainingCount: 0,
      totalCount: 0,
      status: 'active',
      received: false,
      receivedAmount: '',
      amount: '',
      type: 'normal',
      opened: false
    })
    void syncRedPacketDetail()
  }
})

function close() {
  if (opening.value) return
  closeRedPacketReceive()
}

async function openPacket() {
  const msg = packetMsg.value
  const redPacketId =
    msg?.redPacketId ||
    msg?.fileUrl ||
    standalone.redPacketId ||
    redPacketReceivePacketId.value
  if (!redPacketId) {
    message.warning(t('modals.rpInfoMissing'))
    return
  }
  if (isSelfPacket.value) {
    message.info(t('modals.rpOwnPacket'))
    return
  }
  if (msg?.redPacketReceived || msg?.redPacketOpened || standalone.received || standalone.opened) {
    message.info(t('modals.rpAlreadyReceived'))
    return
  }
  if (packetStatus.value === 'finished') {
    message.warning(t('modals.rpFinished'))
    return
  }
  if (packetStatus.value === 'expired') {
    message.warning(t('modals.rpExpired'))
    return
  }
  if (opening.value) return

  opening.value = true
  animPhase.value = 'spinning'
  try {
    const [res] = await Promise.all([
      redPacketApi.receiveRedPacket(redPacketId),
      new Promise<void>(r => setTimeout(r, 720))
    ])
    if (res.code === 200 && res.data) {
      const rp = res.data
      if (msg) {
        msg.redPacketOpened = true
        msg.redPacketReceived = true
        msg.redPacketReceivedAmount = String(rp.receivedAmount ?? '')
        msg.redPacketRemainingCount = rp.remainingCount
        msg.redPacketStatus = rp.status as 'active' | 'finished' | 'expired'
        msg.redPacketType = rp.type
      } else {
        standalone.opened = true
        standalone.received = true
        standalone.receivedAmount = String(rp.receivedAmount ?? '')
        standalone.remainingCount = rp.remainingCount
        standalone.status = rp.status
        standalone.type = rp.type
      }
      redPacketRecords.value = rp.records ?? []
      await nextTick()
      animPhase.value = 'revealed'
    } else {
      animPhase.value = 'idle'
      message.warning(res.message || t('modals.rpReceiveFail'))
    }
  } catch (e) {
    animPhase.value = 'idle'
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('modals.rpReceiveFail'))
  } finally {
    opening.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="redPacketReceiveOpen && viewReady" class="modal-root" @click.self="close">
      <div
        class="packet-card"
        :class="{ lucky: isLucky, spinning: animPhase === 'spinning', revealed: animPhase === 'revealed' }"
        @click.stop
      >
        <div class="packet-cover">
          <span class="type-badge" :class="{ lucky: isLucky }">{{ typeLabel }}</span>
          <p class="from">{{ isSelfPacket ? t('modals.rpFromSelf') : t('modals.rpReceived') }}</p>
          <p class="greeting">{{ greeting }}</p>

          <div v-if="animPhase === 'spinning'" class="open-spinner" aria-hidden="true">
            <span class="open-circle">{{ t('modals.rpOpen') }}</span>
          </div>

          <p
            v-else-if="opened && !isSelfPacket"
            class="amount"
            :class="{ pop: animPhase === 'revealed' }"
          >
            ¥{{ displayAmount }}
          </p>
          <p v-else-if="isSelfPacket" class="amount">¥{{ displayAmount }}</p>
          <p v-else-if="packetStatus === 'finished'" class="hint">{{ t('modals.rpFinished') }}</p>
          <p v-else-if="packetStatus === 'expired'" class="hint">{{ t('modals.rpExpired') }}</p>
          <p v-else-if="!isSelfPacket" class="hint">{{ t('modals.rpTapOpen') }}</p>
          <p v-else class="hint">{{ t('modals.rpWaitPeer') }}</p>

          <p class="status">{{ t('modals.rpStatusRemain', { status: statusText, n: remaining }) }}</p>
        </div>

        <div v-if="redPacketRecords.length > 0 && (isSelfPacket || opened)" class="record-list">
          <div v-for="record in redPacketRecords" :key="record.id" class="record-item">
            <span class="record-name">{{ record.nickname || t('modals.rpUserFallback') }}</span>
            <span class="record-amount">¥{{ Number(record.amount).toFixed(2) }}</span>
            <span v-if="record.isLucky" class="lucky-tag">{{ t('modals.rpLuckyBest') }}</span>
          </div>
        </div>

        <div class="packet-foot">
          <button
            v-if="!opened && !isSelfPacket && packetStatus === 'active'"
            type="button"
            class="open-btn"
            :class="{ lucky: isLucky, disabled: opening }"
            :disabled="opening"
            @click="openPacket"
          >
            {{ opening ? t('modals.rpOpening') : t('modals.rpOpen') }}
          </button>
          <n-button v-else @click="close">{{ t('modals.close') }}</n-button>
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
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  animation: fade-in 0.2s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.packet-card {
  width: min(300px, 90vw);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
  background: var(--lx-bg-card, #fff);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  animation: card-in 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  transform-origin: center center;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: scale(0.86) translateY(12px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.packet-card.spinning {
  animation: card-shake 0.7s ease;
}

@keyframes card-shake {
  0%,
  100% {
    transform: rotate(0deg) scale(1);
  }
  20% {
    transform: rotate(-3deg) scale(1.02);
  }
  40% {
    transform: rotate(3deg) scale(1.02);
  }
  60% {
    transform: rotate(-2deg) scale(1.01);
  }
  80% {
    transform: rotate(2deg) scale(1.01);
  }
}

.packet-cover {
  position: relative;
  background: linear-gradient(180deg, #e84c3d 0%, #c0392b 100%);
  color: #fff;
  text-align: center;
  padding: 40px 24px 28px;
  flex-shrink: 0;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.packet-card.lucky .packet-cover {
  background: linear-gradient(160deg, #f5b041 0%, #e74c3c 48%, #c0392b 100%);
}

.type-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  letter-spacing: 0.04em;
}

.type-badge.lucky {
  background: rgba(255, 215, 0, 0.4);
  color: #fff8e7;
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
  margin: 18px 0 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.amount.pop {
  animation: amount-pop 0.55s cubic-bezier(0.22, 1.4, 0.36, 1);
}

@keyframes amount-pop {
  0% {
    opacity: 0;
    transform: scale(0.4) translateY(16px);
  }
  60% {
    opacity: 1;
    transform: scale(1.12) translateY(0);
  }
  100% {
    transform: scale(1);
  }
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

.open-spinner {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.open-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
  animation: spin-open 0.7s linear infinite;
  box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.12);
}

@keyframes spin-open {
  from {
    transform: rotateY(0deg);
  }
  to {
    transform: rotateY(360deg);
  }
}

.record-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
  background: var(--lx-bg-base, #f5f5f5);
  max-height: 200px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  font-size: 13px;
}

.record-item:last-child {
  border-bottom: none;
}

.record-name {
  flex: 1;
  color: var(--lx-text-body, #333);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-amount {
  font-weight: 600;
  color: var(--lx-danger, #e74c3c);
}

.lucky-tag {
  background: #f39c12;
  color: #fff;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
}

.packet-foot {
  padding: 16px;
  display: flex;
  justify-content: center;
  background: var(--lx-bg-card, #fff);
  flex-shrink: 0;
}

.open-btn {
  width: 72px;
  height: 72px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(145deg, #ffd76a, #f0b429);
  color: #8b4513;
  font-size: 28px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(240, 180, 41, 0.45);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.open-btn.lucky {
  background: linear-gradient(145deg, #ffe566, #f39c12);
}

.open-btn:hover:not(.disabled) {
  transform: scale(1.06);
}

.open-btn:active:not(.disabled) {
  transform: scale(0.96);
}

.open-btn.disabled {
  opacity: 0.75;
  cursor: wait;
}
</style>
