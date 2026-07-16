<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { NButton, NAvatar, NIcon, NSwitch, NModal, NInput, NProgress, useMessage, useDialog } from 'naive-ui'
import { ShieldCheckmarkOutline, DesktopOutline, WalletOutline, MailOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useAppSettingsStore } from '../../stores/appSettings'
import * as accountApi from '../../api/account'
import * as balanceApi from '../../api/balance'
import * as feedbackApi from '../../api/feedback'
import { generateDefaultAvatar } from '../../utils/defaultAvatar'

const message = useMessage()
const dialog = useDialog()
const appStore = useAppStore()
const appSettingsStore = useAppSettingsStore()

const { userProfile, savedLogin } = storeToRefs(appStore)

const displayUsername = computed(
  () => savedLogin.value.username || userProfile.value.username || '—'
)
const defaultAvatar = computed(() =>
  generateDefaultAvatar(userProfile.value.nickname || '我')
)

const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline
} = storeToRefs(appSettingsStore)

// 余额相关
const balance = ref<balanceApi.BalanceInfo | null>(null)
const balanceLoading = ref(false)

async function fetchBalance() {
  balanceLoading.value = true
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
    }
  } catch {
    // 余额接口可能未开通，静默失败
  } finally {
    balanceLoading.value = false
  }
}

function formatMoney(amount: number) {
  return amount.toFixed(2)
}

// 反馈历史相关
const showFeedbackHistory = ref(false)
const feedbackList = ref<feedbackApi.FeedbackVO[]>([])
const feedbackLoading = ref(false)

async function openFeedbackHistory() {
  showFeedbackHistory.value = true
  feedbackLoading.value = true
  try {
    const res = await feedbackApi.listFeedback()
    if (res.code === 200 && res.data) {
      feedbackList.value = res.data
    }
  } catch {
    message.error('获取反馈历史失败')
  } finally {
    feedbackLoading.value = false
  }
}

function getFeedbackStatusType(status: string) {
  if (status === 'resolved') return 'success'
  if (status === 'processing') return 'warning'
  return 'default'
}

function getFeedbackStatusText(status: string) {
  if (status === 'resolved') return '已处理'
  if (status === 'processing') return '处理中'
  return '待处理'
}

function getFeedbackTypeText(type: string) {
  if (type === 'bug') return 'Bug'
  if (type === 'suggestion') return '建议'
  return '其他'
}

// 修改密码弹窗
const showPasswordModal = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordLoading = ref(false)

async function handleChangePassword() {
  if (!passwordForm.value.oldPassword) {
    message.warning('请输入原密码')
    return
  }
  if (!passwordForm.value.newPassword) {
    message.warning('请输入新密码')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    message.warning('新密码长度不能少于6位')
    return
  }

  passwordLoading.value = true
  try {
    const res = await accountApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    if (res.code === 200) {
      message.success('密码修改成功')
      showPasswordModal.value = false
      passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    } else {
      message.error(res.message || '修改密码失败')
    }
  } catch (e) {
    message.error('修改密码失败，请检查原密码是否正确')
  } finally {
    passwordLoading.value = false
  }
}

function openPasswordModal() {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  showPasswordModal.value = true
}

// 设备管理弹窗
const showDeviceModal = ref(false)
const devices = ref<accountApi.DeviceInfo[]>([])
const deviceLoading = ref(false)

async function openDeviceModal() {
  showDeviceModal.value = true
  deviceLoading.value = true
  try {
    const res = await accountApi.listDevices()
    if (res.code === 200 && res.data) {
      devices.value = res.data
    }
  } catch (e) {
    message.error('获取设备列表失败')
  } finally {
    deviceLoading.value = false
  }
}

