window.ChangelogData = {
  highlights: [
    {
      image: "assets/changelog-illust-1.png",
      title: { zh: "即时消息", en: "Instant Messaging" },
      body: {
        zh: "单聊与群聊齐备，支持文本、图片、文件与语音，引用、编辑、撤回、转发一应俱全。",
        en: "One-on-one and group chats with text, images, files, and voice — plus quote, edit, recall, and forward.",
      },
      reverse: false,
    },
    {
      image: "assets/changelog-illust-2.png",
      title: { zh: "实时推送", en: "Real-Time Push" },
      body: {
        zh: "HTTP 拉取历史消息，WebSocket 毫秒级推送，在线状态与消息送达实时同步。",
        en: "HTTP for history, WebSocket for millisecond delivery — online status and message sync stay in step.",
      },
      reverse: true,
    },
    {
      image: "assets/changelog-illust-3.png",
      title: { zh: "音视频会议", en: "Audio & Video" },
      body: {
        zh: "WebRTC 单聊通话与多人 Mesh 会议，通话信令经 WebSocket 实时下发，协作无延迟。",
        en: "WebRTC one-on-one calls and multi-party Mesh conferences with signaling over WebSocket.",
      },
      reverse: false,
    },
    {
      image: "assets/changelog-illust-4.png",
      title: { zh: "文件与网盘", en: "Files & Cloud Drive" },
      body: {
        zh: "聊天文件、群文件/群相册与个人网盘统一接入 MinIO，团队资料集中沉淀、随时取用。",
        en: "Chat files, group albums, and personal cloud drive powered by MinIO — all assets in one place.",
      },
      reverse: true,
    },
    {
      image: "assets/changelog-illust-5.png",
      title: { zh: "管理运营", en: "Admin & Operations" },
      body: {
        zh: "用户权限、内容审核、风控策略与统计大屏，后台运营一站搞定。",
        en: "User permissions, content review, risk policies, and analytics dashboards — operations in one console.",
      },
      reverse: false,
    },
  ],
  platforms: {
    linux: {
      icon: "assets/icon-linux.svg",
      name: { zh: "Linux", en: "Linux" },
      versionLabel: { zh: "v1.0.0 · x64 · AppImage", en: "v1.0.0 · x64 · AppImage" },
      download: {
        url: "https://gitee.com/yangleduo7788/link-x/releases/download/v1.0.0/LinkX-1.0.0-linux-x64.AppImage",
        file: "LinkX-1.0.0-linux-x64.AppImage",
      },
      releases: [
        {
          version: "Unreleased",
          date: null,
          badge: { zh: "开发中", en: "In Progress" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "客户端 Design Token 体系（--lx-*）与 ui-components.css 公共样式",
                  "公共 UI 组件：LxButton、LxIconButton、LxGroupCard",
                  "样式迁移辅助脚本 migrate-*.mjs",
                ],
                en: [
                  "Client Design Token system (--lx-*) and shared ui-components.css",
                  "Shared UI components: LxButton, LxIconButton, LxGroupCard",
                  "Style migration helper scripts migrate-*.mjs",
                ],
              },
            },
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: [
                  "linkx-client 全站迁移至统一按钮、间距、圆角与场景色 Token",
                  "窗控与状态栏置顶统一 .lx-win-caption-btn 圆角块悬停交互",
                  "README 与目录结构、客户端 UI 开发规范对齐",
                ],
                en: [
                  "linkx-client migrated to unified button, spacing, radius, and scene color tokens",
                  "Window controls and status bar use unified .lx-win-caption-btn hover interaction",
                  "README and project structure aligned with client UI development guidelines",
                ],
              },
            },
          ],
        },
        {
          version: "1.0.0",
          date: "2026-08-09",
          badge: { zh: "稳定版", en: "Stable" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "Electron 跨平台桌面客户端，支持 Web / 桌面双模式开发",
                  "单聊 / 群聊、多媒体消息、引用 / 编辑 / 撤回 / 转发",
                  "消息状态机、已读回执、在线状态与正在输入",
                  "朋友圈、日历、笔记编辑器（Markdown 块编辑）",
                  "个人网盘、收藏、锁屏、系统浏览器打开外链",
                  "WebRTC 单聊音视频与多人 Mesh 会议",
                  "Linux x64 AppImage 安装包发布",
                ],
                en: [
                  "Electron cross-platform desktop client with Web / desktop dev modes",
                  "One-on-one and group chat, rich media, quote / edit / recall / forward",
                  "Message state machine, read receipts, online status, typing indicators",
                  "Moments, calendar, note editor (Markdown blocks)",
                  "Personal cloud drive, favorites, lock screen, open links in system browser",
                  "WebRTC one-on-one audio/video and multi-party Mesh conferences",
                  "Linux x64 AppImage release",
                ],
              },
            },
          ],
        },
      ],
    },
    macos: {
      icon: "assets/icon-macos.svg",
      name: { zh: "macOS", en: "macOS" },
      versionLabel: { zh: "v1.0.0 · Apple Silicon / Intel", en: "v1.0.0 · Apple Silicon / Intel" },
      download: {
        url: "https://gitee.com/yangleduo7788/link-x/releases/download/v1.0.0/LinkX-1.0.0-mac-universal.dmg",
        file: "LinkX-1.0.0-mac-universal.dmg",
      },
      releases: [
        {
          version: "Unreleased",
          date: null,
          badge: { zh: "开发中", en: "In Progress" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "客户端 Design Token 体系与公共 UI 组件库",
                  "统一窗控与状态栏交互样式",
                ],
                en: [
                  "Client Design Token system and shared UI component library",
                  "Unified window controls and status bar interaction styles",
                ],
              },
            },
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: ["全站 UI 迁移至统一 Design Token", "文档与 MIT 许可证说明对齐"],
                en: ["Site-wide UI migrated to unified Design Tokens", "Documentation and MIT license notes aligned"],
              },
            },
          ],
        },
        {
          version: "1.0.0",
          date: "2026-08-09",
          badge: { zh: "稳定版", en: "Stable" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "macOS 通用安装包（Apple Silicon / Intel）",
                  "单聊 / 群聊与完整 IM 核心链路",
                  "WebRTC 音视频通话与多人会议",
                  "文件网盘、日历、笔记等协作工具",
                ],
                en: [
                  "macOS universal installer (Apple Silicon / Intel)",
                  "One-on-one / group chat with full IM core flows",
                  "WebRTC audio/video calls and multi-party meetings",
                  "Cloud drive, calendar, notes, and collaboration tools",
                ],
              },
            },
          ],
        },
      ],
    },
    windows: {
      icon: "assets/icon-windows.svg",
      name: { zh: "Windows", en: "Windows" },
      versionLabel: { zh: "v1.0.0 · x64 · 安装包", en: "v1.0.0 · x64 · Installer" },
      download: {
        url: "https://gitee.com/yangleduo7788/link-x/releases/download/v1.0.0/LinkX-1.0.0-win-x64.exe",
        file: "LinkX-1.0.0-win-x64.exe",
      },
      releases: [
        {
          version: "Unreleased",
          date: null,
          badge: { zh: "开发中", en: "In Progress" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: ["客户端 Design Token 与公共组件库", "样式迁移辅助脚本"],
                en: ["Client Design Tokens and shared component library", "Style migration helper scripts"],
              },
            },
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: ["全站 UI 统一至 Design Token 体系", "Electron 窗控交互优化"],
                en: ["Site-wide UI unified under Design Token system", "Electron window control interaction improvements"],
              },
            },
          ],
        },
        {
          version: "1.0.0",
          date: "2026-08-09",
          badge: { zh: "稳定版", en: "Stable" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "Windows x64 安装包（LinkX-Installer）",
                  "单聊 / 群聊、消息状态与已读回执",
                  "WebRTC 音视频与多人会议",
                  "通知卡片流、锁屏与系统浏览器外链",
                ],
                en: [
                  "Windows x64 installer (LinkX-Installer)",
                  "One-on-one / group chat, message status and read receipts",
                  "WebRTC audio/video and multi-party meetings",
                  "Notification feed, lock screen, and system browser for external links",
                ],
              },
            },
          ],
        },
      ],
    },
    android: {
      icon: "assets/icon-android.svg",
      name: { zh: "Android", en: "Android" },
      comingSoon: true,
      releases: [],
    },
    ios: {
      icon: "assets/icon-ios.svg",
      name: { zh: "iOS", en: "iOS" },
      comingSoon: true,
      releases: [],
    },
  },
};
