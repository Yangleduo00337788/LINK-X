/**
 * 作者：yangleduo
 */
import type { ApiEnvelope, MockContext } from '../types'
import { ok } from '../types'
import { avatarMediaPath, chatImageMediaPath } from '../shared/media'
import { DEMO_USER } from './fixtures'
import * as state from './demo-state'

const CAPTCHA =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

function bodyOf(data: unknown): Record<string, unknown> {
  if (data && typeof data === 'object' && !(data instanceof FormData)) {
    return data as Record<string, unknown>
  }
  return {}
}

function tokenPayload() {
  return {
    accessToken: 'demo-client-access',
    refreshToken: 'demo-client-refresh',
    expireTime: Date.now() + 86400_000,
    user: state.getUser(),
  }
}

function uploadResult(name = 'demo-upload.jpg') {
  const url = chatImageMediaPath('chat-photo-01.jpg')
  return { url, fileKey: url, fileName: name, fileSize: 245_760, contentType: 'image/jpeg' }
}

function match(path: string, re: RegExp): RegExpMatchArray | null {
  return path.match(re)
}

export async function resolveClientMock(ctx: MockContext): Promise<ApiEnvelope | undefined> {
  const { method, path, data, params } = ctx
  const body = bodyOf(data)

  if (method === 'get' && path === '/auth/config') {
    return ok({
      captchaEnabled: false,
      captchaType: 'image',
      registerEnabled: true,
      forgotPasswordEmailEnabled: true,
      passwordPolicy: { minLength: 6, maxLength: 64 },
    })
  }
  if (method === 'get' && path === '/auth/captcha') {
    return ok({ type: 'image', captchaId: 'demo-captcha', imageBase64: CAPTCHA, expireSeconds: 120 })
  }
  if (method === 'post' && path === '/auth/login') {
    const user = state.patchUser({ username: String(body.username || DEMO_USER.username) })
    return ok({ ...tokenPayload(), user })
  }
  if (method === 'post' && path === '/auth/register') return ok(null)
  if (method === 'post' && path === '/auth/send-register-code') return ok(null)
  if (method === 'post' && path === '/auth/refresh') return ok(tokenPayload())
  if (method === 'post' && path === '/auth/logout') return ok(null)
  if (method === 'post' && path === '/auth/reset-password-captcha') {
    return ok({ type: 'image', captchaId: 'demo-reset', imageBase64: CAPTCHA, expireSeconds: 120 })
  }
  if (
    method === 'post' &&
    (/^\/auth\/(reset-password|send-reset-code|verify-reset-code|reset-password-by-email)$/.test(path) ||
      path === '/user/change-password' ||
      path === '/user/delete-account' ||
      path === '/user/bind-email/send-code' ||
      path === '/compliance/purge')
  ) {
    return ok(null)
  }
  if (method === 'post' && (path === '/user/bind-email' || path === '/user/bind-phone' || path === '/user/change-username')) {
    return ok(state.patchUser(body))
  }

  if (method === 'get' && path === '/user/me') return ok(state.getUser())
  if (method === 'put' && path === '/user/profile') return ok(state.patchUser(body))
  if (method === 'post' && path === '/user/avatar') {
    return ok(state.patchUser({ avatar: avatarMediaPath(state.getUser().id) }).avatar)
  }
  if (method === 'get' && path === '/user/preference') return ok(state.getPreference())
  if (method === 'put' && path === '/user/preference') return ok(state.patchPreference(body))
  if (method === 'post' && path === '/user/moments-background') {
    return ok(state.patchPreference({ momentsBackground: '/media/moments-background/10001' }))
  }
  if (method === 'get' && path === '/user/devices') return ok(state.listDevices())
  if (method === 'delete' && match(path, /^\/user\/devices\/([^/]+)$/)) return ok(null)
  const profileMatch = match(path, /^\/user\/([^/]+)\/profile$/)
  if (method === 'get' && profileMatch) {
    const id = profileMatch[1]
    const friend = state.listFriends().find(f => f.userId === id)
    if (friend) {
      return ok({
        id: friend.userId,
        username: friend.username,
        nickname: friend.nickname,
        avatar: friend.avatar,
        signature: 'LinkX 演示用户',
      })
    }
    return ok(state.getUser())
  }

  if (method === 'get' && path === '/compliance/export') {
    return ok({
      exportedAt: new Date().toISOString(),
      profile: state.getUser(),
      sessions: state.listSessions().length,
    })
  }

  if (method === 'get' && path === '/chat/sessions') return ok(state.listSessions())
  const privateChat = match(path, /^\/chat\/private\/([^/]+)$/)
  if (method === 'post' && privateChat) return ok(state.openPrivateChat(privateChat[1]))

  const msgMatch = match(path, /^\/chat\/sessions\/([^/]+)\/messages$/)
  if (method === 'get' && msgMatch) return ok(state.getSessionMessages(msgMatch[1]))

  const uploadMatch = match(path, /^\/chat\/sessions\/([^/]+)\/upload$/)
  if (method === 'post' && uploadMatch) return ok(uploadResult())
  if (method === 'post' && path === '/chat/upload/check-hash') {
    return ok({ exists: false, fileKey: null })
  }
  if (method === 'post' && /\/upload\/init$/.test(path)) {
    return ok({ uploadId: 'demo-upload', key: chatImageMediaPath('chat-photo-01.jpg') })
  }
  if (method === 'post' && /\/upload\/part$/.test(path)) {
    return ok({ etag: 'demo-etag', partNumber: '1' })
  }
  if (method === 'get' && /\/upload\/[^/]+\/parts$/.test(path)) return ok([])
  if (method === 'post' && /\/upload\/complete$/.test(path)) return ok(uploadResult())
  if (method === 'post' && /\/upload\/abort$/.test(path)) return ok(null)

  if (method === 'get' && path === '/chat/search') {
    const q = String(params?.keyword || params?.q || '')
    const hits = state.listSessions().flatMap(s => {
      const msgs = state.getSessionMessages(String(s.id))
      return msgs
        .filter(m => !q || String(m.content).includes(q))
        .map(m => ({
          conversationId: s.id,
          messageId: m.id,
          content: m.content,
          senderNickname: m.senderNickname,
          createTime: m.createTime,
        }))
    })
    return ok(hits.slice(0, 40))
  }

  const chatOp = match(path, /^\/chat\/sessions\/([^/]+)\/(pin|important|mute)$/)
  if (method === 'post' && chatOp) return ok(null)
  const draftMatch = match(path, /^\/chat\/sessions\/([^/]+)\/draft$/)
  if (draftMatch && method === 'post') {
    state.setDraft(draftMatch[1], String(body.content || body.draft || ''))
    return ok(null)
  }
  if (draftMatch && method === 'get') return ok(state.getDraft(draftMatch[1]))
  const readMatch = match(path, /^\/chat\/sessions\/([^/]+)\/read$/)
  if (method === 'post' && readMatch) return ok(0)
  const mediaUrl = match(path, /^\/chat\/messages\/([^/]+)\/media-url$/)
  if (method === 'get' && mediaUrl) return ok({ url: chatImageMediaPath('chat-photo-01.jpg') })

  const msgAction = match(
    path,
    /^\/chat\/sessions\/([^/]+)\/messages\/([^/]+)\/(recall|edit|forward|quote|read-count)$/
  )
  if (msgAction) {
    const [, cid, mid, action] = msgAction
    if (action === 'read-count') return ok({ readCount: 2, totalMembers: 6 })
    const item = state.buildAckFromSend({
      clientMsgId: String(body.clientMsgId || mid),
      conversationId: cid,
      msgType: String(body.msgType || 'text'),
      content: String(body.content || (action === 'recall' ? '' : '演示消息')),
    })
    if (action === 'recall') item.type = 'recall'
    state.appendSessionMessage(cid, item)
    return ok(item)
  }

  if (method === 'get' && path === '/friend/list') return ok(state.listFriends())
  if (method === 'get' && path === '/friend/requests/incoming') {
    return ok(state.listFriendRequests().filter(r => r.direction === 'incoming'))
  }
  if (method === 'get' && path === '/friend/requests/outgoing') {
    return ok(state.listFriendRequests().filter(r => r.direction === 'outgoing'))
  }
  if (method === 'get' && path === '/friend/search') {
    const kw = String(params?.keyword || '').toLowerCase()
    return ok(
      state.listFriends()
        .filter(f => !kw || f.nickname.includes(kw) || f.username.includes(kw))
        .map(f => ({ id: f.userId, userId: f.userId, username: f.username, nickname: f.nickname, avatar: f.avatar }))
    )
  }
  if (method === 'post' && path === '/friend/request') return ok(null)
  const frAct = match(path, /^\/friend\/requests\/([^/]+)\/(accept|reject)$/)
  if (method === 'post' && frAct) return ok(null)
  if (method === 'delete' && path === '/friend/requests/clear') return ok(2)
  const friendId = match(path, /^\/friend\/([^/]+)$/)
  if (method === 'delete' && friendId) return ok(null)
  const friendField = match(path, /^\/friend\/([^/]+)\/(remark|group|block|unblock)$/)
  if (friendField) return ok(body.remark || body.groupName || null)

  if (method === 'get' && (path === '/notifications' || path === '/notifications/unread' || path === '/notifications/mine')) {
    return ok(state.listNotifications(path.includes('unread') ? 'unread' : undefined))
  }
  if (method === 'get' && path === '/notifications/unread-count') {
    return ok({ count: state.listNotifications('unread').length })
  }
  if (method === 'post' && (path === '/notifications/read-all' || /\/notifications\/[^/]+\/read$/.test(path))) {
    return ok(null)
  }
  if (method === 'delete' && path === '/notifications/clear') return ok(state.listNotifications().length)
  if (method === 'delete' && path === '/notifications/group-join-requests/clear') return ok(0)
  if (method === 'delete' && match(path, /^\/notifications\/[^/]+$/)) return ok(null)

  if (method === 'get' && path === '/group/list') {
    return ok(state.listSessions().filter(s => s.type === 2))
  }
  if (method === 'post' && path === '/group') {
    return ok(state.createGroup(String(body.name || '新群聊'), (body.memberIds as string[]) || []))
  }
  if (method === 'get' && path === '/group/invitations') return ok(state.listGroupInvites())
  if (method === 'delete' && path === '/group/invitations/clear') return ok(0)
  const giAct = match(path, /^\/group\/invitations\/([^/]+)\/(accept|reject)$/)
  if (method === 'post' && giAct) {
    if (giAct[2] === 'accept') return ok(state.getSession('90021') || state.listSessions()[0])
    return ok(null)
  }
  const giCreate = match(path, /^\/group\/invitations\/([^/]+)$/)
  if (method === 'post' && giCreate) {
    return ok({
      id: state.nextId('inv-'),
      conversationId: giCreate[1],
      inviterUserId: state.getUser().id,
      status: 0,
      createTime: Date.now(),
    })
  }

  const gInfo = match(path, /^\/group\/([^/]+)\/info$/)
  if (method === 'get' && gInfo) return ok(state.groupInfo(gInfo[1]))
  const gMembers = match(path, /^\/group\/([^/]+)\/members$/)
  if (gMembers && method === 'get') return ok(state.groupMembers(gMembers[1]))
  if (gMembers && method === 'post') return ok(state.groupMembers(gMembers[1]))
  const gMember = match(path, /^\/group\/([^/]+)\/members\/([^/]+)$/)
  if (method === 'delete' && gMember) return ok(null)
  const gMemberRole = match(path, /^\/group\/([^/]+)\/members\/([^/]+)\/(role|mute)$/)
  if (gMemberRole && (method === 'put' || method === 'post')) return ok(null)
  const gAnn = match(path, /^\/group\/([^/]+)\/announcements(?:\/display)?$/)
  if (gAnn && method === 'get') {
    const item = {
      id: 'ann-1',
      conversationId: gAnn[1],
      content: '本周发版 checklist 已更新到云盘。',
      publisherNickname: '赵思琪',
      publisherRole: 'owner',
      pinned: true,
      createTime: new Date().toISOString(),
    }
    return ok(path.endsWith('/display') ? item : [item])
  }
  if (gAnn && method === 'post') {
    return ok({
      id: state.nextId('ann-'),
      conversationId: gAnn[1],
      content: body.content,
      pinned: !!body.pinned,
      createTime: new Date().toISOString(),
    })
  }
  const gAnnOne = match(path, /^\/group\/([^/]+)\/announcements\/([^/]+)$/)
  if (gAnnOne && (method === 'put' || method === 'delete')) return ok(null)
  const gAssets = match(path, /^\/group\/([^/]+)\/assets(?:\/upload)?$/)
  if (gAssets && method === 'get') {
    return ok([
      {
        id: 'ga-1',
        name: '发版 checklist.xlsx',
        fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
        fileSize: 48200,
        uploaderName: '李泽远',
        createTime: Date.now(),
      },
    ])
  }
  if (gAssets && method === 'post') {
    return ok({
      id: state.nextId('ga-'),
      name: '演示群文件.jpg',
      fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
      fileSize: 128000,
      createTime: Date.now(),
    })
  }
  const gJoin = match(path, /^\/group\/([^/]+)\/join-requests$/)
  if (method === 'get' && gJoin) return ok([])
  const gRest = match(
    path,
    /^\/group\/([^/]+)(?:\/(quit|transfer|mute-all|linkmate-enabled|group-ai-features|group-ai-summary|remark|members\/batch-remove|members\/batch-mute|join-approval|announcement\/read|announcement\/read-count|invite-policy|join-request|join-request\/[^/]+|assets\/[^/]+))?$/
  )
  if (gRest && method !== 'get') {
    if (path.endsWith('/remark')) return ok(String(body.remark || ''))
    if (path.endsWith('/read-count')) return ok(4)
    if (path.endsWith('/group-ai-summary')) {
      return ok({ summary: '本周群聊重点：发版 checklist、灰度环境、客户端打包。' })
    }
    if (method === 'put' && match(path, /^\/group\/[^/]+$/)) return ok(state.groupInfo(gRest[1]))
    return ok(null)
  }
  if (method === 'get' && path.endsWith('/announcement/read-count')) return ok(4)
  if (method === 'delete' && match(path, /^\/group\/[^/]+$/)) return ok(null)

  if (method === 'get' && path === '/moments') return ok(state.listMoments())
  if (method === 'post' && path === '/moments') return ok(state.addMoment(body))
  if (method === 'post' && path === '/moments/upload') return ok(chatImageMediaPath('moments-01.jpg'))
  const mu = match(path, /^\/moments\/user\/([^/]+)$/)
  if (method === 'get' && mu) return ok(state.listMoments().filter(p => String(p.userId) === mu[1]))
  const ml = match(path, /^\/moments\/([^/]+)\/like$/)
  if (ml && (method === 'post' || method === 'delete')) return ok(null)
  const mc = match(path, /^\/moments\/([^/]+)\/comment$/)
  if (method === 'post' && mc) {
    return ok({
      id: state.nextId('mc-'),
      userId: state.getUser().id,
      nickname: state.getUser().nickname,
      avatar: state.getUser().avatar,
      content: body.content,
      time: new Date().toISOString(),
    })
  }
  if (method === 'delete' && match(path, /^\/moments\/comment\/[^/]+$/)) return ok(null)
  const mo = match(path, /^\/moments\/([^/]+)$/)
  if (mo) {
    const post = state.listMoments().find(p => String(p.id) === mo[1]) || state.listMoments()[0]
    if (method === 'get') return ok(post)
    if (method === 'put') return ok({ ...post, ...body })
    if (method === 'delete') return ok(null)
  }

  if (method === 'get' && path === '/short-video/topics/hot') {
    return ok([
      { name: '企业协作', displayName: '企业协作', postCount: 28, pinned: true },
      { name: '产品演示', displayName: '产品演示', postCount: 16 },
    ])
  }
  if (method === 'get' && path === '/short-video/topics') {
    const items = [
      { name: '企业协作', displayName: '企业协作', postCount: 28, pinned: true },
      { name: '产品演示', displayName: '产品演示', postCount: 16 },
      { name: 'LinkX', displayName: 'LinkX', postCount: 42 },
    ]
    return ok({ items, page: 1, size: 20, total: items.length })
  }
  if (method === 'get' && match(path, /^\/short-video\/topics\/[^/]+$/)) {
    return ok({ name: '企业协作', displayName: '企业协作', postCount: 28 })
  }
  if (method === 'get' && (path === '/short-video/following/users' || /\/following\/users$/.test(path) || /\/followers\/users$/.test(path))) {
    return ok(
      state.listFriends().slice(0, 8).map(f => ({
        userId: f.userId,
        nickname: f.nickname,
        avatar: f.avatar,
        followingAuthor: true,
      }))
    )
  }
  if (method === 'get' && path === '/short-video/following/count') return ok(8)
  if (method === 'get' && path === '/short-video/block/users') return ok([])
  if (method === 'get' && path === '/short-video/block/count') return ok(0)
  if (method === 'post' && path === '/short-video') {
    return ok({
      ...state.listShortVideos()[0],
      id: state.nextId('sv-'),
      userId: state.getUser().id,
      nickname: state.getUser().nickname,
      description: body.description || '新作品',
    })
  }
  if (method === 'post' && path === '/short-video/upload') return ok(chatImageMediaPath('short-video-cover.jpg'))
  const svProfile = match(path, /^\/short-video\/user\/([^/]+)\/profile$/)
  if (method === 'get' && svProfile) {
    const f = state.listFriends().find(x => x.userId === svProfile[1])
    return ok({
      userId: svProfile[1],
      nickname: f?.nickname || state.getUser().nickname,
      avatar: avatarMediaPath(svProfile[1]),
      postCount: 12,
      followingCount: 128,
      followerCount: 2048,
      followingAuthor: true,
    })
  }
  const svUser = match(path, /^\/short-video\/user\/([^/]+)$/)
  if (method === 'get' && svUser) return ok(state.listShortVideos())
  if (
    method === 'get' &&
    (path === '/short-video' ||
      path === '/short-video/hot' ||
      path === '/short-video/following' ||
      path === '/short-video/friends' ||
      path === '/short-video/favorites' ||
      path === '/short-video/likes')
  ) {
    return ok(state.listShortVideos())
  }
  const svCmt = match(path, /^\/short-video\/([^/]+)\/comments$/)
  if (method === 'get' && svCmt) {
    const v = state.listShortVideos().find(x => String(x.id) === svCmt[1])
    return ok(v?.comments || [])
  }
  const svAct = match(
    path,
    /^\/short-video\/(?:comment\/)?([^/]+)\/(like|favorite|comment|not-interested|report|play|share|share-chat)$/
  )
  if (svAct && method !== 'get') {
    if (svAct[2] === 'comment') {
      return ok({
        id: state.nextId('svc-'),
        userId: state.getUser().id,
        nickname: state.getUser().nickname,
        content: body.content || '',
        time: new Date().toISOString(),
        likes: 0,
      })
    }
    return ok(null)
  }
  if (method === 'post' && match(path, /^\/short-video\/follow\/[^/]+$/)) return ok(null)
  if (method === 'delete' && match(path, /^\/short-video\/follow\/[^/]+$/)) return ok(null)
  if (method === 'post' && match(path, /^\/short-video\/block\/[^/]+$/)) return ok(null)
  if (method === 'delete' && match(path, /^\/short-video\/block\/[^/]+$/)) return ok(null)
  if (method === 'delete' && match(path, /^\/short-video\/comment\/[^/]+$/)) return ok(null)
  const svOne = match(path, /^\/short-video\/([^/]+)$/)
  if (svOne) {
    const v = state.listShortVideos().find(x => String(x.id) === svOne[1]) || state.listShortVideos()[0]
    if (method === 'get') return ok(v)
    if (method === 'put') return ok({ ...v, ...body })
    if (method === 'delete') return ok(null)
  }

  if (method === 'get' && path === '/notes') return ok(state.listNotes())
  if (method === 'post' && path === '/notes') return ok(state.addNote(body))
  if (method === 'post' && path === '/notes/upload') return ok(uploadResult())
  if (method === 'get' && path === '/notes/media-url') return ok(chatImageMediaPath('chat-photo-01.jpg'))
  const noteOne = match(path, /^\/notes\/([^/]+)$/)
  if (noteOne) {
    const n = state.listNotes().find(x => String(x.id) === noteOne[1]) || state.listNotes()[0]
    if (method === 'get') return ok(n)
    if (method === 'put') return ok({ ...n, ...body, updateTime: new Date().toISOString() })
    if (method === 'delete') return ok(null)
  }

  if (method === 'get' && path === '/favorites') return ok(state.listFavorites())
  if (method === 'post' && path === '/favorites') return ok(state.addFavorite(body))
  if (method === 'get' && path === '/favorites/storage') {
    return ok({
      usedBytes: 2_400_000,
      quotaBytes: 100_000_000,
      itemCount: state.listFavorites().length,
      usedPercent: 2.4,
      typeCounts: { note: 1, image: 1 },
    })
  }
  if (method === 'get' && path === '/favorites/tags') return ok(state.listFavoriteTags())
  if (method === 'post' && path === '/favorites/tags') {
    return ok({ id: state.nextId('tag-'), name: body.name, color: body.color, count: 0 })
  }
  if (method === 'delete' && match(path, /^\/favorites\/tags\/[^/]+$/)) return ok(null)
  const favOne = match(path, /^\/favorites\/([^/]+)$/)
  if (favOne && method === 'put') {
    const f = state.listFavorites().find(x => String(x.id) === favOne[1])
    return ok({ ...f, ...body })
  }
  if (favOne && method === 'delete') return ok(null)

  if (method === 'get' && path === '/calendar') return ok(state.listCalendar())
  if (method === 'post' && path === '/calendar') return ok(state.addCalendar(body))
  const calDate = match(path, /^\/calendar\/date\/([^/]+)$/)
  if (method === 'get' && calDate) {
    return ok(state.listCalendar().filter(e => e.date === calDate[1]))
  }
  const calOne = match(path, /^\/calendar\/([^/]+)(?:\/remind)?$/)
  if (calOne) {
    const ev = state.listCalendar().find(e => String(e.id) === calOne[1]) || state.listCalendar()[0]
    if (method === 'get') return ok(ev)
    if (method === 'put') return ok({ ...ev, ...body })
    if (method === 'post' || method === 'delete') return ok(null)
  }

  if (method === 'get' && path === '/cloud/storage') {
    return ok({ usedBytes: 12_800_000, quotaBytes: 5_000_000_000, fileCount: state.listDrive(null).length + 2, usedPercent: 0.26 })
  }
  if (method === 'post' && path === '/cloud/storage/expand') {
    return ok({ usedBytes: 12_800_000, quotaBytes: 10_000_000_000, fileCount: 4, usedPercent: 0.13 })
  }
  if (method === 'get' && path === '/cloud/items') {
    return ok(state.listDrive(params?.folderId ? String(params.folderId) : null))
  }
  if (method === 'get' && path === '/cloud/breadcrumb') {
    const id = params?.folderId ? String(params.folderId) : ''
    const item = id ? state.getDriveItem(id) : null
    return ok(item ? [item] : [])
  }
  if (method === 'post' && path === '/cloud/folders') {
    return ok(state.addDriveFolder(String(body.name || '新建文件夹'), body.parentId ? String(body.parentId) : null))
  }
  if (method === 'post' && path === '/cloud/files/upload') {
    return ok(state.addDriveFile('演示上传.jpg', params?.folderId ? String(params.folderId) : null))
  }
  if (method === 'get' && path === '/cloud/activities') return ok(state.listDriveActivities())
  if (method === 'post' && path === '/cloud/shares') return ok(state.addDriveShare(body))
  if (method === 'post' && (path === '/cloud/items/batch-delete' || path === '/cloud/items/batch-move')) return ok(null)
  const sharePub = match(path, /^\/cloud\/share\/([^/]+)$/)
  if (method === 'get' && sharePub) return ok(state.getShareByToken(sharePub[1]) || state.addDriveShare({ targetId: 'file-1', shareType: 'file' }))
  const cloudFile = match(path, /^\/cloud\/files\/([^/]+)(?:\/tags(?:\/.*)?)?$/)
  if (cloudFile) {
    const item = state.getDriveItem(cloudFile[1]) || state.addDriveFile('文件')
    if (method === 'get') return ok(item)
    if (method === 'patch') return ok({ ...item, ...body })
    if (method === 'post') return ok((item.tags as string[]) || [])
    return ok(null)
  }
  const cloudFolder = match(path, /^\/cloud\/folders\/([^/]+)$/)
  if (cloudFolder) return ok(method === 'patch' ? { ...state.getDriveItem(cloudFolder[1]), ...body } : null)
  if (method === 'delete' && match(path, /^\/cloud\/shares\/[^/]+$/)) return ok(null)

  if (method === 'get' && path === '/files') {
    return ok([
      {
        id: 'cf-1',
        source: 'chat_message',
        title: '交互稿.png',
        fileName: '交互稿.png',
        fileSize: 245760,
        fileUrl: chatImageMediaPath('chat-photo-02.jpg'),
        category: 'image',
        conversationName: '林雨桐',
        senderName: '林雨桐',
        createTime: Date.now() - 3600_000,
      },
    ])
  }

  if (method === 'get' && path === '/app/recommends') {
    return ok(state.listRecommends(params?.slotCode ? String(params.slotCode) : undefined))
  }
  if (method === 'get' && path === '/app/activities') return ok(state.listActivities())
  if (method === 'get' && path === '/app/version') {
    const current = String(params?.current || '1.0.1')
    return ok({
      version: '1.0.1',
      currentVersion: current,
      hasUpdate: false,
      forceUpdate: false,
      channel: String(params?.channel || 'demo'),
      platform: params?.platform,
      releaseNotes: '演示站静态版本，无需升级。',
      downloadUrl: '',
      supportEmail: 'support@linkx.io',
      supportPhone: '400-000-0000',
    })
  }

  if (method === 'get' && path === '/location/search') {
    const q = String(params?.q || '上海')
    return ok([
      { name: `${q} · 陆家嘴`, address: '上海市浦东新区世纪大道', lat: 31.239, lon: 121.499 },
      { name: `${q} · 徐家汇`, address: '上海市徐汇区漕溪北路', lat: 31.194, lon: 121.436 },
    ])
  }

  if (method === 'get' && path === '/balance') {
    return ok({ balance: 12800, currency: 'CNY' })
  }
  if (method === 'get' && path === '/balance/logs') {
    return ok([
      { id: 'bl-1', type: 'recharge', amount: 10000, createTime: new Date().toISOString(), remark: '演示充值' },
    ])
  }
  if (method === 'post' && path === '/balance/recharge') {
    return ok({ balance: 12800 + Number(body.amount || 0), currency: 'CNY' })
  }

  if (method === 'get' && path === '/feedback') return ok(state.listFeedback())
  if (method === 'post' && path === '/feedback') return ok(state.addFeedback(body))
  const fbOne = match(path, /^\/feedback\/([^/]+)(?:\/replies)?$/)
  if (fbOne) {
    const item = state.listFeedback().find(x => String(x.id) === fbOne[1]) || state.listFeedback()[0]
    if (method === 'get') return ok(path.endsWith('/replies') ? item.replies || [] : item)
    if (method === 'post') {
      return ok({
        id: state.nextId('fbr-'),
        senderType: 'user',
        senderName: state.getUser().nickname,
        content: body.content,
        createTime: new Date().toISOString(),
      })
    }
  }

  if (method === 'post' && path === '/red-packet') {
    const item = {
      id: state.nextId('rp-'),
      senderId: state.getUser().id,
      senderNickname: state.getUser().nickname,
      senderAvatar: state.getUser().avatar,
      conversationId: body.conversationId,
      type: body.type || 'normal',
      totalAmount: Number(body.totalAmount || 100),
      totalCount: Number(body.totalCount || 1),
      remainingAmount: Number(body.totalAmount || 100),
      remainingCount: Number(body.totalCount || 1),
      greeting: body.greeting || '恭喜发财',
      status: 'active',
      time: new Date().toISOString(),
      received: false,
      isSelf: true,
      records: [],
    }
    return ok(state.addRedPacket(item))
  }
  const rpRecv = match(path, /^\/red-packet\/([^/]+)\/receive$/)
  if (method === 'post' && rpRecv) {
    const p = state.getRedPacket(rpRecv[1])
    if (p) {
      p.received = true
      p.receivedAmount = 88
    }
    return ok(p)
  }
  const rpOne = match(path, /^\/red-packet\/([^/]+)$/)
  if (method === 'get' && rpOne) return ok(state.getRedPacket(rpOne[1]) || null)
  const rpConv = match(path, /^\/red-packet\/conversation\/([^/]+)$/)
  if (method === 'get' && rpConv) return ok(state.listRedPackets(rpConv[1]))

  if (method === 'post' && path === '/call/invite') {
    const convId = String(body.conversationId || '')
    const session = state.getSession(convId)
    const peerUserId = String(session?.peerUserId || '10002')
    return ok({
      callId: `demo-call-${Date.now()}`,
      conversationId: convId,
      callType: body.callType === 'video' ? 'video' : 'voice',
      status: 'ringing',
      peerUserId,
      peerNickname: String(session?.peerNickname || session?.name || '好友'),
      peerAvatar: String(session?.peerAvatar || avatarMediaPath(peerUserId)),
    })
  }
  if (method === 'post' && /^\/call\//.test(path)) return ok(null)

  if (method === 'post' && path === '/conference/create') {
    const item = {
      id: state.nextId('conf-'),
      title: body.title || '演示会议',
      type: body.type || 'video',
      scene: body.scene || 'meeting',
      creatorId: state.getUser().id,
      conversationId: body.conversationId,
      status: 1,
      callId: `demo-call-${Date.now()}`,
      participants: [{ userId: state.getUser().id, nickname: state.getUser().nickname, role: 'host' }],
    }
    return ok(state.addConference(item))
  }
  if (method === 'get' && path === '/conference/active') return ok(state.listConferences())
  if (method === 'get' && path === '/conference/active-in-conversation') return ok(null)
  if (method === 'post' && /^\/conference\//.test(path)) {
    const id = String(body.conferenceId || '')
    const found = state.listConferences().find(c => String(c.id) === id)
    return ok(found || state.listConferences()[0] || null)
  }
  const confInfo = match(path, /^\/conference\/info\/([^/]+)$/)
  if (method === 'get' && confInfo) {
    return ok(state.listConferences().find(c => String(c.id) === confInfo[1]) || null)
  }

  if (method === 'get' && path === '/linkmate/status') {
    return ok({
      enabled: true,
      model: 'demo-linkmate',
      dailyTokenLimit: 200000,
      dailyTokenUsed: 1280,
      deepThinkingSupported: true,
      voiceCallSupported: true,
      agentEnabled: true,
    })
  }
  if (method === 'post' && path === '/linkmate/translate') {
    return ok({ text: String(body.text || body.content || ''), translated: `【演示翻译】${body.text || body.content || ''}` })
  }
  if (method === 'post' && path === '/linkmate/transcribe') {
    return ok({ text: '这是演示语音转写结果。' })
  }
  if (method === 'post' && path === '/linkmate/voice-call/start') {
    return ok({
      callId: `lm-call-${Date.now()}`,
      peerNickname: '灵伴',
      realtimeCallsUrl: 'https://demo.local/realtime',
      provider: 'dashscope',
      ephemeralKey: 'demo-ephemeral',
    })
  }
  if (method === 'post' && path === '/linkmate/voice-call/hangup') return ok(null)
  if (method === 'get' && path === '/linkmate/sessions') return ok(state.listLinkMateSessions())
  if (method === 'post' && path === '/linkmate/sessions') {
    return ok(state.addLinkMateSession(String(body.title || '新对话')))
  }
  const lmMsg = match(path, /^\/linkmate\/sessions\/([^/]+)\/messages$/)
  if (method === 'get' && lmMsg) return ok(state.listLinkMateMessages(lmMsg[1]))
  const lmSess = match(path, /^\/linkmate\/sessions\/([^/]+)$/)
  if (lmSess && method === 'delete') return ok(null)
  if (lmSess && method === 'patch') {
    return ok({ id: lmSess[1], title: body.title || '对话', updateTime: new Date().toISOString() })
  }
  if (method === 'post' && path === '/linkmate/chat/stream') {
    return ok({ messageId: state.nextId('lmm-'), sessionId: body.sessionId, content: '演示站灵伴回复。' })
  }

  if (method === 'post' || method === 'put' || method === 'patch' || method === 'delete') {
    return ok(null)
  }
  if (method === 'get') return ok([])
  return undefined
}
