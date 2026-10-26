/**
 * 作者：yangleduo
 */
import { avatarMediaPath, chatImageMediaPath, momentsBgMediaPath } from '../shared/media'

const now = Date.now()

export const DEMO_USER = {
  id: '10001',
  username: 'demo',
  nickname: '陈晓明',
  avatar: avatarMediaPath('10001'),
  signature: 'LinkX 企业协作 · 演示账号',
  email: 'demo@linkx.io',
  emailBound: true,
  phone: '13800138000',
  phoneBound: true,
  gender: 'male',
  country: '中国',
  province: '上海',
  region: '浦东新区',
}

export const DEMO_PREFERENCE = {
  autoStart: false,
  soundNotify: true,
  messageDetail: true,
  notifyAtMe: true,
  notifySound: true,
  privacyVerifyFriend: true,
  privacyAllowStranger: false,
  privacyShowOnline: true,
  privacySendReadReceipt: true,
  language: 'zh-CN',
  chatBackground: '',
  notifyTone: 'default',
  momentsBackground: momentsBgMediaPath('10001'),
  favoritesViewMode: 'grid',
  favoritesSort: 'time_desc',
  quietHoursEnabled: false,
  notifyChat: true,
  notifySocial: true,
  notifyMoments: true,
  notifySystem: true,
  notifyFriendOnline: true,
}

export const PEERS = [
  { id: '10002', username: 'alice', nickname: '林雨桐', title: '产品经理' },
  { id: '10003', username: 'bob', nickname: '王浩然', title: '设计负责人' },
  { id: '10004', username: 'carol', nickname: '赵思琪', title: '交付经理' },
  { id: '10005', username: 'david', nickname: '李泽远', title: '后端负责人' },
  { id: '10006', username: 'emma', nickname: '周婉清', title: '前端开发' },
  { id: '10007', username: 'frank', nickname: '吴俊杰', title: '测试负责人' },
  { id: '10008', username: 'grace', nickname: '郑雅雯', title: '客户成功' },
  { id: '10009', username: 'henry', nickname: '孙博文', title: '实施顾问' },
  { id: '10010', username: 'iris', nickname: '钱晓彤', title: '运营经理' },
  { id: '10011', username: 'jack', nickname: '冯子轩', title: '安全工程师' },
  { id: '10012', username: 'kate', nickname: '褚梦瑶', title: 'UI 设计师' },
  { id: '10013', username: 'leo', nickname: '卫晨阳', title: '架构师' },
  { id: '10014', username: 'ryan', nickname: '韩若溪', title: '华北实施' },
  { id: '10015', username: 'sara', nickname: '沈嘉怡', title: '解决方案' },
  { id: '10016', username: 'nina', nickname: '唐可欣', title: '商务经理' },
  { id: '10017', username: 'owen', nickname: '潘浩宇', title: '运维工程师' },
  { id: '10018', username: 'paul', nickname: '崔一诺', title: '数据分析' },
  { id: '10019', username: 'quinn', nickname: '姜雨桐', title: '行政助理' },
  { id: '10021', username: 'tina', nickname: '贺思远', title: 'Android 开发' },
  { id: '10022', username: 'uma', nickname: '陆芷若', title: 'iOS 开发' },
  { id: '10023', username: 'victor', nickname: '邓启明', title: '法务' },
  { id: '10024', username: 'willa', nickname: '曹婉宁', title: '财务' },
  { id: '10025', username: 'xavier', nickname: '萧景行', title: '售前顾问' },
  { id: '10026', username: 'yuki', nickname: '叶清禾', title: '内容运营' },
]

const GROUPS = [
  '产品组',
  '研发组',
  '交付实施',
  '客户成功',
  '默认分组',
] as const

