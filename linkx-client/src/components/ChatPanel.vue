<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 聊天主面板组件。
 * <p>
 * 展示当前会话的消息列表、输入框与顶栏操作，
 * 支持好友/群聊/我的手机等不同会话类型，以及语音播放、
 * 文件拖拽、消息右键菜单（复制/收藏/回复/设为精华/撤回）等功能。
 * </p>
 */
// Vue 响应式、计算属性、生命周期、侦听器与 nextTick
import { ref, computed, onUnmounted, watch, nextTick } from 'vue'
// Naive UI 图标、气泡、下拉菜单与消息提示
import { NIcon, NPopover, NDropdown, NModal, NInput, useMessage, type DropdownOption } from 'naive-ui'
// Ionicons5 通话、视频、网格、添加、更多、手机、图片图标
import {
  CallOutline,
  VideocamOutline,
  PeopleOutline,
  GridOutline,
  AddOutline,
  EllipsisHorizontalOutline,
  PhonePortraitOutline,
  ImagesOutline
} from '@vicons/ionicons5'
// 头像组件
import Avatar from './Avatar.vue'
// 空状态 Logo 占位
import PenguinWatermark from './PenguinWatermark.vue'
// 未选会话时的运营发现区（推荐位 + 活动）
import DiscoverEmptyPane from './ops/DiscoverEmptyPane.vue'
// 群聊侧边栏
import GroupChatSidebar from './chat/GroupChatSidebar.vue'
// 好友聊天更多抽屉
import ChatMoreDrawer from './chat/ChatMoreDrawer.vue'
// 群信息抽屉
import GroupInfoDrawer from './chat/GroupInfoDrawer.vue'
// 单条消息组件
import ChatMessageItem from './chat/ChatMessageItem.vue'
import MessageVirtualList from './chat/MessageVirtualList.vue'
// 聊天输入框
import ChatInputBox from './chat/ChatInputBox.vue'
// Pinia 响应式解构
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 全屏 Overlay Store
import { useOverlayStore } from '../stores/overlay'
// 聊天弹窗 Store
import { useChatModalsStore } from '../stores/chatModals'
// 应用设置 Store
import { useAppSettingsStore } from '../stores/appSettings'
// 联系人 Store
import { useContactsStore } from '../stores/contacts'
// 消息与联系人类型
import type { ChatMessage, ContactItem } from '../types'
// 收藏 Store
import { useFavoritesStore } from '../stores/favorites'
// 群元数据 Store（精华、成员角色等）
import { useGroupMetaStore } from '../stores/groupMeta'
// 通话 Store（真实 WebRTC）
import { useCallStore } from '../stores/call'
import { useI18n } from '../i18n'
import { formatMessageDivider, MESSAGE_TIME_GAP_MS } from '../utils/chatTime'
import * as chatApi from '../api/chat'
import * as conferenceApi from '../api/conference'
import { recoverMediaUrlOnError } from '../utils/mediaUrl'
import { chatMessagePreviewText } from '../utils/messagePreviewText'
import { formatLastSeen } from '../utils/messageStatus'
import ConferenceCreateDialog, {
  type ConferenceCreatePayload
} from './chat/ConferenceCreateDialog.vue'
import ForwardPickerModal from './chat/ForwardPickerModal.vue'
import ConferenceSessionBanner from './chat/ConferenceSessionBanner.vue'
import type { SessionBannerInfo } from './chat/ConferenceSessionBanner.vue'
import { useConferenceStore } from '../stores/conference'

// 获取 Naive UI 消息提示实例
const message = useMessage()
const { t } = useI18n()

/** 发起语音/视频：单聊走 1v1；群聊发起对应类型的「电话」（非会议） */
async function startCall(callType: 'voice' | 'video') {
  const session = currentSession.value
  const sessionId = currentSessionId.value
  if (!session || !sessionId) {
    message.warning(t('chat.selectSessionFirst'))
    return
  }
  if (session.isGroup) {
    await quickStartConference(callType)
    return
  }
  try {
    await useCallStore().startOutgoing({
      conversationId: sessionId,
      callType,
      peerName: session.name,
      peerAvatar: session.avatarUrl,
      peerUserId: session.peerUserId
    })
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('chat.callFailed'))
  }
}

const conferenceCreating = ref(false)
const conferenceCreateOpen = ref(false)

/** 打开创建会议弹窗（群聊 / 私聊顶栏） */
function startConference() {
  const sessionId = currentSessionId.value
  if (!sessionId || conferenceCreating.value) return
  conferenceCreateOpen.value = true
}

/** 群聊电话/视频一键发起「电话」（非会议） */
async function quickStartConference(type: 'voice' | 'video') {
  const session = currentSession.value
  if (!session) return
  await onConferenceCreateConfirm({
    title: session.name || (type === 'voice' ? t('conference.voiceCallTitle') : t('conference.videoCallTitle')),
    type,
    scene: 'call',
    maxParticipants: 9,
    lobbyEnabled: false
  })
}

async function onConferenceCreateConfirm(payload: ConferenceCreatePayload) {
  const sessionId = currentSessionId.value
  if (!sessionId || conferenceCreating.value) return
  conferenceCreating.value = true
  conferenceCreateOpen.value = false
  const scene = payload.scene === 'call' ? 'call' : 'meeting'
  try {
    const res = await conferenceApi.create({
      conversationId: sessionId,
      type: payload.type,
      scene,
      title: payload.title,
      password: payload.password,
      maxParticipants: payload.maxParticipants,
      lobbyEnabled: payload.lobbyEnabled
    })
    if (res.code !== 200 || !res.data) throw new Error(res.message || 'failed')
    await useConferenceStore().openCreated(res.data, String(userProfile.value.userId || ''))
    if (res.data.reused) {
      message.info(t('conference.reusedOpened'))
    } else if (scene === 'call') {
      message.success(
        payload.type === 'voice' ? t('conference.voiceStarted') : t('conference.videoStarted')
      )
    } else {
      message.success(t('conference.meetingStarted'))
    }
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || t('modals.conferenceCreateFail'))
  } finally {
    conferenceCreating.value = false
  }
}
// 获取收藏 Store 实例
const favoritesStore = useFavoritesStore()
// 获取群元数据 Store
const groupMetaStore = useGroupMetaStore()
// 获取应用 Store 实例
const appStore = useAppStore()
// 获取 Overlay Store 实例
const overlayStore = useOverlayStore()
// 获取聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 获取应用设置 Store 实例
const appSettingsStore = useAppSettingsStore()
// 获取联系人 Store 实例
const contactsStore = useContactsStore()
// 解构当前会话、消息、用户资料、会话 ID、已保存登录信息
const { currentSession, currentMessages, userProfile, currentSessionId, savedLogin, sessionEnterTick, pendingFocusMessageId, typingBySession } =
  storeToRefs(appStore)
// 解构聊天背景设置
const { chatBackground } = storeToRefs(appSettingsStore)
// 解构撤回消息、加载更多历史消息
const { recallMessage: recallMessageInStore, loadMoreMessages, clearAtMeMessage, clearPendingFocusMessage } = appStore
const {
  editMessage: editMessageInStore,
  forwardMessage: forwardMessageInStore,
  retryFailedMessage
} = appStore
// 解构打开 Overlay 方法
const { open: openOverlay } = overlayStore
// 解构聊天弹窗相关操作方法
const {
  toggleMore,
  toggleGroupInfo,
  closeMore,
  closeGroupInfo,
  openAddMembers,
  openGroupFiles,
  openGroupAlbum,
  openGroupEssence,
  openGroupAnnouncement,
  openRedPacketReceive,
  openRedPacketHistory,
  openContactProfile,
  openSelfProfile,
} = chatModalsStore

