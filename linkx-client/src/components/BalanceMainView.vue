<script setup lang="ts">
/**
 * 余额主视图 — 全宽展示可用余额、汇总、充值与流水。
 */
import { computed, ref, onMounted, type Component } from 'vue'
import { NButton, NIcon, NInput, useMessage } from 'naive-ui'
import {
  WalletOutline,
  RefreshOutline,
  LockClosedOutline,
  ArrowUpCircleOutline,
  ArrowDownCircleOutline,
  CashOutline,
  GiftOutline,
  ReturnDownBackOutline,
  EllipseOutline
} from '@vicons/ionicons5'
import * as balanceApi from '../api/balance'
import { useI18n } from '../i18n'

const message = useMessage()
const { t } = useI18n()

const balance = ref<balanceApi.BalanceInfo | null>(null)
const balanceLoading = ref(false)
const balanceLogs = ref<balanceApi.BalanceLog[]>([])
const balanceLogsLoading = ref(false)
const rechargeAmount = ref('10')
const rechargeLoading = ref(false)
const loadError = ref(false)
const refreshing = ref(false)

const QUICK_AMOUNTS = [10, 50, 100, 200] as const

const summaryItems = computed(() => {
  if (!balance.value) return []
  const b = balance.value
  return [
    { key: 'total', label: t('balance.totalBalance'), value: b.balance },
    { key: 'frozen', label: t('balance.frozen'), value: b.frozen },
    { key: 'recharge', label: t('balance.totalRecharge'), value: b.totalRecharge },
    { key: 'withdraw', label: t('balance.totalWithdraw'), value: b.totalWithdraw }
  ]
})

async function fetchBalance() {
  balanceLoading.value = true
  loadError.value = false
  try {
    const res = await balanceApi.getBalance()
    if (res.code === 200 && res.data) {
      balance.value = {
        userId: String(res.data.userId),
        balance: Number(res.data.balance),
        frozen: Number(res.data.frozen),
        available: Number(res.data.available),
        totalRecharge: Number(res.data.totalRecharge),
        totalWithdraw: Number(res.data.totalWithdraw)
      }
    } else {
      loadError.value = true
    }
  } catch {
    loadError.value = true
  } finally {
    balanceLoading.value = false
  }
}

async function fetchBalanceLogs() {
  balanceLogsLoading.value = true
  try {
    const res = await balanceApi.listBalanceLogs({ limit: 30 })
    if (res.code === 200 && Array.isArray(res.data)) {
      balanceLogs.value = res.data.map(row => ({
        ...row,
        id: String(row.id),
        amount: Number(row.amount),
        balanceBefore: Number(row.balanceBefore),
        balanceAfter: Number(row.balanceAfter)
      }))
    } else {
      balanceLogs.value = []
    }
  } catch {
    balanceLogs.value = []
  } finally {
    balanceLogsLoading.value = false
  }
}

function formatMoney(amount: number) {
  return Number(amount || 0).toFixed(2)
}

function balanceLogLabel(type: string): string {
  const map: Record<string, string> = {
    recharge: t('balance.logRecharge'),
    deduct: t('balance.logDeduct'),
    add: t('balance.logAdd'),
    freeze: t('balance.logSendRp'),
    send_redpacket: t('balance.logSendRp'),
    receive_redpacket: t('balance.logRecvRp'),
    refund: t('balance.logRefund'),
    unfreeze: t('balance.logRefund')
  }
  return map[type] || type || t('balance.logOther')
}

/** 支出类流水（含历史 freeze 正数）统一按支出展示 */
function isExpenseLog(log: balanceApi.BalanceLog): boolean {
  if (log.type === 'freeze' || log.type === 'send_redpacket' || log.type === 'deduct') return true
  return Number(log.amount) < 0
}

function logIcon(log: balanceApi.BalanceLog): Component {
  switch (log.type) {
    case 'recharge':
      return CashOutline
    case 'send_redpacket':
    case 'freeze':
      return GiftOutline
    case 'receive_redpacket':
    case 'add':
      return ArrowDownCircleOutline
    case 'refund':
    case 'unfreeze':
      return ReturnDownBackOutline
    case 'deduct':
      return ArrowUpCircleOutline
    default:
      return EllipseOutline
  }
}

function pickQuickAmount(amount: number) {
  rechargeAmount.value = String(amount)
}

async function submitRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!Number.isFinite(amount) || amount < 0.01 || amount > 1000) {
    message.warning(t('balance.rechargeInvalid'))
    return
  }
  rechargeLoading.value = true
  try {
    const res = await balanceApi.rechargeBalance(amount)
    if (res.code === 200 && res.data) {
      balance.value = {
        userId: String(res.data.userId),
        balance: Number(res.data.balance),
        frozen: Number(res.data.frozen),
        available: Number(res.data.available),
        totalRecharge: Number(res.data.totalRecharge),
        totalWithdraw: Number(res.data.totalWithdraw)
      }
      message.success(t('balance.rechargeOk', { amount: formatMoney(amount) }))
      await fetchBalanceLogs()
    } else {
      message.error(res.message || t('balance.rechargeFail'))
    }
  } catch (e) {
    const err = e as { message?: string }
    message.error(err.message || t('balance.rechargeFail'))
  } finally {
    rechargeLoading.value = false
  }
}

