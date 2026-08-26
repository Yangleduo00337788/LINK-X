<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群资料侧滑抽屉。
 * <p>
 * 展示群头像、群号、成员网格、公告、备注、置顶/免打扰等操作。
 * 普通成员可退出群聊；群主可转让群主或解散群聊（不可直接退出）。
 * 群主可修改群名称；任意成员可设置仅自己可见的群备注。
 * </p>
 */
import { ref, computed, watch, nextTick } from 'vue'
import { NIcon, NSwitch, useMessage, useDialog } from 'naive-ui'
import { SearchOutline, CloseOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import GroupAvatar from '../GroupAvatar.vue'
import GroupMutePanel from './GroupMutePanel.vue'
import GroupReportPanel from './GroupReportPanel.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useNotificationsStore } from '../../stores/notifications'
import * as groupApi from '../../api/group'
import * as conferenceApi from '../../api/conference'
import type { ConferenceInfo } from '../../api/conference'
import { useI18n } from '../../i18n'
import type { GroupMember } from '../../stores/groupMeta'
import { LxButton, LxIconButton } from '../ui'
import GroupAiSidebarBlock from './GroupAiSidebarBlock.vue'

const { t } = useI18n()
const message = useMessage()
const dialog = useDialog()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const notificationsStore = useNotificationsStore()
const { pendingJoinRequestConversationId } = storeToRefs(notificationsStore)
const { groupInfoDrawerOpen, addMembersOpen } = storeToRefs(chatModalsStore)
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

/** 群公告短文本（无内容时显示占位，保证入口可点） */
const announcement = computed(() => {
  const id = currentSessionId.value
  if (!id) return t('extra.noAnnouncement')
  const text = groupMetaStore.announcementShort(id)
  return text || t('extra.noAnnouncement')
})

const announcementEmpty = computed(() => {
  const id = currentSessionId.value
  if (!id) return true
  return !groupMetaStore.announcementShort(id)
})

const joinApproval = ref(false)
const inviteOwnerOnly = ref(false)
const announcementReadCount = ref<number | null>(null)
const CONFERENCE_HISTORY_PREVIEW_COUNT = 3
const conferenceHistory = ref<ConferenceInfo[]>([])
const conferenceHistoryLoading = ref(false)
const conferenceHistoryExpanded = ref(false)
const memberSelectMode = ref(false)
const selectedMemberIds = ref<string[]>([])
const memberSearch = ref('')
const showMemberSearch = ref(false)
const memberSearchInputRef = ref<HTMLInputElement | null>(null)
const joinRequests = ref<groupApi.GroupJoinRequestItem[]>([])
const joinRequestsLoading = ref(false)
const joinHandlingId = ref<string | null>(null)

async function refreshGroupPolicy() {
  const id = currentSessionId.value
  if (!id) return
  try {
    const res = await groupApi.getGroupInfo(id)
    if (res.code === 200 && res.data) {
      joinApproval.value = !!res.data.joinApproval
      inviteOwnerOnly.value = res.data.invitePolicy === 'ownerApprove'
    }
  } catch {
    /* ignore */
  }
}

async function refreshJoinRequests() {
  const id = currentSessionId.value
  if (!id) {
    joinRequests.value = []
    return
  }
  joinRequestsLoading.value = true
  try {
    const res = await groupApi.listJoinRequests(id)
    if (res.code === 200 && res.data) {
      joinRequests.value = res.data.map(item => ({
        ...item,
        applicantId: String(item.applicantId)
      }))
    } else {
      joinRequests.value = []
    }
  } catch {
    // 非管理员会 403，静默清空
    joinRequests.value = []
  } finally {
    joinRequestsLoading.value = false
  }
}

async function handleJoin(applicantId: string, approve: boolean) {
  const id = currentSessionId.value
  if (!id || !applicantId) return
  joinHandlingId.value = applicantId
  try {
    const res = await groupApi.handleJoinRequest(id, applicantId, approve)
    if (res.code !== 200) {
      throw new Error(res.message || t('modals.joinHandleFail'))
    }
    message.success(approve ? t('modals.joinApproved') : t('modals.joinRejected'))
    joinRequests.value = joinRequests.value.filter(r => r.applicantId !== applicantId)
    if (approve) {
      await groupMetaStore.fetchMembers(id, true)
    }
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('modals.joinHandleFail'))
  } finally {
    joinHandlingId.value = null
  }
}