const LAST_SNIPPETS = [
  '下午三点评审会，记得带上交互稿。',
  '[图片]',
  '合同条款我已标注，请过目。',
  '灰度环境已经切到 3.0.1 了。',
  '客户端打包完成，安装包放云盘了。',
  '回归用例跑完了，还剩 2 条 P2。',
  '客户那边想下周做私有化预演。',
  '值班表我更新到表格里了。',
  '本周运营活动物料已定稿。',
  '安全扫描报告附件已发。',
  '走查意见我标在 Figma 评论里。',
  '容量评估文档你看下第二节。',
  '华北两家客户想加急验收。',
  '方案 PPT 我改了封面和报价页。',
  '合同流程法务说还差一页盖章。',
  '服务器巡检没有异常。',
  '看板数据我导出了一份。',
  '会议室改到 2F 海棠厅。',
  'Android 包已经提测。',
  'TestFlight 链接我发群里了。',
  '保密协议模板用最新版。',
  '报销单我已经提交。',
  '售前材料同步到共享盘了。',
  '周报我写好了，帮看一眼语气。',
]

export function demoFriends() {
  return PEERS.map((p, i) => ({
    userId: p.id,
    username: p.username,
    nickname: p.nickname,
    avatar: avatarMediaPath(p.id),
    signature: `${p.title} · LinkX`,
    remark: i % 4 === 0 ? `${p.nickname}（${p.title}）` : undefined,
    groupName: GROUPS[i % GROUPS.length],
    online: i % 3 !== 0,
  }))
}

function groupSession(
  id: string,
  name: string,
  members: ReturnType<typeof demoFriends>,
  lastMessage: string,
  ago: number,
  unread = 0,
  pinned = false
) {
  return {
    id,
    type: 2,
    name,
    avatar: avatarMediaPath(id),
    memberAvatars: members.map(f => ({
      nickname: f.nickname,
      avatar: f.avatar,
      userId: f.userId,
    })),
    ownerId: members[0]?.userId || '10002',
    lastMessage,
    lastMessageTime: now - ago,
    unreadCount: unread,
    pinned,
  }
}

export function demoSessions() {
  const friends = demoFriends()
  const sessions: Array<Record<string, unknown>> = friends.map((f, idx) => ({
    id: String(91000 + idx + 1),
    type: 1,
    peerUserId: f.userId,
    peerUsername: f.username,
    peerNickname: f.nickname,
    peerAvatar: f.avatar,
    peerOnline: f.online,
    lastMessage: LAST_SNIPPETS[idx % LAST_SNIPPETS.length],
    lastMessageTime: now - (idx + 1) * 210_000 - (idx % 5) * 60_000,
    unreadCount: idx < 6 ? (idx % 4) + 1 : idx === 8 ? 2 : 0,
    pinned: idx === 0,
    important: idx === 0 || idx === 2,
    muted: idx === 11,
  }))

  sessions.unshift(
    groupSession('90020', 'LinkX 产品冲刺群', friends.slice(0, 12), '本周发版 checklist 已更新到云盘。', 420_000, 5, true),
    groupSession('90021', '华东交付支持群', friends.slice(3, 15), '@所有人 今晚 20:00 值班同学确认一下。', 1_200_000, 2),
    groupSession('90023', '设计走查 · UI', friends.filter((_, i) => i % 3 !== 2).slice(0, 10), '走查意见我标在 Figma 评论里。', 2_800_000, 1),
    groupSession('90024', '客户-华东制造', friends.slice(6, 16), '客户希望周四下午做一次预演。', 4_200_000, 4),
    groupSession('90025', 'LinkX 全员公告', friends, '3.0 发版说明已同步，请各部门查阅。', 8_600_000, 0)
  )

  sessions.push({
    id: '90022',
    type: 1,
    peerUserId: '10020',
    peerUsername: 'linkmate',
    peerNickname: '灵伴',
    peerAvatar: avatarMediaPath('10020'),
    lastMessage: '需要我帮你汇总本周会议纪要吗？',
    lastMessageTime: now - 5_400_000,
    unreadCount: 0,
  })

  return sessions
}

