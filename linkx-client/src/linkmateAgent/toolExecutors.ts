/**
 * 作者：yangleduo
 */
import type { ChatSession, FavoriteItem, SettingsTab } from '../types'
import { useAppStore } from '../stores/app'
import { useCalendarStore } from '../stores/calendar'
import { useChatModalsStore } from '../stores/chatModals'
import { useContactsStore } from '../stores/contacts'
import { useFavoritesStore } from '../stores/favorites'
import { useNotificationsStore } from '../stores/notifications'
import { useDriveStore } from '../stores/drive'
import { useMomentsStore } from '../stores/moments'
import { useAppSettingsStore, type NotifyToneId } from '../stores/appSettings'
import { t } from '../i18n'
import * as friendApi from '../api/friend'
import * as groupApi from '../api/group'
import * as redPacketApi from '../api/redPacket'
import * as balanceApi from '../api/balance'
import { setLocale } from '../i18n'
import { applyAgentNav, openLinkMatePanel } from './clientNav'
import {
  buildMessageContentWithMentions,
  resolveCalendarEvent,
  resolveContactMembers,
  resolveFavoriteItem,
  resolveFriendRequestId,
  resolveGroupInvitationId,
  resolveReplyMessage
} from './resolvers'
import { resolveChatSession } from './sessionResolve'
import type { LinkMateActionResult, LinkMateAgentToolName } from './types'
import { isNavKey } from './types'
import { clearSimulatedInput } from './uiBridge'
import { asString, asStringArray, parseActionEnum, truncate } from './agentUtils'

const SYNCABLE_SETTING_KEYS = [
  'autoStart',
  'soundNotify',
  'messageDetail',
  'notifyAtMe',
  'notifySound',
  'privacyVerifyFriend',
  'privacyAllowStranger',
  'privacyShowOnline',
  'privacySendReadReceipt',
  'language',
  'translateTargetLang',
  'chatBackground',
  'notifyTone',
  'favoritesViewMode',
  'favoritesSort',
  'quietHoursEnabled',
  'quietHoursStart',
  'quietHoursEnd',
  'notifyChat',
  'notifySocial',
  'notifyMoments',
  'notifySystem',
  'notifyFriendOnline'
] as const

type SyncableSettingKey = (typeof SYNCABLE_SETTING_KEYS)[number]

function openChatSession(session: ChatSession) {
  const app = useAppStore()
  app.setNav('chat')
  app.selectSession(session)
}

function parseFavoriteType(raw: string): FavoriteItem['type'] {
  if (raw === 'link' || raw === 'image' || raw === 'file' || raw === 'message' || raw === 'note') {
    return raw
  }
  return 'note'
}

function parseSettingsTab(raw: string): SettingsTab | undefined {
  const tabs: SettingsTab[] = [
    'account',
    'general',
    'notifications',
    'privacy',
    'chat',
    'files',
    'shortcuts',
    'appearance',
    'about'
  ]
  return tabs.includes(raw as SettingsTab) ? (raw as SettingsTab) : undefined
}

function coerceSettingValue(key: SyncableSettingKey, raw: string): unknown {
  if (
    key.startsWith('notify') ||
    key.startsWith('privacy') ||
    key === 'autoStart' ||
    key === 'soundNotify' ||
    key === 'messageDetail' ||
    key === 'quietHoursEnabled'
  ) {
    if (raw === 'true' || raw === '1' || raw === 'on') return true
    if (raw === 'false' || raw === '0' || raw === 'off') return false
  }
  return raw
}

export async function executeNavigate(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const navRaw = asString(args.nav)
  if (!isNavKey(navRaw)) {
    return { ok: false, message: t('linkmateAgent.invalidNav', { nav: navRaw || '?' }) }
  }
  const settingsTab = parseSettingsTab(asString(args.settingsTab))
  await applyAgentNav(navRaw, settingsTab ? { settingsTab } : undefined)
  return { ok: true, message: t('linkmateAgent.doneNavigate', { nav: t(`nav.${navRaw}`) }) }
}

export async function executeOpenLinkmate(): Promise<LinkMateActionResult> {
  await openLinkMatePanel()
  return { ok: true, message: t('linkmateAgent.doneOpenLinkmate') }
}

export async function executeOpenChat(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const conversationId = asString(args.conversationId)
  const name = asString(args.name)
  if (!conversationId && !name) {
    return { ok: false, message: t('linkmateAgent.openChatMissingArgs') }
  }
  const session = resolveChatSession(args)
  if (!session) {
    if (name) {
      return { ok: false, message: t('linkmateAgent.chatNotFoundByName', { name }) }
    }
    return { ok: false, message: t('linkmateAgent.chatNotFound') }
  }
  openChatSession(session)
  return { ok: true, message: t('linkmateAgent.doneOpenChat', { name: session.name }) }
}