async function refreshAnnouncementRead() {
  const id = currentSessionId.value
  if (!id || announcementEmpty.value) {
    announcementReadCount.value = null
    return
  }
  try {
    await groupApi.markAnnouncementRead(id)
    const res = await groupApi.getAnnouncementReadCount(id)
    if (res.code === 200 && res.data != null) {
      announcementReadCount.value = Number(res.data)
    }
  } catch {
    announcementReadCount.value = null
  }
}

async function loadConferenceHistory() {
  const id = currentSessionId.value
  conferenceHistoryExpanded.value = false
  if (!id) {
    conferenceHistory.value = []
    return
  }
  conferenceHistoryLoading.value = true
  try {
    const res = await conferenceApi.history(id)
    conferenceHistory.value = res.code === 200 && res.data ? res.data : []
  } catch {
    conferenceHistory.value = []
  } finally {
    conferenceHistoryLoading.value = false
  }
}

const visibleConferenceHistory = computed(() => {
  if (conferenceHistoryExpanded.value) return conferenceHistory.value
  return conferenceHistory.value.slice(0, CONFERENCE_HISTORY_PREVIEW_COUNT)
})

const conferenceHistoryHasMore = computed(
  () => conferenceHistory.value.length > CONFERENCE_HISTORY_PREVIEW_COUNT
)

function toggleConferenceHistory() {
  conferenceHistoryExpanded.value = !conferenceHistoryExpanded.value
}

function formatConferenceTime(raw?: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

watch(
  () => pendingJoinRequestConversationId.value,
  convId => {
    const id = String(convId || '').trim()
    if (!id || id !== currentSessionId.value) {
      notificationsStore.clearPendingJoinRequest()
      return
    }
    void refreshJoinRequests().finally(() => notificationsStore.clearPendingJoinRequest())
  }
)

watch(
  () => currentSessionId.value,
  () => {
    void refreshGroupPolicy()
    void refreshAnnouncementRead()
    void refreshJoinRequests()
    void loadConferenceHistory()
  },
  { immediate: true }
)

async function setJoinApproval(val: boolean) {
  const id = currentSessionId.value
  if (!id || !isAdminOrOwner.value) return
  const prev = joinApproval.value
  joinApproval.value = val
  try {
    const res = await groupApi.setJoinApproval(id, val)
    if (res.code !== 200) throw new Error(res.message || 'failed')
    await refreshJoinRequests()
  } catch (e) {
    joinApproval.value = prev
    message.error(apiErrorMessage(e, t('modals.groupNameSaveFail')))
  }
}

async function setInviteOwnerOnly(val: boolean) {
  const id = currentSessionId.value
  if (!id || !isOwner.value) return
  const prev = inviteOwnerOnly.value
  inviteOwnerOnly.value = val
  try {
    const res = await groupApi.setInvitePolicy(id, val ? 'ownerApprove' : 'anyMember')
    if (res.code !== 200) throw new Error(res.message || 'failed')
    await refreshGroupPolicy()
  } catch (e) {
    inviteOwnerOnly.value = prev
    message.error(apiErrorMessage(e, t('modals.groupNameSaveFail')))
  }
}

function canSelectMember(m: GroupMember): boolean {
  const me = userProfile.value.userId
  if (!me || m.id === me || m.role === 'owner') return false
  if (isOwner.value) return true
  return isAdminOrOwner.value && m.role !== 'admin'
}

function toggleMemberSelect(memberId: string) {
  if (!memberSelectMode.value || !isAdminOrOwner.value) return
  const target = members.value.find(m => m.id === memberId)
  if (!target || !canSelectMember(target)) return
  const idx = selectedMemberIds.value.indexOf(memberId)
  if (idx >= 0) selectedMemberIds.value.splice(idx, 1)
  else selectedMemberIds.value.push(memberId)
}

function toggleMemberSelectMode() {
  if (memberSelectMode.value) {
    exitMemberSelect()
    return
  }
  memberSelectMode.value = true
  selectedMemberIds.value = []
}

function exitMemberSelect() {
  memberSelectMode.value = false
  selectedMemberIds.value = []
}

function toggleMemberSearch() {
  showMemberSearch.value = !showMemberSearch.value
  if (!showMemberSearch.value) {
    memberSearch.value = ''
    return
  }
  void nextTick(() => memberSearchInputRef.value?.focus())
}

function onMemberAvatarClick(member: GroupMember) {
  if (memberSelectMode.value) {
    toggleMemberSelect(member.id)
    return
  }
  if (isAdminOrOwner.value && canSelectMember(member)) {
    void removeOneMember(member.id)
  }
}

async function batchRemoveSelected() {
  const id = currentSessionId.value
  if (!id || selectedMemberIds.value.length === 0) return
  dialog.warning({
    title: t('modals.batchRemoveTitle'),
    content: t('modals.batchRemoveContent', { n: selectedMemberIds.value.length }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        const res = await groupApi.batchRemoveMembers(id, [...selectedMemberIds.value])
        if (res.code !== 200) throw new Error(res.message || 'failed')
        // fetchMembers 必须在 exitMemberSelect 之前完成，否则 memberSelectMode 切走
        // 导致成员列表组件卸载，来不及渲染最新数据（仍显示被删成员）
        await groupMetaStore.fetchMembers(id, true)
        exitMemberSelect()
        message.success(t('modals.batchRemoveOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.batchRemoveFail')))
      }
    }
  })
}