function textMsg(
  id: string,
  cid: string,
  senderId: string,
  nick: string,
  content: string,
  ago: number,
  isSelf = false
) {
  return {
    id,
    conversationId: cid,
    senderId,
    senderNickname: nick,
    senderAvatar: avatarMediaPath(senderId),
    type: 'text',
    content,
    createTime: now - ago,
    isSelf,
    deliveryStatus: 'read',
  }
}

function imageMsg(id: string, cid: string, senderId: string, nick: string, ago: number, file: string, isSelf = false) {
  const url = chatImageMediaPath(file)
  return {
    id,
    conversationId: cid,
    senderId,
    senderNickname: nick,
    senderAvatar: avatarMediaPath(senderId),
    type: 'image',
    content: url,
    fileUrl: url,
    fileName: file,
    createTime: now - ago,
    isSelf,
    deliveryStatus: 'read',
  }
}

function fileMsg(
  id: string,
  cid: string,
  senderId: string,
  nick: string,
  ago: number,
  fileName: string,
  isSelf = false
) {
  return {
    id,
    conversationId: cid,
    senderId,
    senderNickname: nick,
    senderAvatar: avatarMediaPath(senderId),
    type: 'file',
    content: fileName,
    fileName,
    fileSize: '48 KB',
    fileUrl: chatImageMediaPath('chat-photo-01.jpg'),
    createTime: now - ago,
    isSelf,
    deliveryStatus: 'read',
  }
}

function threadForPeer(conversationId: string, peer: (typeof PEERS)[number], salt: number) {
  const me = DEMO_USER
  const topics = [
    [`${peer.nickname}，这个需求我们按 3.0 的交互走可以吗？`, '可以，我补充一版验收标准。', '那我下午把清单贴群里。'],
    ['巡检报告你看了吗？磁盘水位有点高。', '看到了，今晚扩 200G。', '好，扩完跟我说一声。'],
    ['客户想看私有化部署的网络拓扑。', '我出一页示意图，你补安全组说明。', '行，今晚给你。'],
    ['走查里那条空状态文案你定了吗？', '定了：暂无会话，去发起聊天。', '我改到稿上。'],
    ['报销单差一张发票，你方便补传吗？', '我现在传云盘。', '收到，我去提交。'],
  ]
  const lines = topics[salt % topics.length]
  const photo = salt % 2 === 0 ? 'chat-photo-01.jpg' : 'chat-photo-02.jpg'
  return [
    textMsg(`8${conversationId}01`, conversationId, peer.id, peer.nickname, lines[0], 1_260_000),
    textMsg(`8${conversationId}02`, conversationId, me.id, me.nickname, lines[1], 1_080_000, true),
    textMsg(`8${conversationId}03`, conversationId, peer.id, peer.nickname, lines[2], 900_000),
    imageMsg(`8${conversationId}04`, conversationId, salt % 3 === 0 ? me.id : peer.id, salt % 3 === 0 ? me.nickname : peer.nickname, 720_000, photo, salt % 3 === 0),
    textMsg(`8${conversationId}05`, conversationId, me.id, me.nickname, '这张图我收藏了，评审时直接投屏。', 540_000, true),
    textMsg(`8${conversationId}06`, conversationId, peer.id, peer.nickname, LAST_SNIPPETS[salt % LAST_SNIPPETS.length], 240_000),
    textMsg(`8${conversationId}07`, conversationId, me.id, me.nickname, `好的，${peer.nickname}，我这边记下了。`, 120_000, true),
  ]
}

