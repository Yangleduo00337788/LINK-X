<script setup lang="ts">
/**
 * 群资料侧滑抽屉。
 * <p>
 * 展示群头像、群号、成员网格、公告、备注、置顶/免打扰等操作。
 * 普通成员可退出群聊；群主可转让群主或解散群聊（不可直接退出）。
 * 群主可修改群名称；任意成员可设置仅自己可见的群备注。
 * </p>
 */
import { ref, computed, watch } from 'vue'
import { NIcon, NSwitch, useMessage, useDialog } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import GroupAvatar from '../GroupAvatar.vue'
import GroupMutePanel from './GroupMutePanel.vue'
import GroupReportPanel from './GroupReportPanel.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'

const { t } = useI18n()
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
  clearSessionMessages
} = appStore

// 群备注 / 群名称输入（与 store 同步）
const groupRemark = ref('')
const groupNameInput = ref('')
const savingName = ref(false)

/** 群公告短文本 */
const announcement = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.announcementShort(id) : ''
})

/** 切换会话或备注异步加载完成后，回填输入框 */
watch(
  () => {
    const id = currentSessionId.value
    if (!id) return { remark: '', groupName: '' }
    return {
      remark: groupMetaStore.remarkFor(id),
      groupName: currentSession.value?.groupName || currentSession.value?.name || ''
    }
  },
  ({ remark, groupName }) => {
    groupRemark.value = remark
    groupNameInput.value = groupName
  },
  { immediate: true }
)

/** 失焦时保存群备注到服务端 */
async function saveRemark() {
  const id = currentSessionId.value
  if (!id) return
  const ok = await groupMetaStore.setRemark(id, groupRemark.value)
  if (ok) {
    message.success(t('modals.remarkSaved'))
  } else {
    message.error(t('extra.opFail'))
  }
}

/** 失焦时保存群名称（仅群主） */
async function saveGroupName() {
  const id = currentSessionId.value
  if (!id || !isOwner.value) return
  const next = groupNameInput.value.trim()
  const current = currentSession.value?.groupName || currentSession.value?.name || ''
  if (!next || next === current) {
    groupNameInput.value = current
    return
  }
  savingName.value = true
  try {
    const ok = await groupMetaStore.renameGroup(id, next)
    if (ok) {
      message.success(t('modals.groupNameSaved'))
      groupNameInput.value = currentSession.value?.groupName || next
    } else {
      message.error(t('modals.groupNameSaveFail'))
      groupNameInput.value = current
    }
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('modals.groupNameSaveFail'))
    groupNameInput.value = current
  } finally {
    savingName.value = false
  }
}

/** 展示用群号：从 sessionId 提取数字后缀 */
const groupId = computed(() => currentSessionId.value?.replace(/\D/g, '').slice(-10) || '—')

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
  navigator.clipboard.writeText(t('modals.groupIdCopy', { id: groupId.value }))
  message.success(t('modals.groupIdCopied'))
}

/** 二次确认清空群聊天记录 */
function clearChat() {
  if (!currentSessionId.value) return
  dialog.warning({
    title: t('modals.clearChatHistory'),
    content: t('modals.clearGroupChatConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: () => {
      clearSessionMessages(currentSessionId.value!)
      message.success(t('modals.chatCleared'))
    }
  })
}

/** 从接口错误中取出可读文案 */
function apiErrorMessage(e: unknown, fallback: string): string {
  const ax = e as { response?: { data?: { message?: string } }; message?: string }
  return ax.response?.data?.message || ax.message || fallback
}

/** 二次确认退出群聊（非群主） */
async function quitGroup() {
  if (!currentSession.value || !currentSessionId.value) return
  if (isOwner.value) {
    message.warning(t('modals.transferOwnerHint'))
    return
  }
  dialog.warning({
    title: t('modals.quitGroup'),
    content: t('modals.quitGroupConfirm', { name: currentSession.value.name }),
    positiveText: t('modals.quit'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await appStore.leaveGroup(currentSessionId.value!)
        message.success(t('modals.quitOk'))
        close()
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.quitFail')))
      }
    }
  })
}

/** 可转让的成员（排除自己） */
const transferCandidates = computed(() => {
  const me = appStore.userProfile.userId
  return members.value.filter(m => m.id !== me)
})

const transferPanelOpen = ref(false)
const transferring = ref(false)

