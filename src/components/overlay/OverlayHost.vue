<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NButton,
  NIcon,
  NForm,
  NFormItem,
  NInput,
  useMessage
} from 'naive-ui'
import {
  ArrowBackOutline,
  HelpCircleOutline,
  ChatbubblesOutline,
  PersonOutline,
  CloudOutline,
  MoonOutline,
  ApertureOutline,
  MailOutline,
  TimeOutline
} from '@vicons/ionicons5'
import WindowControls from '../WindowControls.vue'
import AppWebView from '../AppWebView.vue'
import EmptyState from '../common/EmptyState.vue'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../stores/overlay'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import type { OverlayPage } from '../../types'

const message = useMessage()
const overlayStore = useOverlayStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { currentPage, overlayApp, filePreview } = storeToRefs(overlayStore)
const { close } = overlayStore
const { currentSession, currentMessages, userProfile } = storeToRefs(appStore)
const { setNav, updateNickname, updateSignature, createGroup } = appStore

const addFriendAccount = ref('')
const addFriendMsg = ref('我是…')
const createGroupName = ref('')
const createGroupMembers = ref('')
const channelName = ref('')
const channelDesc = ref('')
const feedbackText = ref('')
const expandedFaq = ref<number | null>(0)

const faqItems = [
  {
    icon: CloudOutline,
    q: '如何同步消息？',
    a: '对接后端 WebSocket 后即可实时同步，当前为本地 Mock 演示。'
  },
  {
    icon: MoonOutline,
    q: '如何切换深色模式？',
    a: '点击侧栏调色盘图标，或进入设置 → 外观与显示。'
  },
  {
    icon: ApertureOutline,
    q: '友链独立窗口如何使用？',
    a: '在 Electron 客户端点击侧栏友链图标，将打开独立浏览窗口。'
  }
]

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
  help: '帮助与反馈',
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