export function demoMessages(conversationId: string): Array<Record<string, unknown>> {
  const me = DEMO_USER
  const pool: Record<string, Array<Record<string, unknown>>> = {
    '91001': [
      textMsg('800001', '91001', '10002', '林雨桐', '下午三点评审会，记得带上交互稿。', 2_400_000),
      textMsg('800002', '91001', me.id, me.nickname, '收到，我会准时参加。稿我刚同步到云盘。', 2_220_000, true),
      textMsg('800002a', '91001', '10002', '林雨桐', '封面用 3.0 的品牌色，别再用旧蓝了。', 2_100_000),
      imageMsg('800003', '91001', '10002', '林雨桐', 1_920_000, 'chat-photo-01.jpg'),
      textMsg('800004', '91001', me.id, me.nickname, 'UI 细节我和设计对齐过了，空状态也改了。', 1_740_000, true),
      fileMsg('800004a', '91001', me.id, me.nickname, 1_560_000, '评审材料-交互稿.pdf', true),
      textMsg('800005', '91001', '10002', '林雨桐', '太好了，会议室改到 3F 梧桐厅。', 1_200_000),
      textMsg('800005a', '91001', me.id, me.nickname, '我提前 10 分钟去投屏。', 960_000, true),
      textMsg('800005b', '91001', '10002', '林雨桐', '法务也要旁听，你留两个位置。', 720_000),
      textMsg('800005c', '91001', me.id, me.nickname, '记下了。评审纪要还是我来写？', 480_000, true),
      textMsg('800005d', '91001', '10002', '林雨桐', '对，写完丢到产品冲刺群。', 210_000),
    ],
    '91002': [
      textMsg('800201', '91002', '10003', '王浩然', '合同条款我已标注，请过目。', 3_200_000),
      fileMsg('800202', '91002', '10003', '王浩然', 3_000_000, '华东制造-合同批注.docx'),
      textMsg('800203', '91002', me.id, me.nickname, '第 4.2 条数据出境我改成「不出境」。', 2_700_000, true),
      textMsg('800204', '91002', '10003', '王浩然', '好，我让法务再扫一遍。', 2_400_000),
      imageMsg('800205', '91002', me.id, me.nickname, 1_800_000, 'moments-01.jpg', true),
      textMsg('800206', '91002', '10003', '王浩然', '这页示意图可以放进方案附录。', 1_200_000),
      textMsg('800207', '91002', me.id, me.nickname, '那我今晚出一版完整 PDF。', 600_000, true),
    ],
    '90020': [
      textMsg('800101', '90020', '10004', '赵思琪', '各位，冲刺进入 D-2，今天把风险项过一遍。', 3_600_000),
      textMsg('800101a', '90020', '10005', '李泽远', '后端灰度环境已就绪，回滚脚本也验证过了。', 3_300_000),
      textMsg('800101b', '90020', '10007', '吴俊杰', '回归还剩 2 条 P2，明天上午清完。', 3_000_000),
      textMsg('800103', '90020', me.id, me.nickname, '我这边客户端打包也完成了，安装包在云盘「发版」目录。', 2_700_000, true),
      fileMsg('800103a', '90020', me.id, me.nickname, 2_500_000, '发版 checklist.xlsx', true),
      textMsg('800103b', '90020', '10002', '林雨桐', 'checklist 我补了运营开关和帮助中心链接。', 2_200_000),
      imageMsg('800104', '90020', '10006', '周婉清', 1_800_000, 'chat-photo-02.jpg'),
      textMsg('800104a', '90020', '10012', '褚梦瑶', '这张图可以当发版海报主视觉。', 1_500_000),
      textMsg('800104b', '90020', '10010', '钱晓彤', '物料今晚出，群公告我来写。', 1_200_000),
      textMsg('800104c', '90020', '10004', '赵思琪', '@所有人 明天下午 3 点梧桐厅终审，请准时。', 900_000),
      textMsg('800104d', '90020', me.id, me.nickname, '收到，我带演示账号和投影线。', 480_000, true),
      textMsg('800104e', '90020', '10004', '赵思琪', '本周发版 checklist 已更新到云盘。', 180_000),
    ],
    '90021': [
      textMsg('800301', '90021', '10004', '赵思琪', '华东两家客户本周要预演，资源排一下。', 5_400_000),
      textMsg('800302', '90021', '10009', '孙博文', '制造客户周四下午，我去现场。', 5_100_000),
      textMsg('800303', '90021', '10008', '郑雅雯', '金融客户想看审计日志，我准备截图。', 4_800_000),
      textMsg('800304', '90021', me.id, me.nickname, '演示环境我今晚刷到 3.0.1。', 4_200_000, true),
      imageMsg('800305', '90021', '10009', '孙博文', 3_600_000, 'moments-02.jpg'),
      textMsg('800306', '90021', '10017', '潘浩宇', '机房网络窗口申请已经批了。', 2_400_000),
      textMsg('800307', '90021', '10004', '赵思琪', '@所有人 今晚 20:00 值班同学确认一下。', 900_000),
      textMsg('800308', '90021', me.id, me.nickname, '我值班，有问题直接电话。', 480_000, true),
    ],
    '90023': [
      textMsg('800401', '90023', '10003', '王浩然', '今天走查登录、主会话、图片预览三条主路径。', 6_000_000),
      textMsg('800402', '90023', '10012', '褚梦瑶', '登录页字标和状态栏对齐了。', 5_400_000),
      imageMsg('800403', '90023', '10012', '褚梦瑶', 4_800_000, 'banner-ops.jpg'),
      textMsg('800404', '90023', me.id, me.nickname, '预览窗关闭要回到会话，我记到缺陷里。', 3_600_000, true),
      textMsg('800405', '90023', '10003', '王浩然', '走查意见我标在 Figma 评论里。', 1_200_000),
    ],
    '90024': [
      textMsg('800501', '90024', '10008', '郑雅雯', '华东制造希望周四下午做一次预演。', 7_200_000),
      textMsg('800502', '90024', '10016', '唐可欣', '他们老板会参加，准备一页价值说明。', 6_600_000),
      fileMsg('800503', '90024', me.id, me.nickname, 5_400_000, '华东制造-预演议程.pptx', true),
      textMsg('800504', '90024', '10025', '萧景行', '售前问题我整理了 12 条 FAQ。', 3_600_000),
      textMsg('800505', '90024', '10008', '郑雅雯', '客户希望周四下午做一次预演。', 1_800_000),
    ],
    '90025': [
      textMsg('800601', '90025', '10010', '钱晓彤', '3.0 发版说明已同步，请各部门查阅。', 9_000_000),
      fileMsg('800602', '90025', '10010', '钱晓彤', 8_700_000, 'LinkX-3.0-发版说明.pdf'),
      textMsg('800603', '90025', '10019', '姜雨桐', '全员邮件也发了，会议室日历已占用。', 8_100_000),
      textMsg('800604', '90025', me.id, me.nickname, '产品侧已阅。', 7_200_000, true),
    ],
    '90022': [
      textMsg('800701', '90022', '10020', '灵伴', '你好，我是 LinkX 灵伴。需要我帮你汇总本周会议纪要吗？', 5_400_000),
      textMsg('800702', '90022', me.id, me.nickname, '先帮我列一下评审会待办。', 5_100_000, true),
      textMsg(
        '800703',
        '90022',
        '10020',
        '灵伴',
        '待办：1）交互稿终稿；2）3F 梧桐厅投屏；3）发版 checklist 同步云盘；4）写评审纪要发到产品冲刺群。',
        4_800_000
      ),
    ],
  }
  if (pool[conversationId]) return pool[conversationId]

  const idx = Math.max(0, (Number(conversationId) % 1000) - 1)
  const peer = PEERS[idx] || PEERS[0]
  return threadForPeer(conversationId, peer, idx)
}

