<script setup lang="ts">
import { NButton, NAvatar, NIcon, NSwitch, useMessage } from 'naive-ui'
import { ShieldCheckmarkOutline } from '@vicons/ionicons5'
import { storeToRefs } from 'pinia'
import { useAppStore } from '../../stores/app'
import { useAppSettingsStore } from '../../stores/appSettings'

const message = useMessage()
const appStore = useAppStore()
const appSettingsStore = useAppSettingsStore()

const { userProfile } = storeToRefs(appStore)
const {
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline
} = storeToRefs(appSettingsStore)

function changePassword() {
  message.info('修改密码需对接后端账号服务')
}

function manageDevices() {
  message.info('当前设备：本机 · Windows（本地演示）')
}
</script>

<template>
  <div class="settings-scroll">
    <section class="profile-card">
      <n-avatar
        :size="72"
        src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
        class="profile-avatar"
      />
      <div class="profile-meta">
        <div class="profile-name">{{ userProfile.nickname }}</div>
        <div class="profile-id">LinkX ID · linkx_888888</div>
        <div class="profile-badge">已登录</div>
      </div>
    </section>

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
        <n-button size="small" secondary @click="changePassword">修改</n-button>
      </div>
      <div class="setting-row">
        <div class="setting-text">
          <span class="setting-name">设备管理</span>
          <span class="setting-desc">查看已登录的设备与会话</span>
        </div>
        <n-button size="small" secondary @click="manageDevices">查看</n-button>
      </div>
    </section>

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
  </div>
</template>

<style scoped>
@import './settings-common.css';

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
</style>