// 群应用快捷菜单项
const groupGridItems = computed(() => [
  { key: 'files', label: t('chat.groupFiles') },
  { key: 'album', label: t('chat.groupAlbum') },
  { key: 'essence', label: t('chat.groupEssence') },
  { key: 'announcement', label: t('chat.groupAnnouncement') },
  { key: 'redPackets', label: t('chat.redPacketHistory') }
])

// 群应用菜单项点击：打开对应群功能弹窗
function onGroupAppClick(key: string) {
  if (key === 'files') openGroupFiles()
  else if (key === 'album') openGroupAlbum()
  else if (key === 'essence') openGroupEssence()
  else if (key === 'announcement') openGroupAnnouncement()
  else if (key === 'redPackets') openRedPacketHistory()
}

// 是否为「我的手机」会话
const isMyPhone = computed(() => {
  const name = currentSession.value?.name
  return name === '我的手机' || name === t('chat.myPhone')
})
// 是否有选中的会话
const hasSession = computed(() => !!currentSession.value)
// 是否为群聊（有会话、是群、且非「我的手机」）
const isGroupChat = computed(
  () => hasSession.value && !!currentSession.value?.isGroup && !isMyPhone.value
)
// 是否为好友单聊（有会话、非群、非我的手机）
const isFriendChat = computed(
  () => hasSession.value && !currentSession.value?.isGroup && !isMyPhone.value
)

const peerOnline = computed(() => {
  const session = currentSession.value
  if (!isFriendChat.value || !session) return false
  if (session.online) return true
  if (session.peerUserId) {
    const peerId = String(session.peerUserId)
    const contact = contactsStore.items.find(c => String(c.userId ?? c.id) === peerId)
    if (contact?.online) return true
  }
  return false
})

const peerLastSeenText = computed(() => {
  if (!isFriendChat.value || peerOnline.value) return ''
  const session = currentSession.value
  let at = session?.lastSeenAt
  if (!at && session?.peerUserId) {
    const peerId = String(session.peerUserId)
    const contact = contactsStore.items.find(c => String(c.userId ?? c.id) === peerId)
    at = contact?.lastSeenAt
  }
  if (!at) return t('chat.lastSeenLongAgo')
  return formatLastSeen(at, t)
})

const typingHint = computed(() => {
  if (!isFriendChat.value) return ''
  const sid = currentSessionId.value
  if (!sid) return ''
  const tip = typingBySession.value[sid]
  if (!tip || tip.until < Date.now()) return ''
  return t('chat.typingPrivate')
})

/** 当前用户是否为群主或管理员（可设精华） */
const isGroupAdmin = computed(() => {
  if (!isGroupChat.value || !currentSessionId.value) return false
  const me = userProfile.value.userId
  if (!me) return false
  const members = groupMetaStore.membersFor(currentSessionId.value)
  return members.some(m => m.id === me && (m.role === 'owner' || m.role === 'admin'))
})

// 进入群聊时预加载成员与禁言状态
watch(
  () => (isGroupChat.value ? currentSessionId.value : null),
  (id) => {
    if (id) {
      void groupMetaStore.fetchMembers(id)
      void groupMetaStore.fetchAnnouncement(id)
    }
  },
  { immediate: true }
)

// 插入时间分割线（首条 + 间隔超过 5 分钟）
const chatMessages = computed(() => {
  const list = currentMessages.value
  const result: ChatMessage[] = []
  let lastMs = 0
  for (const m of list) {
    const ms = m.createTime || 0
    if (m.type !== 'time' && ms && lastMs && ms - lastMs >= MESSAGE_TIME_GAP_MS) {
      result.push({
        id: `time-${m.id}`,
        sessionId: m.sessionId,
        content: formatMessageDivider(ms),
        time: m.time,
        createTime: ms,
        isSelf: false,
        type: 'time'
      })
    }
    result.push(m)
    if (ms) lastMs = ms
  }
  if (result.length > 0 && result[0].type !== 'time') {
    const first = result.find(m => m.type !== 'time')
    if (first?.createTime) {
      result.unshift({
        id: `time-start-${first.id}`,
        sessionId: first.sessionId,
        content: formatMessageDivider(first.createTime),
        time: first.time,
        createTime: first.createTime,
        isSelf: false,
        type: 'time'
      })
    }
  }
  return result
})

/** 是否贴底：仅贴底时新消息才自动滚到底，避免上拉历史被拽回底部 */
const stickToBottom = ref(true)
const loadingMore = ref(false)
let loadMoreLockUntil = 0
/** 跳转高亮的消息 ID */
const highlightAtMeId = ref<string | null>(null)
let highlightAtMeTimer = 0

// 监听消息数量变化：贴底时才自动滚到底（发送/收到新消息）
watch(
  () => chatMessages.value.length,
  (newLen, oldLen) => {
    if (newLen > oldLen && hasSession.value && stickToBottom.value && !loadingMore.value) {
      scrollToBottom()
    }
  }
)

/** 切换会话贴底完成前隐藏列表，避免从顶部闪到底部 */
const messageEntering = ref(false)

/** 每次点选会话（含重复点同一会话）都进入最新消息位置 */
watch(sessionEnterTick, () => {
  if (!hasSession.value) return
  // 搜索/收藏跳转优先，不要强制贴底盖掉定位
  if (pendingFocusMessageId.value) return
  stickToBottom.value = true
  loadingMore.value = false
  // 进会话短暂锁定上拉，避免 VirtualList 挂载在顶部时误触发 load-more 把视口钉在旧消息
  loadMoreLockUntil = Date.now() + 500
  highlightAtMeId.value = null
  if (highlightAtMeTimer) {
    window.clearTimeout(highlightAtMeTimer)
    highlightAtMeTimer = 0
  }
  messageEntering.value = true
  const finish = () => {
    messageEntering.value = false
  }
  const run = () => scrollToBottom(true)
  nextTick(() => {
    run()
    requestAnimationFrame(() => {
      run()
      requestAnimationFrame(finish)
    })
  })
})

/** 首屏历史从 loading → 完成后再强制贴底（缓存命中时 length 不变，仅靠 sessionEnterTick 可能早于列表挂载） */
watch(
  () => {
    const sid = currentSessionId.value
    return sid ? !!appStore.messagesLoading[sid] : false
  },
  (loading, wasLoading) => {
    if (wasLoading && !loading && hasSession.value && stickToBottom.value && !pendingFocusMessageId.value) {
      messageEntering.value = true
      nextTick(() => {
        scrollToBottom(true)
        requestAnimationFrame(() => {
          scrollToBottom(true)
          messageEntering.value = false
        })
      })
    }
  }
)

// 切换会话时重置高亮（sessionEnterTick 已负责贴底）
watch(currentSessionId, () => {
  highlightAtMeId.value = null
  if (highlightAtMeTimer) {
    window.clearTimeout(highlightAtMeTimer)
    highlightAtMeTimer = 0
  }
})

// 当前正在播放的语音消息 ID
const playingVoiceId = ref<string | null>(null)

// 根据聊天背景设置计算消息区背景样式
const chatBgStyle = computed(() => {
  const id = chatBackground.value
  if (id === 'purple') {
    return { background: 'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)' }
  }
  if (id === 'orange') {
    return { background: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)' }
  }
  return { background: 'var(--lx-bg-panel)' } // 默认背景
})

// 语音播放 Audio 实例引用
let voiceAudio: HTMLAudioElement | null = null


