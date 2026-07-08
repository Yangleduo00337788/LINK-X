<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NButton,
  NIcon,
  NForm,
  NFormItem,
  NInput,
  NSwitch,
  NSelect,
  NList,
  NListItem,
  NThing,
  useMessage
} from 'naive-ui'
import { ArrowBackOutline } from '@vicons/ionicons5'
import WindowControls from '../WindowControls.vue'
import AppWebView from '../AppWebView.vue'
import { storeToRefs } from 'pinia'
import { useAppSettingsStore } from '../../stores/appSettings'
import { useOverlayStore } from '../../stores/overlay'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import type { OverlayPage } from '../../types'

const message = useMessage()
const overlayStore = useOverlayStore()
const appSettingsStore = useAppSettingsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { currentPage, overlayApp, filePreview } = storeToRefs(overlayStore)
const { close } = overlayStore
const {
  theme,
  currentSession,
  currentMessages,
  userProfile
} = storeToRefs(appStore)
const {
  toggleTheme,
  setNav,
  updateNickname,
  updateSignature,
  createGroup
} = appStore

const addFriendAccount = ref('')
const addFriendMsg = ref('我是…')
const createGroupName = ref('')
const createGroupMembers = ref('')
const channelName = ref('')
const channelDesc = ref('')

const {
  autoStart,
  soundNotify,
  messageDetail,
  notifyAtMe,
  notifySound,
  privacyVerifyFriend,
  privacyAllowStranger,
  privacyShowOnline
} = storeToRefs(appSettingsStore)

const profileNick = ref(userProfile.value.nickname)
const profileSig = ref(userProfile.value.signature)

watch(currentPage, p => {
  if (p === 'profile') {
    profileNick.value = userProfile.value.nickname
    profileSig.value = userProfile.value.signature
  }
})

const historyMessages = computed(() =>
  currentMessages.value.filter(m => m.type !== 'system')
)