export function demoFriendRequests() {
  const incoming = [
    { id: '10014', username: 'ryan', nickname: '韩若溪', msg: '我是华北区实施顾问韩若溪，加个好友～', ago: 86_400_000 },
    { id: '10016', username: 'nina', nickname: '唐可欣', msg: '商务侧想同步一下华东制造的报价。', ago: 54_000_000 },
    { id: '10021', username: 'tina', nickname: '贺思远', msg: 'Android 包联调，方便加一下吗？', ago: 18_000_000 },
    { id: '10025', username: 'xavier', nickname: '萧景行', msg: '售前材料想请你帮忙过一眼。', ago: 9_000_000 },
  ]
  const outgoing = [
    { id: '10015', username: 'sara', nickname: '沈嘉怡', msg: '你好，想交流一下私有化部署方案', ago: 172_800_000 },
    { id: '10023', username: 'victor', nickname: '邓启明', msg: '合同条款想请法务帮忙看下。', ago: 36_000_000 },
  ]
  return [
    ...incoming.map((p, i) => ({
      id: String(70001 + i),
      fromUserId: p.id,
      toUserId: DEMO_USER.id,
      fromUsername: p.username,
      fromNickname: p.nickname,
      fromAvatar: avatarMediaPath(p.id),
      peerUserId: p.id,
      peerUsername: p.username,
      peerNickname: p.nickname,
      peerAvatar: avatarMediaPath(p.id),
      message: p.msg,
      status: 0,
      direction: 'incoming',
      createTime: new Date(now - p.ago).toISOString(),
    })),
    ...outgoing.map((p, i) => ({
      id: String(70021 + i),
      fromUserId: DEMO_USER.id,
      toUserId: p.id,
      fromUsername: DEMO_USER.username,
      fromNickname: DEMO_USER.nickname,
      fromAvatar: DEMO_USER.avatar,
      peerUserId: p.id,
      peerUsername: p.username,
      peerNickname: p.nickname,
      peerAvatar: avatarMediaPath(p.id),
      message: p.msg,
      status: 0,
      direction: 'outgoing',
      createTime: new Date(now - p.ago).toISOString(),
    })),
  ]
}

