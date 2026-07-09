<script setup lang="ts">
/**
 * 群资料侧滑抽屉。
 * <p>
 * 展示群头像、群号、成员网格、公告、备注、置顶/免打扰及退群等操作。
 * </p>
 */
import { ref, computed, watch } from 'vue'
import { NIcon, NSwitch, useMessage, useDialog } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import PinIcon from '../icons/PinIcon.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'

const message = useMessage()
const dialog = useDialog()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { groupInfoDrawerOpen } = storeToRefs(chatModalsStore)
const { closeGroupInfo, openGroupAnnouncement, openAddMembers } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)
const {
  toggleSessionPin,
  toggleSessionMute,
  clearSessionMessages,
  leaveGroup
} = appStore

// 群备注输入（与 store 同步）
const groupRemark = ref('')

/** 群公告短文本 */
const announcement = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.announcementShort(id) : ''
})

// 切换会话时加载对应群备注
watch(
  currentSessionId,
  id => {
    groupRemark.value = id ? groupMetaStore.remarkFor(id) : ''
  },
  { immediate: true }
)

/** 失焦时保存群备注到 groupMetaStore */
function saveRemark() {
  const id = currentSessionId.value
  if (!id) return
  groupMetaStore.setRemark(id, groupRemark.value)
  message.success('群备注已保存')
}

/** 展示用群号：从 sessionId 提取数字后缀，缺省为 mock 群号 */
const groupId = computed(() => currentSessionId.value?.replace(/\D/g, '').slice(-10) || '1007446249')

/** 当前群成员列表 */
const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.membersFor(id)
})

/** 设置群会话置顶 */
function setPin(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.pinned === val) return
  toggleSessionPin(currentSessionId.value)
}

/** 设置群消息免打扰 */
function setMute(val: boolean) {
  if (!currentSessionId.value || !!currentSession.value?.muted === val) return
  toggleSessionMute(currentSessionId.value)
}

/** 关闭抽屉 */
function close() {
  closeGroupInfo()
}

/** 复制群号到剪贴板 */
function shareGroup() {
  navigator.clipboard.writeText(`群号：${groupId.value}`)
  message.success('群号已复制')
}

/** 二次确认清空群聊天记录 */
function clearChat() {
  if (!currentSessionId.value) return
  dialog.warning({
    title: '删除聊天记录',
    content: '确定清空本群聊天记录？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: () => {
      clearSessionMessages(currentSessionId.value!)
      message.success('聊天记录已清空')
    }
  })
}

/** 二次确认退出群聊 */
function quitGroup() {
  if (!currentSession.value || !currentSessionId.value) return
  dialog.warning({
    title: '退出群聊',
    content: `确定退出「${currentSession.value.name}」？`,
    positiveText: '退出',
    negativeText: '取消',
    onPositiveClick: () => {
      leaveGroup(currentSessionId.value!)
      message.success('已退出群聊')
      close()
    }
  })
}

/** 举报群（原型提示） */
function reportGroup() {
  message.info('举报已记录，感谢反馈')
}
</script>

