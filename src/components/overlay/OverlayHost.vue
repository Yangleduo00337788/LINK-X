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
import { useOverlay } from '../../composables/useOverlay'
import { useAppState } from '../../composables/useAppState'
import type { OverlayPage } from '../../types'

const message = useMessage()
const { currentPage, close, overlayApp, overlayFileName } = useOverlay()
const {
  theme,
  toggleTheme,
  currentSession,
  currentMessages,
  ensureSession,
  setNav,
  userProfile,
  updateNickname,
  updateSignature
} = useAppState()

const profileNick = ref(userProfile.value.nickname)
const profileSig = ref(userProfile.value.signature)

watch(currentPage, p => {
  if (p === 'profile') {
    profileNick.value = userProfile.value.nickname
    profileSig.value = userProfile.value.signature
  }
})

function saveProfile() {
  updateNickname(profileNick.value.trim() || '晚香玉')
  updateSignature(profileSig.value.trim() || '编辑个性签名')
  message.success('资料已保存（演示）')
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
  get: () => true,
  set: () => message.success('已保存（演示）')
})

function saveDemo(label: string) {
  message.success(`${label}已保存（演示，待对接后端）`)
  close()
}

function createGroupAndEnter() {
  ensureSession({
    id: `new-${Date.now()}`,
    name: '新建群聊',
    lastMessage: '欢迎加入',
    time: '刚刚',
    avatarText: '群',
    avatarColor: '#0099ff',
    isGroup: true
  })
  setNav('chat')
  message.success('群聊已创建（演示）')
  close()
}

function createChannelDone() {
  setNav('moments')
  message.success('频道已创建（演示）')
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
            <n-switch />
          </n-form-item>
        </n-form>
        <n-button type="primary" @click="saveDemo('设置')">保存</n-button>
      </template>

      <template v-else-if="currentPage === 'notifications'">
        <n-list>
          <n-list-item>
            <n-thing title="新消息提醒" description="收到消息时通知" />
            <template #suffix><n-switch v-model:value="notifyEnabled" /></template>
          </n-list-item>
          <n-list-item>
            <n-thing title="群聊 @ 我" />
            <template #suffix><n-switch :default-value="true" /></template>
          </n-list-item>
          <n-list-item>
            <n-thing title="声音" />
            <template #suffix><n-switch :default-value="false" /></template>
          </n-list-item>
        </n-list>
        <n-button type="primary" class="mt" @click="saveDemo('通知设置')">保存</n-button>
      </template>

      <template v-else-if="currentPage === 'privacy'">
        <n-list>
          <n-list-item><n-thing title="加我为好友时需要验证" /><template #suffix><n-switch :default-value="true" /></template></n-list-item>
          <n-list-item><n-thing title="允许陌生人临时会话" /><template #suffix><n-switch /></template></n-list-item>
          <n-list-item><n-thing title="在线状态对他人可见" /><template #suffix><n-switch :default-value="true" /></template></n-list-item>
        </n-list>
        <n-button type="primary" class="mt" @click="saveDemo('隐私设置')">保存</n-button>
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
          <h2>LinkX / QQ Replica</h2>
          <p>版本 1.0.0（前端演示）</p>
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
          <n-form-item label="QQ号 / 手机号"><n-input placeholder="输入账号" /></n-form-item>
          <n-form-item label="验证信息"><n-input placeholder="我是…" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="saveDemo('好友申请')">发送申请</n-button>
      </template>

      <template v-else-if="currentPage === 'create-group'">
        <n-form>
          <n-form-item label="群名称"><n-input placeholder="起个群名" /></n-form-item>
          <n-form-item label="邀请成员"><n-input placeholder="搜索好友" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="createGroupAndEnter">创建并进入</n-button>
      </template>

      <template v-else-if="currentPage === 'create-channel'">
        <n-form>
          <n-form-item label="频道名称"><n-input placeholder="频道名" /></n-form-item>
          <n-form-item label="简介"><n-input type="textarea" placeholder="介绍频道" /></n-form-item>
        </n-form>
        <n-button type="primary" @click="createChannelDone">创建频道</n-button>
      </template>

      <template v-else-if="currentPage === 'weather'">
        <div class="weather-card">
          <div class="temp">24°</div>
          <div>多云 · 体感 26°</div>
          <div class="muted">定位：演示城市（待对接天气 API）</div>
        </div>
      </template>

      <template v-else-if="currentPage === 'app-runner' && overlayApp">
        <div class="app-run">
          <div class="app-icon-lg" :style="{ background: overlayApp.color }">{{ overlayApp.icon }}</div>
          <h2>{{ overlayApp.name }}</h2>
          <p>{{ overlayApp.desc }}</p>
          <p class="tip">此处可嵌入 WebView 或调用后端打开第三方应用。</p>
          <n-button type="primary" @click="message.info('打开应用（演示）')">进入应用</n-button>
        </div>
      </template>

      <template v-else-if="currentPage === 'file-preview'">
        <div class="file-preview">
          <div class="preview-box">📷</div>
          <p>{{ overlayFileName || 'Screenshot 2026-07-05-18-48.png' }}</p>
          <p class="muted">355.33 KB · 已下载到本地（演示）</p>
        </div>
      </template>

      <template v-else-if="currentPage === 'chat-history'">
        <p class="muted">当前会话：{{ currentSession?.name || '—' }}</p>
        <div class="history-list">
          <div v-for="msg in currentMessages" :key="msg.id" class="history-row">
            <span class="t">{{ msg.time }}</span>
            <span :class="{ self: msg.isSelf }">{{ msg.content || `[${msg.type}]` }}</span>
          </div>
          <p v-if="!currentMessages.length" class="muted">暂无消息</p>
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
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.overlay-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 4px;
  border-bottom: 1px solid #e8e8e8;
  background: #f5f5f5;
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
  color: #333;
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
  color: #666;
  line-height: 1.6;
}

.about-card {
  text-align: center;
  padding: 24px;
  background: #fff;
  border-radius: var(--lx-radius);
}

.logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: var(--lx-radius);
  background: #0099ff;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.muted {
  color: #999;
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
  background: linear-gradient(135deg, #0099ff, #66b3ff);
  color: #fff;
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
  background: #fff;
  border-radius: var(--lx-radius);
}

.app-icon-lg {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: var(--lx-radius);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tip {
  color: #0099ff;
  font-size: 13px;
  margin: 16px 0;
}

.file-preview {
  text-align: center;
}

.preview-box {
  width: 100%;
  max-width: 400px;
  height: 240px;
  margin: 0 auto 16px;
  background: #e8e8e8;
  border-radius: var(--lx-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
}

.history-list {
  margin-top: 12px;
  background: #fff;
  border-radius: var(--lx-radius);
  padding: 12px;
}

.history-row {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
}

.history-row .t {
  color: #999;
  flex-shrink: 0;
  width: 48px;
}

.history-row .self {
  color: #0099ff;
}
</style>