export function demoNotifications() {
  const iso = (ms: number) => new Date(now - ms).toISOString()
  const social = [
    { id: 10002, name: '林雨桐', type: 'moments_like', content: '赞了你的友链' },
    { id: 10006, name: '周婉清', type: 'moments_comment', content: '评论了你：现场验收那张图很有感觉' },
    { id: 10003, name: '王浩然', type: 'moments_like', content: '赞了你的友链' },
    { id: 10010, name: '钱晓彤', type: 'moments_comment', content: '评论了你：海报主视觉可以用' },
    { id: 10016, name: '唐可欣', type: 'friend_request', content: '请求添加你为好友' },
  ]
  return [
    { id: 601, senderId: 0, senderName: '日历', type: 'calendar_remind', category: 'system', content: '15:00 产品评审会 · 3F 梧桐厅', readStatus: 0, createTime: iso(1_800_000) },
    { id: 602, senderId: 0, senderName: 'LinkX 官方', type: 'feedback_replied', category: 'system', content: '您的反馈「移动端登录闪退」已处理完成，感谢耐心等待。', readStatus: 0, createTime: iso(3_600_000) },
    { id: 603, senderId: 0, senderName: '系统公告', type: 'notice_system', category: 'system', content: 'LinkX 3.0 发版说明：IM、云盘、审批与短视频能力全面升级。', readStatus: 0, createTime: iso(7_200_000) },
    { id: 606, senderId: 0, senderName: '日历', type: 'calendar_remind', category: 'system', content: '明天 10:00 华东制造预演', readStatus: 0, createTime: iso(4_800_000) },
    { id: 607, senderId: 0, senderName: '云盘', type: 'notice_system', category: 'system', content: '发版 checklist.xlsx 已更新，请查阅最新版。', readStatus: 1, createTime: iso(9_600_000) },
    ...social.map((s, i) => ({
      id: 610 + i,
      senderId: s.id,
      senderName: s.name,
      senderAvatar: avatarMediaPath(s.id),
      type: s.type,
      category: 'social',
      content: s.content,
      readStatus: i > 2 ? 1 : 0,
      createTime: iso((i + 3) * 3_600_000),
    })),
  ]
}

function dayOffset(delta: number) {
  const t = new Date()
  t.setDate(t.getDate() + delta)
  return t.toISOString().slice(0, 10)
}