function submitFeedback() {
  const text = feedbackText.value.trim()
  if (!text) {
    message.warning('请先描述您遇到的问题')
    return
  }
  console.info('[LinkX] 用户反馈', text)
  message.success('反馈已提交，感谢您的建议')
  feedbackText.value = ''
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
      <template v-if="currentPage === 'help'">
        <div class="page-wrap help-page">
          <section class="panel-card">
            <div class="panel-head">
              <div class="panel-head-icon">
                <n-icon :component="HelpCircleOutline" :size="20" />
              </div>
              <div>
                <h2 class="panel-title">常见问题</h2>
                <p class="panel-sub">快速了解 LinkX 常用功能</p>
              </div>
            </div>
            <div class="faq-list">
              <button
                v-for="(item, index) in faqItems"
                :key="item.q"
                type="button"
                class="faq-row"
                :class="{ open: expandedFaq === index }"
                @click="expandedFaq = expandedFaq === index ? null : index"
              >
                <div class="faq-row-head">
                  <n-icon :component="item.icon" :size="18" class="faq-ico" />
                  <span class="faq-q">{{ item.q }}</span>
                  <span class="faq-chevron">{{ expandedFaq === index ? '−' : '+' }}</span>
                </div>
                <p v-if="expandedFaq === index" class="faq-a">{{ item.a }}</p>
              </button>
            </div>
          </section>

          <section class="panel-card feedback-card">
            <div class="panel-head compact">
              <div class="panel-head-icon soft">
                <n-icon :component="MailOutline" :size="20" />
              </div>
              <div>
                <h2 class="panel-title">问题反馈</h2>
                <p class="panel-sub">你的建议会帮助我们改进产品</p>
              </div>
            </div>
            <n-input
              v-model:value="feedbackText"
              type="textarea"
              placeholder="描述你遇到的问题或建议…"
              :rows="5"
              class="feedback-input"
            />
            <div class="feedback-actions">
              <n-button type="primary" @click="submitFeedback">提交反馈</n-button>
            </div>
          </section>
        </div>
      </template>

      <template v-else-if="currentPage === 'profile'">
        <section class="profile-card">
          <img
            src="https://api.dicebear.com/7.x/avataaars/svg?seed=qq-user"
            alt=""
            class="big-avatar"
          />
          <div class="profile-meta">
            <div class="profile-name">{{ profileNick || userProfile.nickname }}</div>
            <div class="profile-id">LinkX ID · linkx_888888</div>
          </div>
        </section>

        <section class="group-card">
          <div class="group-head">
            <n-icon :component="PersonOutline" :size="18" class="group-ico" />
            <span>基本信息</span>
          </div>
          <n-form label-placement="top">
            <n-form-item label="昵称">
              <n-input v-model:value="profileNick" placeholder="输入昵称" />
            </n-form-item>
            <n-form-item label="个性签名">
              <n-input v-model:value="profileSig" placeholder="编辑个性签名" />
            </n-form-item>
            <n-form-item label="LinkX ID">
              <n-input value="linkx_888888" disabled />
            </n-form-item>
          </n-form>
          <n-button type="primary" @click="saveProfile">保存资料</n-button>
        </section>
      </template>

      <template v-else-if="currentPage === 'add-friend'">
        <section class="group-card">
          <n-form label-placement="top">
            <n-form-item label="LinkX ID / 手机号">
              <n-input v-model:value="addFriendAccount" placeholder="输入账号" />
            </n-form-item>
            <n-form-item label="验证信息">
              <n-input v-model:value="addFriendMsg" placeholder="我是…" />
            </n-form-item>
          </n-form>
          <n-button type="primary" @click="submitAddFriend">发送申请</n-button>
        </section>
      </template>

      <template v-else-if="currentPage === 'create-group'">
        <section class="group-card">
          <n-form label-placement="top">
            <n-form-item label="群名称">
              <n-input v-model:value="createGroupName" placeholder="起个群名" />
            </n-form-item>
            <n-form-item label="邀请成员">
              <n-input v-model:value="createGroupMembers" placeholder="多个成员用逗号分隔" />
            </n-form-item>
          </n-form>
          <n-button type="primary" @click="submitCreateGroup">创建并进入</n-button>
        </section>
      </template>

      <template v-else-if="currentPage === 'create-channel'">
        <section class="group-card">
          <n-form label-placement="top">
            <n-form-item label="频道名称">
              <n-input v-model:value="channelName" placeholder="频道名" />
            </n-form-item>
            <n-form-item label="简介">
              <n-input v-model:value="channelDesc" type="textarea" placeholder="介绍频道" />
            </n-form-item>
          </n-form>
          <n-button type="primary" @click="submitCreateChannel">创建频道</n-button>
        </section>
      </template>

      <template v-else-if="currentPage === 'weather'">
        <section class="weather-card">
          <div class="temp">24°</div>
          <div>多云 · 体感 26°</div>
          <div class="muted">定位：深圳 · 多云</div>
        </section>
      </template>

      <template v-else-if="currentPage === 'app-runner' && overlayApp">
        <div v-if="overlayApp.url" class="app-run-embed">
          <AppWebView :url="overlayApp.url" :title="overlayApp.name" />
        </div>
        <section v-else class="group-card app-run">
          <div class="app-icon-lg" :style="{ background: overlayApp.color }">{{ overlayApp.icon }}</div>
          <h2>{{ overlayApp.name }}</h2>
          <p>{{ overlayApp.desc }}</p>
          <p class="tip">该应用暂未配置内嵌 URL。</p>
        </section>
      </template>

      <template v-else-if="currentPage === 'file-preview'">
        <section class="group-card file-preview">
          <div v-if="filePreview?.fileUrl && filePreview.isImage" class="preview-box preview-img-wrap">
            <img :src="filePreview.fileUrl" :alt="filePreview.fileName" class="preview-img" />
          </div>
          <div v-else class="preview-box">{{ filePreview?.isImage ? '🖼️' : '📄' }}</div>
          <p class="file-name">{{ filePreview?.fileName || '文件' }}</p>
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
        </section>
      </template>

      <template v-else-if="currentPage === 'chat-history'">
        <div class="page-wrap history-page">
          <section class="panel-card history-card">
            <div class="history-hero">
              <div class="history-avatar">
                <n-icon :component="ChatbubblesOutline" :size="28" />
              </div>
              <div class="history-meta">
                <h2 class="history-name">{{ currentSession?.name || '—' }}</h2>
                <p class="history-sub">
                  <n-icon :component="TimeOutline" :size="14" />
                  共 {{ historyMessages.length }} 条消息
                </p>
              </div>
            </div>

            <div v-if="historyMessages.length" class="history-scroll">
              <div
                v-for="msg in historyMessages"
                :key="msg.id"
                class="history-item"
                :class="{ self: msg.isSelf }"
              >
                <div class="history-bubble">
                  <p class="history-text">{{ historyPreview(msg) }}</p>
                  <span class="history-time">{{ msg.time }}</span>
                </div>
              </div>
            </div>
            <EmptyState
              v-else
              title="暂无消息"
              description="当前会话还没有聊天记录"
            />
          </section>
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
  border-bottom: 1px solid var(--lx-border-light);
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
  background: var(--lx-bg-list, var(--lx-bg-panel));
  display: flex;
  justify-content: center;
}