function historyPreview(msg: (typeof currentMessages.value)[number]) {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  if (msg.type === 'voice') return '[语音]'
  if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || msg.content}`
  return msg.content
}

function saveProfile() {
  updateNickname(profileNick.value.trim() || '晚香玉')
  updateSignature(profileSig.value.trim() || '编辑个性签名')
  message.success('资料已保存')
  close()
}

const titleMap: Record<OverlayPage, string> = {
  settings: '设置',
  notifications: '消息通知',
  privacy: '隐私与安全',
  help: '帮助与反馈',
  about: '关于 LinkX',
  profile: '个人资料',
  'add-friend': '添加好友',
  'create-group': '发起群聊',
  'create-channel': '创建频道',
  weather: '天气',
  'app-runner': '应用',
  'file-preview': '文件预览',
  'chat-history': '聊天记录'
}

const pageTitle = computed(() => {
  const p = currentPage.value
  if (!p) return ''
  if (p === 'app-runner' && overlayApp.value) return overlayApp.value.name
  return titleMap[p]
})

const notifyEnabled = computed({
  get: () => soundNotify.value,
  set: (v: boolean) => { soundNotify.value = v }
})

function saveSettings() {
  if (window.electronAPI?.setAutoStart) {
    window.electronAPI.setAutoStart(autoStart.value)
  }
  message.success('设置已保存')
  close()
}

function saveNotifications() {
  message.success('通知设置已保存')
  close()
}

function savePrivacy() {
  message.success('隐私设置已保存')
  close()
}

function saveDemo(label: string) {
  message.success(`${label}已提交`)
  close()
}

function submitAddFriend() {
  const account = addFriendAccount.value.trim()
  if (!account) {
    message.warning('请输入账号')
    return
  }
  appStore.addFriendSession(account)
  contactsStore.addByName(account)
  message.success(`已向「${account}」发送好友申请（本地模拟）`)
  addFriendAccount.value = ''
  close()
}

function submitCreateGroup() {
  const name = createGroupName.value.trim() || '新建群聊'
  const memberNames = createGroupMembers.value
    .split(/[,，、\s]+/)
    .map(s => s.trim())
    .filter(Boolean)
  const members = memberNames.map((n, i) => ({
    id: `invite-${i}-${Date.now()}`,
    name: n,
    avatarText: n.charAt(0) || '?',
    avatarColor: '#12b7f5'
  }))
  if (!members.length) {
    members.push({
      id: `invite-self-${Date.now()}`,
      name: userProfile.value.nickname,
      avatarText: userProfile.value.nickname.charAt(0) || '我',
      avatarColor: '#12b7f5'
    })
  }
  createGroup(members, name)
  setNav('chat')
  message.success('群聊已创建')
  createGroupName.value = ''
  createGroupMembers.value = ''
  close()
}

function submitCreateChannel() {
  const name = channelName.value.trim()
  if (!name) {
    message.warning('请输入频道名称')
    return
  }
  contactsStore.addByName(name)
  setNav('moments')
  message.success(`频道「${name}」已创建`)
  channelName.value = ''
  channelDesc.value = ''
  close()
}

function createGroupAndEnter() {
  submitCreateGroup()
}

function createChannelDone() {
  submitCreateChannel()
}
</script>

<template>
  <div v-if="currentPage" class="overlay-host">
    <div class="overlay-header">
      <div class="left">
        <n-button quaternary circle @click="close">
          <template #icon>
            <n-icon :component="ArrowBackOutline" />
          </template>
        </n-button>
        <span class="title">{{ pageTitle }}</span>
      </div>
      <WindowControls />
    </div>

    <div class="overlay-body">
      <template v-if="currentPage === 'settings'">
        <n-form label-placement="left" label-width="100">
          <n-form-item label="账号">
            <n-input value="demo@linkx.local" disabled />
          </n-form-item>
          <n-form-item label="语言">
            <n-select :value="'zh-CN'" :options="[{ label: '简体中文', value: 'zh-CN' }]" />
          </n-form-item>
          <n-form-item label="深色模式">
            <n-switch :value="theme === 'dark'" @update:value="toggleTheme" />
          </n-form-item>
          <n-form-item label="开机自启">
            <n-switch v-model:value="autoStart" />
          </n-form-item>
        </n-form>
        <n-button type="primary" @click="saveSettings">保存</n-button>
      </template>

      <template v-else-if="currentPage === 'notifications'">
        <n-list>
          <n-list-item>
            <n-thing title="新消息提醒" description="收到消息时通知" />
            <template #suffix><n-switch v-model:value="notifyEnabled" /></template>
          </n-list-item>
          <n-list-item>
            <n-thing title="群聊 @ 我" />
            <template #suffix><n-switch v-model:value="notifyAtMe" /></template>
          </n-list-item>
          <n-list-item>
            <n-thing title="声音" />
            <template #suffix><n-switch v-model:value="notifySound" /></template>
          </n-list-item>
          <n-list-item>
            <n-thing title="通知显示消息详情" />
            <template #suffix><n-switch v-model:value="messageDetail" /></template>
          </n-list-item>
        </n-list>
        <n-button type="primary" class="mt" @click="saveNotifications">保存</n-button>
      </template>

      <template v-else-if="currentPage === 'privacy'">
        <n-list>
          <n-list-item><n-thing title="加我为好友时需要验证" /><template #suffix><n-switch v-model:value="privacyVerifyFriend" /></template></n-list-item>
          <n-list-item><n-thing title="允许陌生人临时会话" /><template #suffix><n-switch v-model:value="privacyAllowStranger" /></template></n-list-item>
          <n-list-item><n-thing title="在线状态对他人可见" /><template #suffix><n-switch v-model:value="privacyShowOnline" /></template></n-list-item>
        </n-list>
        <n-button type="primary" class="mt" @click="savePrivacy">保存</n-button>
      </template>

      <template v-else-if="currentPage === 'help'">
        <h3>常见问题</h3>
        <p class="para">如何同步消息？对接后端 WebSocket 后即可实时同步。</p>
        <h3>反馈</h3>
        <n-input type="textarea" placeholder="描述你遇到的问题…" :rows="4" />
        <n-button type="primary" class="mt" @click="saveDemo('反馈')">提交反馈</n-button>
      </template>

      <template v-else-if="currentPage === 'about'">
        <div class="about-card">
          <div class="logo">LX</div>
          <h2>LinkX</h2>
          <p>版本 1.0.0</p>
          <p class="muted">Electron + Vue 3 + Naive UI</p>
          <p class="muted">下一步：对接 Java 后端 API</p>
        </div>
      </template>

      <template v-else-if="currentPage === 'profile'">
        <div class="profile">
          <img
            src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
            alt=""
            class="big-avatar"
          />
          <n-form class="mt">
            <n-form-item label="昵称"><n-input v-model:value="profileNick" /></n-form-item>
            <n-form-item label="签名"><n-input v-model:value="profileSig" /></n-form-item>
            <n-form-item label="QQ号"><n-input value="10086" disabled /></n-form-item>
          </n-form>
          <n-button type="primary" @click="saveProfile">保存资料</n-button>
        </div>
      </template>

      <template v-else-if="currentPage === 'add-friend'">
        <n-form>
          <n-form-item label="LinkX ID / 手机号"><n-input v-model:value="addFriendAccount" placeholder="输入账号" /></n-form-item>
          <n-form-item label="验证信息"><n-input v-model:value="addFriendMsg" placeholder="我是…" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="submitAddFriend">发送申请</n-button>
      </template>

      <template v-else-if="currentPage === 'create-group'">
        <n-form>
          <n-form-item label="群名称"><n-input v-model:value="createGroupName" placeholder="起个群名" /></n-form-item>
          <n-form-item label="邀请成员"><n-input v-model:value="createGroupMembers" placeholder="多个成员用逗号分隔" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="createGroupAndEnter">创建并进入</n-button>
      </template>

      <template v-else-if="currentPage === 'create-channel'">
        <n-form>
          <n-form-item label="频道名称"><n-input v-model:value="channelName" placeholder="频道名" /></n-form-item>
          <n-form-item label="简介"><n-input v-model:value="channelDesc" type="textarea" placeholder="介绍频道" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="createChannelDone">创建频道</n-button>
      </template>

      <template v-else-if="currentPage === 'weather'">
        <div class="weather-card">
          <div class="temp">24°</div>
          <div>多云 · 体感 26°</div>
          <div class="muted">定位：深圳 · 多云</div>
        </div>
      </template>

      <template v-else-if="currentPage === 'app-runner' && overlayApp">
        <div v-if="overlayApp.url" class="app-run-embed">
          <AppWebView :url="overlayApp.url" :title="overlayApp.name" />
        </div>
        <div v-else class="app-run">
          <div class="app-icon-lg" :style="{ background: overlayApp.color }">{{ overlayApp.icon }}</div>
          <h2>{{ overlayApp.name }}</h2>
          <p>{{ overlayApp.desc }}</p>
          <p class="tip">该应用暂未配置内嵌 URL。</p>
        </div>
      </template>

      <template v-else-if="currentPage === 'file-preview'">
        <div class="file-preview">
          <div v-if="filePreview?.fileUrl && filePreview.isImage" class="preview-box preview-img-wrap">
            <img :src="filePreview.fileUrl" :alt="filePreview.fileName" class="preview-img" />
          </div>
          <div v-else class="preview-box">{{ filePreview?.isImage ? '🖼️' : '📄' }}</div>
          <p>{{ filePreview?.fileName || '文件' }}</p>
          <p class="muted">{{ filePreview?.fileSize || '—' }} · 本地预览</p>
          <n-button
            v-if="filePreview?.fileUrl"
            tag="a"
            :href="filePreview.fileUrl"
            target="_blank"
            rel="noopener"
            type="primary"
            class="mt"
          >
            下载 / 打开
          </n-button>
        </div>
      </template>

      <template v-else-if="currentPage === 'chat-history'">
        <p class="muted">当前会话：{{ currentSession?.name || '—' }}</p>
        <div class="history-list">
          <div v-for="msg in historyMessages" :key="msg.id" class="history-row">
            <span class="t">{{ msg.time }}</span>
            <span :class="{ self: msg.isSelf }">{{ historyPreview(msg) }}</span>
          </div>
          <p v-if="!historyMessages.length" class="muted">暂无消息</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.overlay-host {
  position: absolute;
  inset: 0;
  z-index: 100;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
}

.overlay-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 4px;
  border-bottom: 1px solid var(--lx-bg-panel-deep);
  background: var(--lx-bg-panel);
  -webkit-app-region: drag;
}

.left {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag;
}

.title {
  font-size: 16px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.overlay-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  max-width: 640px;
}

.mt {
  margin-top: 16px;
}

.para {
  color: var(--lx-text-secondary);
  line-height: 1.6;
}

.about-card {
  text-align: center;
  padding: 24px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
}

.logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.muted {
  color: var(--lx-text-muted);
  font-size: 13px;
}

.profile .big-avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  display: block;
  margin: 0 auto;
}

.weather-card {
  background: linear-gradient(135deg, var(--lx-accent), var(--lx-accent-light));
  color: var(--lx-bg-card);
  padding: 32px;
  border-radius: var(--lx-radius);
  text-align: center;
}

.temp {
  font-size: 48px;
  font-weight: 300;
}

.app-run {
  text-align: center;
  padding: 24px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
}

.app-icon-lg {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: var(--lx-radius);
  color: var(--lx-bg-card);
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tip {
  color: var(--lx-accent);
  font-size: 13px;
  margin: 16px 0;
}

.app-run-embed {
  height: min(520px, 70vh);
  display: flex;
  flex-direction: column;
}

.preview-img-wrap {
  padding: 0;
  overflow: hidden;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.file-preview {
  text-align: center;
}

.preview-box {
  width: 100%;
  max-width: 400px;
  height: 240px;
  margin: 0 auto 16px;
  background: var(--lx-bg-panel-deep);
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
}

.history-list {
  margin-top: 12px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  padding: 12px;
}

.history-row {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--lx-border-light);
  font-size: 14px;
}

.history-row .t {
  color: var(--lx-text-muted);
  flex-shrink: 0;
  width: 48px;
}

.history-row .self {
  color: var(--lx-accent);
}
</style>