export function demoCalendarEvents() {
  return [
    { id: 'cal-1', title: '产品评审会', date: dayOffset(0), time: '15:00', endTime: '16:30', color: '#3b82f6' },
    { id: 'cal-2', title: '客户方案演示', date: dayOffset(0), time: '10:00', endTime: '11:00', color: '#10b981' },
    { id: 'cal-3', title: '周会总结', date: dayOffset(0), time: '17:30', endTime: '18:00', color: '#f59e0b' },
    { id: 'cal-4', title: '设计走查', date: dayOffset(-1), time: '14:00', endTime: '15:00', color: '#8b5cf6' },
    { id: 'cal-5', title: '灰度值班交接', date: dayOffset(-1), time: '19:30', endTime: '20:00', color: '#64748b' },
    { id: 'cal-6', title: '华东制造预演', date: dayOffset(1), time: '14:00', endTime: '16:00', color: '#ef4444' },
    { id: 'cal-7', title: '发版评审终审', date: dayOffset(1), time: '15:00', endTime: '16:00', color: '#3b82f6' },
    { id: 'cal-8', title: '安全扫描复盘', date: dayOffset(2), time: '11:00', endTime: '12:00', color: '#0ea5e9' },
    { id: 'cal-9', title: '客户成功例会', date: dayOffset(2), time: '16:00', endTime: '17:00', color: '#10b981' },
    { id: 'cal-10', title: '全员发版沟通会', date: dayOffset(3), time: '10:30', endTime: '11:30', color: '#f59e0b' },
  ]
}

const MOMENT_TEXTS = [
  '今天在客户现场完成 LinkX 私有化部署验收，团队协作效率明显提升。',
  '分享一张出差路上的随拍，天气不错。',
  '评审会纪要写完了，梧桐厅的投影线下次记得带备用。',
  '走查结束，登录页和主会话的间距终于对齐了。',
  '华东制造的预演材料第三版，封面换了品牌色。',
  '值班的夜晚，机房比想象中安静。',
  '把发版 checklist 补了运营开关和帮助中心链接。',
  '午餐后和设计对了空状态文案，轻松多了。',
  '客户成功案例写到金融行业这一章，素材很扎实。',
  '周末把帮助中心「图片预览」那篇更新了。',
]

export function demoMomentsPosts() {
  const photos = ['moments-01.jpg', 'moments-02.jpg', 'chat-photo-01.jpg', 'chat-photo-02.jpg', 'banner-ops.jpg']
  return Array.from({ length: 28 }).map((_, i) => {
    const peer = PEERS[i % PEERS.length]
    const mine = i % 3 !== 1
    const userId = mine ? DEMO_USER.id : peer.id
    const nickname = mine ? DEMO_USER.nickname : peer.nickname
    const comments = Array.from({ length: 1 + (i % 4) }).map((_, c) => ({
      id: String(61000 + i * 10 + c),
      userId: PEERS[(i + c) % PEERS.length].id,
      nickname: PEERS[(i + c) % PEERS.length].nickname,
      avatar: avatarMediaPath(PEERS[(i + c) % PEERS.length].id),
      content:
        c === 0
          ? '看起来很棒，改天一起同步。'
          : c === 1
            ? '已阅，我这边跟进。'
            : '这张图适合放进案例集。',
      time: new Date(now - (i + 1) * 2400_000 + c * 80_000).toISOString(),
    }))
    const imageCount = i % 5 === 0 ? 0 : i % 5 === 1 ? 1 : i % 5 === 2 ? 2 : 1
    const images = Array.from({ length: imageCount }).map((_, n) =>
      chatImageMediaPath(photos[(i + n) % photos.length])
    )
    const likedBy = PEERS.slice(0, 2 + (i % 4)).map(x => x.nickname)
    return {
      id: String(60000 + i),
      userId,
      username: mine ? DEMO_USER.username : peer.username,
      nickname,
      avatar: avatarMediaPath(userId),
      content: MOMENT_TEXTS[i % MOMENT_TEXTS.length],
      images,
      location: i % 4 === 0 ? '上海 · 陆家嘴' : i % 4 === 2 ? '杭州 · 云栖' : undefined,
      visibility: 0,
      time: new Date(now - (i + 1) * 2400_000).toISOString(),
      likes: 8 + i * 3,
      liked: i % 4 === 0,
      likedBy: i % 4 === 0 ? likedBy : likedBy.slice(1),
      comments,
    }
  })
}