.page-wrap {
  width: 100%;
  max-width: 760px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-card {
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: 12px;
  padding: 20px 22px;
  box-shadow: var(--lx-shadow-soft, 0 2px 12px rgba(0, 0, 0, 0.04));
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.panel-head.compact {
  margin-bottom: 14px;
}

.panel-head-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--lx-accent-soft);
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.panel-head-icon.soft {
  background: var(--lx-bg-panel);
}

.panel-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.panel-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.faq-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.faq-row {
  width: 100%;
  border: 1px solid var(--lx-border-light);
  background: var(--lx-bg-panel);
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.faq-row:hover,
.faq-row.open {
  border-color: rgba(18, 183, 245, 0.35);
  background: var(--lx-accent-soft);
}

.faq-row-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.faq-ico {
  color: var(--lx-accent);
  flex-shrink: 0;
}

.faq-q {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.faq-chevron {
  color: var(--lx-text-muted);
  font-size: 18px;
  line-height: 1;
  flex-shrink: 0;
}

.faq-a {
  margin: 10px 0 0 28px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--lx-text-secondary);
}

.feedback-card {
  display: flex;
  flex-direction: column;
}

.feedback-input :deep(textarea) {
  border-radius: 10px;
}

.feedback-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.history-card {
  display: flex;
  flex-direction: column;
  min-height: min(560px, calc(100vh - 140px));
}

.history-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: 16px;
}

.history-avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--lx-accent-soft), var(--lx-accent-light, #8ec5fc));
  color: var(--lx-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.history-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.history-sub {
  margin: 6px 0 0;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.history-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.history-item {
  display: flex;
}

.history-item.self {
  justify-content: flex-end;
}

.history-bubble {
  max-width: min(88%, 520px);
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
}

.history-item.self .history-bubble {
  background: var(--lx-accent-soft);
  border-color: rgba(18, 183, 245, 0.2);
}

.history-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--lx-text-body);
  word-break: break-word;
}

.history-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--lx-text-muted);
  text-align: right;
}

.group-card {
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 16px 18px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin-bottom: 14px;
}

.group-ico {
  color: var(--lx-accent);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-accent-soft), var(--lx-bg-card));
  border: 1px solid var(--lx-border-light);
}

.big-avatar {
  width: 72px;
  height: 72px;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.profile-id {
  font-size: 13px;
  color: var(--lx-text-muted);
  margin-top: 4px;
}

.faq-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.faq-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.faq-q {
  font-size: 14px;
  font-weight: 500;
  color: var(--lx-text-body);
  margin-bottom: 4px;
}

.faq-a {
  font-size: 13px;
  color: var(--lx-text-secondary);
  line-height: 1.5;
}

.mt {
  margin-top: 16px;
}

.muted {
  color: var(--lx-text-muted);
  font-size: 13px;
}

.session-tip {
  margin: -8px 0 12px;
}

.weather-card {
  background: linear-gradient(135deg, var(--lx-accent), var(--lx-accent-light));
  color: var(--lx-text-on-accent);
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
}

.app-icon-lg {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: var(--lx-radius);
  color: var(--lx-text-on-accent);
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tip {
  color: var(--lx-accent);
  font-size: 13px;
  margin: 16px 0 0;
}

.app-run-embed {
  height: min(520px, 70vh);
  display: flex;
  flex-direction: column;
  border-radius: var(--lx-radius);
  overflow: hidden;
  border: 1px solid var(--lx-border-light);
}

.file-preview {
  text-align: center;
}

.file-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--lx-text-body);
  margin: 0 0 4px;
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

.preview-img-wrap {
  padding: 0;
  overflow: hidden;
  max-width: 100%;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