export async function executeOpenSearch(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const keyword = asString(args.keyword)
  const modals = useChatModalsStore()
  modals.openComprehensiveSearch(keyword || undefined)
  if (keyword) {
    return { ok: true, message: t('linkmateAgent.doneOpenSearch', { keyword }) }
  }
  return { ok: true, message: t('linkmateAgent.doneOpenSearchEmpty') }
}

export async function executeOpenCalendar(): Promise<LinkMateActionResult> {
  await applyAgentNav('calendar')
  return { ok: true, message: t('linkmateAgent.doneOpenCalendar') }
}

export async function executeOpenContacts(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const app = useAppStore()
  await applyAgentNav('contacts')
  const view = parseActionEnum(args.view, ['friend-notifs', 'group-notifs', 'default'] as const)
  if (view === 'friend-notifs') {
    app.contactsActiveView = 'friend-notifs'
  } else if (view === 'group-notifs') {
    app.contactsActiveView = 'group-notifs'
  } else {
    app.resetContactsView()
  }
  return { ok: true, message: t('linkmateAgent.doneOpenContacts') }
}

export async function executeSendMessage(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  let content = asString(args.content)
  const mentionNames = asStringArray(args.mentionNames)
  content = buildMessageContentWithMentions(content, mentionNames)
  if (!content) {
    return { ok: false, message: t('linkmateAgent.sendMessageEmpty') }
  }

  const session = resolveChatSession(args)
  if (!session) {
    const name = asString(args.name)
    if (name) {
      return { ok: false, message: t('linkmateAgent.chatNotFoundByName', { name }) }
    }
    return { ok: false, message: t('linkmateAgent.sendMessageNoSession') }
  }
  if (session.blocked) {
    return { ok: false, message: t('linkmateAgent.sendMessageBlocked') }
  }

  openChatSession(session)
  const app = useAppStore()
  const replyToMessageId = asString(args.replyToMessageId)
  const replyTo = replyToMessageId
    ? resolveReplyMessage(session.id, replyToMessageId)
    : undefined
  if (replyToMessageId && !replyTo) {
    return { ok: false, message: t('linkmateAgent.replyMessageNotFound') }
  }

  await app.sendMessage(content, { type: 'text', replyTo: replyTo ?? undefined })
  clearSimulatedInput()
  return {
    ok: true,
    message: t('linkmateAgent.doneSendMessage', {
      name: session.name,
      preview: truncate(content)
    })
  }
}

export async function executeAddFriend(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const username = asString(args.username)
  if (!username) {
    return { ok: false, message: t('linkmateAgent.addFriendMissingUsername') }
  }
  const messageText = asString(args.message) || t('modals.friendRequestMsg')
  try {
    const res = await friendApi.sendFriendRequest({ username, message: messageText })
    if (res.code !== 200) {
      return { ok: false, message: res.message || t('modals.friendRequestFail') }
    }
    const notifications = useNotificationsStore()
    await Promise.all([notifications.fetchFriendRequests(), useContactsStore().fetchFriends()])
    return { ok: true, message: t('linkmateAgent.doneAddFriend', { name: username }) }
  } catch (err) {
    const msg = err instanceof Error ? err.message : t('modals.friendRequestFail')
    return { ok: false, message: msg }
  }
}

export async function executeHandleFriendRequest(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const action = parseActionEnum(args.action, ['accept', 'reject'] as const)
  if (!action) {
    return { ok: false, message: t('linkmateAgent.invalidRequestAction') }
  }
  const requestId = resolveFriendRequestId(args)
  if (!requestId) {
    return { ok: false, message: t('linkmateAgent.friendRequestNotFound') }
  }
  try {
    const notifications = useNotificationsStore()
    if (action === 'accept') {
      await notifications.acceptFriendRequest(requestId)
    } else {
      await notifications.rejectFriendRequest(requestId)
    }
    return {
      ok: true,
      message:
        action === 'accept'
          ? t('linkmateAgent.doneAcceptFriendRequest')
          : t('linkmateAgent.doneRejectFriendRequest')
    }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('linkmateAgent.executeFailed') }
  }
}

