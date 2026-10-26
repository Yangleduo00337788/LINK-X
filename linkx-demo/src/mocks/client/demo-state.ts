/**
 * 作者：yangleduo
 */
import { chatImageMediaPath, avatarMediaPath } from '../shared/media'
import {
  DEMO_PREFERENCE,
  DEMO_USER,
  PEERS,
  demoActivities,
  demoCalendarEvents,
  demoFriendRequests,
  demoFriends,
  demoMessages,
  demoMomentsPosts,
  demoNotifications,
  demoRecommends,
  demoSessions,
  demoShortVideos,
} from './fixtures'

let seq = 1_000_000
export function nextId(prefix = ''): string {
  seq += 1
  return prefix ? `${prefix}${seq}` : String(seq)
}

const user = { ...DEMO_USER }
let preference = { ...DEMO_PREFERENCE }
const friends = demoFriends()
const friendRequests = demoFriendRequests()
const sessions = demoSessions()
const extraBySession: Record<string, Array<Record<string, unknown>>> = {}
const drafts: Record<string, string> = {}
const notifications = demoNotifications()
const moments = demoMomentsPosts()
const shortVideos = demoShortVideos()
const calendar = demoCalendarEvents()
const recommends = demoRecommends()
const activities = demoActivities()
const notes: Array<Record<string, unknown>> = [
  { id: 'note-1', title: '华东交付周报', content: '本周完成 3 家客户验收，待办：更新部署手册、整理问题清单。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-2', title: '评审会纪要', content: '交互稿确认；3F 梧桐厅；发版 checklist 已同步云盘。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-3', title: '灰度值班备忘', content: '回滚脚本路径、值班电话、告警群 @规则。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-4', title: '华东制造 FAQ', content: '审计日志、数据不出境、并发容量、培训账号。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-5', title: '走查问题清单', content: '登录字标、预览关闭回会话、空状态文案。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-6', title: '3.0 发版沟通要点', content: '能力清单、帮助中心链接、运营开关、回滚窗口。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-7', title: '售前 FAQ 摘录', content: '并发、审计、培训账号、报价有效期 30 天。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'note-8', title: '差旅备忘', content: '周四华东制造，带备用投影线和演示账号。', type: 'note', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
]
const favorites: Array<Record<string, unknown>> = [
  { id: 'fav-1', title: '合同条款摘录', content: '私有化部署 SLA 99.9%，数据不出境。', type: 'note', tags: '工作', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'fav-2', title: '评审会截图', content: chatImageMediaPath('chat-photo-01.jpg'), type: 'image', fileSize: 245760, createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'fav-3', title: '发版海报主视觉', content: chatImageMediaPath('chat-photo-02.jpg'), type: 'image', fileSize: 268800, tags: '灵感', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'fav-4', title: '空状态文案', content: '暂无会话，去发起聊天。', type: 'note', tags: '灵感', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'fav-5', title: '部署拓扑示意', content: chatImageMediaPath('moments-01.jpg'), type: 'image', fileSize: 312000, tags: '工作', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
  { id: 'fav-6', title: '值班电话', content: '机房值班 400-800-9000 转 3。', type: 'note', tags: '工作', createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
]
const favoriteTags = [
  { id: 'tag-1', name: '工作', color: '#3b82f6', sortOrder: 1, preset: true, count: 4 },
  { id: 'tag-2', name: '灵感', color: '#10b981', sortOrder: 2, preset: false, count: 2 },
  { id: 'tag-3', name: '客户', color: '#f59e0b', sortOrder: 3, preset: false, count: 1 },
]
const driveItems: Array<Record<string, unknown>> = [
  {
    kind: 'folder',
    id: 'folder-1',
    name: '客户资料',
    parentId: null,
    childCount: 2,
    createTime: Date.now() - 86400_000,
    updateTime: Date.now(),
  },
  {
    kind: 'file',
    id: 'file-1',
    name: '发版 checklist.xlsx',
    parentId: 'folder-1',
    folderId: 'folder-1',
    fileSize: 48200,
    fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
    contentType: 'application/vnd.ms-excel',
    ext: 'xlsx',
    category: 'document',
    uploaderName: DEMO_USER.nickname,
    uploaderAvatar: DEMO_USER.avatar,
    createTime: Date.now() - 3600_000,
    updateTime: Date.now() - 3600_000,
    tags: ['发版'],
  },
  {
    kind: 'file',
    id: 'file-2',
    name: '交互稿.png',
    parentId: null,
    folderId: null,
    fileSize: 245760,
    fileUrl: chatImageMediaPath('chat-photo-02.jpg'),
    contentType: 'image/png',
    ext: 'png',
    category: 'image',
    uploaderName: '林雨桐',
    uploaderAvatar: avatarMediaPath('10002'),
    createTime: Date.now() - 7200_000,
    updateTime: Date.now() - 7200_000,
    tags: [],
  },
  {
    kind: 'folder',
    id: 'folder-2',
    name: '发版材料',
    parentId: null,
    childCount: 4,
    createTime: Date.now() - 172800_000,
    updateTime: Date.now() - 3600_000,
  },
  {
    kind: 'file',
    id: 'file-3',
    name: 'LinkX-3.0-发版说明.pdf',
    parentId: 'folder-2',
    folderId: 'folder-2',
    fileSize: 860_000,
    fileUrl: chatImageMediaPath('banner-ops.jpg'),
    contentType: 'application/pdf',
    ext: 'pdf',
    category: 'document',
    uploaderName: '钱晓彤',
    uploaderAvatar: avatarMediaPath('10010'),
    createTime: Date.now() - 5400_000,
    updateTime: Date.now() - 5400_000,
    tags: ['发版'],
  },
  {
    kind: 'file',
    id: 'file-4',
    name: '华东制造-预演议程.pptx',
    parentId: 'folder-1',
    folderId: 'folder-1',
    fileSize: 2_140_000,
    fileUrl: chatImageMediaPath('moments-02.jpg'),
    contentType: 'application/vnd.ms-powerpoint',
    ext: 'pptx',
    category: 'document',
    uploaderName: DEMO_USER.nickname,
    uploaderAvatar: DEMO_USER.avatar,
    createTime: Date.now() - 9600_000,
    updateTime: Date.now() - 9600_000,
    tags: ['客户'],
  },
  {
    kind: 'file',
    id: 'file-5',
    name: '现场验收.jpg',
    parentId: null,
    folderId: null,
    fileSize: 412_000,
    fileUrl: chatImageMediaPath('moments-01.jpg'),
    contentType: 'image/jpeg',
    ext: 'jpg',
    category: 'image',
    uploaderName: '孙博文',
    uploaderAvatar: avatarMediaPath('10009'),
    createTime: Date.now() - 12_000_000,
    updateTime: Date.now() - 12_000_000,
    tags: [],
  },
  {
    kind: 'file',
    id: 'file-6',
    name: '安全扫描报告.pdf',
    parentId: 'folder-2',
    folderId: 'folder-2',
    fileSize: 640_000,
    fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
    contentType: 'application/pdf',
    ext: 'pdf',
    category: 'document',
    uploaderName: '冯子轩',
    uploaderAvatar: avatarMediaPath('10011'),
    createTime: Date.now() - 15_000_000,
    updateTime: Date.now() - 15_000_000,
    tags: ['发版'],
  },
  {
    kind: 'file',
    id: 'file-7',
    name: '走查标注.png',
    parentId: 'folder-2',
    folderId: 'folder-2',
    fileSize: 198_000,
    fileUrl: chatImageMediaPath('banner-ops.jpg'),
    contentType: 'image/png',
    ext: 'png',
    category: 'image',
    uploaderName: '褚梦瑶',
    uploaderAvatar: avatarMediaPath('10012'),
    createTime: Date.now() - 18_000_000,
    updateTime: Date.now() - 18_000_000,
    tags: [],
  },
]
const driveShares: Array<Record<string, unknown>> = []
const groupInvites = [
  {
    id: '8001',
    conversationId: '90021',
    groupName: '华东交付支持群',
    inviterUserId: '10004',
    inviterNickname: '赵思琪',
    inviterAvatar: avatarMediaPath('10004'),
    message: '拉你进交付群，方便同步值班。',
    status: 0,
    createTime: Date.now() - 3600_000,
  },
  {
    id: '8002',
    conversationId: '90023',
    groupName: '设计走查 · UI',
    inviterUserId: '10003',
    inviterNickname: '王浩然',
    inviterAvatar: avatarMediaPath('10003'),
    message: '走查群，方便对齐空状态和预览窗。',
    status: 0,
    createTime: Date.now() - 7200_000,
  },
  {
    id: '8003',
    conversationId: '90024',
    groupName: '客户-华东制造',
    inviterUserId: '10008',
    inviterNickname: '郑雅雯',
    inviterAvatar: avatarMediaPath('10008'),
    message: '预演群，方便同步议程和 FAQ。',
    status: 0,
    createTime: Date.now() - 10_800_000,
  },
]
const feedbacks: Array<Record<string, unknown>> = [
  {
    id: 'fb-1',
    type: 'bug',
    content: '移动端登录偶发闪退，已附日志。',
    status: 'replied',
    reply: '已在 3.0.1 修复，感谢反馈。',
    replyTime: new Date().toISOString(),
    createTime: new Date(Date.now() - 86400_000).toISOString(),
    replies: [
      {
        id: 'fbr-1',
        senderType: 'admin',
        senderName: '客服小沈',
        content: '已在 3.0.1 修复，感谢反馈。',
        createTime: new Date().toISOString(),
      },
    ],
  },
  {
    id: 'fb-2',
    type: 'suggestion',
    content: '希望会话列表支持按未读筛选。',
    status: 'pending',
    createTime: new Date(Date.now() - 12_000_000).toISOString(),
    replies: [],
  },
  {
    id: 'fb-3',
    type: 'bug',
    content: '图片预览关闭后偶发停留在空白页。',
    status: 'replied',
    reply: '演示环境已改为关闭后回到会话。',
    replyTime: new Date().toISOString(),
    createTime: new Date(Date.now() - 48_000_000).toISOString(),
    replies: [
      {
        id: 'fbr-2',
        senderType: 'admin',
        senderName: '客服小沈',
        content: '演示环境已改为关闭后回到会话。',
        createTime: new Date().toISOString(),
      },
    ],
  },
]
const devices = [
  {
    id: 'dev-current',
    deviceName: 'Windows PC · 演示浏览器',
    deviceType: 'desktop',
    lastActive: new Date().toISOString(),
    current: true,
  },
  {
    id: 'dev-phone',
    deviceName: 'iPhone 15',
    deviceType: 'mobile',
    lastActive: new Date(Date.now() - 3600_000).toISOString(),
    current: false,
  },
  {
    id: 'dev-mac',
    deviceName: 'MacBook Pro',
    deviceType: 'desktop',
    lastActive: new Date(Date.now() - 86_400_000).toISOString(),
    current: false,
  },
]
const linkmateSessions = [
  { id: 'lm-1', title: '本周会议纪要', updateTime: new Date().toISOString() },
  { id: 'lm-2', title: '华东制造预演提纲', updateTime: new Date(Date.now() - 3600_000).toISOString() },
  { id: 'lm-3', title: '发版风险清单', updateTime: new Date(Date.now() - 7200_000).toISOString() },
]
const linkmateMessages: Record<string, Array<Record<string, unknown>>> = {
  'lm-1': [
    { id: 'lmm-1', sessionId: 'lm-1', role: 'user', content: '帮我汇总本周评审会要点', createTime: new Date(Date.now() - 600_000).toISOString() },
    { id: 'lmm-2', sessionId: 'lm-1', role: 'assistant', content: '本周要点：交互稿确认、3F 梧桐厅评审、发版 checklist 已同步云盘、纪要由你整理后发产品冲刺群。', createTime: new Date(Date.now() - 580_000).toISOString(), tokenCount: 86 },
  ],
  'lm-2': [
    { id: 'lmm-3', sessionId: 'lm-2', role: 'user', content: '给华东制造预演列一个 40 分钟议程', createTime: new Date(Date.now() - 3800_000).toISOString() },
    { id: 'lmm-4', sessionId: 'lm-2', role: 'assistant', content: '建议：5 分钟价值说明、15 分钟 IM/已读/文件、10 分钟云盘权限、10 分钟问答。老板在场时把「数据不出境」放在第一页。', createTime: new Date(Date.now() - 3700_000).toISOString(), tokenCount: 112 },
  ],
  'lm-3': [
    { id: 'lmm-5', sessionId: 'lm-3', role: 'user', content: 'D-2 还有哪些发版风险？', createTime: new Date(Date.now() - 7400_000).toISOString() },
    { id: 'lmm-6', sessionId: 'lm-3', role: 'assistant', content: 'P2 回归 2 条、运营开关未核对、帮助中心链接、投影线备用、回滚脚本值班人口径。', createTime: new Date(Date.now() - 7300_000).toISOString(), tokenCount: 74 },
  ],
}
const redPackets: Array<Record<string, unknown>> = []
const conferences: Array<Record<string, unknown>> = []

export function getUser() {
  return user
}
export function patchUser(patch: Record<string, unknown>) {
  Object.assign(user, patch)
  return user
}
export function getPreference() {
  return preference
}
export function patchPreference(patch: Record<string, unknown>) {
  preference = { ...preference, ...patch }
  return preference
}

export function listFriends() {
  return friends
}
export function listFriendRequests() {
  return friendRequests
}
export function listSessions() {
  return sessions
}
export function getSession(id: string) {
  return sessions.find(s => String(s.id) === String(id))
}

export function getSessionMessages(conversationId: string) {
  const base = demoMessages(conversationId)
  const extra = extraBySession[conversationId] || []
  return [...base, ...extra]
}

export function appendSessionMessage(conversationId: string, item: Record<string, unknown>) {
  if (!extraBySession[conversationId]) extraBySession[conversationId] = []
  extraBySession[conversationId].push(item)
  const session = getSession(conversationId)
  if (session) {
    session.lastMessage = item.content || session.lastMessage
    session.lastMessageTime = item.createTime || Date.now()
  }
}

export function buildAckFromSend(payload: {
  clientMsgId: string
  conversationId: string
  msgType: string
  content?: string
  fileName?: string
  fileUrl?: string
  voiceDuration?: number
}) {
  const type = payload.msgType === 'location' ? 'text' : payload.msgType
  return {
    id: nextId(),
    conversationId: payload.conversationId,
    senderId: user.id,
    senderNickname: user.nickname,
    senderAvatar: user.avatar,
    type,
    content: payload.content || '',
    fileName: payload.fileName,
    fileUrl: payload.fileUrl || payload.content,
    voiceDuration: payload.voiceDuration,
    createTime: Date.now(),
    isSelf: true,
    clientMsgId: payload.clientMsgId,
    deliveryStatus: 'sent',
  }
}

export function openPrivateChat(friendId: string) {
  const existing = sessions.find(s => String(s.peerUserId) === String(friendId))
  if (existing) return existing
  const friend = friends.find(f => String(f.userId) === String(friendId)) || PEERS.find(p => p.id === friendId)
  const item = {
    id: nextId('9'),
    type: 1,
    peerUserId: friendId,
    peerUsername: (friend as { username?: string })?.username,
    peerNickname: (friend as { nickname?: string })?.nickname || '好友',
    peerAvatar: avatarMediaPath(friendId),
    peerOnline: true,
    lastMessage: '',
    lastMessageTime: Date.now(),
    unreadCount: 0,
  }
  sessions.unshift(item)
  return item
}

export function groupMembers(conversationId: string) {
  const session = getSession(conversationId)
  const avatars = (session?.memberAvatars as Array<{ userId?: string; nickname?: string; avatar?: string }>) || []
  const members = [
    {
      userId: user.id,
      nickname: user.nickname,
      avatar: user.avatar,
      role: String(session?.ownerId) === user.id ? 'owner' : 'member',
      joinTime: Date.now() - 86400_000 * 30,
    },
    ...avatars.map((m, i) => ({
      userId: String(m.userId || PEERS[i].id),
      nickname: m.nickname || PEERS[i].nickname,
      avatar: m.avatar || avatarMediaPath(PEERS[i].id),
      role: i === 0 ? 'owner' : i === 1 ? 'admin' : 'member',
      joinTime: Date.now() - 86400_000 * (10 - i),
    })),
  ]
  if (!members.some(m => m.userId === user.id)) {
    members.unshift({
      userId: user.id,
      nickname: user.nickname,
      avatar: user.avatar,
      role: 'member',
      joinTime: Date.now(),
    })
  }
  return members
}

export function groupInfo(conversationId: string) {
  const session = getSession(conversationId) || {
    id: conversationId,
    name: '演示群聊',
    type: 2,
    ownerId: user.id,
  }
  const members = groupMembers(conversationId)
  return {
    id: session.id,
    type: 2,
    name: session.name || '演示群聊',
    avatar: session.avatar,
    memberAvatars: session.memberAvatars,
    announcement: '本周发版 checklist 已更新到云盘，请各位同步进度。',
    ownerId: session.ownerId || members[0]?.userId,
    ownerNickname: '赵思琪',
    memberCount: members.length,
    lastMessage: session.lastMessage,
    lastMessageTime: session.lastMessageTime,
    muteAll: false,
    joinApproval: false,
    invitePolicy: 'anyMember',
    linkmateEnabled: true,
    groupAiProactiveEnabled: false,
    groupAiSmartSummaryEnabled: true,
  }
}

export function createGroup(name: string, memberIds: string[]) {
  const item = {
    id: nextId('9'),
    type: 2,
    name: name || '新群聊',
    avatar: avatarMediaPath('90020'),
    memberAvatars: memberIds.slice(0, 4).map(id => {
      const f = friends.find(x => x.userId === id)
      return { userId: id, nickname: f?.nickname, avatar: avatarMediaPath(id) }
    }),
    ownerId: user.id,
    lastMessage: '群聊已创建',
    lastMessageTime: Date.now(),
    unreadCount: 0,
  }
  sessions.unshift(item)
  return groupInfo(String(item.id))
}

export function listNotifications(filter?: string) {
  if (filter === 'unread') return notifications.filter(n => n.readStatus === 0)
  return notifications
}

export function listMoments() {
  return moments
}
export function listShortVideos() {
  return shortVideos
}
export function listCalendar() {
  return calendar
}
export function listNotes() {
  return notes
}
export function listFavorites() {
  return favorites
}
export function listFavoriteTags() {
  return favoriteTags
}
export function listDrive(folderId?: string | null) {
  const fid = folderId || null
  return driveItems.filter(i => (i.parentId || null) === fid)
}
export function getDriveItem(id: string) {
  return driveItems.find(i => String(i.id) === String(id))
}
export function listDriveActivities() {
  return [
    { id: 'act-d1', targetType: 'file', targetId: 'file-1', targetName: '发版 checklist.xlsx', action: 'upload', detail: `${user.nickname} 上传了文件`, createTime: Date.now() - 3600_000 },
    { id: 'act-d2', targetType: 'file', targetId: 'file-3', targetName: 'LinkX-3.0-发版说明.pdf', action: 'upload', detail: '钱晓彤 上传了文件', createTime: Date.now() - 5400_000 },
    { id: 'act-d3', targetType: 'file', targetId: 'file-4', targetName: '华东制造-预演议程.pptx', action: 'update', detail: `${user.nickname} 更新了文件`, createTime: Date.now() - 9600_000 },
    { id: 'act-d4', targetType: 'file', targetId: 'file-5', targetName: '现场验收.jpg', action: 'upload', detail: '孙博文 上传了文件', createTime: Date.now() - 12_000_000 },
    { id: 'act-d5', targetType: 'file', targetId: 'file-6', targetName: '安全扫描报告.pdf', action: 'upload', detail: '冯子轩 上传了文件', createTime: Date.now() - 15_000_000 },
  ]
}
export function listGroupInvites() {
  return groupInvites
}
export function listFeedback() {
  return feedbacks
}
export function listDevices() {
  return devices
}
export function listRecommends(slot?: string) {
  if (!slot) return recommends
  return recommends.filter(r => r.slotCode === slot)
}
export function listActivities() {
  return activities
}
export function getDraft(cid: string) {
  return drafts[cid] || ''
}
export function setDraft(cid: string, text: string) {
  drafts[cid] = text
}

export function listLinkMateSessions() {
  return linkmateSessions
}

export function addLinkMateSession(title = '新对话') {
  const item = { id: nextId('lm-'), title, updateTime: new Date().toISOString() }
  linkmateSessions.unshift(item)
  return item
}
export function listLinkMateMessages(sessionId: string) {
  return linkmateMessages[sessionId] || []
}
export function appendLinkMate(sessionId: string, role: string, content: string) {
  const item = {
    id: nextId('lmm-'),
    sessionId,
    role,
    content,
    createTime: new Date().toISOString(),
  }
  if (!linkmateMessages[sessionId]) linkmateMessages[sessionId] = []
  linkmateMessages[sessionId].push(item)
  return item
}

export function listRedPackets(conversationId?: string) {
  if (!conversationId) return redPackets
  return redPackets.filter(p => String(p.conversationId) === String(conversationId))
}
export function addRedPacket(item: Record<string, unknown>) {
  redPackets.unshift(item)
  return item
}
export function getRedPacket(id: string) {
  return redPackets.find(p => String(p.id) === String(id))
}

export function addConference(item: Record<string, unknown>) {
  conferences.unshift(item)
  return item
}
export function listConferences() {
  return conferences
}

export function addNote(payload: Record<string, unknown>) {
  const item = {
    id: nextId('note-'),
    title: payload.title || '未命名笔记',
    content: payload.content || '',
    type: payload.type || 'note',
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
  }
  notes.unshift(item)
  return item
}

export function addFavorite(payload: Record<string, unknown>) {
  const item = {
    id: nextId('fav-'),
    title: payload.title || '收藏',
    content: payload.content || '',
    type: payload.type || 'note',
    tags: payload.tags,
    fileSize: payload.fileSize,
    sourceType: payload.sourceType,
    sourceId: payload.sourceId,
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
  }
  favorites.unshift(item)
  return item
}

export function addCalendar(payload: Record<string, unknown>) {
  const item = {
    id: nextId('cal-'),
    title: payload.title,
    date: payload.date,
    time: payload.time,
    endTime: payload.endTime,
    color: payload.color || '#3b82f6',
  }
  calendar.push(item)
  return item
}

export function addDriveFile(name: string, folderId?: string | null) {
  const item = {
    kind: 'file',
    id: nextId('file-'),
    name,
    parentId: folderId || null,
    folderId: folderId || null,
    fileSize: 128000,
    fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
    contentType: 'image/jpeg',
    category: 'image',
    uploaderName: user.nickname,
    uploaderAvatar: user.avatar,
    createTime: Date.now(),
    updateTime: Date.now(),
    tags: [],
  }
  driveItems.push(item)
  return item
}

export function addDriveFolder(name: string, parentId?: string | null) {
  const item = {
    kind: 'folder',
    id: nextId('folder-'),
    name,
    parentId: parentId || null,
    childCount: 0,
    createTime: Date.now(),
    updateTime: Date.now(),
  }
  driveItems.push(item)
  return item
}

export function addDriveShare(body: Record<string, unknown>) {
  const token = nextId('share-')
  const item = {
    id: nextId('ds-'),
    shareType: body.shareType || 'file',
    targetId: body.targetId,
    token,
    shareUrl: `/client/#/share/${token}`,
    hasPassword: !!body.password,
    expireAt: body.expireHours ? Date.now() + Number(body.expireHours) * 3600_000 : undefined,
    maxDownloads: body.maxDownloads,
    downloadCount: 0,
    targetName: getDriveItem(String(body.targetId))?.name,
  }
  driveShares.push(item)
  return item
}

export function getShareByToken(token: string) {
  return driveShares.find(s => String(s.token) === token)
}

export function addFeedback(payload: Record<string, unknown>) {
  const item = {
    id: nextId('fb-'),
    type: payload.type || 'other',
    content: payload.content || '',
    contact: payload.contact,
    status: 'pending',
    createTime: new Date().toISOString(),
    replies: [],
  }
  feedbacks.unshift(item)
  return item
}

export function addMoment(payload: Record<string, unknown>) {
  const item = {
    id: nextId('6'),
    userId: user.id,
    nickname: user.nickname,
    avatar: user.avatar,
    content: payload.content || '',
    images: payload.images || [],
    location: payload.location,
    visibility: payload.visibility ?? 0,
    time: new Date().toISOString(),
    likes: 0,
    liked: false,
    likedBy: [],
    comments: [],
  }
  moments.unshift(item)
  return item
}

export function nextMessageId(): string {
  return nextId()
}