async function refreshAll() {
  refreshing.value = true
  try {
    await Promise.all([fetchBalance(), fetchBalanceLogs()])
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  void refreshAll()
})
</script>

<template>
  <div class="balance-page">
    <header class="page-head">
      <div class="page-title">
        <span class="title-badge">
          <n-icon :component="WalletOutline" :size="18" />
        </span>
        <span>{{ t('nav.balance') }}</span>
      </div>
      <button
        type="button"
        class="refresh-btn"
        :class="{ spinning: refreshing }"
        :disabled="balanceLoading || balanceLogsLoading"
        :title="t('balance.refresh')"
        @click="refreshAll"
      >
        <n-icon :component="RefreshOutline" :size="18" />
      </button>
    </header>

    <div class="page-body">
      <div v-if="balanceLoading && !balance" class="state-tip">{{ t('common.loading') }}</div>
      <div v-else-if="loadError && !balance" class="state-tip">
        <div class="state-icon">
          <n-icon :component="WalletOutline" :size="28" />
        </div>
        <p>{{ t('balance.loadFail') }}</p>
        <n-button type="primary" size="small" @click="refreshAll">{{ t('balance.refresh') }}</n-button>
      </div>

      <template v-else-if="balance">
        <!-- 可用余额主卡 -->
        <section class="hero-card">
          <div class="hero-glow" aria-hidden="true" />
          <div class="hero-top">
            <div class="hero-label">
              <n-icon :component="WalletOutline" :size="16" />
              <span>{{ t('balance.available') }}</span>
            </div>
            <div v-if="balance.frozen > 0" class="frozen-chip">
              <n-icon :component="LockClosedOutline" :size="12" />
              <span>{{ t('balance.frozen') }} ¥ {{ formatMoney(balance.frozen) }}</span>
            </div>
          </div>
          <div class="hero-amount">
            <span class="currency">¥</span>
            <span class="digits">{{ formatMoney(balance.available) }}</span>
          </div>
        </section>

        <!-- 汇总 -->
        <section class="summary-grid">
          <div v-for="item in summaryItems" :key="item.key" class="summary-item" :data-key="item.key">
            <span class="summary-label">{{ item.label }}</span>
            <span class="summary-value">¥ {{ formatMoney(item.value) }}</span>
          </div>
        </section>

        <!-- 充值 -->
        <section class="panel-card recharge-card">
          <div class="panel-head">
            <span class="panel-title">{{ t('balance.recharge') }}</span>
          </div>
          <div class="quick-amounts">
            <button
              v-for="amt in QUICK_AMOUNTS"
              :key="amt"
              type="button"
              class="quick-chip"
              :class="{ active: rechargeAmount === String(amt) }"
              @click="pickQuickAmount(amt)"
            >
              ¥{{ amt }}
            </button>
          </div>
          <div class="recharge-row">
            <n-input
              v-model:value="rechargeAmount"
              class="recharge-input"
              :placeholder="t('balance.rechargePh')"
              maxlength="8"
            >
              <template #prefix>
                <span class="input-prefix">¥</span>
              </template>
            </n-input>
            <n-button
              type="primary"
              class="recharge-btn"
              :loading="rechargeLoading"
              @click="submitRecharge"
            >
              {{ t('balance.rechargeBtn') }}
            </n-button>
          </div>
        </section>

        <!-- 流水 -->
        <section class="panel-card logs-card">
          <div class="panel-head">
            <span class="panel-title">{{ t('balance.balanceLogs') }}</span>
            <button
              type="button"
              class="logs-refresh"
              :disabled="balanceLogsLoading"
              @click="fetchBalanceLogs"
            >
              <n-icon :component="RefreshOutline" :size="14" />
              {{ t('balance.refresh') }}
            </button>
          </div>
          <div v-if="balanceLogsLoading && !balanceLogs.length" class="logs-empty">
            {{ t('common.loading') }}
          </div>
          <div v-else-if="!balanceLogs.length" class="logs-empty">
            {{ t('balance.balanceLogsEmpty') }}
          </div>
          <ul v-else class="logs-list">
            <li v-for="log in balanceLogs" :key="log.id" class="log-row">
              <div
                class="log-icon"
                :class="isExpenseLog(log) ? 'expense' : 'income'"
              >
                <n-icon :component="logIcon(log)" :size="18" />
              </div>
              <div class="log-main">
                <span class="log-type">{{ balanceLogLabel(log.type) }}</span>
                <span class="log-remark">{{ log.remark || '—' }}</span>
              </div>
              <div class="log-side">
                <span class="log-amount" :class="{ income: !isExpenseLog(log) }">
                  {{ isExpenseLog(log) ? '-' : '+' }}¥
                  {{ formatMoney(Math.abs(Number(log.amount))) }}
                </span>
                <span class="log-time">{{ log.time }}</span>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.balance-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(ellipse 80% 50% at 10% -10%, color-mix(in srgb, var(--lx-accent) 16%, transparent), transparent 55%),
    radial-gradient(ellipse 60% 40% at 100% 0%, color-mix(in srgb, var(--lx-accent-light, var(--lx-accent)) 10%, transparent), transparent 50%),
    var(--lx-bg-panel);
  overflow: hidden;
}