/** 打开转让群主面板 */
async function openTransferOwner() {
  const id = currentSessionId.value
  if (!id) return
  await groupMetaStore.fetchMembers(id)
  if (transferCandidates.value.length === 0) {
    message.warning(t('modals.transferOwnerEmpty'))
    return
  }
  transferPanelOpen.value = true
}

function closeTransferPanel() {
  transferPanelOpen.value = false
}

/** 确认转让给指定成员 */
function confirmTransfer(memberId: string, memberName: string) {
  if (!currentSessionId.value || transferring.value) return
  dialog.warning({
    title: t('modals.transferOwner'),
    content: t('modals.transferOwnerConfirm', { name: memberName }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      transferring.value = true
      try {
        await appStore.transferGroupOwner(currentSessionId.value!, memberId)
        message.success(t('modals.transferOwnerOk'))
        closeTransferPanel()
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.transferOwnerFail')))
      } finally {
        transferring.value = false
      }
    }
  })
}

/** 可管理角色的成员（排除自己与群主） */
const adminCandidates = computed(() => {
  const me = appStore.userProfile.userId
  return members.value.filter(m => m.id !== me && m.role !== 'owner')
})

const adminPanelOpen = ref(false)
const updatingRole = ref(false)

/** 打开设置管理员面板 */
async function openManageAdmins() {
  const id = currentSessionId.value
  if (!id) return
  await groupMetaStore.fetchMembers(id)
  if (adminCandidates.value.length === 0) {
    message.warning(t('modals.manageAdminsEmpty'))
    return
  }
  adminPanelOpen.value = true
}

function closeAdminPanel() {
  adminPanelOpen.value = false
}

/** 设为 / 取消管理员 */
function toggleAdmin(memberId: string, memberName: string, isAdmin: boolean) {
  if (!currentSessionId.value || updatingRole.value) return
  const nextRole = isAdmin ? 'member' : 'admin'
  dialog.warning({
    title: isAdmin ? t('modals.unsetAdmin') : t('modals.setAdmin'),
    content: isAdmin
      ? t('modals.unsetAdminConfirm', { name: memberName })
      : t('modals.setAdminConfirm', { name: memberName }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      updatingRole.value = true
      try {
        await appStore.updateMemberRole(currentSessionId.value!, memberId, nextRole)
        message.success(isAdmin ? t('modals.unsetAdminOk') : t('modals.setAdminOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.setAdminFail')))
      } finally {
        updatingRole.value = false
      }
    }
  })
}

/** 解散群聊（仅 owner） */
async function dissolve() {
  if (!currentSession.value || !currentSessionId.value) return
  dialog.warning({
    title: t('modals.dissolveGroup'),
    content: t('modals.dissolveConfirm', { name: currentSession.value.name }),
    positiveText: t('modals.dissolve'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await appStore.dissolveGroup(currentSessionId.value!)
        message.success(t('modals.dissolveOk'))
        close()
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.dissolveFail')))
      }
    }
  })
}

/** 当前用户是否为群主（用于显示群主专属按钮） */
const isOwner = computed(() => {
  const me = appStore.userProfile.userId
  if (!me) return false
  return members.value.some(m => m.id === me && m.role === 'owner')
})

/** 群主或管理员（可管理禁言） */
const isAdminOrOwner = computed(() => {
  const me = appStore.userProfile.userId
  if (!me) return false
  return members.value.some(m => m.id === me && (m.role === 'owner' || m.role === 'admin'))
})

const mutePanelOpen = ref(false)
const reportPanelOpen = ref(false)

function openMutePanel() {
  mutePanelOpen.value = true
}

function closeMutePanel() {
  mutePanelOpen.value = false
}

function openReportPanel() {
  reportPanelOpen.value = true
}

function closeReportPanel() {
  reportPanelOpen.value = false
}

function onReportSubmitted() {
  reportPanelOpen.value = false
  close()
}

/** 打开抽屉时拉取成员与禁言状态 */
watch(groupInfoDrawerOpen, open => {
  if (open && currentSessionId.value) {
    void groupMetaStore.fetchMembers(currentSessionId.value)
    void groupMetaStore.fetchAnnouncement(currentSessionId.value)
  } else {
    transferPanelOpen.value = false
    adminPanelOpen.value = false
    mutePanelOpen.value = false
    reportPanelOpen.value = false
  }
})

/** 举报群（打开举报页） */
function reportGroup() {
  openReportPanel()
}
</script>