// 生成对方头像组件 props
function peerAvatarProps(size = 36) {
  const s = currentSession.value
  return {
    text: s?.avatarText || '?',
    color: s?.avatarColor || 'var(--lx-accent)',
    size,
    imageUrl: s?.avatarUrl,
    icon: isMyPhone.value ? PhonePortraitOutline : undefined // 我的手机显示手机图标
  }
}

// 点击对方头像打开联系人资料卡
function openPeerProfile(e: MouseEvent) {
  e.stopPropagation()
  if (!isFriendChat.value || !currentSession.value) return
  const session = currentSession.value
  // 从联系人 Store 查找匹配联系人，优先按 peerUserId 匹配
  const found = contactsStore.items.find(
    c =>
      (session.peerUserId && String(c.userId ?? c.id) === session.peerUserId) ||
      c.id === session.id ||
      c.name === session.name
  )
  const contact: ContactItem = found ?? {
    id: session.peerUserId || session.id,
    userId: session.peerUserId,
    name: session.name,
    avatarText: session.avatarText,
    avatarColor: session.avatarColor,
    group: t('chat.myFriends'),
    online: session.online
  }
  openContactProfile(contact, e)
}

// 点击自己头像打开个人资料卡
function openSelfProfileClick(e: MouseEvent) {
  e.stopPropagation()
  openSelfProfile(
    {
      nickname: userProfile.value.nickname,
      username: savedLogin.value.username || userProfile.value.username || undefined,
      avatarText: userProfile.value.nickname.charAt(0) || t('chat.me'),
      avatarUrl: userProfile.value.avatar || undefined,
      userId: userProfile.value.userId ? Number(userProfile.value.userId) : undefined
    },
    e
  )
}

// 切换会话时关闭更多抽屉与群信息抽屉
watch(currentSessionId, () => {
  closeMore()
  closeGroupInfo()
})










// 播放或暂停语音消息（预签名过期时自动刷新）
async function playVoice(msg: ChatMessage) {
  if (!msg.voiceUrl && !msg.fileUrl) {
    message.info(`${t('chat.voice')} ${formatVoiceDuration(msg.voiceDuration)}`) // 无 URL 时仅提示时长
    return
  }
  if (playingVoiceId.value === msg.id) {
    voiceAudio?.pause() // 再次点击则暂停
    playingVoiceId.value = null
    return
  }
  voiceAudio?.pause() // 停止上一段语音
  let src = msg.voiceUrl || msg.fileUrl || ''
  const tryPlay = (url: string) => {
    voiceAudio = new Audio(url)
    playingVoiceId.value = msg.id
    voiceAudio.onended = () => {
      playingVoiceId.value = null
    }
    voiceAudio.onerror = () => {
      void (async () => {
        const next = await recoverMediaUrlOnError(url, async () => {
          const res = await chatApi.refreshMessageMediaUrl(msg.id)
          if (res.code === 200 && res.data?.url) return res.data.url
          return null
        })
        if (next && next !== url) {
          msg.voiceUrl = next
          msg.fileUrl = next
          tryPlay(next)
        } else {
          playingVoiceId.value = null
          message.error(t('chat.voicePlayFail'))
        }
      })()
    }
    voiceAudio.play().catch(() => {
      playingVoiceId.value = null
      message.error(t('chat.voicePlayFail'))
    })
  }
  tryPlay(src)
}

// 双击图片：Electron 开独立预览窗；Web 回退 Overlay
async function openImageView(msg: ChatMessage) {
  let fileUrl = msg.content || msg.fileUrl || ''
  if (!fileUrl) {
    message.warning(t('chat.fileOpenMissing'))
    return
  }
  // object key 等非 http 地址先刷新为可访问 URL
  if (!/^https?:\/\//i.test(fileUrl) && !fileUrl.startsWith('blob:') && !fileUrl.startsWith('data:')) {
    const next = await recoverMediaUrlOnError(fileUrl, async () => {
      const res = await chatApi.refreshMessageMediaUrl(msg.id)
      if (res.code === 200 && res.data?.url) return res.data.url
      return null
    })
    if (next) fileUrl = next
  }

  const conversationId = currentSessionId.value || msg.sessionId || ''
  const gallery = (currentMessages.value || [])
    .filter(m => m.type === 'image' || m.isImage)
    .map(m => ({
      url: m.content || m.fileUrl || '',
      fileName: m.fileName || t('chat.imageMessage'),
      fileSize: m.fileSize,
      messageId: m.id,
      conversationId: m.sessionId || conversationId
    }))
    .filter(i => !!i.url)
  let index = msg.id ? gallery.findIndex(i => i.messageId === msg.id) : -1
  if (index < 0) index = gallery.findIndex(i => i.url === fileUrl)
  if (index < 0) index = 0
  // 当前张使用已刷新的可访问 URL
  if (gallery[index]) {
    gallery[index] = { ...gallery[index], url: fileUrl }
  }

  const payload = {
    url: fileUrl,
    fileName: msg.fileName || t('chat.imageMessage'),
    fileSize: msg.fileSize,
    items: gallery.length
      ? gallery
      : [
          {
            url: fileUrl,
            fileName: msg.fileName,
            fileSize: msg.fileSize,
            messageId: msg.id,
            conversationId
          }
        ],
    index: gallery.length ? index : 0
  }

  if (window.electronAPI?.openImageViewer) {
    window.electronAPI.openImageViewer(payload)
    return
  }

  openOverlay('file-preview', {
    filePreview: {
      fileName: payload.fileName,
      fileUrl: payload.url,
      fileSize: payload.fileSize,
      isImage: true
    }
  })
}

// 组件卸载时停止语音播放
onUnmounted(() => {
  voiceAudio?.pause()
  if (highlightAtMeTimer) {
    window.clearTimeout(highlightAtMeTimer)
    highlightAtMeTimer = 0
  }
})

// 打开文件预览 Overlay（非聊天主路径；群文件等仍可能调用）
function openFileView(msg?: ChatMessage) {
  const fileName = msg?.fileName || msg?.content || t('chat.fileFallback')
  openOverlay('file-preview', {
    filePreview: {
      fileName,
      fileUrl: msg?.fileUrl || msg?.content,
      fileSize: msg?.fileSize,
      isImage: msg?.type === 'image' || msg?.isImage
    }
  })
}

/** 聊天文件：点击 / 左侧图标 → 下载并用系统默认程序打开 */
async function openChatFile(msg: ChatMessage) {
  let url = msg.fileUrl || (msg.type === 'file' ? '' : msg.content) || ''
  if (!url) {
    message.error(t('chat.fileOpenMissing'))
    return
  }
  if (!/^https?:\/\//i.test(url) && !url.startsWith('blob:') && !url.startsWith('data:')) {
    const next = await recoverMediaUrlOnError(url, async () => {
      const res = await chatApi.refreshMessageMediaUrl(msg.id)
      if (res.code === 200 && res.data?.url) return res.data.url
      return null
    })
    if (next) url = next
  }
  const { downloadFileWithSettings } = await import('../utils/downloadFile')
  const result = await downloadFileWithSettings(url, msg.fileName || msg.content || 'file', {
    openAfter: true
  })
  if (result.canceled) return
  if (!result.ok) {
    message.error(result.message || t('files.downloadFail'))
  }
}

// 点击红包消息：打开红包详情弹窗（自己发的也可查看）
function onRedPacketClick(msg: ChatMessage) {
  openRedPacketReceive(msg.id)
}