async function handleLogoutDevice(deviceId: string) {
  dialog.warning({
    title: '确认下线',
    content: '确定要强制该设备下线吗？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await accountApi.logoutDevice(deviceId)
        if (res.code === 200) {
          message.success('设备已下线')
          devices.value = devices.value.filter(d => d.id !== deviceId)
        }
      } catch {
        message.error('操作失败')
      }
    }
  })
}

// 组件挂载时获取余额
onMounted(() => {
  void fetchBalance()
})
</script>

<template>
  <div class="settings-scroll">
    <!-- 用户资料卡片 -->
    <section class="profile-card">
      <n-avatar
        :size="72"
        :src="userProfile.avatar || defaultAvatar"
        class="profile-avatar"
      />
      <div class="profile-meta">
        <div class="profile-name">{{ userProfile.nickname || '未设置昵称' }}</div>
        <div class="profile-id">LinkX ID · {{ displayUsername }}</div>
        <div class="profile-badge">已登录</div>
      </div>
    </section>

    <!-- 余额展示 -->
    <section v-if="balance" class="group-card">
      <div class="group-head">
        <n-icon :component="WalletOutline" :size="18" class="group-ico" />
        <span>我的余额</span>
      </div>
      <div class="balance-display">
        <div class="balance-main">
          <span class="balance-label">可用余额</span>
          <span class="balance-amount">¥ {{ formatMoney(balance.available) }}</span>
        </div>
        <div class="balance-details">
          <div class="balance-item">
            <span class="balance-item-label">总余额</span>
            <span class="balance-item-value">¥ {{ formatMoney(balance.balance) }}</span>
          </div>
          <div class="balance-item">
            <span class="balance-item-label">冻结中</span>
            <span class="balance-item-value">¥ {{ formatMoney(balance.frozen) }}</span>
          </div>
          <div class="balance-item">
            <span class="balance-item-label">累计充值</span>
            <span class="balance-item-value">¥ {{ formatMoney(balance.totalRecharge) }}</span>
          </div>
          <div class="balance-item">
            <span class="balance-item-label">累计提现</span>
            <span class="balance-item-value">¥ {{ formatMoney(balance.totalWithdraw) }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 安全设置分组 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="ShieldCheckmarkOutline" :size="18" class="group-ico" />
        <span>安全设置</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">登录密码</span>
          <span class="setting-desc">定期更换密码保护账号安全</span>
        </div>
        <n-button size="small" secondary @click="openPasswordModal">修改</n-button>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">设备管理</span>
          <span class="setting-desc">查看已登录的设备与会话</span>
        </div>
        <n-button size="small" secondary @click="openDeviceModal">
          <template #icon>
            <n-icon :component="DesktopOutline" :size="14" />
          </template>
          查看
        </n-button>
      </div>
    </section>

    <!-- 隐私设置分组 -->
    <section class="group-card">
      <div class="group-head">
        <span>隐私</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">加好友需验证</span>
        </div>
        <n-switch v-model:value="privacyVerifyFriend" size="small" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">允许陌生人会话</span>
        </div>
        <n-switch v-model:value="privacyAllowStranger" size="small" />
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">在线状态可见</span>
        </div>
        <n-switch v-model:value="privacyShowOnline" size="small" />
      </div>
    </section>

    <!-- 反馈历史 -->
    <section class="group-card">
      <div class="group-head">
        <n-icon :component="MailOutline" :size="18" class="group-ico" />
        <span>问题反馈</span>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">反馈记录</span>
          <span class="setting-desc">查看已提交的问题反馈及处理状态</span>
        </div>
        <n-button size="small" secondary @click="openFeedbackHistory">查看</n-button>
      </div>
    </section>

    <!-- 修改密码弹窗 -->
    <n-modal v-model:show="showPasswordModal" preset="card" title="修改密码" style="max-width: 400px">
      <div class="password-form">
        <div class="form-item">
          <label>原密码</label>
          <n-input
            v-model:value="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password-on="click"
          />
        </div>
        <div class="form-item">
          <label>新密码</label>
          <n-input
            v-model:value="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
            show-password-on="click"
          />
        </div>
        <div class="form-item">
          <label>确认新密码</label>
          <n-input
            v-model:value="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password-on="click"
          />
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showPasswordModal = false">取消</n-button>
          <n-button type="primary" :loading="passwordLoading" @click="handleChangePassword">确认修改</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 设备管理弹窗 -->
    <n-modal v-model:show="showDeviceModal" preset="card" title="登录设备" style="max-width: 500px">
      <div v-if="deviceLoading" class="loading-state">
        <span>加载中...</span>
      </div>
      <div v-else-if="devices.length === 0" class="empty-state">
        <span>暂无设备记录</span>
      </div>
      <div v-else class="device-list">
        <div v-for="device in devices" :key="device.id" class="device-item">
          <div class="device-info">
            <div class="device-name">{{ device.deviceName || '未知设备' }}</div>
            <div class="device-meta">
              {{ device.deviceType }} · {{ device.lastActive }}
              <span v-if="device.current" class="current-badge">当前设备</span>
            </div>
          </div>
          <n-button
            v-if="!device.current"
            size="tiny"
            type="error"
            secondary
            @click="handleLogoutDevice(device.id)"
          >
            下线
          </n-button>
        </div>
      </div>
    </n-modal>

    <!-- 反馈历史弹窗 -->
    <n-modal v-model:show="showFeedbackHistory" preset="card" title="我的反馈记录" style="max-width: 600px">
      <div v-if="feedbackLoading" class="loading-state">
        <span>加载中...</span>
      </div>
      <div v-else-if="feedbackList.length === 0" class="empty-state">
        <span>暂无反馈记录</span>
      </div>
      <div v-else class="feedback-list">
        <div v-for="item in feedbackList" :key="item.id" class="feedback-item">
          <div class="feedback-header">
            <n-tag :type="getFeedbackStatusType(item.status)" size="small">
              {{ getFeedbackStatusText(item.status) }}
            </n-tag>
            <n-tag size="small">{{ getFeedbackTypeText(item.type) }}</n-tag>
            <span class="feedback-time">{{ item.createTime }}</span>
          </div>
          <div class="feedback-content">{{ item.content }}</div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.settings-scroll {
  max-width: 520px;
  margin: 0 auto;
  overflow-x: hidden;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--lx-accent-soft) 0%, var(--lx-bg-panel) 60%);
  border: 1px solid var(--lx-border-light);
}

