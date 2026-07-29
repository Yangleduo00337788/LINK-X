<script setup lang="ts">
/**
 * 会话红包记录：列出当前会话发出的红包，可查看详情并领取。
 */
import { ref, watch, computed } from 'vue'
import { NModal, NSpin, NEmpty, NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import * as redPacketApi from '../../api/redPacket'
import type { RedPacket } from '../../api/redPacket'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { redPacketHistoryOpen } = storeToRefs(chatModalsStore)
const { closeRedPacketHistory, openRedPacketReceive } = chatModalsStore
const { currentSessionId, currentMessages } = storeToRefs(appStore)

const loading = ref(false)
const items = ref<RedPacket[]>([])
const receivingId = ref<string | null>(null)

const title = computed(() => t('modals.redPacketHistory'))

async function loadList() {
  const cid = currentSessionId.value
  if (!cid) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await redPacketApi.listRedPackets(cid)
    if (res.code === 200 && Array.isArray(res.data)) {
      items.value = res.data
    } else {
      items.value = []
      if (res.message) message.warning(res.message)
    }
  } catch (e) {
    items.value = []
    const err = e as { message?: string }
    message.error(err.message || t('common.fail'))
  } finally {
    loading.value = false
  }
}

watch(redPacketHistoryOpen, open => {
  if (open) void loadList()
})

function statusLabel(p: RedPacket): string {
  if (p.status === 'finished') return t('modals.rpFinished')
  if (p.status === 'expired') return t('modals.rpExpired')
  if (p.received) return t('modals.rpReceived')
  return t('modals.rpActive')
}

function formatAmount(n: number): string {
  return Number.isFinite(n) ? n.toFixed(2) : String(n)
}

function openDetail(p: RedPacket) {
  const msg = currentMessages.value.find(
    m => m.redPacketId === p.id || m.fileUrl === p.id
  )
  if (msg) {
    openRedPacketReceive(msg.id)
    closeRedPacketHistory()
    return
  }
  // 消息不在当前列表时，用 packetId 打开领取弹窗
  openRedPacketReceive('', p.id)
  closeRedPacketHistory()
}

async function quickReceive(p: RedPacket) {
  if (p.isSelf || p.received || p.status !== 'active') {
    openDetail(p)
    return
  }
  receivingId.value = p.id
  try {
    const res = await redPacketApi.receiveRedPacket(p.id)
    if (res.code === 200 && res.data) {
      message.success(t('modals.rpReceiveOk', { amount: formatAmount(res.data.receivedAmount ?? 0) }))
      await loadList()
    } else {
      message.warning(res.message || t('common.fail'))
    }
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('common.fail'))
  } finally {
    receivingId.value = null
  }
}
</script>

<template>
  <n-modal
    :show="redPacketHistoryOpen"
    preset="card"
    :title="title"
    style="width: min(440px, 92vw)"
    :mask-closable="true"
    @update:show="(v: boolean) => !v && closeRedPacketHistory()"
  >
    <n-spin :show="loading">
      <n-empty v-if="!loading && !items.length" :description="t('modals.rpHistoryEmpty')" />
      <ul v-else class="rp-list">
        <li v-for="p in items" :key="p.id" class="rp-item">
          <button type="button" class="rp-main" @click="openDetail(p)">
            <div class="rp-top">
              <span class="rp-greet">{{ p.greeting || t('modals.rpDefaultGreet') }}</span>
              <span class="rp-status" :data-status="p.status">{{ statusLabel(p) }}</span>
            </div>
            <div class="rp-meta">
              <span>{{ p.senderNickname || '—' }}</span>
              <span>
                {{ formatAmount(p.totalAmount) }} / {{ p.totalCount - p.remainingCount }}/{{ p.totalCount }}
              </span>
              <span class="rp-time">{{ p.time }}</span>
            </div>
          </button>
          <n-button
            v-if="!p.isSelf && !p.received && p.status === 'active'"
            size="tiny"
            type="warning"
            :loading="receivingId === p.id"
            @click="quickReceive(p)"
          >
            {{ t('modals.rpOpen') }}
          </n-button>
        </li>
      </ul>
    </n-spin>
  </n-modal>
</template>

<style scoped>
.rp-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: min(60vh, 480px);
  overflow-y: auto;
}
.rp-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--lx-border-light);
}
.rp-main {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
  color: inherit;
}
.rp-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.rp-greet {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rp-status {
  font-size: 12px;
  color: var(--lx-text-muted);
  flex-shrink: 0;
}
.rp-status[data-status='active'] {
  color: var(--lx-warning, #d48806);
}
.rp-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
}
.rp-time {
  margin-left: auto;
}
</style>
