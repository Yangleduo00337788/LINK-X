<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { NButton, NIcon } from 'naive-ui'
import { ArrowBackOutline } from '@vicons/ionicons5'
import WindowControls from '../WindowControls.vue'
import { storeToRefs } from 'pinia'
import { useOverlayStore } from '../../stores/overlay'
import type { OverlayPage } from '../../types'

const overlayStore = useOverlayStore()
const { currentPage, overlayApp } = storeToRefs(overlayStore)
const { close } = overlayStore

const HelpPage = defineAsyncComponent(() => import('./pages/HelpPage.vue'))
const ProfilePage = defineAsyncComponent(() => import('./pages/ProfilePage.vue'))
const AddFriendPage = defineAsyncComponent(() => import('./pages/AddFriendPage.vue'))
const CreateGroupPage = defineAsyncComponent(() => import('./pages/CreateGroupPage.vue'))
const CreateChannelPage = defineAsyncComponent(() => import('./pages/CreateChannelPage.vue'))
const WeatherPage = defineAsyncComponent(() => import('./pages/WeatherPage.vue'))
const AppRunnerPage = defineAsyncComponent(() => import('./pages/AppRunnerPage.vue'))
const FilePreviewPage = defineAsyncComponent(() => import('./pages/FilePreviewPage.vue'))
const ChatHistoryPage = defineAsyncComponent(() => import('./pages/ChatHistoryPage.vue'))

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
      <HelpPage v-if="currentPage === 'help'" />
      <ProfilePage v-else-if="currentPage === 'profile'" />
      <AddFriendPage v-else-if="currentPage === 'add-friend'" />
      <CreateGroupPage v-else-if="currentPage === 'create-group'" />
      <CreateChannelPage v-else-if="currentPage === 'create-channel'" />
      <WeatherPage v-else-if="currentPage === 'weather'" />
      <AppRunnerPage v-else-if="currentPage === 'app-runner'" />
      <FilePreviewPage v-else-if="currentPage === 'file-preview'" />
      <ChatHistoryPage v-else-if="currentPage === 'chat-history'" />
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
</style>
