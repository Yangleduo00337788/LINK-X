<!-- 作者：yangleduo -->
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
import { useMessage } from 'naive-ui'
import { LxButton } from '../ui'
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
const typeLabel = computed(() => (isLucky.value ? t('extra.luckyPacket') : t('extra.normalPacket')))
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
  () => packetMsg.value?.redPacketGreeting || standalone.greeting || t('extra.greetingFallback')
)
const statusText = computed(() => {
  const s = packetStatus.value
  if (s === 'finished') return t('extra.rpStatusFinished')
  if (s === 'expired') return t('extra.rpStatusExpired')
  if (isSelfPacket.value) return t('extra.rpStatusSent')
  if (opened.value) return t('extra.rpStatusClaimed')
  return t('extra.rpStatusPending')
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
    message.warning(t('extra.rpInfoMissing'))
    return
  }
  if (isSelfPacket.value) {
    message.info(t('extra.rpOwnPacket'))
    return
  }
  if (msg?.redPacketReceived || msg?.redPacketOpened || standalone.received || standalone.opened) {
    message.info(t('extra.rpAlreadyReceived'))
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
      message.warning(res.message || t('extra.rpReceiveFail'))
    }
  } catch (e) {
    animPhase.value = 'idle'
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('extra.rpReceiveFail'))
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
        :class="{ 'is-lucky': isLucky, spinning: animPhase === 'spinning', revealed: animPhase === 'revealed' }"
        @click.stop
      >
        <div class="packet-cover">
          <span class="type-badge" :class="{ 'is-lucky': isLucky }">{{ typeLabel }}</span>
          <p class="from">{{ isSelfPacket ? t('extra.rpFromSelf') : t('modals.rpReceived') }}</p>
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
          <p v-else-if="!isSelfPacket" class="hint">{{ t('extra.rpTapOpen') }}</p>
          <p v-else class="hint">{{ t('extra.rpWaitPeer') }}</p>

          <p class="status">{{ t('extra.rpStatusRemain', { status: statusText, n: remaining }) }}</p>
        </div>

        <div v-if="redPacketRecords.length > 0 && (isSelfPacket || opened)" class="record-list">
          <div v-for="record in redPacketRecords" :key="record.id" class="record-item">
            <span class="record-name">{{ record.nickname || t('extra.rpUserFallback') }}</span>
            <span class="record-amount">¥{{ Number(record.amount).toFixed(2) }}</span>
            <span v-if="record.isLucky" class="lucky-tag">{{ t('extra.rpLuckyBest') }}</span>
          </div>
        </div>

        <div class="packet-foot">
          <LxButton
            v-if="!opened && !isSelfPacket && packetStatus === 'active'"
            variant="redpacket-open"
            :class="{ 'is-lucky': isLucky, 'is-disabled': opening }"
            :disabled="opening"
            @click="openPacket"
          >
            {{ opening ? t('extra.rpOpening') : t('modals.rpOpen') }}
          </LxButton>
          <LxButton v-else variant="modal" @click="close">{{ t('modals.close') }}</LxButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog-packet-recv);
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-3xl);
  animation: fade-in var(--lx-duration-md) ease;
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
  border-radius: var(--lx-radius-2xl);
  overflow: hidden;
  background: var(--lx-bg-card);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  animation: card-in var(--lx-duration-slow) cubic-bezier(0.22, 1, 0.36, 1);
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
  animation: card-shake var(--lx-duration-emphasis) ease;
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
  background: linear-gradient(180deg, var(--lx-danger) 0%, var(--lx-danger-deep) 100%);
  color: var(--lx-text-on-accent);
  text-align: center;
  padding: var(--lx-space-section) var(--lx-space-4xl) var(--lx-space-5xl-minus);
  flex-shrink: 0;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.packet-card.lucky .packet-cover {
  background: var(--lx-packet-main-gradient);
}

.type-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: var(--lx-font-xs);
  font-weight: 600;
  padding: var(--lx-space-xs) var(--lx-space);
  border-radius: var(--lx-radius-pill);
  background: rgba(255, 255, 255, 0.22);
  letter-spacing: 0.04em;
}

.type-badge.lucky {
  background: rgba(255, 215, 0, 0.4);
  color: var(--lx-packet-text-cream);
}

.from {
  margin: 0 0 var(--lx-space);
  font-size: var(--lx-font-md);
  opacity: 0.9;
}

.greeting {
  margin: 0;
  font-size: var(--lx-font-4xl);
  font-weight: 600;
}

.amount {
  margin: var(--lx-space-2xl) 0 0;
  font-size: var(--lx-font-7xl);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.amount.pop {
  animation: amount-pop var(--lx-duration-slowest) cubic-bezier(0.22, 1.4, 0.36, 1);
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
  margin: var(--lx-space-2xl) 0 0;
  font-size: var(--lx-font-md);
  opacity: 0.85;
}

.status {
  margin: var(--lx-space-lg) 0 0;
  font-size: var(--lx-font-sm);
  opacity: 0.85;
}

.open-spinner {
  margin-top: var(--lx-space-3xl);
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
  font-size: var(--lx-font-5xl);
  font-weight: 700;
  animation: spin-open var(--lx-duration-emphasis) linear infinite;
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
  padding: var(--lx-space) var(--lx-space-2xl);
  background: var(--lx-bg-panel);
  max-height: 200px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  padding: var(--lx-space) 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  font-size: var(--lx-font-md);
}

.record-item:last-child {
  border-bottom: none;
}

.record-name {
  flex: 1;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-amount {
  font-weight: 600;
  color: var(--lx-danger, var(--lx-danger));
}

.lucky-tag {
  background: var(--lx-packet-gold-deep);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-2xs);
  padding: var(--lx-space-hair) var(--lx-space-xs);
  border-radius: var(--lx-radius-2xs);
  flex-shrink: 0;
}

.packet-foot {
  padding: var(--lx-space-2xl);
  display: flex;
  justify-content: center;
  background: var(--lx-bg-card);
  flex-shrink: 0;
}
</style>
