import type { ChatSession, ChatMessage, ContactItem, FavoriteItem, AppItem } from '../types'

export const initialSessions: ChatSession[] = [
  {
    id: 'f-zwz',
    name: '吱唔猪',
    lastMessage: 'Flet (Python版的Flutter)',
    time: '15:43',
    avatarText: '吱',
    avatarColor: '#7cb342',
    online: true,
    avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhiwuzhu'
  },
  {
    id: '0',
    name: '恋语PC',
    lastMessage: '系统：欢迎加入恋语PC',
    time: '11:35',
    avatarText: '恋',
    avatarColor: '#ff6b9d',
    unread: 120,
    isGroup: true
  },
  {
    id: '1',
    name: '软件开发交流',
    lastMessage: '幻梦：[图片] @南方叛徒 欢...',
    time: '11:33',
    avatarText: '软',
    avatarColor: '#ff9a9e',
    muted: true,
    isGroup: true
  },
  {
    id: '2',
    name: 'Cursor/GPT/CC AI...',
    lastMessage: '有BB机的小豆包：剃掉',
    time: '11:30',
    avatarText: 'C',
    avatarColor: '#e74c3c',
    unread: 32,
    isGroup: true
  },
  {
    id: '3',
    name: '极客软件园A12',
    lastMessage: '小豪邀请*加入了群聊。',
    time: '19:27',
    avatarText: '极',
    avatarColor: '#333333',
    isGroup: true
  },
  {
    id: '4',
    name: '速云17聚合ai模型a...',
    lastMessage: '飞逝的时光邀请wuliYang加入...',
    time: '19:08',
    avatarText: '速',
    avatarColor: '#9b59b6',
    isGroup: true
  },
  {
    id: '5',
    name: 'Trae 助手交流群1',
    lastMessage: '兔七哥：好想干一个小助手 ...',
    time: '11:28',
    avatarText: 'T',
    avatarColor: '#1a1a2e',
    unread: 4,
    isGroup: true
  },
  {
    id: '6',
    name: '我的手机',
    lastMessage: 'Screenshot 2026-07-05-18-...',
    time: '18:48',
    avatarText: '',
    avatarColor: '#0099ff'
  },
  {
    id: '7',
    name: 'Cursor无限续杯7群',
    lastMessage: '月满西楼加入了群聊。',
    time: '18:26',
    avatarText: '★',
    avatarColor: '#f39c12',
    isGroup: true
  },
  {
    id: '8',
    name: 'cursor分享群',
    lastMessage: 'Q群管家：@从前车马慢 欢迎...',
    time: '18:17',
    avatarText: 'C',
    avatarColor: '#2c3e50',
    isGroup: true
  },
  {
    id: '9',
    name: '爱语交流14群',
    lastMessage: 'MTK8200：[视频]',
    time: '11:20',
    avatarText: '爱',
    avatarColor: '#ff9ff3',
    unread: 35,
    isGroup: true
  },
  {
    id: '10',
    name: 'QQ游戏中心',
    lastMessage: '推荐：王者荣耀五排上分',
    time: '16:27',
    avatarText: 'Q',
    avatarColor: '#0099ff'
  }
]

