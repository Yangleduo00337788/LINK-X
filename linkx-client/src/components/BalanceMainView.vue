<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 余额主视图 — 全宽展示可用余额、汇总、充值与流水。
 */
import { computed, ref, onMounted, type Component } from 'vue'
import { NIcon, NInput, useMessage } from 'naive-ui'
import { LxButton, LxIconButton } from './ui'
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
      <LxIconButton
        :class="{ spinning: refreshing }"
        :disabled="balanceLoading || balanceLogsLoading"
        :title="t('balance.refresh')"
        @click="refreshAll"
      >
        <n-icon :component="RefreshOutline" :size="18" />
      </LxIconButton>
    </header>

    <div class="page-body">
      <div v-if="balanceLoading && !balance" class="state-tip">{{ t('common.loading') }}</div>
      <div v-else-if="loadError && !balance" class="state-tip">
        <div class="state-icon">
          <n-icon :component="WalletOutline" :size="28" />
        </div>
        <p>{{ t('balance.loadFail') }}</p>
        <LxButton variant="sm-primary" @click="refreshAll">{{ t('balance.refresh') }}</LxButton>
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
              :class="{ 'is-active': rechargeAmount === String(amt) }"
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
            <LxButton
              variant="toolbar-primary"
              class="recharge-btn"
              :disabled="rechargeLoading"
              @click="submitRecharge"
            >
              {{ t('balance.rechargeBtn') }}
            </LxButton>
          </div>
        </section>

        <!-- 流水 -->
        <section class="panel-card logs-card">
          <div class="panel-head">
            <span class="panel-title">{{ t('balance.balanceLogs') }}</span>
            <LxButton
              variant="link-refresh"
              :disabled="balanceLogsLoading"
              @click="fetchBalanceLogs"
            >
              <n-icon :component="RefreshOutline" :size="14" />
              {{ t('balance.refresh') }}
            </LxButton>
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
  padding: 0 var(--lx-space-4xl);
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
  gap: var(--lx-space-md);
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-text);
}

.title-badge {
  width: 30px;
  height: 30px;
  border-radius: var(--lx-radius);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-text-on-accent);
  background: var(--lx-gradient-accent, linear-gradient(135deg, var(--lx-accent-light, var(--lx-accent-light)), var(--lx-accent)));
  box-shadow: 0 4px 12px color-mix(in srgb, var(--lx-accent) 28%, transparent);
}

.page-body {
  flex: 1;
  overflow: auto;
  padding: var(--lx-space-3xl) var(--lx-space-5xl-minus) var(--lx-space-6xl-minus);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-xl);
  width: 100%;
  box-sizing: border-box;
}

.state-tip {
  padding: var(--lx-space-block-xl) var(--lx-space-2xl);
  text-align: center;
  color: var(--lx-text-muted);
  font-size: var(--lx-font);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space-lg);
}

.state-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--lx-radius-2xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-accent);
  background: var(--lx-accent-soft);
  margin-bottom: var(--lx-space-xs);
}

/* —— 主余额卡 —— */
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--lx-radius-2xl);
  padding: var(--lx-space-3xl-plus) var(--lx-space-4xl) var(--lx-space-4xl);
  color: var(--lx-text-on-accent);
  background: var(--lx-gradient-accent, linear-gradient(135deg, var(--lx-accent-light, var(--lx-accent-hover)), var(--lx-accent)));
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
  gap: var(--lx-space-lg);
  margin-bottom: var(--lx-space-xl);
}

.hero-label {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-sm);
  font-size: var(--lx-font-md);
  font-weight: 500;
  opacity: 0.92;
}

.frozen-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space-xs);
  padding: var(--lx-space-xs) var(--lx-space-md);
  border-radius: var(--lx-radius-pill);
  font-size: var(--lx-font-xs);
  background: rgba(0, 0, 0, 0.14);
  backdrop-filter: blur(4px);
}

.hero-amount {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: var(--lx-space-sm);
  line-height: var(--lx-leading-none);
}

.currency {
  font-size: var(--lx-font-5xl);
  font-weight: 600;
  opacity: 0.9;
}

.digits {
  font-size: var(--lx-font-7xl);
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

/* —— 汇总 —— */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--lx-space-md);
}

.summary-item {
  padding: var(--lx-space-xl) var(--lx-space-lg);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  box-shadow: var(--lx-shadow-soft);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
  min-width: 0;
}

.summary-item[data-key='frozen'] .summary-value {
  color: var(--lx-warning, var(--lx-warning));
}

.summary-label {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-value {
  font-size: var(--lx-font-lg);
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
  border-radius: var(--lx-radius-card);
  padding: var(--lx-space-xs) 0 var(--lx-space-xl);
  box-shadow: var(--lx-shadow-soft);
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-xl) var(--lx-space-2xl) var(--lx-space-md);
}

.panel-title {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}

.quick-amounts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space);
  padding: 0 var(--lx-space-2xl) var(--lx-space-lg);
}

.quick-chip {
  min-width: 64px;
  height: 32px;
  padding: 0 var(--lx-space-lg);
  border-radius: var(--lx-radius-sm);
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  color: var(--lx-text-body);
  font-size: var(--lx-font-md);
  font-weight: 500;
  cursor: pointer;
  transition: border-color var(--lx-duration), background var(--lx-duration), color var(--lx-duration);
}

.quick-chip:hover {
  border-color: color-mix(in srgb, var(--lx-accent) 45%, var(--lx-border-light));
  color: var(--lx-accent);
}

.quick-chip.is-active {
  border-color: var(--lx-accent);
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 600;
}

.recharge-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: 0 var(--lx-space-2xl);
}

.recharge-input {
  flex: 1;
  min-width: 0;
}

.input-prefix {
  color: var(--lx-text-muted);
  font-weight: 600;
  margin-right: var(--lx-space-2xs);
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
  padding-bottom: var(--lx-space-sm);
}

.logs-empty {
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
  padding: var(--lx-space-5xl-minus) var(--lx-space-2xl);
  text-align: center;
}

.logs-list {
  list-style: none;
  margin: 0;
  padding: 0 var(--lx-space-md) var(--lx-space);
  flex: 1;
  overflow-y: auto;
}

.log-row {
  display: flex;
  align-items: center;
  gap: var(--lx-space-lg);
  padding: var(--lx-space-md-plus) var(--lx-space);
  border-radius: var(--lx-radius-xl);
  transition: background var(--lx-duration-fast);
}

.log-row:hover {
  background: var(--lx-bg-hover);
}

.log-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--lx-radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.log-icon.income {
  color: var(--lx-success, var(--lx-success));
  background: color-mix(in srgb, var(--lx-success, var(--lx-success)) 14%, transparent);
}

.log-icon.expense {
  color: var(--lx-text-secondary);
  background: var(--lx-bg-panel-deep, var(--lx-bg-panel));
}

.log-main {
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-2xs);
  min-width: 0;
  flex: 1;
}

.log-type {
  font-size: var(--lx-font-md);
  font-weight: 560;
  color: var(--lx-text-body);
}

.log-remark {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--lx-space-2xs);
  flex-shrink: 0;
}

.log-amount {
  font-size: var(--lx-font);
  font-weight: 650;
  color: var(--lx-text-body);
  font-variant-numeric: tabular-nums;
}

.log-amount.income {
  color: var(--lx-success, var(--lx-success));
}

.log-time {
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
}

@media (max-width: 560px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .digits {
    font-size: var(--lx-font-8xl);
  }

  .page-body {
    padding: var(--lx-space-2xl) var(--lx-space-2xl) var(--lx-space-5xl-minus);
  }
}
</style>