export function demoShortVideos() {
  const cover = '/media/ops/short-video-cover.jpg'
  return Array.from({ length: 18 }).map((_, i) => {
    const peer = PEERS[i % PEERS.length]
    return {
      id: String(50000 + i),
      userId: peer.id,
      nickname: peer.nickname,
      avatar: avatarMediaPath(peer.id),
      description: [
        '30 秒看懂企业 IM 会话与已读回执',
        '云盘权限、标签和分享怎么用',
        '审批流：合同盖章到归档',
        '图片预览、多图切换与关闭回会话',
        '私有化部署验收现场花絮',
        '值班交接：告警到群消息的闭环',
      ][i % 6],
      coverUrl: cover,
      videoUrl: cover,
      durationMs: 12_000 + i * 400,
      playCount: 320 + i * 41,
      shares: 6 + i,
      time: new Date(now - i * 7200_000).toISOString(),
      likes: 120 + i * 17,
      liked: i % 5 === 0,
      favorites: 20 + i,
      favorited: i % 3 === 0,
      followingAuthor: i % 2 === 0,
      commentCount: 3 + (i % 4),
      comments: [
        {
          id: String(51000 + i),
          userId: DEMO_USER.id,
          nickname: DEMO_USER.nickname,
          avatar: DEMO_USER.avatar,
          content: '这个演示很清楚，收藏了。',
          time: new Date(now - i * 3600_000).toISOString(),
          likes: 4,
          liked: false,
        },
      ],
      topics: i % 2 === 0 ? ['企业协作', 'LinkX'] : ['产品演示'],
      transcodeStatus: 'ready',
    }
  })
}

export function demoRecommends() {
  return [
    { id: '1', slotCode: 'discover', title: 'LinkX 3.0 发布说明', subtitle: 'IM、云盘、审批与短视频能力全面升级', imageUrl: '/media/ops/banner-ops.jpg', linkUrl: '#', sortOrder: 1 },
    { id: '2', slotCode: 'chat_sidebar', title: '企业安全白皮书', subtitle: '私有化部署与合规实践', imageUrl: '/media/ops/chat-photo-02.jpg', linkUrl: '#', sortOrder: 2 },
    { id: '3', slotCode: 'moments', title: '客户成功案例', subtitle: '制造、金融、政务行业落地', imageUrl: '/media/ops/moments-01.jpg', linkUrl: '#', sortOrder: 3 },
    { id: '4', slotCode: 'discover', title: '灰度发布手册', subtitle: '回滚脚本、值班表与检查清单', imageUrl: '/media/ops/moments-02.jpg', linkUrl: '#', sortOrder: 4 },
    { id: '5', slotCode: 'chat_sidebar', title: '帮助中心精选', subtitle: '图片预览、会话搜索与已读回执', imageUrl: '/media/ops/chat-photo-01.jpg', linkUrl: '#', sortOrder: 5 },
  ]
}

export function demoActivities() {
  return [
    { id: 'act-1', title: '春季企业协作沙龙', coverUrl: '/media/ops/banner-ops.jpg', linkUrl: '#', description: '线下交流 LinkX 3.0 新能力', sortOrder: 1 },
    { id: 'act-2', title: '私有化部署工作坊', coverUrl: '/media/ops/moments-02.jpg', linkUrl: '#', description: '两小时完成演示环境搭建', sortOrder: 2 },
    { id: 'act-3', title: '客户成功开放日', coverUrl: '/media/ops/moments-01.jpg', linkUrl: '#', description: '制造与金融行业落地复盘', sortOrder: 3 },
    { id: 'act-4', title: '安全合规圆桌', coverUrl: '/media/ops/chat-photo-02.jpg', linkUrl: '#', description: '等保、审计日志与数据不出境', sortOrder: 4 },
  ]
}