<template>
  <!-- 群资料右侧抽屉 -->
  <Transition name="chat-drawer">
    <div v-if="groupInfoDrawerOpen" class="drawer-root" @click.self="close">
      <aside class="drawer-panel" @click.stop>
        <div class="drawer-scroll">
              <!-- 群头部：头像、名称、群号、分享 -->
              <div class="group-hero">
                <Avatar
                  :text="currentSession?.avatarText || '群'"
                  :color="currentSession?.avatarColor || '#e74c3c'"
                  :size="56"
                />
                <h2 class="g-name">{{ currentSession?.name || '群聊' }}</h2>
                <p class="g-id">群号：{{ groupId }}</p>
                <button type="button" class="share-btn" @click="shareGroup">分享</button>
              </div>

              <!-- 成员头像网格 -->
              <section class="block">
                <div class="block-head">
                  <span>群聊成员</span>
                  <n-icon :component="SearchOutline" :size="18" class="ico" />
                </div>
                <div class="avatar-grid">
                  <div v-for="m in members.slice(0, 14)" :key="m.id" class="av">
                    <Avatar :text="m.avatarText" :color="m.avatarColor" :size="40" />
                  </div>
                  <button type="button" class="av invite" title="邀请" @click="openAddMembers">+</button>
                </div>
              </section>

              <!-- 群公告 -->
              <section class="block">
                <h3 class="block-title">群公告</h3>
                <button type="button" class="announce announce-btn" @click="openGroupAnnouncement">
                  {{ announcement }}
                </button>
              </section>

              <!-- 本群昵称（只读） -->
              <section class="block row-item">
                <span>我的本群昵称</span>
                <span class="muted">{{ userProfile.nickname }}</span>
              </section>

              <!-- 群备注编辑 -->
              <section class="block">
                <div class="row-item"><span>群聊备注</span></div>
                <input
                  v-model="groupRemark"
                  type="text"
                  class="remark-input"
                  placeholder="填写备注"
                  @blur="saveRemark"
                />
              </section>

              <!-- 置顶与免打扰 -->
              <div class="switch-block">
                <div class="switch-row">
                  <span class="switch-label">
                    <PinIcon :size="16" />
                    设为置顶
                  </span>
                  <n-switch :value="!!currentSession?.pinned" size="small" @update:value="setPin" />
                </div>
                <div class="switch-row">
                  <span>消息免打扰</span>
                  <n-switch :value="!!currentSession?.muted" size="small" @update:value="setMute" />
                </div>
                <p class="hint">接收消息但不提醒</p>
              </div>

              <!-- 危险操作与举报 -->
              <button type="button" class="action-btn" @click="clearChat">删除聊天记录</button>
              <button type="button" class="action-btn danger" @click="quitGroup">退出群聊</button>
              <p class="report">
                <a href="#" @click.prevent="reportGroup">被骚扰了？举报该群</a>
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
  width: min(320px, 88%);
  max-width: 360px;
  background: var(--lx-bg-card);
  box-shadow: -4px 0 24px var(--lx-shadow-color);
  display: flex;
  flex-direction: column;
  will-change: transform;
}

.drawer-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 20px 18px 28px;
}

.group-hero {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: 12px;
}

.group-hero :deep(.avatar) {
  margin: 0 auto 10px;
}

.g-name {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: 1.3;
}

.g-id {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.share-btn {
  min-width: 88px;
  height: 32px;
  border-radius: var(--lx-radius);
  border: 1px solid var(--lx-border-strong);
  background: var(--lx-bg-card);
  font-size: 13px;
  cursor: pointer;
  color: var(--lx-text-body);
}

.block {
  padding: 12px 0;
  border-bottom: 1px solid var(--lx-bg-panel);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
  margin-bottom: 10px;
}

.ico {
  color: var(--lx-text-muted);
  cursor: pointer;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.av {
  display: flex;
  justify-content: center;
}

.invite {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-avatar-radius);
  border: 1px dashed var(--lx-border-strong);
  background: var(--lx-bg-panel);
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.announce {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--lx-text-secondary);
  word-break: break-all;
}

.announce-btn {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.announce-btn:hover {
  color: var(--lx-accent);
}

.row-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: var(--lx-text-body);
}

.muted {
  color: var(--lx-text-muted);
  font-size: 13px;
}

.remark-input {
  width: 100%;
  margin-top: 8px;
  height: 36px;
  border: none;
  border-bottom: 1px solid var(--lx-border-light);
  font-size: 13px;
  outline: none;
  color: var(--lx-text-body);
  background: transparent;
}

.switch-block {
  padding: 8px 0 16px;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  font-size: 14px;
  color: var(--lx-text-body);
}

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hint {
  margin: -4px 0 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.action-btn {
  width: 100%;
  height: 40px;
  border: none;
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  font-size: 14px;
  color: var(--lx-text-body);
  cursor: pointer;
  margin-bottom: 10px;
}

.action-btn.danger {
  background: transparent;
  color: #e34d59;
}

.report {
  text-align: center;
  margin: 16px 0 0;
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