async function batchMuteSelected() {
  const id = currentSessionId.value
  if (!id || selectedMemberIds.value.length === 0) return
  dialog.warning({
    title: t('modals.groupMute'),
    content: t('modals.batchMuteContent', { n: selectedMemberIds.value.length }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        const res = await groupApi.batchMuteMembers(id, [...selectedMemberIds.value], true)
        if (res.code !== 200) throw new Error(res.message || 'failed')
        await groupMetaStore.fetchMembers(id, true)
        exitMemberSelect()
        message.success(t('modals.batchMuteOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.batchMuteFail')))
      }
    }
  })
}

async function removeOneMember(memberId: string) {
  const id = currentSessionId.value
  if (!id || !isAdminOrOwner.value) return
  dialog.warning({
    title: t('modals.removeMemberTitle'),
    content: t('modals.removeMemberContent'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await appStore.removeGroupMember(id, memberId)
        message.success(t('modals.removeMemberOk'))
      } catch (e) {
        message.error(apiErrorMessage(e, t('modals.removeMemberFail')))
      }
    }
  })
}

/** 打开群公告管理（CRUD 弹窗）；无公告时管理员可直接发布 */
function openAnnouncementManage() {
  openGroupAnnouncement()
  void refreshAnnouncementRead()
}

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

/** 当前群成员列表（只读 store；加载由下方 watch 触发，避免 computed 副作用请求风暴） */
const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  void groupMetaStore.membersRefreshSeq[id]
  return groupMetaStore.membersFor(id)
})

const filteredMembers = computed(() => {
  const q = memberSearch.value.trim().toLowerCase()
  if (!q) return members.value
  return members.value.filter(
    m => m.name.toLowerCase().includes(q) || m.badge?.toLowerCase().includes(q)
  )
})

const displayedMembers = computed(() => {
  const list = filteredMembers.value
  if (showMemberSearch.value || memberSelectMode.value) return list
  return list.slice(0, 14)
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
    // force：避免加人后仍展示旧缓存
    void groupMetaStore.fetchMembers(currentSessionId.value, true)
    void groupMetaStore.fetchAnnouncement(currentSessionId.value)
    void refreshGroupPolicy()
    void refreshJoinRequests()
    void loadConferenceHistory()
  } else {
    transferPanelOpen.value = false
    adminPanelOpen.value = false
    mutePanelOpen.value = false
    reportPanelOpen.value = false
    conferenceHistoryExpanded.value = false
    exitMemberSelect()
    showMemberSearch.value = false
    memberSearch.value = ''
  }
}, { immediate: true })

/** 切换会话时刷新成员（抽屉已打开状态下切换） */
watch(currentSessionId, newId => {
  if (newId && groupInfoDrawerOpen.value) {
    void groupMetaStore.fetchMembers(newId, true)
  }
})