.page-head {
  flex-shrink: 0;
  height: 52px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--lx-border-light);
  background: color-mix(in srgb, var(--lx-bg-card) 88%, transparent);
  backdrop-filter: blur(8px);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text);
}

.title-badge {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-on-accent);
  background: var(--lx-gradient-accent, linear-gradient(135deg, var(--lx-accent-light, #6eb5ff), var(--lx-accent)));
  box-shadow: 0 4px 12px color-mix(in srgb, var(--lx-accent) 28%, transparent);
}

.refresh-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--lx-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}

.refresh-btn:hover:not(:disabled) {
  background: var(--lx-bg-hover);
  color: var(--lx-text-body);
}

.refresh-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.refresh-btn.spinning :deep(svg) {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.page-body {
  flex: 1;
  overflow: auto;
  padding: 20px 28px 36px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  box-sizing: border-box;
}

.state-tip {
  padding: 64px 16px;
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.state-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
  margin-bottom: 4px;
}

/* —— 主余额卡 —— */
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 22px 24px 24px;
  color: var(--lx-text-on-accent);
  background: var(--lx-gradient-accent, linear-gradient(135deg, var(--lx-accent-light, #39c2f6), var(--lx-accent)));
  box-shadow:
    0 10px 28px color-mix(in srgb, var(--lx-accent) 28%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.hero-glow {
  position: absolute;
  right: -40px;
  top: -50px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.28), transparent 68%);
  pointer-events: none;
}

.hero-top {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.hero-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  opacity: 0.92;
}

.frozen-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.14);
  backdrop-filter: blur(4px);
}

.hero-amount {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1;
}

.currency {
  font-size: 22px;
  font-weight: 600;
  opacity: 0.9;
}

.digits {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

/* —— 汇总 —— */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  padding: 14px 12px;
  border-radius: 12px;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  box-shadow: var(--lx-shadow-soft);
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.summary-item[data-key='frozen'] .summary-value {
  color: var(--lx-warning, #faad14);
}

.summary-label {
  font-size: 12px;
  color: var(--lx-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-value {
  font-size: 15px;
  font-weight: 650;
  color: var(--lx-text-body);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* —— 面板 —— */
.panel-card {
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 14px;
  padding: 4px 0 14px;
  box-shadow: var(--lx-shadow-soft);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px 10px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.quick-amounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 18px 12px;
}

.quick-chip {
  min-width: 64px;
  height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}

.quick-chip:hover {
  border-color: color-mix(in srgb, var(--lx-accent) 45%, var(--lx-border-light));
  color: var(--lx-accent);
}

.quick-chip.active {
  border-color: var(--lx-accent);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
}

.recharge-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
}

.recharge-input {
  flex: 1;
  min-width: 0;
}

.input-prefix {
  color: var(--lx-text-muted);
  font-weight: 600;
  margin-right: 2px;
}

.recharge-btn {
  flex-shrink: 0;
  min-width: 96px;
}

/* —— 流水 —— */
.logs-card {
  flex: 1;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  padding-bottom: 6px;
}

.logs-refresh {
  border: none;
  background: transparent;
  color: var(--lx-accent);
  cursor: pointer;
  font-size: 12px;
  padding: 4px 6px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.logs-refresh:hover:not(:disabled) {
  background: var(--lx-accent-soft);
}

.logs-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.logs-empty {
  font-size: 13px;
  color: var(--lx-text-muted);
  padding: 28px 18px;
  text-align: center;
}

.logs-list {
  list-style: none;
  margin: 0;
  padding: 0 10px 8px;
  flex: 1;
  overflow-y: auto;
}

.log-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 8px;
  border-radius: 10px;
  transition: background 0.12s;
}

.log-row:hover {
  background: var(--lx-bg-hover);
}

.log-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.log-icon.income {
  color: var(--lx-success, #18a058);
  background: color-mix(in srgb, var(--lx-success, #18a058) 14%, transparent);
}

.log-icon.expense {
  color: var(--lx-text-secondary);
  background: var(--lx-bg-panel-deep, var(--lx-bg-panel));
}

.log-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.log-type {
  font-size: 13px;
  font-weight: 560;
  color: var(--lx-text-body);
}

.log-remark {
  font-size: 12px;
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  flex-shrink: 0;
}

.log-amount {
  font-size: 14px;
  font-weight: 650;
  color: var(--lx-text-body);
  font-variant-numeric: tabular-nums;
}

.log-amount.income {
  color: var(--lx-success, #18a058);
}

.log-time {
  font-size: 11px;
  color: var(--lx-text-muted);
}

@media (max-width: 560px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .digits {
    font-size: 34px;
  }

  .page-body {
    padding: 16px 16px 28px;
  }
}
</style>
