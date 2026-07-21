<script setup lang="ts">
/**
 * 单聊「更多」侧滑抽屉。
 * <p>
 * 提供置顶、免打扰、屏蔽、文件传输、清空记录、删除好友与举报入口。
 * </p>
 */
// Naive UI 开关、消息与确认对话框
import { NSwitch, useMessage, useDialog } from 'naive-ui'
import { storeToRefs } from 'pinia'
// 聊天弹窗 store：抽屉开关
import { useChatModalsStore } from '../../stores/chatModals'
// 会话操作：置顶、静音、屏蔽、清空、删除
import { useAppStore } from '../../stores/app'
// 全屏 overlay：帮助/反馈
import { useOverlayStore } from '../../stores/overlay'
// 联系人：删除好友
import { useContactsStore } from '../../stores/contacts'
// 置顶图标
import PinIcon from '../icons/PinIcon.vue'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const overlayStore = useOverlayStore()
const contactsStore = useContactsStore()
const message = useMessage()
const dialog = useDialog()
// 抽屉是否打开、当前会话
const { moreDrawerOpen } = storeToRefs(chatModalsStore)
const { closeMore } = chatModalsStore
const { currentSession, currentSessionId } = storeToRefs(appStore)
// 会话相关操作方法
const {
  toggleSessionPin,
  toggleSessionMute,
  toggleSessionBlock,
  clearSessionMessages,
  deleteSession,
  removePrivateSessionByPeer,
  setNav
} = appStore
const { open: openOverlay } = overlayStore

/**
 * 设置会话置顶。
 * 若目标值与当前一致则忽略，避免重复 toggle。
 */
function setPin(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.pinned === val) return
  toggleSessionPin(currentSessionId.value)
}

/** 设置消息免打扰 */
function setMute(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.muted === val) return
  toggleSessionMute(currentSessionId.value)
}

/** 设置屏蔽；开启时提示无法发消息 */
function setBlock(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.blocked === val) return
  toggleSessionBlock(currentSessionId.value)
  if (val) message.info(t('modals.blockedInfo'))
}

/** 点击遮罩关闭抽屉 */
function onBackdrop() {
  closeMore()
}

/** 跳转到文件传输列表导航 */
function openFileTransfer() {
  setNav('files')
  closeMore()
  message.success(t('modals.fileTransferOpened'))
}

/** 二次确认后清空当前会话消息 */
function clearChat() {
  if (!currentSessionId.value) return
  dialog.warning({
    title: t('modals.clearChatHistory'),
    content: t('modals.clearChatConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      clearSessionMessages(currentSessionId.value!)
      message.success(t('modals.chatCleared'))
      closeMore()
    }
  })
}

/** 删除好友并移除对应会话 */
function deleteFriend() {
  if (!currentSession.value || !currentSessionId.value) return
  const session = currentSession.value
  const sessionId = currentSessionId.value
  const friendUserId = session.peerUserId

  dialog.warning({
    title: t('modals.deleteFriend'),
    content: t('modals.deleteFriendConfirm', { name: session.name }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        if (!friendUserId) {
          message.error(t('modals.deleteFriendFail'))
          return
        }
        await contactsStore.deleteFriend(friendUserId)
        removePrivateSessionByPeer(friendUserId)
        deleteSession(sessionId)
        message.success(t('modals.friendDeleted'))
        closeMore()
      } catch (e) {
        message.error(e instanceof Error ? e.message : t('modals.deleteFriendFail'))
      }
    }
  })
}

/** 打开帮助页引导用户举报 */
function reportUser() {
  openOverlay('help')
  closeMore()
  message.info(t('modals.reportHint'))
}
</script>

<template>
  <!-- 右侧滑入抽屉：会话更多操作 -->
  <Transition name="chat-drawer">
    <div v-if="moreDrawerOpen" class="drawer-root" @click.self="onBackdrop">
      <aside class="drawer-panel" @click.stop>
        <div class="drawer-inner">
          <!-- 置顶 / 免打扰 / 屏蔽开关 -->
          <div class="row switch-row">
            <span class="switch-label">
              <PinIcon :size="16" />
              {{ t('modals.pinSession') }}
            </span>
            <n-switch :value="!!currentSession?.pinned" size="small" @update:value="setPin" />
          </div>
          <div class="row switch-row">
            <span>{{ t('modals.muteMessages') }}</span>
            <n-switch :value="!!currentSession?.muted" size="small" @update:value="setMute" />
          </div>
          <div class="row switch-row">
            <span>{{ t('modals.blockPerson') }}</span>
            <n-switch :value="!!currentSession?.blocked" size="small" @update:value="setBlock" />
          </div>
          <!-- 快捷入口与危险操作 -->
          <button type="button" class="row link-row" @click="openFileTransfer">
            {{ t('modals.fileTransferList') }}
          </button>
          <button type="button" class="row danger-text" @click="clearChat">
            {{ t('modals.clearChatHistory') }}
          </button>
          <button type="button" class="row danger-text" @click="deleteFriend">
            {{ t('modals.deleteFriend') }}
          </button>
          <p class="report">
            <a href="#" @click.prevent="reportUser">{{ t('modals.reportUser') }}</a>
          </p>
        </div>
      </aside>
    </div>
  </Transition>
</template>

<style scoped>
.drawer-root {
  position: absolute;
  inset: 0;
  z-index: 30;
  background: var(--lx-bg-overlay);
}

.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(280px, 88%);
  max-width: 320px;
  background: var(--lx-bg-card);
  box-shadow: -4px 0 24px var(--lx-shadow-color);
  display: flex;
  flex-direction: column;
  will-change: transform;
}

.drawer-inner {
  padding: 20px 18px 24px;
  overflow-y: auto;
  flex: 1;
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

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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
  color: var(--lx-danger);
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

.chat-drawer-enter-active,
.chat-drawer-leave-active {
  transition: opacity 0.25s ease;
}

.chat-drawer-enter-active .drawer-panel,
.chat-drawer-leave-active .drawer-panel {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-drawer-enter-from,
.chat-drawer-leave-to {
  opacity: 0;
}

.chat-drawer-enter-from .drawer-panel,
.chat-drawer-leave-to .drawer-panel {
  transform: translateX(100%);
}
</style>