/** 添加成员模态框关闭后刷新成员列表 */
watch(addMembersOpen, async (open, prev) => {
  if (open === false && prev === true && currentSessionId.value) {
    await groupMetaStore.fetchMembers(currentSessionId.value, true)
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
                  :size="56"
                  :image-url="currentSession?.avatarUrl"
                  :default-image-url="
                    currentSession?.ownerAvatarUrl ||
                    members.find(m => m.role === 'owner')?.avatarUrl
                  "
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
                  <LxButton variant="share" @click="shareGroup">{{ t('modals.share') }}</LxButton>
                </div>
              </div>

              <!-- 成员头像网格 -->
              <section class="block">
                <div class="block-head">
                  <span>{{ t('modals.groupMembers') }} ({{ members.length }})</span>
                  <div class="block-head-actions">
                    <button
                      v-if="isAdminOrOwner"
                      type="button"
                      class="lx-link-btn lx-link-btn--head"
                      @click.stop="toggleMemberSelectMode"
                    >
                      {{ memberSelectMode ? t('common.cancel') : t('modals.batchManage') }}
                    </button>
                    <LxIconButton
                      :title="t('extra.searchMembers')"
                      @click.stop="toggleMemberSearch"
                    >
                      <n-icon :component="showMemberSearch ? CloseOutline : SearchOutline" :size="18" />
                    </LxIconButton>
                  </div>
                </div>
                <div v-if="showMemberSearch" class="member-search">
                  <input
                    ref="memberSearchInputRef"
                    v-model="memberSearch"
                    type="text"
                    class="member-search-input"
                    :placeholder="t('extra.searchMembersPh')"
                  />
                </div>
                <p v-if="memberSelectMode" class="field-hint">{{ t('modals.batchManageHint') }}</p>
                <p v-if="showMemberSearch && displayedMembers.length === 0" class="muted">
                  {{ t('extra.noMatchMembers') }}
                </p>
                <div v-else class="avatar-grid" :class="{ 'is-selecting': memberSelectMode }">
                  <button
                    v-for="m in displayedMembers"
                    :key="m.id"
                    type="button"
                    class="av"
                    :class="{
                      selected: selectedMemberIds.includes(m.id),
                      selectable: memberSelectMode && canSelectMember(m)
                    }"
                    :disabled="memberSelectMode ? !canSelectMember(m) : !(isAdminOrOwner && canSelectMember(m))"
                    :title="m.name"
                    @click.stop="onMemberAvatarClick(m)"
                  >
                    <Avatar :text="m.avatarText" :color="m.avatarColor" :image-url="m.avatarUrl" :size="40" />
                    <span v-if="memberSelectMode && canSelectMember(m)" class="av-check" :class="{ on: selectedMemberIds.includes(m.id) }">
                      {{ selectedMemberIds.includes(m.id) ? '✓' : '' }}
                    </span>
                    <span v-if="m.badge" class="member-badge">{{ m.badge }}</span>
                    <span class="av-name">{{ m.name }}</span>
                  </button>
                  <button
                    v-if="!memberSelectMode"
                    type="button"
                    class="av invite"
                    :title="t('chat.invite')"
                    @click.stop="openAddMembers"
                  >
                    +
                  </button>
                </div>
                <div v-if="memberSelectMode" class="batch-bar">
                  <LxButton
                    variant="block-danger"
                    :disabled="selectedMemberIds.length === 0"
                    @click="batchRemoveSelected"
                  >
                    {{ t('modals.batchRemove', { n: selectedMemberIds.length }) }}
                  </LxButton>
                  <LxButton
                    variant="block"
                    :disabled="selectedMemberIds.length === 0"
                    @click="batchMuteSelected"
                  >
                    {{ t('modals.batchMute', { n: selectedMemberIds.length }) }}
                  </LxButton>
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

              <!-- 群公告：点击进入 CRUD 弹窗 -->
              <section class="block">
                <button type="button" class="announce-row" @click="openAnnouncementManage">
                  <div class="announce-row-main">
                    <h3 class="block-title">{{ t('chat.groupAnnouncement') }}</h3>
                    <p class="announce" :class="{ 'is-empty': announcementEmpty }">{{ announcement }}</p>
                    <p
                      v-if="!announcementEmpty && announcementReadCount != null"
                      class="announce-read"
                    >
                      {{ t('modals.announcementReadCount', { n: announcementReadCount }) }}
                    </p>
                  </div>
                  <span class="announce-arrow">›</span>
                </button>
              </section>

              <!-- 群聊小助手（群主/管理员可开启） -->
              <section class="block">
                <h3 class="block-title">{{ t('groupAi.sectionTitle') }}</h3>
                <GroupAiSidebarBlock embedded :can-manage="isAdminOrOwner" />
              </section>

              <!-- 会议历史（元数据，无回放） -->
              <section class="block">
                <h3 class="block-title">{{ t('conference.historyTitle') }}</h3>
                <p class="field-hint">{{ t('conference.historyHint') }}</p>
                <p v-if="conferenceHistoryLoading" class="muted">{{ t('common.loading') }}</p>
                <p v-else-if="conferenceHistory.length === 0" class="muted">{{ t('conference.historyEmpty') }}</p>
                <ul v-else class="conf-history-list">
                  <li v-for="c in visibleConferenceHistory" :key="String(c.id)" class="conf-history-item">
                    <div class="conf-history-title">{{ c.title || t('conference.defaultTitle') }}</div>
                    <div class="conf-history-meta">
                      <span>{{ c.type === 'voice' ? t('conference.typeVoice') : t('conference.typeVideo') }}</span>
                      <span>·</span>
                      <span>{{ formatConferenceTime(c.startTime) }}</span>
                      <span v-if="c.endTime">→ {{ formatConferenceTime(c.endTime) }}</span>
                    </div>
                  </li>
                </ul>
                <button
                  v-if="!conferenceHistoryLoading && conferenceHistoryHasMore"
                  type="button"
                  class="conf-history-more"
                  @click="toggleConferenceHistory"
                >
                  {{
                    conferenceHistoryExpanded
                      ? t('conference.historyCollapse')
                      : t('conference.historyViewMore')
                  }}
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
                <div v-if="isAdminOrOwner" class="switch-row">
                  <span>{{ t('modals.joinApproval') }}</span>
                  <n-switch :value="joinApproval" size="small" @update:value="setJoinApproval" />
                </div>
                <div v-if="isOwner" class="switch-row">
                  <span>{{ t('modals.inviteOwnerOnly') }}</span>
                  <n-switch :value="inviteOwnerOnly" size="small" @update:value="setInviteOwnerOnly" />
                </div>
                <p class="hint">{{ t('modals.muteHint') }}</p>
              </div>

              <!-- 入群申请审批（群主/管理员） -->
              <section v-if="isAdminOrOwner && joinApproval" class="block join-requests">
                <div class="row-item">
                  <span>{{ t('modals.joinRequests') }}</span>
                  <button type="button" class="link-btn" @click="refreshJoinRequests">
                    {{ t('contacts.refresh') }}
                  </button>
                </div>
                <div v-if="joinRequestsLoading" class="join-empty">{{ t('common.loading') }}</div>
                <div v-else-if="!joinRequests.length" class="join-empty">{{ t('modals.joinRequestEmpty') }}</div>
                <ul v-else class="join-list">
                  <li v-for="req in joinRequests" :key="req.applicantId" class="join-item">
                    <Avatar
                      :text="(req.applicantNickname || '?').charAt(0)"
                      color="var(--lx-accent)"
                      :size="36"
                      :image-url="req.applicantAvatar"
                    />
                    <div class="join-meta">
                      <div class="join-name">{{ req.applicantNickname || req.applicantId }}</div>
                      <div class="join-msg">{{ req.message || t('modals.joinRequests') }}</div>
                    </div>
                    <div class="join-actions">
                      <button
                        type="button"
                        class="lx-join-btn lx-join-btn--compact lx-join-btn--ok"
                        :disabled="joinHandlingId === req.applicantId"
                        @click="handleJoin(req.applicantId, true)"
                      >
                        {{ t('modals.joinApprove') }}
                      </button>
                      <button
                        type="button"
                        class="lx-join-btn lx-join-btn--compact lx-join-btn--no"
                        :disabled="joinHandlingId === req.applicantId"
                        @click="handleJoin(req.applicantId, false)"
                      >
                        {{ t('modals.joinReject') }}
                      </button>
                    </div>
                  </li>
                </ul>
              </section>

              <!-- 危险操作与举报 -->
              <LxButton variant="block" @click="clearChat">{{ t('modals.clearChatHistory') }}</LxButton>
              <LxButton
                v-if="isAdminOrOwner"
                variant="block"
                @click="openMutePanel"
              >
                {{ t('modals.groupMute') }}
              </LxButton>
              <LxButton
                v-if="!isOwner"
                variant="block-danger"
                @click="quitGroup"
              >
                {{ t('modals.quitGroup') }}
              </LxButton>
              <LxButton
                v-if="isOwner"
                variant="block"
                @click="openManageAdmins"
              >
                {{ t('modals.manageAdmins') }}
              </LxButton>
              <LxButton
                v-if="isOwner"
                variant="block"
                @click="openTransferOwner"
              >
                {{ t('modals.transferOwner') }}
              </LxButton>
              <LxButton
                v-if="isOwner"
                variant="block-danger"
                @click="dissolve"
              >
                {{ t('modals.dissolveGroup') }}
              </LxButton>
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
          target-kind="group"
          :target-id="currentSessionId"
          :target-name="currentSession?.groupName || currentSession?.name || ''"
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
  z-index: var(--lx-z-dock);
  background: var(--lx-bg-overlay);
  -webkit-app-region: no-drag;
}

.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(320px, 88%);
  max-width: 360px;
  background: var(--lx-bg-card);
  box-shadow: var(--lx-shadow-drawer);
  display: flex;
  flex-direction: column;
  will-change: transform;
  -webkit-app-region: no-drag;
}

