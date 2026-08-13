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
      comingSoon: true,
      releases: [],
    },
    macos: {
      icon: "assets/icon-macos.svg",
      name: { zh: "macOS", en: "macOS" },
      comingSoon: true,
      releases: [],
    },
    windows: {
      icon: "assets/icon-windows.svg",
      name: { zh: "Windows", en: "Windows" },
      versionLabel: { zh: "v1.0.0 · x64 · 安装包", en: "v1.0.0 · x64 · Installer" },
      download: {
        url: "https://yangleduo1.oss-cn-beijing.aliyuncs.com/releases/2026/08/12/LinkX-Installer-1.0.0.exe",
        file: "LinkX-Installer-1.0.0.exe",
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
                  "客户端 Design Token（--lx-*）与 ui-components.css",
                  "公共 UI 组件：LxButton、LxIconButton、LxGroupCard",
                  "样式迁移辅助脚本",
                  "服务端消息落库加密（可选，AES-256-GCM）",
                ],
                en: [
                  "Client Design Tokens (--lx-*) and ui-components.css",
                  "Shared UI components: LxButton, LxIconButton, LxGroupCard",
                  "Style migration helper scripts",
                  "Optional server-side message content encryption (AES-256-GCM)",
                ],
              },
            },
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: [
                  "全站 UI 迁移至统一 Design Token 体系",
                  "窗控与状态栏统一 .lx-win-caption-btn 交互",
                  "ui-components.css 改由 main.ts 显式引入",
                  "README / CONTRIBUTING 文档对齐",
                ],
                en: [
                  "Site-wide UI migrated to unified Design Token system",
                  "Window controls unified under .lx-win-caption-btn",
                  "ui-components.css explicitly imported in main.ts",
                  "README and CONTRIBUTING documentation aligned",
                ],
              },
            },
          ],
        },
        {
          version: "1.0.0",
          date: "2026-08-12",
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
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: [
                  "Electron 窗控交互优化",
                ],
                en: [
                  "Electron window control interaction improvements",
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
