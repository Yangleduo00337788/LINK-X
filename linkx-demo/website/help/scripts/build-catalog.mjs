/**
 * 作者：yangleduo
 * 生成 help 目录 catalog.zh-CN.js / catalog.en-US.js
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const outDir = path.join(__dirname, '../data')

function article(id, title, goal, sections, related = []) {
  return { id, title, description: goal, goal, sections, related }
}

function steps(title, items) {
  return { id: slug(title), title, level: 2, content: [{ type: 'steps', items }] }
}

function done(title, text) {
  return { id: slug(title), title, level: 2, content: [{ type: 'p', text }] }
}

function faqSection(items) {
  return { id: 'faq', title: items[0].title || '常见问题', level: 2, content: [{ type: 'faq', items: items.map(({ q, a }) => ({ q, a })) }] }
}

function tip(text) {
  return { type: 'tip', text }
}

function slug(s) {
  return s.replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '').toLowerCase().slice(0, 40) || 'section'
}

const zh = {
  siteTitle: 'LinkX 帮助中心',
  brandTitle: '帮助中心',
  breadcrumbHome: '帮助中心',
  homeNavLabel: '首页',
  homeTitle: '你好，需要什么帮助？',
  homeSubtitle: '按任务查找操作步骤，快速解决使用问题。',
  searchPlaceholder: '搜索问题，例如：发送图片、添加好友',
  tocTitle: '本页目录',
  tocEmpty: '本页暂无目录',
  noResults: '未找到相关内容',
  articleNotFound: '未找到该帮助主题',
  relatedTitle: '相关帮助',
  quickTitle: '热门问题',
  labels: { tip: '提示', note: '说明', warn: '注意' },
  categoryCards: [
    { id: 'start', icon: '🚀', title: '新手上路', description: '注册登录、认识界面、添加好友' },
    { id: 'chat', icon: '💬', title: '聊天', description: '发消息、发图片文件、回复转发' },
    { id: 'group', icon: '👥', title: '群聊', description: '建群、群公告、成员管理' },
    { id: 'calls', icon: '📞', title: '通话', description: '语音与视频通话' },
    { id: 'tools', icon: '📁', title: '文件与工具', description: '云盘、友链、日历、笔记' },
    { id: 'settings', icon: '⚙️', title: '设置', description: '账号、隐私、通知、外观' },
    { id: 'support', icon: '❓', title: '常见问题', description: '同步、网络、提交反馈' }
  ],
  quickLinks: [
    { title: '如何登录', articleId: 'login-register' },
    { title: '如何发图片', articleId: 'send-image' },
    { title: '如何创建群聊', articleId: 'create-group' },
    { title: '消息不同步', articleId: 'faq-sync' },
    { title: '提交反馈', articleId: 'submit-feedback' }
  ],
  categories: [
    {
      id: 'start',
      title: '新手上路',
      articles: [
        { id: 'login-register', title: '如何注册与登录', description: '使用账号进入 LinkX 主界面' },
        { id: 'main-interface', title: '认识主界面', description: '了解侧栏与各区域用途' },
        { id: 'add-friend', title: '如何添加好友', description: '通过 LinkX ID 添加联系人' }
      ]
    },
    {
      id: 'chat',
      title: '聊天',
      articles: [
        { id: 'send-text', title: '如何发送文字消息', description: '在会话中发送文字与表情' },
        { id: 'send-image', title: '如何发送图片', description: '发送本地图片到聊天' },
        { id: 'send-file', title: '如何发送文件', description: '发送文档等附件' },
        { id: 'send-voice', title: '如何发送语音', description: '录制并发送语音消息' },
        { id: 'reply-forward', title: '如何回复与转发', description: '引用回复或转发到其他会话' },
        { id: 'recall-message', title: '如何撤回消息', description: '撤回自己发送的消息' },
        { id: 'search-chat', title: '如何搜索聊天记录', description: '在会话或全局查找消息' },
        { id: 'favorites-msg', title: '如何收藏消息', description: '把重要消息保存到收藏' }
      ]
    },
    {
      id: 'group',
      title: '群聊',
      articles: [
        { id: 'create-group', title: '如何创建群聊', description: '邀请好友建立群会话' },
        { id: 'group-announce', title: '如何查看群公告', description: '阅读群主发布的公告' },
        { id: 'group-members', title: '如何管理群成员', description: '邀请、移除成员与设置管理员' }
      ]
    },
    {
      id: 'calls',
      title: '通话',
      articles: [{ id: 'start-call', title: '如何发起语音或视频通话', description: '在单聊中开始通话' }]
    },
    {
      id: 'tools',
      title: '文件与工具',
      articles: [
        { id: 'use-drive', title: '如何使用云盘', description: '上传与管理个人文件' },
        { id: 'share-link', title: '如何分享文件链接', description: '生成带提取码的分享' },
        { id: 'moments-view', title: '如何浏览友链', description: '查看好友动态' },
        { id: 'moments-post', title: '如何发布友链动态', description: '发布图文动态' },
        { id: 'calendar-event', title: '如何创建日程', description: '添加提醒事项' },
        { id: 'use-notes', title: '如何使用笔记', description: '新建与编辑笔记' }
      ]
    },
    {
      id: 'settings',
      title: '设置',
      articles: [
        { id: 'edit-profile', title: '如何修改个人资料', description: '头像、昵称与 LinkX ID' },
        { id: 'privacy-options', title: '如何设置隐私', description: '好友验证与在线状态' },
        { id: 'notify-settings', title: '如何设置消息通知', description: '声音、桌面通知与免打扰' },
        { id: 'theme-language', title: '如何切换主题与语言', description: '深色模式与中英文' },
        { id: 'tray-lock', title: '如何使用托盘与锁屏', description: '后台运行与临时锁定' }
      ]
    },
    {
      id: 'support',
      title: '常见问题',
      articles: [
        { id: 'faq-sync', title: '消息会同步吗', description: '多设备与历史记录说明' },
        { id: 'faq-network', title: '连不上或消息发不出', description: '网络与登录排查' },
        { id: 'submit-feedback', title: '如何提交问题反馈', description: '在客户端反馈 Bug 或建议' }
      ]
    }
  ],
  articles: {}
}

zh.articles['login-register'] = article(
  'login-register',
  '如何注册与登录',
  '使用 LinkX 账号进入主界面，开始收发消息。',
  [
    steps('操作步骤', [
      '打开 LinkX，在登录页输入<strong>账号</strong>和<strong>密码</strong>。',
      '完成<strong>滑块验证码</strong>验证。',
      '点击<strong>登录</strong>。首次使用可点击<strong>注册</strong>，按提示设置账号、密码和昵称。',
      '登录成功后进入主界面；左下角头像可查看个人资料与 <strong>LinkX ID</strong>。'
    ]),
    done('完成后', '左侧显示「消息」等导航，中间为会话列表。'),
    {
      id: 'faq',
      title: '常见问题',
      level: 2,
      content: [
        {
          type: 'faq',
          items: [
            { q: '忘记密码怎么办？', a: '在登录页使用找回密码流程，或联系管理员重置。' },
            { q: '可以记住账号吗？', a: '登录页可勾选「记住账号」；也可在「设置 → 通用设置」中开启自动登录。' }
          ]
        }
      ]
    }
  ],
  ['main-interface', 'add-friend']
)

zh.articles['main-interface'] = article(
  'main-interface',
  '认识主界面',
  '快速了解 LinkX 桌面端各区域做什么用。',
  [
    steps('界面区域', [
      '最左侧<strong>图标栏</strong>：切换「消息」「联系人」「收藏」「文件」「日历」「友链」「余额」「设置」。',
      '消息页左侧为<strong>会话列表</strong>，可搜索、置顶、设置免打扰。',
      '中间为<strong>聊天主区域</strong>，阅读与发送消息。',
      '群聊时右侧可展开<strong>群信息</strong>（成员、公告等）。',
      '左下角<strong>头像</strong>查看个人资料；点击<strong>更多</strong>（菜单图标）可打开帮助、锁屏、退出等。'
    ]),
    { id: 'tip-1', title: '提示', level: 2, content: [tip('窗口顶部可拖动移动；右上角为最小化、最大化、关闭。')] }
  ],
  ['login-register', 'send-text']
)

zh.articles['add-friend'] = article(
  'add-friend',
  '如何添加好友',
  '通过 LinkX ID 搜索并添加联系人。',
  [
    steps('操作步骤', [
      '点击左侧<strong>联系人</strong>。',
      '点击添加好友入口，输入对方的 <strong>LinkX ID</strong> 搜索。',
      '在结果中发送好友申请；对方同意后出现在联系人列表。',
      '在联系人上右键或点击即可发起单聊。'
    ]),
    done('完成后', '新好友会出现在联系人列表，可开始聊天。'),
    {
      id: 'faq',
      title: '常见问题',
      level: 2,
      content: [
        {
          type: 'faq',
          items: [
            { q: '对方收不到申请？', a: '确认 LinkX ID 无误；若对方开启「加好友需验证」，需等待对方在通知中同意。' },
            { q: '我的 LinkX ID 在哪？', a: '点击左下角头像打开资料，或在「设置 → 我的账号」中查看。' }
          ]
        }
      ]
    }
  ],
  ['login-register', 'send-text']
)

zh.articles['send-text'] = article(
  'send-text',
  '如何发送文字消息',
  '在单聊或群聊中发送文字与表情。',
  [
    steps('操作步骤', [
      '在左侧<strong>消息</strong>中打开一个会话。',
      '在底部输入框输入文字；点击表情按钮可插入表情。',
      '按 <strong>Enter</strong> 发送；<strong>Shift + Enter</strong> 换行。',
      '群聊中输入 <strong>@</strong> 可提及成员。'
    ]),
    done('完成后', '消息出现在聊天记录中，对方在线时会实时收到。')
  ],
  ['send-image', 'reply-forward']
)

zh.articles['send-image'] = article(
  'send-image',
  '如何发送图片',
  '把本地图片发送到当前聊天。',
  [
    steps('操作步骤', [
      '打开要发送的会话。',
      '点击输入框旁的 <strong>+</strong> 或工具栏中的图片入口。',
      '选择本地图片并确认发送。',
      '也可将图片<strong>拖拽</strong>到聊天区域直接发送。'
    ]),
    done('完成后', '图片显示在消息流中；双击可预览大图。'),
    {
      id: 'faq',
      title: '常见问题',
      level: 2,
      content: [
        {
          type: 'faq',
          items: [{ q: '发送失败？', a: '检查网络连接；图片过大时可能需等待上传完成。' }]
        }
      ]
    }
  ],
  ['send-file', 'send-text']
)

zh.articles['send-file'] = article(
  'send-file',
  '如何发送文件',
  '在聊天中发送文档、压缩包等附件。',
  [
    steps('操作步骤', [
      '打开会话，点击 <strong>+</strong> 选择<strong>文件</strong>。',
      '在文件选择器选中文件并发送。',
      '或将文件拖拽到聊天窗口。'
    ]),
    done('完成后', '以文件消息形式展示，点击可下载或打开。')
  ],
  ['send-image', 'use-drive']
)

zh.articles['send-voice'] = article(
  'send-voice',
  '如何发送语音',
  '录制一段语音并发送。',
  [
    steps('操作步骤', [
      '打开会话，点击麦克风/语音按钮开始录制。',
      '再次点击结束并发送；录制过程中可取消。',
      '首次使用需允许系统<strong>麦克风</strong>权限。'
    ]),
    done('完成后', '对方可点击播放语音消息。')
  ],
  ['send-text']
)

zh.articles['reply-forward'] = article(
  'reply-forward',
  '如何回复与转发',
  '引用某条消息回复，或转发到其他会话。',
  [
    steps('回复消息', [
      '在消息上<strong>右键</strong>，选择<strong>回复</strong>。',
      '输入框上方会显示被引用内容，输入回复后发送。'
    ]),
    steps('转发消息', [
      '右键消息，选择<strong>转发</strong>。',
      '在弹窗中选择一个或多个会话，确认发送。'
    ])
  ],
  ['send-text', 'favorites-msg']
)

zh.articles['recall-message'] = article(
  'recall-message',
  '如何撤回消息',
  '撤回自己刚发送的消息。',
  [
    steps('操作步骤', [
      '在要撤回的消息上<strong>右键</strong>。',
      '选择<strong>撤回</strong>（仅在允许的时间窗口内可用）。'
    ]),
    done('完成后', '会话中显示撤回提示。'),
    { id: 'tip-1', title: '提示', level: 2, content: [tip('超过可撤回时间后，选项将不可用。')] }
  ],
  ['send-text']
)

zh.articles['search-chat'] = article(
  'search-chat',
  '如何搜索聊天记录',
  '在当前会话或消息列表中查找历史消息。',
  [
    steps('操作步骤', [
      '在<strong>消息</strong>列表顶部使用搜索框，按联系人或群名查找会话。',
      '进入会话后，使用会话内搜索（如有）或滚动浏览历史记录。',
      '左下角菜单中的<strong>聊天记录管理</strong>可打开独立窗口集中检索（桌面端）。'
    ])
  ],
  ['favorites-msg']
)

zh.articles['favorites-msg'] = article(
  'favorites-msg',
  '如何收藏消息',
  '把重要消息保存到「收藏」便于回看。',
  [
    steps('操作步骤', [
      '在消息上右键，选择<strong>收藏</strong>。',
      '点击左侧<strong>收藏</strong>查看已收藏内容。'
    ]),
    done('完成后', '可在收藏中按类型浏览消息、文件等。')
  ],
  ['reply-forward']
)

zh.articles['create-group'] = article(
  'create-group',
  '如何创建群聊',
  '邀请多位好友建立群会话。',
  [
    steps('操作步骤', [
      '在<strong>消息</strong>或<strong>联系人</strong>中点击创建群聊。',
      '勾选要邀请的好友，确认创建。',
      '创建后可设置群名称与群头像（若你是群主/管理员）。'
    ]),
    done('完成后', '群聊出现在消息列表，成员可共同聊天。')
  ],
  ['group-announce', 'group-members']
)

zh.articles['group-announce'] = article(
  'group-announce',
  '如何查看群公告',
  '阅读群主或管理员发布的群公告。',
  [
    steps('操作步骤', [
      '打开群聊，点击右上角或右侧<strong>群信息</strong>入口。',
      '在群资料中找到<strong>群公告</strong>并点击查看。'
    ])
  ],
  ['create-group']
)

zh.articles['group-members'] = article(
  'group-members',
  '如何管理群成员',
  '邀请新成员或管理群角色（需相应权限）。',
  [
    steps('操作步骤', [
      '打开群聊，进入<strong>群信息</strong>。',
      '点击<strong>邀请</strong>添加好友入群。',
      '群主/管理员可移除成员、设置管理员或开启入群验证（以实际界面为准）。'
    ])
  ],
  ['create-group']
)

zh.articles['start-call'] = article(
  'start-call',
  '如何发起语音或视频通话',
  '与好友进行一对一语音或视频通话。',
  [
    steps('操作步骤', [
      '打开与对方的<strong>单聊</strong>会话。',
      '点击顶部<strong>语音</strong>或<strong>视频</strong>图标发起通话。',
      '首次使用请允许<strong>麦克风</strong>和<strong>摄像头</strong>（视频时）权限。'
    ]),
    done('完成后', '进入通话界面，可静音、关闭摄像头或挂断。'),
    { id: 'tip-1', title: '提示', level: 2, content: [tip('网络不稳定时建议使用语音通话，或更换更稳定的网络环境。')] }
  ],
  ['send-text']
)

zh.articles['use-drive'] = article(
  'use-drive',
  '如何使用云盘',
  '在个人云盘中上传和管理文件。',
  [
    steps('操作步骤', [
      '点击左侧<strong>文件</strong>进入云盘。',
      '点击上传或拖拽文件到列表区域。',
      '可新建文件夹、重命名、移动或删除文件。'
    ])
  ],
  ['share-link']
)

zh.articles['share-link'] = article(
  'share-link',
  '如何分享文件链接',
  '生成外链让他人下载云盘文件。',
  [
    steps('操作步骤', [
      '在<strong>文件</strong>中对目标文件点击分享。',
      '设置有效期与提取码（如支持）。',
      '复制分享链接发送给他人。'
    ])
  ],
  ['use-drive']
)

zh.articles['moments-view'] = article(
  'moments-view',
  '如何浏览友链',
  '查看好友发布的动态。',
  [
    steps('操作步骤', [
      '点击左侧<strong>友链</strong>。',
      '桌面端可能打开独立窗口；在时间线中浏览好友动态。',
      '点击动态可查看详情、点赞与评论。'
    ])
  ],
  ['moments-post']
)

zh.articles['moments-post'] = article(
  'moments-post',
  '如何发布友链动态',
  '发布文字或图片动态。',
  [
    steps('操作步骤', [
      '进入<strong>友链</strong>，点击发布。',
      '输入文字或选择图片/视频，确认发布。'
    ]),
    done('完成后', '动态出现在你的时间线，好友可见。')
  ],
  ['moments-view']
)

zh.articles['calendar-event'] = article(
  'calendar-event',
  '如何创建日程',
  '添加带提醒的日程事项。',
  [
    steps('操作步骤', [
      '点击左侧<strong>日历</strong>。',
      '选择日期，点击新建日程。',
      '填写标题、时间与提醒方式并保存。'
    ]),
    done('完成后', '到期时客户端会推送提醒通知。')
  ],
  ['use-notes']
)

zh.articles['use-notes'] = article(
  'use-notes',
  '如何使用笔记',
  '创建和编辑个人笔记。',
  [
    steps('操作步骤', [
      '在日历或笔记入口打开笔记列表（以实际入口为准）。',
      '新建笔记，填写标题与正文，可插入图片或附件。',
      '保存后可在列表中置顶或继续编辑。'
    ])
  ],
  ['calendar-event']
)

zh.articles['edit-profile'] = article(
  'edit-profile',
  '如何修改个人资料',
  '更新头像、昵称等信息。',
  [
    steps('操作步骤', [
      '点击左侧<strong>设置</strong>，进入<strong>我的账号</strong>。',
      '修改头像、昵称、性别、地区等字段。',
      '查看并复制你的 <strong>LinkX ID</strong> 供好友添加。'
    ])
  ],
  ['privacy-options']
)

zh.articles['privacy-options'] = article(
  'privacy-options',
  '如何设置隐私',
  '控制谁可以加你为好友、谁可以给你发消息。',
  [
    steps('操作步骤', [
      '打开<strong>设置 → 隐私设置</strong>。',
      '开启或关闭<strong>加好友需验证</strong>、<strong>允许陌生人会话</strong>、<strong>在线状态可见</strong>等选项。'
    ])
  ],
  ['edit-profile', 'notify-settings']
)

zh.articles['notify-settings'] = article(
  'notify-settings',
  '如何设置消息通知',
  '配置声音、桌面通知与免打扰。',
  [
    steps('操作步骤', [
      '打开<strong>设置 → 消息通知</strong>。',
      '开关聊天提醒、@ 提醒、声音与桌面通知详情。',
      '可设置<strong>免打扰时段</strong>，在指定时间段静音。'
    ])
  ],
  ['theme-language']
)

zh.articles['theme-language'] = article(
  'theme-language',
  '如何切换主题与语言',
  '切换深色模式与界面语言。',
  [
    steps('操作步骤', [
      '打开<strong>设置 → 外观设置</strong>，选择浅色或深色主题。',
      '打开<strong>设置 → 通用设置</strong>，在语言中选择简体中文或 English。'
    ])
  ],
  ['notify-settings']
)

zh.articles['tray-lock'] = article(
  'tray-lock',
  '如何使用托盘与锁屏',
  '关闭窗口后保持后台运行，或临时锁定应用。',
  [
    steps('最小化到托盘', [
      '在<strong>设置 → 通用设置</strong>中开启<strong>关闭时最小化到托盘</strong>。',
      '关闭主窗口后，LinkX 仍在托盘运行，可继续收消息。'
    ]),
    steps('锁定屏幕', [
      '点击左下角<strong>更多</strong>菜单，选择<strong>锁定</strong>。',
      '返回后需验证密码解锁。'
    ])
  ],
  ['notify-settings']
)

zh.articles['faq-sync'] = article(
  'faq-sync',
  '消息会同步吗',
  '了解聊天记录在多设备间的同步方式。',
  [
    {
      id: 'answer',
      title: '说明',
      level: 2,
      content: [
        {
          type: 'p',
          text: '会。使用同一账号登录后，聊天记录、好友列表和大部分设置会保存在服务器，换电脑登录仍可查看历史消息。'
        },
        {
          type: 'p',
          text: '同步速度取决于网络；较早的消息可能需要滚动加载。'
        }
      ]
    }
  ],
  ['faq-network']
)

zh.articles['faq-network'] = article(
  'faq-network',
  '连不上或消息发不出',
  '排查网络与登录相关问题。',
  [
    steps('请依次检查', [
      '确认电脑已联网，可访问其他网站。',
      '查看 LinkX 顶部是否显示<strong>离线</strong>或连接失败提示。',
      '退出后重新登录，并完成验证码。',
      '若使用公司网络/VPN，确认未拦截 WebSocket 连接。'
    ]),
    {
      id: 'faq',
      title: '仍无法解决',
      level: 2,
      content: [
        {
          type: 'faq',
          items: [{ q: '下一步怎么办？', a: '请通过「如何提交问题反馈」向我们说明现象与截图。' }]
        }
      ]
    }
  ],
  ['submit-feedback', 'faq-sync']
)

zh.articles['submit-feedback'] = article(
  'submit-feedback',
  '如何提交问题反馈',
  '在客户端提交 Bug、功能建议或咨询。',
  [
    steps('提交新反馈', [
      '在<strong>登录页</strong>点击右上角菜单，选择<strong>问题反馈</strong>（无需登录也可提交）。',
      '选择反馈类型，填写内容与联系方式（选填），点击提交。'
    ]),
    steps('查看我的反馈', [
      '登录后打开<strong>设置 → 我的账号</strong>。',
      '在页面中找到<strong>我的反馈</strong>，可查看历史记录与官方回复。',
      '对未结案的反馈可继续追问补充说明。'
    ]),
    done('完成后', '提交成功后会收到提示；有回复时会在通知中提醒。')
  ],
  ['faq-network']
)

// —— English mirror (shorter labels, same structure) ——
const en = JSON.parse(JSON.stringify(zh))
en.siteTitle = 'LinkX Help Center'
en.brandTitle = 'Help'
en.breadcrumbHome = 'Help Center'
en.homeNavLabel = 'Home'
en.homeTitle = 'How can we help?'
en.homeSubtitle = 'Find step-by-step guides for common tasks.'
en.searchPlaceholder = 'Search, e.g. send image, add friend'
en.relatedTitle = 'Related'
en.quickTitle = 'Popular topics'
en.labels = { tip: 'Tip', note: 'Note', warn: 'Warning' }
en.categoryCards = [
  { id: 'start', icon: '🚀', title: 'Getting started', description: 'Sign in, interface, add friends' },
  { id: 'chat', icon: '💬', title: 'Chat', description: 'Messages, media, reply & forward' },
  { id: 'group', icon: '👥', title: 'Groups', description: 'Create groups, announcements' },
  { id: 'calls', icon: '📞', title: 'Calls', description: 'Voice and video' },
  { id: 'tools', icon: '📁', title: 'Files & tools', description: 'Drive, Moments, calendar, notes' },
  { id: 'settings', icon: '⚙️', title: 'Settings', description: 'Account, privacy, notifications' },
  { id: 'support', icon: '❓', title: 'FAQ', description: 'Sync, network, feedback' }
]
en.quickLinks = [
  { title: 'Sign in', articleId: 'login-register' },
  { title: 'Send images', articleId: 'send-image' },
  { title: 'Create a group', articleId: 'create-group' },
  { title: 'Messages not syncing', articleId: 'faq-sync' },
  { title: 'Send feedback', articleId: 'submit-feedback' }
]

const enTitles = {
  'login-register': ['How to sign in or register', 'Sign in to LinkX and reach the main screen.'],
  'main-interface': ['Learn the main interface', 'Understand the sidebar and main areas.'],
  'add-friend': ['How to add a friend', 'Find people by LinkX ID.'],
  'send-text': ['How to send text messages', 'Send text and emoji in a chat.'],
  'send-image': ['How to send images', 'Send a local image in chat.'],
  'send-file': ['How to send files', 'Send documents and attachments.'],
  'send-voice': ['How to send voice messages', 'Record and send voice.'],
  'reply-forward': ['How to reply and forward', 'Quote or forward messages.'],
  'recall-message': ['How to recall a message', 'Undo a message you sent.'],
  'search-chat': ['How to search chat history', 'Find past messages.'],
  'favorites-msg': ['How to favorite messages', 'Save important messages.'],
  'create-group': ['How to create a group chat', 'Start a group with friends.'],
  'group-announce': ['How to view group announcements', 'Read group notices.'],
  'group-members': ['How to manage group members', 'Invite or manage members.'],
  'start-call': ['How to start a voice or video call', 'Call a friend in a direct chat.'],
  'use-drive': ['How to use cloud drive', 'Upload and manage files.'],
  'share-link': ['How to share file links', 'Create share links with access codes.'],
  'moments-view': ['How to browse Moments', 'View friends\' posts.'],
  'moments-post': ['How to post on Moments', 'Publish text or photos.'],
  'calendar-event': ['How to create an event', 'Add calendar reminders.'],
  'use-notes': ['How to use notes', 'Create and edit notes.'],
  'edit-profile': ['How to edit your profile', 'Avatar, nickname, LinkX ID.'],
  'privacy-options': ['How to change privacy settings', 'Friend requests and online status.'],
  'notify-settings': ['How to set notifications', 'Sounds, desktop alerts, quiet hours.'],
  'theme-language': ['How to change theme and language', 'Dark mode and locale.'],
  'tray-lock': ['Tray and lock screen', 'Run in background or lock the app.'],
  'faq-sync': ['Do messages sync?', 'Multi-device and history.'],
  'faq-network': ['Cannot connect or send messages', 'Network troubleshooting.'],
  'submit-feedback': ['How to send feedback', 'Report bugs or suggestions.']
}

en.categories.forEach((cat) => {
  const catEn = {
    start: 'Getting started',
    chat: 'Chat',
    group: 'Groups',
    calls: 'Calls',
    tools: 'Files & tools',
    settings: 'Settings',
    support: 'FAQ'
  }
  cat.title = catEn[cat.id] || cat.title
  cat.articles.forEach((a) => {
    const t = enTitles[a.id]
    if (t) {
      a.title = t[0]
      a.description = t[1]
    }
  })
})

Object.keys(en.articles).forEach((id) => {
  const t = enTitles[id]
  if (t) {
    en.articles[id].title = t[0]
    en.articles[id].goal = t[1]
    en.articles[id].description = t[1]
  }
})

const enSectionTitles = {
  '操作步骤': 'Steps',
  '完成后': 'When you\'re done',
  '常见问题': 'FAQ',
  '提示': 'Tip',
  '说明': 'Note',
  '界面区域': 'Main areas',
  '回复消息': 'Reply to a message',
  '转发消息': 'Forward a message',
  '请依次检查': 'Try these steps',
  '仍无法解决': 'Still stuck?',
  '提交新反馈': 'Submit feedback',
  '查看我的反馈': 'View my feedback',
  '最小化到托盘': 'Minimize to tray',
  '锁定屏幕': 'Lock the app'
}

Object.keys(en.articles).forEach((id) => {
  const art = en.articles[id]
  if (!art.sections) return
  art.sections.forEach((sec) => {
    if (enSectionTitles[sec.title]) sec.title = enSectionTitles[sec.title]
    sec.content.forEach((block) => {
      if (block.type === 'tip' && block.text) {
        block.text = block.text
          .replace('网络不稳定时建议使用语音通话，或更换更稳定的网络环境。', 'On unstable networks, prefer voice calls or switch to a better connection.')
          .replace('超过可撤回时间后，选项将不可用。', 'Recall is only available for a limited time after sending.')
          .replace('窗口顶部可拖动移动；右上角为最小化、最大化、关闭。', 'Drag the top bar to move the window; use the top-right buttons to minimize, maximize, or close.')
      }
      if (block.type === 'p' && block.text) {
        block.text = block.text
          .replace('会。使用同一账号登录后，聊天记录、好友列表和大部分设置会保存在服务器，换电脑登录仍可查看历史消息。', 'Yes. Chat history, contacts, and most settings are stored on the server when you sign in with the same account.')
          .replace('同步速度取决于网络；较早的消息可能需要滚动加载。', 'Sync speed depends on your network; older messages may load as you scroll.')
      }
      if (block.type === 'faq' && block.items) {
        block.items.forEach((item) => {
          if (item.q === '下一步怎么办？') {
            item.q = 'What next?'
            item.a = 'See “How to send feedback” to contact us with details.'
          }
        })
      }
    })
  })
})

function emit(locale, data) {
  const body =
    '/**\n * 作者：yangleduo\n * @generated by scripts/build-catalog.mjs\n */\n' +
    '(function () {\n  window.__HELP_DATA__ = window.__HELP_DATA__ || {}\n  window.__HELP_DATA__[\'' +
    locale +
    "'] = " +
    JSON.stringify(data, null, 2)
      .replace(/"([^"]+)":/g, '$1:')
      .replace(/"/g, "'") +
    '\n})()\n'
  // Fix: JSON.stringify with single quote replace breaks content. Use proper JS stringify instead.
  const js =
    '/**\n * 作者：yangleduo\n * @generated by scripts/build-catalog.mjs\n */\n(function () {\n  window.__HELP_DATA__ = window.__HELP_DATA__ || {}\n  window.__HELP_DATA__[\'' +
    locale +
    '\'] = ' +
    JSON.stringify(data, null, 2) +
    '\n})()\n'
  fs.writeFileSync(path.join(outDir, 'catalog.' + locale + '.js'), js, 'utf8')
}

emit('zh-CN', zh)
emit('en-US', en)
console.log('Wrote catalog.zh-CN.js and catalog.en-US.js')
