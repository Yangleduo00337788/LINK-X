window.ChangelogData = {
  /** 与根目录 CHANGELOG.md「版本规划」保持一致 */
  roadmap: [
    {
      version: "1.1.0",
      status: { zh: "下一版", en: "Next" },
      summary: {
        zh: "Linux 桌面端、Android 移动端",
        en: "Linux desktop and Android mobile clients",
      },
      sections: [
        {
          title: { zh: "规划", en: "Planned" },
          items: {
            zh: [
              "Linux 安装包构建与发布（AppImage / deb 等）",
              "官网与管理端版本记录支持 Linux 平台下载与自动更新",
              "Android 客户端 IM 核心链路（登录、单聊 / 群聊、推送、多媒体）",
            ],
            en: [
              "Linux installer builds and releases (AppImage / deb, etc.)",
              "Website and admin version records for Linux download and auto-update",
              "Android client IM essentials: login, chat, push, and rich media",
            ],
          },
        },
      ],
    },
    {
      version: "1.2.0",
      status: { zh: "再下一版", en: "Following" },
      summary: {
        zh: "灵伴知识库与 Agent 策略、本地搜索与消息同步、短视频推荐与运营",
        en: "LinkMate knowledge base & policies, local search & sync, short-video ops",
      },
      sections: [
        {
          title: { zh: "规划 — 灵伴 Agent", en: "Planned — LinkMate Agent" },
          items: {
            zh: [
              "企业知识库（RAG）与 @灵伴 问答",
              "Agent 写操作确认卡与管理端策略（模型、额度、工具白名单、审计）",
            ],
            en: [
              "Enterprise knowledge base (RAG) and @LinkMate Q&A",
              "Agent confirmation cards and admin policies (models, quotas, tools, audit)",
            ],
          },
        },
        {
          title: { zh: "规划 — IM 与短视频", en: "Planned — IM & Short Video" },
          items: {
            zh: [
              "本地聊天全文搜索",
              "消息同步与已读回执优化",
              "短视频推荐流与管理端运营后台",
            ],
            en: [
              "Local full-text chat search",
              "Message sync and read-receipt improvements",
              "Short-video feed recommendations and admin operations console",
            ],
          },
        },
      ],
    },
  ],
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
      releases: [
        {
          version: "1.1.0",
          date: "",
          badge: { zh: "计划中", en: "Planned" },
          sections: [
            {
              title: { zh: "规划", en: "Planned" },
              items: {
                zh: [
                  "Linux 安装包构建与发布",
                  "官网与管理端版本记录、自动更新",
                ],
                en: [
                  "Linux installer build and release",
                  "Website, admin version records, and auto-update",
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
      comingSoon: true,
      releases: [],
    },
    windows: {
      icon: "assets/icon-windows.svg",
      name: { zh: "Windows", en: "Windows" },
      versionLabel: { zh: "v1.0.1 · x64 · 安装包", en: "v1.0.1 · x64 · Installer" },
      releases: [
        {
          version: "1.0.1",
          date: "2026-08-29",
          badge: { zh: "最新", en: "Latest" },
          sections: [
            {
              title: { zh: "新增", en: "Added" },
              items: {
                zh: [
                  "灵伴 Agent 代操模式：LLM 函数调用在客户端执行导航、发消息等操作",
                  "灵伴 Agent 全局开关与群 AI 默认策略（管理端可配置）",
                  "启动时自动检查更新，桌面端后台静默下载安装包",
                  "管理端版本发布驱动「本次更新」弹窗（currentReleaseNotes）",
                  "客户端 Design Token 与公共 UI 组件（LxButton、LxIconButton、LxGroupCard）",
                  "服务端消息落库加密（AES-256-GCM，可选）",
                ],
                en: [
                  "LinkMate Agent hands-on mode: LLM tool calls for navigation, messaging, and more",
                  "LinkMate Agent global toggle and default group AI policy (admin configurable)",
                  "Startup update check with silent background download on desktop",
                  "Admin release notes drive the post-update What's New dialog (currentReleaseNotes)",
                  "Client Design Tokens and shared UI components (LxButton, LxIconButton, LxGroupCard)",
                  "Optional server-side message at-rest encryption (AES-256-GCM)",
                ],
              },
            },
            {
              title: { zh: "变更", en: "Changed" },
              items: {
                zh: [
                  "Electron 升级至 43，桌面端体积与性能优化",
                  "全站迁移至统一 Design Token 与窗控交互",
                  "Playwright E2E 与灵伴 Agent 单元测试补充",
                ],
                en: [
                  "Electron upgraded to 43 with smaller footprint and performance tweaks",
                  "App-wide migration to unified Design Tokens and window controls",
                  "Added Playwright E2E and LinkMate Agent unit tests",
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
                zh: ["Electron 窗控交互优化"],
                en: ["Electron window control interaction improvements"],
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
      releases: [
        {
          version: "1.1.0",
          date: "",
          badge: { zh: "计划中", en: "Planned" },
          sections: [
            {
              title: { zh: "规划", en: "Planned" },
              items: {
                zh: [
                  "Android 客户端 IM 核心链路",
                  "与服务端 REST + WebSocket 协议对齐",
                ],
                en: [
                  "Android client IM essentials",
                  "Aligned with server REST + WebSocket APIs",
                ],
              },
            },
          ],
        },
      ],
    },
    ios: {
      icon: "assets/icon-ios.svg",
      name: { zh: "iOS", en: "iOS" },
      comingSoon: true,
      releases: [],
    },
  },
};
