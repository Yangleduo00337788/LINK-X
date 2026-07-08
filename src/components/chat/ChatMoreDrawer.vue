<script setup lang="ts">
import { NSwitch, useMessage, useDialog } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useOverlayStore } from '../../stores/overlay'
import { useContactsStore } from '../../stores/contacts'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const contactsStore = useContactsStore()
const message = useMessage()
const dialog = useDialog()
const { moreDrawerOpen } = storeToRefs(chatModalsStore)
const { closeMore } = chatModalsStore
const { currentSession, currentSessionId } = storeToRefs(appStore)
const {
  toggleSessionPin,
  toggleSessionMute,
  toggleSessionBlock,
  clearSessionMessages,
  deleteSession,
  setNav
} = appStore
const { open: openOverlay } = overlayStore

function setPin(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.pinned === val) return
  toggleSessionPin(currentSessionId.value)
}

function setMute(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.muted === val) return
  toggleSessionMute(currentSessionId.value)
}

function setBlock(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.blocked === val) return
  toggleSessionBlock(currentSessionId.value)
}

function onBackdrop() {
  closeMore()
}

function openFileTransfer() {
  setNav('files')
  closeMore()
  message.success('已打开文件传输列表')
}

function clearChat() {
  if (!currentSessionId.value) return
  dialog.warning({
    title: '删除聊天记录',
    content: '确定清空当前会话的所有消息？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: () => {
      clearSessionMessages(currentSessionId.value!)
      message.success('聊天记录已清空')
      closeMore()
    }
  })
}

function deleteFriend() {
  if (!currentSession.value || !currentSessionId.value) return
  dialog.warning({
    title: '删除好友',
    content: `确定删除好友「${currentSession.value.name}」并移除会话？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: () => {
      contactsStore.remove(currentSessionId.value!)
      deleteSession(currentSessionId.value!)
      message.success('已删除好友')
      closeMore()
    }
  })
}

function reportUser() {
  openOverlay('help')
  closeMore()
  message.info('请在帮助与反馈中提交举报')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="moreDrawerOpen" class="drawer-root" @click.self="onBackdrop">
        <Transition name="drawer-slide">
          <aside v-if="moreDrawerOpen" class="drawer-panel" @click.stop>
            <div class="drawer-inner">
              <div class="row switch-row">
                <span>设为置顶</span>
                <n-switch :value="!!currentSession?.pinned" size="small" @update:value="setPin" />
              </div>
              <div class="row switch-row">
                <span>消息免打扰</span>
                <n-switch :value="!!currentSession?.muted" size="small" @update:value="setMute" />
              </div>
              <div class="row switch-row">
                <span>屏蔽此人</span>
                <n-switch :value="!!currentSession?.blocked" size="small" @update:value="setBlock" />
              </div>
              <button type="button" class="row link-row" @click="openFileTransfer">
                文件传输列表
              </button>
              <button type="button" class="row danger-text" @click="clearChat">
                删除聊天记录
              </button>
              <button type="button" class="row danger-text" @click="deleteFriend">
                删除好友
              </button>
              <p class="report">
                <a href="#" @click.prevent="reportUser">被骚扰了？举报该用户</a>
              </p>
              <p v-if="currentSession" class="hint-name">{{ currentSession.name }}</p>
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: var(--lx-shadow-color-heavy);
}

.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: min(280px, 42vw);
  height: 100%;
  background: var(--lx-bg-card);
  box-shadow: -4px 0 24px var(--lx-shadow-color);
  display: flex;
  flex-direction: column;
}

.drawer-inner {
  padding: 20px 18px 24px;
}

.row {
  width: 100%;
  text-align: left;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--lx-text-body);
  padding: 14px 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.link-row {
  cursor: pointer;
  color: var(--lx-text-body);
}

.link-row:hover {
  color: var(--lx-accent);
}

.danger-text {
  cursor: pointer;
  color: #e34d59;
  font-size: 14px;
}

.report {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
}

.report a {
  color: var(--lx-accent);
  text-decoration: none;
}

.hint-name {
  display: none;
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.25s ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(100%);
}
</style>