/** 点击通话/会议气泡：1v1 回拨；群电话加入通话；会议则加入 */
async function onConferenceClick(msg: ChatMessage) {
  const content = msg.content || ''
  const isCall =
    msg.conferenceScene === 'call' ||
    /语音通话|视频通话/.test(content) ||
    msg.fileName === '语音通话' ||
    msg.fileName === '视频通话'
  const isMeetingHint = /会议/.test(content) || msg.conferenceScene === 'meeting'
  const conferenceId = msg.conferenceId || msg.fileUrl

  // 群聊电话气泡：加入已有通话（数字会议 ID），不要再新建一场
  if (
    isCall &&
    !isMeetingHint &&
    currentSession.value?.isGroup &&
    conferenceId &&
    /^\d+$/.test(String(conferenceId))
  ) {
    await joinConferenceById(
      String(conferenceId),
      msg.conferenceTitle || msg.fileName || t('conference.voiceCallTitle'),
      !!msg.conferenceHasPassword,
      msg.sessionId || currentSessionId.value || '',
      'call'
    )
    return
  }

  if (isCall && !isMeetingHint) {
    const callType: 'voice' | 'video' =
      msg.conferenceType === 'voice' || /语音通话/.test(content) || msg.fileName === '语音通话'
        ? 'voice'
        : 'video'
    await startCall(callType)
    return
  }

  if (!conferenceId) {
    message.error(t('conference.joinFail'))
    return
  }
  await joinConferenceById(
    String(conferenceId),
    msg.conferenceTitle || msg.fileName || t('conference.defaultTitle'),
    !!msg.conferenceHasPassword,
    msg.sessionId || currentSessionId.value || '',
    'meeting'
  )
}

/** 顶栏条：加入 / 返回进行中电话或会议 */
async function onSessionConferenceJoin() {
  const info = sessionMediaBanner.value
  if (!info) return
  if (info.kind === 'private_call') {
    return
  }
  const store = useConferenceStore()
  // 已在会中（含收起）：恢复会议窗
  if (
    store.conferenceId &&
    String(store.conferenceId) === String(info.conferenceId) &&
    (store.phase === 'in_room' || store.phase === 'waiting')
  ) {
    store.restoreUi()
    return
  }
  await joinConferenceById(
    info.conferenceId,
    info.title,
    info.hasPassword,
    info.conversationId,
    info.scene === 'call' ? 'call' : 'meeting'
  )
}

async function joinConferenceById(
  conferenceId: string,
  title: string,
  hasPassword: boolean,
  conversationId: string,
  scene: 'call' | 'meeting' = 'meeting'
) {
  const myId = String(userProfile.value.userId || '')
  if (!myId) {
    message.error(t('conference.joinFail'))
    return
  }
  const store = useConferenceStore()
  if (
    store.phase === 'in_room' &&
    store.conferenceId &&
    String(store.conferenceId) === String(conferenceId)
  ) {
    store.restoreUi()
    return
  }
  if (hasPassword) {
    store.invitePrompt = {
      conferenceId: String(conferenceId),
      title: title || t('conference.defaultTitle'),
      conversationId: conversationId || currentSessionId.value || '',
      hasPassword: true,
      scene
    }
    if (store.phase === 'idle' || store.phase === 'ended') {
      store.phase = 'lobby'
    }
    return
  }
  try {
    await store.joinExisting(String(conferenceId), myId)
  } catch (e) {
    const err = e as Error & { code?: number }
    message.error(err.message || t('conference.joinFail'))
  }
}

const conferenceStore = useConferenceStore()
const {
  sessionActives,
  phase: conferencePhase,
  conferenceId: myConferenceId,
  conversationId: myConferenceConversationId,
  title: myConferenceTitle,
  type: myConferenceType,
  scene: myConferenceScene,
  hasPassword: myConferenceHasPassword,
  participants: myConferenceParticipants
} = storeToRefs(conferenceStore)

const callStore = useCallStore()
const {
  phase: callPhase,
  conversationId: callConversationId,
  callType: privateCallType,
  peerName: privateCallPeerName,
  isActive: privateCallActive
} = storeToRefs(callStore)

const sessionMediaBanner = computed((): SessionBannerInfo | null => {
  const sid = currentSessionId.value
  if (!sid) return null

  // 私聊 1v1 通话优先
  if (
    privateCallActive.value &&
    callConversationId.value === sid &&
    (callPhase.value === 'outgoing' ||
      callPhase.value === 'connecting' ||
      callPhase.value === 'connected' ||
      callPhase.value === 'incoming')
  ) {
    return {
      kind: 'private_call',
      conversationId: sid,
      type: privateCallType.value === 'voice' ? 'voice' : 'video',
      title: privateCallPeerName.value || currentSession.value?.name || '',
      participantCount: 2
    }
  }

  const cached = sessionActives.value[sid]
  if (cached) {
    return { kind: 'conference', ...cached }
  }

  // 本端已在该会话的电话/会议中
  if (
    myConferenceConversationId.value === sid &&
    myConferenceId.value &&
    (conferencePhase.value === 'in_room' || conferencePhase.value === 'waiting')
  ) {
    const admitted = (myConferenceParticipants.value || []).filter(p => {
      const a = p.admitStatus
      return a == null || Number(a) !== 0
    }).length
    return {
      kind: 'conference',
      conferenceId: String(myConferenceId.value),
      conversationId: sid,
      title: myConferenceTitle.value || t('conference.defaultTitle'),
      type: myConferenceType.value === 'voice' ? 'voice' : 'video',
      scene: myConferenceScene.value === 'call' ? 'call' : 'meeting',
      hasPassword: !!myConferenceHasPassword.value,
      participantCount: admitted || 1
    }
  }
  return null
})

const sessionConferenceInRoom = computed(() => {
  const banner = sessionMediaBanner.value
  if (!banner) return false
  if (banner.kind === 'private_call') {
    return callPhase.value === 'connected' || callPhase.value === 'connecting'
  }
  return (
    (conferencePhase.value === 'in_room' || conferencePhase.value === 'waiting') &&
    myConferenceId.value != null &&
    String(myConferenceId.value) === String(banner.conferenceId)
  )
})

// 切换会话 / 消息变化时同步顶栏进行中会议
watch(
  [currentSessionId, () => chatMessages.value.length],
  () => {
    const sid = currentSessionId.value
    if (!sid) return
    let hint: string | undefined
    const msgs = chatMessages.value
    for (let i = msgs.length - 1; i >= 0; i--) {
      const m = msgs[i]
      if (m.type !== 'conference' || !(m.conferenceId || m.fileUrl)) continue
      // 结束类系统文案不要当「进行中」hint；且须为数字会议 ID
      const text = m.content || ''
      if (/结束了/.test(text)) continue
      const id = String(m.conferenceId || m.fileUrl)
      if (!/^\d+$/.test(id)) continue
      hint = id
      break
    }
    void conferenceStore.fetchSessionActive(sid, hint)
  },
  { immediate: true }
)

// 格式化语音时长显示
function formatVoiceDuration(sec?: number) {
  const s = sec ?? 0
  return s < 60 ? `${s}"` : `${Math.floor(s / 60)}'${s % 60}"`
}

// 当前正在回复的消息
const replyingTo = ref<ChatMessage | undefined>()
// 虚拟消息列表
const messageListRef = ref<InstanceType<typeof MessageVirtualList> | null>(null)
// 消息列表容器引用
const messageListContainer = ref<HTMLElement | null>(null)
// 聊天输入框组件引用
const chatInputRef = ref<InstanceType<typeof ChatInputBox> | null>(null)

function scrollToBottom(force = false) {
  stickToBottom.value = true
  messageListRef.value?.scrollToBottom(force)
  const sid = currentSessionId.value
  if (sid) {
    void appStore.reportSessionRead(sid)
  }
}