.drawer-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-3xl) var(--lx-space-2xl) var(--lx-space-5xl-minus);
}

.group-hero {
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space-xl);
  padding-bottom: var(--lx-space-2xl);
  border-bottom: 1px solid var(--lx-border-light);
  margin-bottom: var(--lx-space-lg);
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
  margin: 0 0 var(--lx-space-sm);
  font-size: var(--lx-font-xl);
  font-weight: 600;
  color: var(--lx-text-body);
  line-height: var(--lx-leading-snug);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.g-real-name {
  margin: 0 0 var(--lx-space-sm);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.g-id {
  margin: 0 0 var(--lx-space-md);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.block {
  padding: var(--lx-space-lg) 0;
  border-bottom: 1px solid var(--lx-bg-panel);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
  margin-bottom: var(--lx-space-md);
}

.block-head-actions {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
}

.member-search {
  margin: 0 0 var(--lx-space-md);
}

.member-search-input {
  width: 100%;
  height: 32px;
  border: 1px solid var(--lx-border-light);
  border-radius: var(--lx-radius);
  padding: 0 var(--lx-space-md);
  font-size: var(--lx-font-sm);
  outline: none;
  background: var(--lx-bg-card);
  color: var(--lx-text-body);
}

.member-search-input:focus {
  border-color: var(--lx-accent);
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--lx-space-md);
  margin-bottom: var(--lx-space-md);
}

.av {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lx-space-xs);
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.av:disabled {
  cursor: default;
  opacity: 0.72;
}

.av.selected {
  outline: 2px solid var(--lx-accent);
  border-radius: var(--lx-radius-xl);
}

.av-name {
  max-width: 56px;
  font-size: var(--lx-font-2xs);
  line-height: var(--lx-leading-snug);
  color: var(--lx-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.av-check {
  position: absolute;
  right: 2px;
  top: 24px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid var(--lx-border-strong);
  background: var(--lx-bg-card);
  color: var(--lx-text-on-accent);
  font-size: var(--lx-font-2xs);
  line-height: 14px;
  text-align: center;
}

.av-check.on {
  border-color: var(--lx-accent);
  background: var(--lx-accent);
}

.member-badge {
  position: absolute;
  left: 0;
  bottom: -2px;
  padding: 0 var(--lx-space-xs);
  font-size: var(--lx-font-2xs);
  line-height: var(--lx-font);
  color: var(--lx-text-on-accent);
  background: rgba(0, 0, 0, 0.55);
  border-radius: var(--lx-radius-pill);
  transform: scale(0.9);
}

.batch-bar {
  margin-top: var(--lx-space);
  display: flex;
  gap: var(--lx-space);
}

.invite {
  width: 40px;
  height: 40px;
  border-radius: var(--lx-avatar-radius);
  border: 1px dashed var(--lx-border-strong);
  background: var(--lx-bg-panel);
  font-size: var(--lx-font-5xl);
  color: var(--lx-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.block-title {
  margin: 0 0 var(--lx-space);
  font-size: var(--lx-font);
  font-weight: 600;
}

.announce-row {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: var(--lx-space);
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.announce-row-main {
  flex: 1;
  min-width: 0;
}

.announce {
  margin: 0;
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-secondary);
  word-break: break-all;
  min-height: 18px;
}

.announce.empty {
  color: var(--lx-text-muted);
}

.announce-read {
  margin: var(--lx-space-xs) 0 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.announce-arrow {
  flex-shrink: 0;
  font-size: var(--lx-font-4xl);
  line-height: var(--lx-leading-none);
  color: var(--lx-text-muted);
  margin-top: var(--lx-space-2xs);
}

.announce-row:hover .announce-arrow {
  color: var(--lx-accent);
}

.row-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--lx-font);
  color: var(--lx-text-body);
}

.muted {
  color: var(--lx-text-muted);
  font-size: var(--lx-font-md);
}

.remark-input {
  width: 100%;
  margin-top: var(--lx-space);
  height: 36px;
  border: none;
  border-bottom: 1px solid var(--lx-border-light);
  font-size: var(--lx-font-md);
  outline: none;
  color: var(--lx-text-body);
  background: transparent;
}

.remark-input[readonly] {
  color: var(--lx-text-secondary);
  cursor: default;
}

.field-hint {
  margin: var(--lx-space-sm) 0 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.conf-history-list {
  list-style: none;
  margin: var(--lx-space) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space);
}
.conf-history-item {
  padding: var(--lx-space) var(--lx-space-md);
  border-radius: var(--lx-radius-sm);
  background: var(--lx-bg-muted, rgba(0, 0, 0, 0.04));
}
.conf-history-title {
  font-size: var(--lx-font-md);
  font-weight: 600;
}
.conf-history-meta {
  margin-top: var(--lx-space-xs);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space-xs);
}
.conf-history-more {
  display: block;
  width: 100%;
  margin-top: var(--lx-space);
  padding: var(--lx-space) 0;
  border: none;
  background: none;
  color: var(--lx-accent);
  font-size: var(--lx-font-sm);
  cursor: pointer;
  text-align: center;
}
.conf-history-more:hover {
  opacity: 0.85;
}

.switch-block {
  padding: var(--lx-space) 0 var(--lx-space-2xl);
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lx-space-lg) 0;
  font-size: var(--lx-font);
  color: var(--lx-text-body);
}

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: var(--lx-space);
}

.hint {
  margin: -var(--lx-space-xs) 0 0;
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.join-requests {
  margin-top: var(--lx-space);
}

.join-requests .row-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lx-space);
}