export async function executeHandleGroupInvitation(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const action = parseActionEnum(args.action, ['accept', 'reject'] as const)
  if (!action) {
    return { ok: false, message: t('linkmateAgent.invalidRequestAction') }
  }
  const invitationId = resolveGroupInvitationId(args)
  if (!invitationId) {
    return { ok: false, message: t('linkmateAgent.groupInvitationNotFound') }
  }
  try {
    const notifications = useNotificationsStore()
    if (action === 'accept') {
      await notifications.acceptGroupInvitationAction(invitationId)
    } else {
      await notifications.rejectGroupInvitationAction(invitationId)
    }
    return {
      ok: true,
      message:
        action === 'accept'
          ? t('linkmateAgent.doneAcceptGroupInvitation')
          : t('linkmateAgent.doneRejectGroupInvitation')
    }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('linkmateAgent.executeFailed') }
  }
}

export async function executeCreateCalendarEvent(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const title = asString(args.title)
  const date = asString(args.date)
  if (!title) {
    return { ok: false, message: t('linkmateAgent.createEventMissingTitle') }
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return { ok: false, message: t('linkmateAgent.createEventInvalidDate') }
  }

  await applyAgentNav('calendar')
  const calendar = useCalendarStore()
  const eventId = await calendar.addEvent({
    title,
    date,
    time: asString(args.time) || undefined,
    endTime: asString(args.endTime) || undefined,
    color: asString(args.color) || undefined
  })
  if (!eventId) {
    return { ok: false, message: t('linkmateAgent.createEventFailed') }
  }

  const [year, month, day] = date.split('-').map(Number)
  await calendar.setSelectedDate(new Date(year, month - 1, day).getTime())
  return {
    ok: true,
    message: t('linkmateAgent.doneCreateEvent', { title, date })
  }
}

export async function executeUpdateCalendarEvent(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const event = resolveCalendarEvent(args)
  if (!event) {
    return { ok: false, message: t('linkmateAgent.eventNotFound') }
  }
  const patch: Record<string, string | undefined> = {}
  const title = asString(args.title)
  const date = asString(args.date)
  const time = asString(args.time)
  const endTime = asString(args.endTime)
  const color = asString(args.color)
  if (title) patch.title = title
  if (date) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return { ok: false, message: t('linkmateAgent.createEventInvalidDate') }
    }
    patch.date = date
  }
  if (time) patch.time = time
  if (endTime) patch.endTime = endTime
  if (color) patch.color = color
  if (!Object.keys(patch).length) {
    return { ok: false, message: t('linkmateAgent.updateEventMissingFields') }
  }

  await applyAgentNav('calendar')
  const calendar = useCalendarStore()
  const ok = await calendar.updateEvent(event.id, {
    title: patch.title ?? event.title,
    date: patch.date ?? event.date,
    time: patch.time ?? event.time,
    endTime: patch.endTime ?? event.endTime,
    color: patch.color ?? event.color
  })
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.updateEventFailed') }
  }
  return {
    ok: true,
    message: t('linkmateAgent.doneUpdateEvent', { title: patch.title ?? event.title })
  }
}

export async function executeDeleteCalendarEvent(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const event = resolveCalendarEvent(args)
  if (!event) {
    return { ok: false, message: t('linkmateAgent.eventNotFound') }
  }
  await applyAgentNav('calendar')
  const ok = await useCalendarStore().removeEvent(event.id)
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.deleteEventFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneDeleteEvent', { title: event.title }) }
}

export async function executeAddFavorite(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const title = asString(args.title)
  if (!title) {
    return { ok: false, message: t('linkmateAgent.addFavoriteMissingTitle') }
  }
  const content = asString(args.content) || title
  const type = parseFavoriteType(asString(args.type) || 'note')

  await applyAgentNav('favorites')
  const ok = await useFavoritesStore().add({
    title,
    content,
    preview: content,
    type
  })
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.addFavoriteFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneAddFavorite', { title }) }
}

export async function executeUpdateFavorite(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const item = resolveFavoriteItem(args)
  if (!item) {
    return { ok: false, message: t('linkmateAgent.favoriteNotFound') }
  }
  const title = asString(args.title)
  const content = asString(args.content)
  if (!title && !content) {
    return { ok: false, message: t('linkmateAgent.updateFavoriteMissingFields') }
  }
  await applyAgentNav('favorites')
  const ok = await useFavoritesStore().update(item.id, {
    title: title || undefined,
    content: content || undefined,
    preview: content || undefined
  })
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.updateFavoriteFailed') }
  }
  return {
    ok: true,
    message: t('linkmateAgent.doneUpdateFavorite', { title: title || item.title })
  }
}