/** 是否展示对话框内「有人@我」浮层 */
const showAtMeFab = computed(() => {
  if (!isGroupChat.value) return false
  const s = currentSession.value
  const targetId = s?.atMeMessageId
  if (!targetId) return false
  // 从列表点进来、或未贴底：必须显示浮层，避免「只剩一条普通消息」
  if (s?.atMeNeedAck) return true
  const idx = chatMessages.value.findIndex(m => m.id === targetId)
  if (idx < 0) return true
  const nearEnd = idx >= chatMessages.value.length - 2
  if (stickToBottom.value && nearEnd) return false
  return true
})

/** 贴底看见 @消息时：仅在不需要手动确认时自动清除 */
watch(
  [stickToBottom, () => currentSession.value?.atMeMessageId, () => chatMessages.value.length],
  () => {
    const s = currentSession.value
    const sid = currentSessionId.value
    const targetId = s?.atMeMessageId
    if (!sid || !targetId || !stickToBottom.value || s?.atMeNeedAck) return
    const idx = chatMessages.value.findIndex(m => m.id === targetId)
    if (idx >= 0 && idx >= chatMessages.value.length - 2) {
      clearAtMeMessage(sid)
    }
  }
)

/** 点击「有人@我」：滚动定位并短暂高亮 */
async function jumpToAtMeMessage() {
  const sid = currentSessionId.value
  const targetId = currentSession.value?.atMeMessageId
  if (!sid || !targetId) return

  let idx = chatMessages.value.findIndex(m => m.id === targetId)
  let attempts = 0
  while (idx < 0 && attempts < 15 && appStore.messagesHasMore[sid] !== false) {
    attempts++
    await loadMoreMessages(sid)
    await nextTick()
    idx = chatMessages.value.findIndex(m => m.id === targetId)
  }

  if (idx < 0) {
    clearAtMeMessage(sid)
    message.info(t('chat.atMeNotFound'))
    return
  }

  stickToBottom.value = false
  messageListRef.value?.scrollToKey(targetId)
  highlightAtMeId.value = targetId
  if (highlightAtMeTimer) window.clearTimeout(highlightAtMeTimer)
  highlightAtMeTimer = window.setTimeout(() => {
    highlightAtMeId.value = null
    highlightAtMeTimer = 0
  }, 1800)
  clearAtMeMessage(sid)
}

/** 搜索/收藏跳转：定位 pendingFocusMessageId */
async function jumpToPendingFocusMessage() {
  const sid = currentSessionId.value
  const targetId = pendingFocusMessageId.value
  if (!sid || !targetId) return

  let idx = chatMessages.value.findIndex(m => m.id === targetId)
  let attempts = 0
  while (idx < 0 && attempts < 20 && appStore.messagesHasMore[sid] !== false) {
    attempts++
    await loadMoreMessages(sid)
    await nextTick()
    idx = chatMessages.value.findIndex(m => m.id === targetId)
  }

  if (idx < 0) {
    clearPendingFocusMessage()
    message.info(t('overlay.messageNotFound'))
    return
  }

  stickToBottom.value = false
  await nextTick()
  messageListRef.value?.scrollToKey(targetId)
  highlightAtMeId.value = targetId
  if (highlightAtMeTimer) window.clearTimeout(highlightAtMeTimer)
  highlightAtMeTimer = window.setTimeout(() => {
    highlightAtMeId.value = null
    highlightAtMeTimer = 0
  }, 1800)
  clearPendingFocusMessage()
  message.success(t('overlay.jumpedToMessage'))
}

watch(
  [pendingFocusMessageId, currentSessionId, () => chatMessages.value.length, sessionEnterTick],
  async ([msgId, sid]) => {
    if (!msgId || !sid) return
    // 等首屏历史加载完成再跳
    if (appStore.messagesLoading[sid]) return
    await nextTick()
    await jumpToPendingFocusMessage()
  }
)

function onVirtualScroll(payload: {
  scrollTop: number
  scrollHeight: number
  clientHeight: number
}) {
  // 进会话强制贴底窗口内：保持 stickToBottom，且不拉更早消息
  if (Date.now() < loadMoreLockUntil) {
    if (!stickToBottom.value) stickToBottom.value = true
    return
  }
  const distanceFromBottom = payload.scrollHeight - payload.scrollTop - payload.clientHeight
  const nextStick = distanceFromBottom < 24
  // 值不变不写 ref，避免滚动时整页重渲染
  if (stickToBottom.value !== nextStick) {
    stickToBottom.value = nextStick
    if (nextStick) {
      const sid = currentSessionId.value
      if (sid) void appStore.reportSessionRead(sid)
    }
  }
  void maybeLoadOlderMessages(payload.scrollTop)
}

async function maybeLoadOlderMessages(scrollTop: number) {
  const sessionId = currentSessionId.value
  if (!sessionId || !currentSession.value?.isReal) return
  if (scrollTop > 12) return
  if (loadingMore.value || Date.now() < loadMoreLockUntil) return
  if (appStore.messagesHasMore[sessionId] === false) return
  if (appStore.messagesLoading[sessionId]) return

  const el = messageListRef.value?.getScrollElement()
  if (!el) return

  loadingMore.value = true
  loadMoreLockUntil = Date.now() + 400
  const prevHeight = el.scrollHeight
  const prevTop = el.scrollTop
  try {
    await loadMoreMessages(sessionId)
    await nextTick()
    messageListRef.value?.restoreAfterPrepend(prevHeight, prevTop)
  } finally {
    loadingMore.value = false
  }
}

// 复制消息内容到剪贴板
function copyMessage(msg: ChatMessage) {
  const text =
    msg.type === 'file'
      ? msg.fileName || msg.content // 文件消息复制文件名
      : msg.content
  void import('../utils/clipboard').then(({ copyText }) => {
    void copyText(text || '').then(ok => {
      if (ok) message.success(t('chat.copied'))
      else message.warning(t('account.copyFail'))
    })
  })
}

// 收藏消息到收藏夹（图片/文件必须存完整 URL，禁止截断）
function favoriteMessage(msg: ChatMessage) {
  const mediaUrl = (msg.fileUrl || msg.content || '').trim()
  const sizeBytes = parseFavoriteFileSize(msg.fileSize)
  const sourceType = 'conversation'
  const sourceId = msg.sessionId ? `${msg.sessionId}#${msg.id}` : msg.sessionId
  if (msg.type === 'file') {
    void favoritesStore.add({
      title: msg.fileName || msg.content || t('chat.file'),
      content: mediaUrl || msg.fileName || msg.content,
      preview: msg.fileSize || msg.fileName || '',
      type: 'file',
      fileSize: sizeBytes,
      sourceType,
      sourceId
    })
  } else if (msg.type === 'image' || msg.isImage) {
    void favoritesStore.add({
      title: msg.fileName || t('chat.imageMessage'),
      content: mediaUrl,
      preview: msg.fileName || t('chat.imageMessage'),
      type: 'image',
      fileSize: sizeBytes,
      sourceType,
      sourceId
    })
  } else if (msg.type === 'link') {
    void favoritesStore.add({
      title: msg.content.slice(0, 30),
      content: msg.linkUrl || msg.content,
      preview: msg.content,
      type: 'link',
      sourceType,
      sourceId
    })
  } else {
    // 聊天文本等 → 聊天记录（勿存成 note，否则会打开笔记编辑器）
    const preview = msg.content || t('chat.messageFallback')
    void favoritesStore.add({
      title: preview.slice(0, 40),
      content: preview,
      preview,
      type: 'message',
      sourceType,
      sourceId
    })
  }
  message.success(t('chat.favorited'))
}