.link-btn {
  border: none;
  background: none;
  color: var(--lx-accent);
  font-size: var(--lx-font-sm);
  cursor: pointer;
  padding: 0;
}

.join-empty {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  padding: var(--lx-space) 0;
}

.join-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
}

.join-item {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
}

.join-meta {
  flex: 1;
  min-width: 0;
}

.join-name {
  font-size: var(--lx-font-md);
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.join-msg {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.join-actions {
  display: flex;
  gap: var(--lx-space-sm);
  flex-shrink: 0;
}

.report {
  text-align: center;
  margin: var(--lx-space-2xl) 0 0;
  font-size: var(--lx-font-sm);
}

.report a {
  color: var(--lx-accent);
  text-decoration: none;
}

.transfer-panel {
  position: absolute;
  inset: 0;
  z-index: var(--lx-z-raised-2);
  background: var(--lx-bg-card);
  display: flex;
  flex-direction: column;
  padding: var(--lx-space-2xl) var(--lx-space-xl) var(--lx-space-3xl);
}

.transfer-head {
  display: flex;
  align-items: center;
  gap: var(--lx-space);
  margin-bottom: var(--lx-space);
}

.transfer-head h3 {
  margin: 0;
  font-size: var(--lx-font-lg);
  font-weight: 600;
  color: var(--lx-text-body);
}

.transfer-back {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: var(--lx-font-5xl);
  line-height: var(--lx-leading-none);
  color: var(--lx-text-body);
  cursor: pointer;
}

.transfer-hint {
  margin: 0 0 var(--lx-space-lg);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading);
}

.transfer-list {
  flex: 1;
  overflow-y: auto;
}

.transfer-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  padding: var(--lx-space-md) var(--lx-space);
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
  font-size: var(--lx-font);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transfer-badge {
  font-size: var(--lx-font-xs);
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
  padding: 0 var(--lx-space-md);
  border: 1px solid var(--lx-border-strong);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-card);
  font-size: var(--lx-font-sm);
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
  transition: opacity var(--lx-duration-md) ease;
}

.chat-drawer-enter-active .drawer-panel,
.chat-drawer-leave-active .drawer-panel {
  transition: transform var(--lx-duration-slow) cubic-bezier(0.4, 0, 0.2, 1);
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