.profile-avatar {
  box-shadow: 0 4px 12px var(--lx-shadow-color);
  border: 2px solid var(--lx-bg-card);
}

.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.profile-id {
  font-size: 13px;
  color: var(--lx-text-secondary);
  margin-top: 4px;
}

.profile-badge {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  font-weight: 500;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 14px;
  color: var(--lx-text-secondary);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.loading-state,
.empty-state {
  padding: 24px;
  text-align: center;
  color: var(--lx-text-muted);
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.device-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: var(--lx-radius);
  background: var(--lx-bg-panel);
}

.device-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.device-meta {
  font-size: 12px;
  color: var(--lx-text-muted);
  margin-top: 4px;
}

.current-badge {
  margin-left: 8px;
  padding: 2px 6px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  border-radius: 4px;
  font-size: 11px;
}

/* 余额展示样式 */
.balance-display {
  padding: 8px 0;
}

.balance-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.balance-label {
  font-size: 13px;
  color: var(--lx-text-secondary);
}

.balance-amount {
  font-size: 28px;
  font-weight: 700;
  color: var(--lx-accent);
}

.balance-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.balance-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
}

.balance-item-label {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.balance-item-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

/* 反馈历史样式 */
.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feedback-item {
  padding: 12px;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
}

.feedback-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.feedback-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.feedback-content {
  font-size: 14px;
  color: var(--lx-text-body);
  line-height: 1.5;
}
</style>