/** 将「1.2 MB」类展示串尽量还原为字节；无法解析则忽略 */
function parseFavoriteFileSize(raw?: string): number | undefined {
  if (!raw) return undefined
  const s = raw.trim()
  if (!s) return undefined
  const m = s.match(/^([\d.]+)\s*(B|KB|MB|GB|TB)?$/i)
  if (!m) return undefined
  const n = Number(m[1])
  if (!Number.isFinite(n)) return undefined
  const unit = (m[2] || 'B').toUpperCase()
  const mul =
    unit === 'TB' ? 1024 ** 4 : unit === 'GB' ? 1024 ** 3 : unit === 'MB' ? 1024 ** 2 : unit === 'KB' ? 1024 : 1
  return Math.round(n * mul)
}

// 消息右键菜单相关状态
const ctxMsg = ref<ChatMessage | null>(null) // 右键选中的消息
const ctxShow = ref(false) // 是否显示菜单
const ctxX = ref(0) // 菜单 X 坐标
const ctxY = ref(0) // 菜单 Y 坐标

// 根据消息类型与是否自己发送动态生成右键菜单选项
const ctxOptions = computed<DropdownOption[]>(() => {
  const msg = ctxMsg.value
  if (!msg) return []
  const copyLabel =
    msg.type === 'file'
      ? t('chat.copyFileName')
      : msg.type === 'image' || msg.isImage
        ? t('chat.copyLink')
        : t('chat.copy')
  const opts: DropdownOption[] = [
    { label: copyLabel, key: 'copy' },
    { label: t('chat.favorite'), key: 'fav' }
  ]
  if (msg.type !== 'conference' && msg.type !== 'redPacket') {
    opts.push({ label: t('chat.replyAction'), key: 'reply' })
  }
  if (canSetEssence(msg)) {
    opts.push({ label: t('chat.setAsEssence'), key: 'essence' })
  }
  if (msg.isSelf && canRecallMessage(msg)) {
    opts.push({ type: 'divider', key: 'd' }, { label: t('chat.recall'), key: 'recall' })
  }
  if (msg.isSelf && msg.type === 'text' && msg.sendStatus !== 'failed') {
    opts.push({ label: t('chat.edit'), key: 'edit' })
  }
  if (
    msg.type !== 'system' &&
    msg.type !== 'recall' &&
    msg.type !== 'redPacket' &&
    msg.type !== 'conference'
  ) {
    opts.push({ label: t('chat.forward'), key: 'forward' })
  }
  if (msg.isSelf && msg.sendStatus === 'failed') {
    opts.push({ type: 'divider', key: 'd-retry' }, { label: t('chat.retry'), key: 'retry' })
  }
  return opts
})

/** 与后端一致：发送后 2 分钟内可撤回 */
const RECALL_WINDOW_MS = 2 * 60 * 1000

function canRecallMessage(msg: ChatMessage) {
  if (!msg.isSelf || msg.type === 'recall' || msg.type === 'system') return false
  if (msg.createTime == null || !Number.isFinite(msg.createTime)) return true
  return Date.now() - msg.createTime <= RECALL_WINDOW_MS
}

/** 群主/管理员可将文本、图片、文件、链接消息设为精华 */
function canSetEssence(msg: ChatMessage) {
  if (!isGroupChat.value || !isGroupAdmin.value) return false
  if (
    msg.type === 'system' ||
    msg.type === 'recall' ||
    msg.type === 'redPacket' ||
    msg.type === 'conference' ||
    msg.type === 'dataCard'
  ) {
    return false
  }
  return true
}

// 消息右键：记录坐标与目标消息
function onMsgContext(e: MouseEvent, msg: ChatMessage) {
  e.preventDefault()
  ctxMsg.value = msg
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxShow.value = true
}

// 处理消息右键菜单选项选中
function onCtxSelect(key: string) {
  const msg = ctxMsg.value
  if (!msg) return
  if (key === 'copy') copyMessage(msg)
  else if (key === 'fav') favoriteMessage(msg)
  else if (key === 'reply') replyMessage(msg)
  else if (key === 'essence') void setMessageAsEssence(msg)
  else if (key === 'recall') recallMessage(msg)
  else if (key === 'edit') openEditMessage(msg)
  else if (key === 'forward') openForwardMessage(msg)
  else if (key === 'retry') void retryMessage(msg)
  ctxShow.value = false
}

// 设置回复目标并聚焦输入框
function replyMessage(msg: ChatMessage) {
  replyingTo.value = msg
  document.querySelector<HTMLTextAreaElement>('.message-input textarea')?.focus()
}

/** 将消息设为群精华 */
async function setMessageAsEssence(msg: ChatMessage) {
  const sessionId = currentSessionId.value
  if (!sessionId || !canSetEssence(msg)) return

  let content = ''
  let type: 'link' | 'video' | 'text' = 'text'
  if (msg.type === 'file') {
    content = msg.fileName || msg.content || t('chat.messageFallback')
  } else if (msg.type === 'image' || msg.isImage) {
    content = msg.content || t('chat.imageMessage')
    type = 'link'
  } else if (msg.type === 'link') {
    content = msg.linkUrl || msg.content
    type = 'link'
  } else if (msg.type === 'voice') {
    content = t('chat.voice')
  } else {
    content = (msg.content || '').trim()
  }
  if (!content) {
    message.warning(t('chat.essenceUnsupported'))
    return
  }

  const user =
    msg.senderName ||
    (msg.isSelf ? userProfile.value.nickname : '') ||
    t('chat.messageFallback')
  const date = new Date().toISOString().slice(0, 10)

  try {
    const ok = await groupMetaStore.addEssence(sessionId, {
      user,
      date,
      type,
      content,
      messageId: msg.id
    })
    if (ok) message.success(t('chat.essenceSetOk'))
    else message.error(t('chat.essenceSetFail'))
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.essenceSetFail'))
  }
}

// 撤回消息
async function recallMessage(msg: ChatMessage) {
  try {
    await recallMessageInStore(msg.id)
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.recallFail'))
  }
}

const editModalShow = ref(false)
const editContent = ref('')
const editingMsg = ref<ChatMessage | null>(null)
const editSaving = ref(false)

function openEditMessage(msg: ChatMessage) {
  if (!msg.isSelf || msg.type !== 'text') return
  editingMsg.value = msg
  editContent.value = msg.content || ''
  editModalShow.value = true
}

async function confirmEditMessage() {
  const msg = editingMsg.value
  const text = editContent.value.trim()
  if (!msg || !text) return
  editSaving.value = true
  try {
    await editMessageInStore(msg.id, text)
    message.success(t('chat.editOk'))
    editModalShow.value = false
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.editFail'))
  } finally {
    editSaving.value = false
  }
}

const forwardModalShow = ref(false)
const forwardingMsg = ref<ChatMessage | null>(null)
const forwardSaving = ref(false)

function openForwardMessage(msg: ChatMessage) {
  forwardingMsg.value = msg
  forwardModalShow.value = true
}

async function confirmForward(payload: { targetIds: string[]; leaveMessage: string }) {
  const msg = forwardingMsg.value
  if (!msg || !payload.targetIds.length) return
  forwardSaving.value = true
  const sourceSession = currentSessionId.value
  try {
    for (const targetId of payload.targetIds) {
      if (sourceSession) appStore.currentSessionId = sourceSession
      await forwardMessageInStore(msg.id, targetId)
      if (payload.leaveMessage) {
        appStore.currentSessionId = targetId
        await appStore.sendMessage(payload.leaveMessage, { type: 'text' })
      }
    }
    message.success(t('chat.forwardOk'))
    forwardModalShow.value = false
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.forwardFail'))
  } finally {
    if (sourceSession) appStore.currentSessionId = sourceSession
    forwardSaving.value = false
  }
}