<template>
  <!-- 群资料右侧抽屉 -->
  <Transition name="chat-drawer">
    <div v-if="groupInfoDrawerOpen" class="drawer-root" @click.self="close">
      <aside class="drawer-panel" @click.stop>
        <div class="drawer-scroll">
              <!-- 群头部：左侧头像，右侧名称/群号/分享 -->
              <div class="group-hero">
                <GroupAvatar
                  :text="currentSession?.avatarText || t('modals.groupChar')"
                  :color="currentSession?.avatarColor || '#e74c3c'"
                  :size="56"
                  :image-url="currentSession?.avatarUrl"
                  :faces="currentSession?.memberAvatars || members.slice(0, 9).map(m => ({
                    text: m.avatarText,
                    color: m.avatarColor,
                    imageUrl: m.avatarUrl
                  }))"
                />
                <div class="hero-meta">
                  <h2 class="g-name">{{ currentSession?.name || t('modals.groupChat') }}</h2>
                  <p
                    v-if="currentSession?.groupRemark && currentSession?.groupName"
                    class="g-real-name"
                  >
                    {{ t('modals.groupRealName', { name: currentSession.groupName }) }}
                  </p>
                  <p class="g-id">{{ t('modals.groupIdLabel', { id: groupId }) }}</p>
                  <button type="button" class="share-btn" @click="shareGroup">{{ t('modals.share') }}</button>
                </div>
              </div>

              <!-- 成员头像网格 -->
              <section class="block">
                <div class="block-head">
                  <span>{{ t('modals.groupMembers') }}</span>
                  <n-icon :component="SearchOutline" :size="18" class="ico" />
                </div>
                <div class="avatar-grid">
                  <div v-for="m in members.slice(0, 14)" :key="m.id" class="av">
                    <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="40" />
                  </div>
                  <button type="button" class="av invite" :title="t('chat.invite')" @click="openAddMembers">+</button>
                </div>
              </section>

              <!-- 群名称（群主可改） -->
              <section class="block">
                <div class="row-item"><span>{{ t('modals.groupName') }}</span></div>
                <input
                  v-model="groupNameInput"
                  type="text"
                  class="remark-input"
                  :placeholder="t('modals.groupNamePh')"
                  :readonly="!isOwner"
                  :disabled="savingName"
                  maxlength="50"
                  @blur="saveGroupName"
                  @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
                />
                <p v-if="!isOwner" class="field-hint">{{ t('modals.groupNameOwnerOnly') }}</p>
              </section>

              <!-- 群公告 -->
              <section class="block">
                <h3 class="block-title">{{ t('chat.groupAnnouncement') }}</h3>
                <button type="button" class="announce announce-btn" @click="openGroupAnnouncement">
                  {{ announcement }}
                </button>
              </section>

              <!-- 本群昵称（只读） -->
              <section class="block row-item">
                <span>{{ t('modals.myGroupNickname') }}</span>
                <span class="muted">{{ userProfile.nickname }}</span>
              </section>

              <!-- 群备注编辑 -->
              <section class="block">
                <div class="row-item"><span>{{ t('modals.groupRemark') }}</span></div>
                <input
                  v-model="groupRemark"
                  type="text"
                  class="remark-input"
                  :placeholder="t('modals.remarkPh')"
                  maxlength="64"
                  @blur="saveRemark"
                  @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
                />
                <p class="field-hint">{{ t('modals.remarkHint') }}</p>
              </section>

              <!-- 置顶与免打扰 -->
              <div class="switch-block">
                <div class="switch-row">
                  <span>{{ t('modals.pinSession') }}</span>
                  <n-switch :value="!!currentSession?.pinned" size="small" @update:value="setPin" />
                </div>
                <div class="switch-row">
                  <span>{{ t('modals.muteMessages') }}</span>
                  <n-switch :value="!!currentSession?.muted" size="small" @update:value="setMute" />
                </div>
                <p class="hint">{{ t('modals.muteHint') }}</p>
              </div>

              <!-- 危险操作与举报 -->
              <button type="button" class="action-btn" @click="clearChat">{{ t('modals.clearChatHistory') }}</button>
              <button
                v-if="isAdminOrOwner"
                type="button"
                class="action-btn"
                @click="openMutePanel"
              >
                {{ t('modals.groupMute') }}
              </button>
              <button
                v-if="!isOwner"
                type="button"
                class="action-btn danger"
                @click="quitGroup"
              >
                {{ t('modals.quitGroup') }}
              </button>
              <button
                v-if="isOwner"
                type="button"
                class="action-btn"
                @click="openManageAdmins"
              >
                {{ t('modals.manageAdmins') }}
              </button>
              <button
                v-if="isOwner"
                type="button"
                class="action-btn"
                @click="openTransferOwner"
              >
                {{ t('modals.transferOwner') }}
              </button>
              <button
                v-if="isOwner"
                type="button"
                class="action-btn danger"
                @click="dissolve"
              >
                {{ t('modals.dissolveGroup') }}
              </button>
              <p class="report">
                <a href="#" @click.prevent="reportGroup">{{ t('modals.reportGroup') }}</a>
              </p>
        </div>

        <!-- 转让群主：选择新群主 -->
        <div v-if="transferPanelOpen" class="transfer-panel">
          <div class="transfer-head">
            <button type="button" class="transfer-back" @click="closeTransferPanel">‹</button>
            <h3>{{ t('modals.transferOwnerPick') }}</h3>
          </div>
          <p class="transfer-hint">{{ t('modals.transferOwnerHint') }}</p>
          <div class="transfer-list">
            <button
              v-for="m in transferCandidates"
              :key="m.id"
              type="button"
              class="transfer-row"
              :disabled="transferring"
              @click="confirmTransfer(m.id, m.name)"
            >
              <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="36" />
              <span class="transfer-name">{{ m.name }}</span>
              <span v-if="m.badge" class="transfer-badge">{{ m.badge }}</span>
            </button>
          </div>
        </div>

        <!-- 设置管理员 -->
        <div v-if="adminPanelOpen" class="transfer-panel">
          <div class="transfer-head">
            <button type="button" class="transfer-back" @click="closeAdminPanel">‹</button>
            <h3>{{ t('modals.manageAdminsPick') }}</h3>
          </div>
          <p class="transfer-hint">{{ t('modals.manageAdminsHint') }}</p>
          <div class="transfer-list">
            <div
              v-for="m in adminCandidates"
              :key="m.id"
              class="transfer-row admin-row"
            >
              <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="36" />
              <span class="transfer-name">{{ m.name }}</span>
              <span v-if="m.badge" class="transfer-badge">{{ m.badge }}</span>
              <button
                type="button"
                class="role-action"
                :class="{ danger: m.role === 'admin' }"
                :disabled="updatingRole"
                @click="toggleAdmin(m.id, m.name, m.role === 'admin')"
              >
                {{ m.role === 'admin' ? t('modals.unsetAdmin') : t('modals.setAdmin') }}
              </button>
            </div>
          </div>
        </div>

        <!-- 群聊禁言（全体 / 定时 CRUD / 指定成员） -->
        <GroupMutePanel
          v-if="mutePanelOpen && currentSessionId"
          :session-id="currentSessionId"
          :is-owner="isOwner"
          @back="closeMutePanel"
        />

        <!-- 举报群聊 -->
        <GroupReportPanel
          v-if="reportPanelOpen && currentSessionId"
          :group-id="currentSessionId"
          :group-name="currentSession?.groupName || currentSession?.name || ''"
          @back="closeReportPanel"
          @submitted="onReportSubmitted"
        />
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
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: 12px;
}