export const initialMessages: Record<string, ChatMessage[]> = {
  'f-zwz': [
    {
      id: 'zwz-f1',
      sessionId: 'f-zwz',
      content: '',
      time: '15:20',
      isSelf: true,
      type: 'file',
      fileName: 'app-debug.apk',
      fileSize: '63.39 MB',
      fileStatus: '已发送'
    },
    {
      id: 'zwz-l1',
      sessionId: 'f-zwz',
      content: '9- #在抖音，记录美好生活#【小煜yy🐟】正在直播，来和我一起支持Ta吧。复制下方链接，打开【抖音】，直接观看直播！',
      time: '15:28',
      isSelf: true,
      type: 'link',
      linkUrl: 'https://v.douyin.com/example'
    },
    {
      id: 'zwz-t1',
      sessionId: 'f-zwz',
      content:
        '9- #在抖音，记录美好生活#【蒜泥狠🧄】正在直播，来和我一起支持Ta吧。复制下方链接，打开【抖音】，直接观看直播！',
      time: '15:32',
      isSelf: false,
      type: 'link',
      linkUrl: 'https://v.douyin.com/example2'
    },
    {
      id: 'zwz-rp1',
      sessionId: 'f-zwz',
      content: '恭喜发财，大吉大利',
      time: '15:35',
      isSelf: false,
      senderName: '吱唔猪',
      type: 'redPacket',
      redPacketGreeting: '恭喜发财，大吉大利',
      redPacketAmount: '8.88',
      redPacketOpened: false
    },
    {
      id: 'zwz-t2',
      sessionId: 'f-zwz',
      content: 'Flet (Python版的Flutter)',
      time: '15:38',
      isSelf: true,
      type: 'text'
    },
    {
      id: 'zwz-t3',
      sessionId: 'f-zwz',
      content:
        '9- #在抖音，记录美好生活#【李慕婉】正在直播，来和我一起支持Ta吧。复制下方链接，打开【抖音】，直接观看直播！',
      time: '15:43',
      isSelf: false,
      type: 'link',
      linkUrl: 'https://v.douyin.com/example3'
    }
  ],
  '6': [
    { id: 'm1', sessionId: '6', content: '', time: '18:48', isSelf: false, type: 'system' }
  ],
  '1': [
    { id: 'm2', sessionId: '1', content: '大家好，今晚有人一起写代码吗？', time: '19:50', isSelf: false },
    { id: 'm3', sessionId: '1', content: '我在，可以结对。', time: '19:52', isSelf: true }
  ],
  '2': [
    { id: 'm4', sessionId: '2', content: '模型额度又用完了…', time: '19:30', isSelf: false },
    { id: 'm5', sessionId: '2', content: '试试换账号或本地代理。', time: '19:35', isSelf: true }
  ]
}

export const contacts: ContactItem[] = [
  { id: 'c1', name: '张三', avatarText: '张', avatarColor: '#0099ff', group: '我的好友', online: true },
  { id: 'c2', name: '李四', avatarText: '李', avatarColor: '#52c41a', group: '我的好友', online: false },
  { id: 'c3', name: '王五', avatarText: '王', avatarColor: '#fa8c16', group: '我的好友', online: true },
  { id: 'c4', name: '项目组', avatarText: '项', avatarColor: '#722ed1', group: '群聊', online: true },
  { id: 'c5', name: '家人', avatarText: '家', avatarColor: '#eb2f96', group: '分组', online: false }
]

export const favorites: FavoriteItem[] = [
  { id: 'f1', title: 'LinkX 对接清单', preview: '1. 登录 2. 会话列表 3. WebSocket…', time: '今天', type: 'note' },
  { id: 'f2', title: '架构图.png', preview: '2.4 MB', time: '昨天', type: 'image' },
  { id: 'f3', title: 'API 文档', preview: 'https://docs.example.com', time: '3天前', type: 'link' }
]

export const apps: AppItem[] = [
  { id: 'a1', name: '腾讯文档', desc: '在线协作', icon: '文', color: '#1890ff', url: 'https://docs.qq.com/' },
  { id: 'a2', name: '微云', desc: '网盘备份', icon: '云', color: '#52c41a', url: 'https://www.weiyun.com/' },
  { id: 'a3', name: 'QQ音乐', desc: '听歌', icon: '乐', color: '#fa541c', url: 'https://y.qq.com/' },
  { id: 'a4', name: '小游戏', desc: '休闲娱乐', icon: '戏', color: '#722ed1', url: 'https://example.com/' }
]

export function sessionFromContact(c: ContactItem): ChatSession {
  return {
    id: c.id,
    name: c.name,
    lastMessage: '点击开始聊天',
    time: '刚刚',
    avatarText: c.avatarText,
    avatarColor: c.avatarColor,
    online: c.online
  }
}