function onForwardCreateGroup() {
  message.info(t('viewer.forwardCreateGroupHint'))
}

const forwardPreviewText = computed(() => {
  const msg = forwardingMsg.value
  return msg ? chatMessagePreviewText(msg) : ''
})

const forwardPreviewImageUrl = computed(() => {
  const msg = forwardingMsg.value
  if (!msg || (msg.type !== 'image' && !msg.isImage)) return ''
  return msg.fileUrl || ''
})

async function retryMessage(msg: ChatMessage) {
  try {
    await retryFailedMessage(msg.id)
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('chat.messageSendFail'))
  }
}

// 是否正在拖拽文件到聊天区
const isDraggingFile = ref(false)

// 文件拖入聊天区：显示拖放提示
function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (hasSession.value) {
    isDraggingFile.value = true
  }
}

// 文件拖离聊天区：隐藏拖放提示
function onDragLeave(e: DragEvent) {
  e.preventDefault()
  isDraggingFile.value = false
}

// 文件放下：通过输入框组件发送文件
function onDrop(e: DragEvent) {
  e.preventDefault()
  isDraggingFile.value = false
  if (!hasSession.value) return
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    for (let i = 0; i < files.length; i++) {
      chatInputRef.value?.handleFileSend(files[i])
    }
  }
}

</script>

<template>
  <!-- 聊天面板根容器，群聊附加样式类，支持文件拖放 -->
  <div class="chat-panel" :class="{ 'chat-panel--group': isGroupChat }"
       @dragover="onDragOver"
       @dragleave="onDragLeave"
       @drop="onDrop">
    <!-- 文件拖放提示遮罩 -->
    <div v-if="isDraggingFile" class="drag-overlay">
      <div class="drag-overlay-content">
        <n-icon :component="ImagesOutline" :size="48" />
        <span>{{ t('chat.dropToSend') }}</span>
      </div>
    </div>
    <div class="functional-region">
      <!-- 好友顶栏 -->
      <header v-if="isFriendChat" class="chat-header">
        <div class="chat-header-left">
          <button type="button" class="avatar-btn" @click="openPeerProfile">
            <Avatar v-bind="peerAvatarProps(32)" />
          </button>
          <div class="peer-meta">
            <span class="chat-peer-name">{{ currentSession?.name }}</span>
            <div class="peer-status-line">
              <span v-if="peerOnline" class="peer-online-text">{{ t('chat.online') }}</span>
              <span v-else-if="peerLastSeenText" class="peer-last-seen">{{ peerLastSeenText }}</span>
              <span v-if="typingHint" class="peer-typing">{{ typingHint }}</span>
            </div>
          </div>
        </div>
        <div class="chat-header-actions">
          <button
            type="button"
            class="hdr-btn"
            :title="t('chat.voiceCall')"
            @click="() => startCall('voice')"
          >
            <n-icon :component="CallOutline" :size="20" />
          </button>
          <button
            type="button"
            class="hdr-btn"
            :title="t('chat.videoCall')"
            @click="() => startCall('video')"
          >
            <n-icon :component="VideocamOutline" :size="20" />
          </button>
          <button
            type="button"
            class="hdr-btn"
            :title="t('modals.startConference')"
            :disabled="conferenceCreating"
            @click="startConference"
          >
            <n-icon :component="PeopleOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" :title="t('chat.more')" @click="toggleMore">
            <n-icon :component="EllipsisHorizontalOutline" :size="20" />
          </button>
        </div>
      </header>

      <!-- 群聊顶栏 -->
      <header v-else-if="isGroupChat" class="chat-header chat-header--group">
        <div class="chat-header-left">
          <span class="chat-peer-name chat-peer-name--group">{{ currentSession?.name }}</span>
        </div>
        <div class="chat-header-actions">
          <button
            type="button"
            class="hdr-btn"
            :title="t('chat.voiceCall')"
            @click="() => startCall('voice')"
          >
            <n-icon :component="CallOutline" :size="20" />
          </button>
          <button
            type="button"
            class="hdr-btn"
            :title="t('chat.videoCall')"
            @click="() => startCall('video')"
          >
            <n-icon :component="VideocamOutline" :size="20" />
          </button>
          <button
            type="button"
            class="hdr-btn"
            :title="t('modals.startConference')"
            :disabled="conferenceCreating"
            @click="startConference"
          >
            <n-icon :component="PeopleOutline" :size="20" />
          </button>
          <n-popover
            trigger="click"
            placement="bottom-end"
            :show-arrow="false"
            raw
            class="group-apps-popover"
          >
            <template #trigger>
              <button type="button" class="hdr-btn" :title="t('chat.groupApps')">
                <n-icon :component="GridOutline" :size="20" />
              </button>
            </template>
            <div class="group-grid-menu">
              <button
                v-for="item in groupGridItems"
                :key="item.key"
                type="button"
                class="grid-menu-item"
                @click="onGroupAppClick(item.key)"
              >
                {{ item.label }}
              </button>
            </div>
          </n-popover>
          <button type="button" class="hdr-btn" :title="t('chat.invite')" @click="openAddMembers">
            <n-icon :component="AddOutline" :size="20" />
          </button>
          <button type="button" class="hdr-btn" :title="t('chat.more')" @click="toggleGroupInfo">
            <n-icon :component="EllipsisHorizontalOutline" :size="20" />
          </button>
        </div>
      </header>

      <!-- 我的手机等 -->
      <div v-else-if="hasSession && !isFriendChat" class="session-subheader">
        <span class="session-subheader-title">{{ currentSession?.name }}</span>
      </div>

      <!-- 聊天主体：消息区 + 输入框 + 侧边抽屉 -->
      <div class="chat-body-row">
        <div class="chat-main-col">
          <!-- 固定在群名下方、消息列表上方 -->
          <ConferenceSessionBanner
            v-if="sessionMediaBanner"
            :info="sessionMediaBanner"
            :in-room="sessionConferenceInRoom"
            @join="onSessionConferenceJoin"
          />
          <div class="chat-content-stack">
            <!-- 消息列表区域 -->
            <div
              class="message-area"
              :class="{ 'message-area--padded': hasSession }"
              :style="hasSession ? chatBgStyle : undefined"
            >
              <div
                class="message-list-container"
                ref="messageListContainer"
                :class="{ 'is-entering': messageEntering }"
              >

                <MessageVirtualList
                  v-if="hasSession"
                  v-show="chatMessages.length"
                  :session-id="currentSessionId || ''"
                  ref="messageListRef"
                  :items="chatMessages"
                  @scroll="onVirtualScroll"
                >
                  <template #default="{ msg }">
                      <div
                      v-memo="[msg.id, msg.content, msg.type, msg.sendStatus, msg.uploadProgress, msg.fileStatus, msg.edited, msg.readCount, msg.totalMembers, playingVoiceId === msg.id, msg.senderAvatar, msg.isSelf, highlightAtMeId === msg.id]"
                    >
                      <ChatMessageItem
                        :msg="msg"
                        :playing="playingVoiceId === msg.id"
                        :highlight="highlightAtMeId === msg.id"
                        @contextmenu="onMsgContext"
                        @play-voice="playVoice"
                        @open-file-view="openFileView"
                        @open-chat-file="openChatFile"
                        @open-image-view="openImageView"
                        @click-red-packet="onRedPacketClick"
                        @click-conference="onConferenceClick"
                        @open-peer-profile="openPeerProfile"
                        @open-self-profile="openSelfProfileClick"
                        @retry="retryMessage"
                      />
                    </div>
                  </template>
                </MessageVirtualList>

                <!-- 群聊：有人@我浮层（右上角红字），点击跳转 -->
                <button
                  v-if="showAtMeFab"
                  type="button"
                  class="at-me-fab"
                  @click="jumpToAtMeMessage"
                >
                  {{ t('chat.someoneAtMeFab') }}
                </button>

                <!-- 未选会话：发现区（推荐位 + 活动）；已选但无消息：水印 -->
                <DiscoverEmptyPane v-else-if="!hasSession" />
                <PenguinWatermark
                  v-else-if="!chatMessages.length"
                  :hint="t('chat.emptyChat')"
                />
              </div>
            </div>

            <!-- 聊天输入框 -->
            <ChatInputBox
              ref="chatInputRef"
              v-if="hasSession"
              :is-my-phone="isMyPhone"
              :is-friend-chat="isFriendChat"
              :is-group-chat="isGroupChat"
              v-model:replying-to="replyingTo"
              @scroll-to-bottom="scrollToBottom"
            />
          </div>
          <!-- 好友聊天更多抽屉 -->
          <ChatMoreDrawer v-if="isFriendChat" />
        </div>
        <!-- 群聊右侧成员侧栏 -->
        <GroupChatSidebar v-if="isGroupChat" />
        <!-- 群信息抽屉 -->
        <GroupInfoDrawer v-if="isGroupChat" />
      </div>
    </div>

    <!-- 消息右键菜单 -->
    <n-dropdown
      trigger="manual"
      placement="bottom-start"
      :show="ctxShow"
      :x="ctxX"
      :y="ctxY"
      :options="ctxOptions"
      @select="onCtxSelect"
      @clickoutside="ctxShow = false"
    />

    <n-modal
      v-model:show="editModalShow"
      preset="card"
      :title="t('chat.edit')"
      style="max-width: 420px"
      :mask-closable="!editSaving"
    >
      <n-input
        v-model:value="editContent"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 8 }"
        :placeholder="t('chat.inputPlaceholder')"
      />
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px">
          <button type="button" class="lx-modal-btn" :disabled="editSaving" @click="editModalShow = false">
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="lx-modal-btn primary"
            :disabled="editSaving || !editContent.trim()"
            @click="confirmEditMessage"
          >
            {{ t('common.confirm') }}
          </button>
        </div>
      </template>
    </n-modal>

    <ForwardPickerModal
      v-model:show="forwardModalShow"
      :exclude-session-id="currentSessionId ?? undefined"
      :loading="forwardSaving"
      :preview-text="forwardPreviewText"
      :preview-image-url="forwardPreviewImageUrl"
      @confirm="confirmForward"
      @create-group="onForwardCreateGroup"
    />

    <ConferenceCreateDialog
      :show="conferenceCreateOpen"
      :default-title="currentSession?.name || ''"
      @confirm="onConferenceCreateConfirm"
      @cancel="conferenceCreateOpen = false"
    />
  </div>