.group-hero :deep(.avatar),
.group-hero :deep(.group-avatar) {
  flex-shrink: 0;
}

.hero-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.g-name {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.g-real-name {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.g-id {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--lx-text-muted);
}

.share-btn {
  min-width: 72px;
  height: 28px;
  padding: 0 14px;
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

.remark-input[readonly] {
  color: var(--lx-text-secondary);
  cursor: default;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--lx-text-muted);
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
  color: var(--lx-danger);
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

.transfer-panel {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  padding: 16px 14px 20px;
}

.transfer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.transfer-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.transfer-back {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--lx-text-body);
  cursor: pointer;
}

.transfer-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--lx-text-muted);
  line-height: 1.45;
}

.transfer-list {
  flex: 1;
  overflow-y: auto;
}

.transfer-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border: none;
  border-radius: var(--lx-radius);
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--lx-text-body);
}

.transfer-row:hover:not(:disabled) {
  background: var(--lx-bg-panel);
}

.transfer-row:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.transfer-name {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transfer-badge {
  font-size: 11px;
  color: var(--lx-text-muted);
}

.admin-row {
  cursor: default;
}

.admin-row:hover {
  background: transparent;
}

.role-action {
  flex-shrink: 0;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--lx-border-strong);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-card);
  font-size: 12px;
  color: var(--lx-accent);
  cursor: pointer;
}

.role-action.danger {
  color: var(--lx-danger);
  border-color: color-mix(in srgb, var(--lx-danger) 35%, var(--lx-border-light));
}

.role-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