export async function executeDeleteFavorite(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const item = resolveFavoriteItem(args)
  if (!item) {
    return { ok: false, message: t('linkmateAgent.favoriteNotFound') }
  }
  await applyAgentNav('favorites')
  const ok = await useFavoritesStore().remove(item.id)
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.deleteFavoriteFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneDeleteFavorite', { title: item.title }) }
}

export async function executeTagFavorite(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const item = resolveFavoriteItem(args)
  if (!item) {
    return { ok: false, message: t('linkmateAgent.favoriteNotFound') }
  }
  const tags = asStringArray(args.tags)
  if (!tags.length) {
    return { ok: false, message: t('linkmateAgent.tagFavoriteMissingTags') }
  }
  await applyAgentNav('favorites')
  const ok = await useFavoritesStore().update(item.id, { tags })
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.tagFavoriteFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneTagFavorite', { title: item.title }) }
}

export async function executeCreateFolder(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const name = asString(args.name)
  if (!name) {
    return { ok: false, message: t('linkmateAgent.createFolderMissingName') }
  }
  await applyAgentNav('files')
  const drive = useDriveStore()
  try {
    await drive.createFolder(name)
  } catch {
    return { ok: false, message: t('linkmateAgent.createFolderFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneCreateFolder', { name }) }
}

export async function executeUploadFile(): Promise<LinkMateActionResult> {
  await applyAgentNav('files')
  return { ok: false, message: t('linkmateAgent.uploadFileNeedsManual') }
}

export async function executePublishMoment(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const content = asString(args.content)
  if (!content) {
    return { ok: false, message: t('linkmateAgent.publishMomentMissingContent') }
  }
  await applyAgentNav('moments')
  const result = await useMomentsStore().addPost(content)
  if (!result.ok) {
    return { ok: false, message: result.message || t('linkmateAgent.publishMomentFailed') }
  }
  return { ok: true, message: t('linkmateAgent.donePublishMoment') }
}

export async function executePublishShortVideo(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const description = asString(args.description)
  await applyAgentNav('shortVideo')
  if (!description) {
    return { ok: true, message: t('linkmateAgent.doneOpenShortVideoPublish') }
  }
  return { ok: false, message: t('linkmateAgent.publishShortVideoNeedsFile') }
}

export async function executeSendRedPacket(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const session = resolveChatSession(args)
  if (!session?.id) {
    return { ok: false, message: t('linkmateAgent.sendMessageNoSession') }
  }
  const amount = Number(asString(args.amount))
  const totalCount = Number(asString(args.totalCount) || '1')
  if (!Number.isFinite(amount) || amount < 0.01) {
    return { ok: false, message: t('linkmateAgent.redPacketInvalidAmount') }
  }
  if (!Number.isInteger(totalCount) || totalCount < 1) {
    return { ok: false, message: t('linkmateAgent.redPacketInvalidCount') }
  }
  const type = parseActionEnum(args.type, ['normal', 'lucky'] as const) ?? 'normal'
  const greeting = asString(args.greeting) || t('extra.greetingFallback')
  openChatSession(session)
  try {
    const res = await redPacketApi.sendRedPacket({
      conversationId: session.id,
      type,
      totalAmount: amount,
      totalCount,
      greeting,
      clientMsgId: crypto.randomUUID()
    })
    if (res.code !== 200) {
      return { ok: false, message: res.message || t('linkmateAgent.sendRedPacketFailed') }
    }
    return { ok: true, message: t('linkmateAgent.doneSendRedPacket', { amount: amount.toFixed(2) }) }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('linkmateAgent.sendRedPacketFailed') }
  }
}

export async function executeStartCall(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const callType = parseActionEnum(args.callType, ['voice', 'video'] as const)
  if (!callType) {
    return { ok: false, message: t('linkmateAgent.invalidCallType') }
  }
  const session = resolveChatSession(args)
  if (!session) {
    return { ok: false, message: t('linkmateAgent.sendMessageNoSession') }
  }
  if (session.isGroup) {
    return { ok: false, message: t('linkmateAgent.callGroupUnsupported') }
  }
  openChatSession(session)
  const modals = useChatModalsStore()
  if (callType === 'voice') {
    modals.openVoiceCall()
  } else {
    modals.openVideoCall()
  }
  return {
    ok: true,
    message:
      callType === 'voice'
        ? t('linkmateAgent.doneStartVoiceCall', { name: session.name })
        : t('linkmateAgent.doneStartVideoCall', { name: session.name })
  }
}