</template>

<style scoped>
.chat-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.functional-region {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  position: relative;
}

.drag-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drag-overlay-content {
  background: var(--lx-bg-panel);
  padding: 32px 48px;
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--lx-accent);
  font-size: 18px;
  font-weight: 600;
  box-shadow: var(--lx-shadow-dropdown);
  pointer-events: none;
}

.chat-body-row {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: row;
  overflow: hidden;
  position: relative;
}

.chat-main-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.chat-content-stack {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  isolation: isolate;
}

.chat-peer-name--group {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: min(420px, 45vw);
}

.group-grid-menu {
  display: flex;
  flex-direction: column;
  min-width: 148px;
  padding: 6px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-shadow-dropdown);
  border: 1px solid var(--lx-border-light);
}

.grid-menu-item {
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 14px;
  font-size: 14px;
  color: var(--lx-text-body);
  cursor: pointer;
  border-radius: var(--lx-radius);
  margin: 0;
}

.grid-menu-item:hover {
  background: var(--lx-bg-panel);
}

/* 好友顶栏 */
.chat-header {
  flex-shrink: 0;
  height: 52px;
  padding: 0 16px 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--lx-bg-panel);
  border-bottom: none;
  box-shadow: 0 1px 0 var(--lx-separator-fade, rgba(0, 0, 0, 0.04));
  position: relative;
  z-index: 31;
}

.chat-header-left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  flex: 1;
  padding-top: 2px;
}

.peer-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
}

.peer-status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  line-height: 1.2;
}

.chat-peer-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  line-height: 1.25;
}

.peer-online-text {
  font-size: 12px;
  color: var(--lx-success);
  line-height: 1.2;
}

.peer-last-seen {
  font-size: 12px;
  color: var(--lx-text-muted);
  white-space: nowrap;
  line-height: 1.2;
}

.peer-typing {
  font-size: 12px;
  color: var(--lx-accent);
  line-height: 1.2;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.hdr-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  color: var(--lx-text-nav);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hdr-btn:hover {
  background: var(--lx-border-light);
  color: var(--lx-text-body);
}

.hdr-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.hdr-btn:disabled:hover {
  background: transparent;
  color: var(--lx-text-nav);
}

.session-subheader {
  flex-shrink: 0;
  padding: 10px 20px;
  border-bottom: none;
  box-shadow: 0 1px 0 var(--lx-separator-fade, rgba(0, 0, 0, 0.04));
  background: rgba(255, 255, 255, 0.35);
}

.session-subheader-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--lx-text-body);
}

.message-area {
  flex: 1;
  overflow: hidden;
  background: transparent;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.message-area--padded {
  padding: 12px 16px 16px;
}

.message-list-container {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

.message-list-container.is-entering :deep(.msg-vl) {
  visibility: hidden;
}

.at-me-fab {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 8;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--lx-danger, #f04040);
  cursor: pointer;
  max-width: calc(100% - 24px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.at-me-fab:hover {
  color: #d93636;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.message-time {
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 12px;
}

.message-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.message-row.left {
  justify-content: flex-start;
}

.message-row.right {
  justify-content: flex-end;
}

.avatar-btn {
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: pointer;
  border-radius: var(--lx-avatar-radius);
  flex-shrink: 0;
  line-height: 0;
}

.avatar-btn:hover {
  opacity: 0.88;
}

.avatar-btn:focus-visible {
  outline: 2px solid var(--lx-accent);
  outline-offset: 2px;
}

/* 右键菜单 */
.msg-context-menu {
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  min-width: 100px;
  padding: 4px;
}

.menu-item {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--lx-text-body);
  cursor: pointer;
  border-radius: var(--lx-radius);
}

.menu-item:hover {
  background: var(--lx-bg-panel);
}

.menu-item.danger {
  color: var(--lx-danger);
}

.menu-item.danger:hover {
  background: var(--lx-danger-bg);
}

.lx-modal-btn {
  border: 1px solid var(--lx-border);
  background: var(--lx-bg-card);
  color: var(--lx-text);
  border-radius: var(--lx-radius);
  padding: 6px 14px;
  cursor: pointer;
  font-size: 13px;
}

.lx-modal-btn.primary {
  background: var(--lx-accent);
  border-color: var(--lx-accent);
  color: #fff;
}

.lx-modal-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.hidden-file-input {
  display: none;
}

.lx-bubble-image {
  max-width: 220px;
  max-height: 280px;
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
  display: block;
}

.image-bubble {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 4px;
}

.emoji-btn {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
}

</style>