export async function executeCreateGroup(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const memberNames = asStringArray(args.memberNames)
  const members = resolveContactMembers(memberNames)
  if (!members.length) {
    return { ok: false, message: t('linkmateAgent.createGroupMissingMembers') }
  }
  const groupName = asString(args.groupName) || undefined
  try {
    const session = await useAppStore().createGroup(members, groupName)
    if (!session) {
      return { ok: false, message: t('modals.createFail') }
    }
    openChatSession(session)
    return { ok: true, message: t('linkmateAgent.doneCreateGroup', { name: session.name }) }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('modals.createFail') }
  }
}

export async function executeAddGroupMembers(
  args: Record<string, unknown>
): Promise<LinkMateActionResult> {
  const session = resolveChatSession(args)
  if (!session?.isGroup) {
    return { ok: false, message: t('linkmateAgent.groupNotFound') }
  }
  const memberNames = asStringArray(args.memberNames)
  const members = resolveContactMembers(memberNames)
  if (!members.length) {
    return { ok: false, message: t('linkmateAgent.addGroupMembersMissing') }
  }
  openChatSession(session)
  try {
    const res = await groupApi.addGroupMembers(session.id, {
      memberIds: members.map(item => item.id)
    })
    if (res.code !== 200) {
      return { ok: false, message: res.message || t('extra.inviteFail') }
    }
    const added = res.data?.length ?? members.length
    return { ok: true, message: t('linkmateAgent.doneAddGroupMembers', { count: added }) }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('extra.inviteFail') }
  }
}

export async function executeUpdateSetting(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const key = asString(args.key) as SyncableSettingKey
  const valueRaw = asString(args.value)
  if (!key || !SYNCABLE_SETTING_KEYS.includes(key)) {
    return { ok: false, message: t('linkmateAgent.invalidSettingKey') }
  }
  if (!valueRaw) {
    return { ok: false, message: t('linkmateAgent.invalidSettingValue') }
  }
  await applyAgentNav('settings')
  const settings = useAppSettingsStore()
  const value = coerceSettingValue(key, valueRaw)
  ;(settings as unknown as Record<string, unknown>)[key] = value
  if (key === 'language' && typeof value === 'string') {
    setLocale(value)
  }
  if (key === 'notifyTone' && typeof value === 'string') {
    settings.setNotifyTone(value as NotifyToneId)
  }
  settings.scheduleSave(key)
  await settings.syncDesktopPrefs()
  return { ok: true, message: t('linkmateAgent.doneUpdateSetting', { key }) }
}

export async function executeRechargeBalance(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const amount = Number(asString(args.amount))
  if (!Number.isFinite(amount) || amount <= 0) {
    return { ok: false, message: t('linkmateAgent.rechargeInvalidAmount') }
  }
  await applyAgentNav('balance')
  try {
    const res = await balanceApi.rechargeBalance(amount)
    if (res.code !== 200) {
      return { ok: false, message: res.message || t('linkmateAgent.rechargeFailed') }
    }
    return { ok: true, message: t('linkmateAgent.doneRecharge', { amount: amount.toFixed(2) }) }
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : t('linkmateAgent.rechargeFailed') }
  }
}

export const TOOL_EXECUTORS: Record<
  LinkMateAgentToolName,
  (args: Record<string, unknown>) => Promise<LinkMateActionResult>
> = {
  navigate: executeNavigate,
  open_linkmate: () => executeOpenLinkmate(),
  open_chat: executeOpenChat,
  open_search: executeOpenSearch,
  open_calendar: () => executeOpenCalendar(),
  open_contacts: executeOpenContacts,
  send_message: executeSendMessage,
  add_friend: executeAddFriend,
  handle_friend_request: executeHandleFriendRequest,
  handle_group_invitation: executeHandleGroupInvitation,
  create_calendar_event: executeCreateCalendarEvent,
  update_calendar_event: executeUpdateCalendarEvent,
  delete_calendar_event: executeDeleteCalendarEvent,
  add_favorite: executeAddFavorite,
  update_favorite: executeUpdateFavorite,
  delete_favorite: executeDeleteFavorite,
  tag_favorite: executeTagFavorite,
  create_folder: executeCreateFolder,
  upload_file: () => executeUploadFile(),
  publish_moment: executePublishMoment,
  publish_short_video: executePublishShortVideo,
  send_red_packet: executeSendRedPacket,
  start_call: executeStartCall,
  create_group: executeCreateGroup,
  add_group_members: executeAddGroupMembers,
  update_setting: executeUpdateSetting,
  recharge_balance: executeRechargeBalance
}
