(function () {
  const links = Array.from(document.querySelectorAll(".docs-sidebar__link"));
  const sections = links
    .map((link) => {
      const id = link.getAttribute("href")?.slice(1);
      return id ? document.getElementById(id) : null;
    })
    .filter(Boolean);

  function setActiveLink(id) {
    links.forEach((link) => {
      link.classList.toggle("is-active", link.getAttribute("href") === `#${id}`);
    });
  }

  function onScroll() {
    const offset = 120;
    let current = sections[0]?.id;
    for (const section of sections) {
      if (section.getBoundingClientRect().top - offset <= 0) {
        current = section.id;
      }
    }
    if (current) setActiveLink(current);
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  const lang = new URLSearchParams(window.location.search).get("lang") === "en" ? "en" : "zh";
  const i18n = {
    zh: {
      navProduct: "产品",
      navDocs: "文档",
      navChangelog: "版本日志",
      navJoinUs: "加入我们",
      docsTitle: "LinkX 文档",
      docsSubtitle: "企业级即时通讯与协同平台完整指南，涵盖产品介绍、功能说明、快速上手、技术架构与常见问题。",
      navGroupStart: "入门",
      navOverview: "产品概述",
      navQuickstart: "快速开始",
      navGroupFeatures: "功能",
      navMessaging: "即时消息",
      navPush: "实时推送",
      navAv: "音视频会议",
      navFiles: "文件与网盘",
      navAdmin: "管理运营",
      navLinkmate: "灵伴 Agent",
      navGroupTech: "技术",
      navArchitecture: "技术架构",
      navMessageEncrypt: "消息落库加密",
      navUpdates: "版本与更新",
      navDeploy: "部署说明",
      navFaq: "常见问题",
      footerHome: "返回首页",
    },
    en: {
      navProduct: "Product",
      navDocs: "Docs",
      navChangelog: "Changelog",
      navJoinUs: "Join Us",
      docsTitle: "LinkX Documentation",
      docsSubtitle:
        "Complete guide to the enterprise IM platform — product overview, features, quick start, architecture, and FAQ.",
      navGroupStart: "Getting Started",
      navOverview: "Overview",
      navQuickstart: "Quick Start",
      navGroupFeatures: "Features",
      navMessaging: "Messaging",
      navPush: "Real-Time Push",
      navAv: "Audio & Video",
      navFiles: "Files & Drive",
      navAdmin: "Admin",
      navLinkmate: "LinkMate Agent",
      navGroupTech: "Technical",
      navArchitecture: "Architecture",
      navMessageEncrypt: "Message Encryption",
      navUpdates: "Updates",
      navDeploy: "Deployment",
      navFaq: "FAQ",
      footerHome: "Back to Home",
    },
  };

  document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
  const dict = i18n[lang];
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const text = dict[el.dataset.i18n];
    if (text) el.textContent = text;
  });

  const contentI18n = {
    zh: {
      secOverviewP1:
        "LinkX 是一套前后端分离的企业级即时通讯（IM）解决方案，由桌面客户端、运营管理后台与单体后端服务组成，适用于团队内部沟通、协同办公与后台运营场景。",
      secOverviewP2:
        "当前已发布 Windows 桌面端（v1.0.1）。下一版 1.1.0 计划交付 Linux 桌面端与 Android 移动端；再下一版 1.2.0 将聚焦灵伴知识库与 Agent 策略、本地搜索与消息同步优化、短视频推荐与运营后台。macOS 与 iOS 仍在路线图中。",
      secArchL5: "可选消息落库加密（AES-256-GCM，默认关闭，非端到端加密）",
      secMessageEncryptTitle: "消息落库加密",
      secMessageEncryptP1:
        "LinkX 服务端支持将 IM 消息等内容在写入 MySQL 前使用 AES-256-GCM 加密存储。该能力默认关闭，对客户端透明：客户端仍通过 HTTPS / WSS 传输，无需改动。",
      secMessageEncryptH1: "加密范围",
      secMessageEncryptL1: "单聊 / 群聊消息正文与引用内容（im_message）",
      secMessageEncryptL2: "朋友圈动态正文与位置、评论内容（moments_*）",
      secMessageEncryptH2: "与端到端加密（E2EE）的区别",
      secMessageEncryptP2:
        "落库加密由服务端持有密钥并在应用层加解密，属于存储层保护，不是端到端加密。管理端的敏感词过滤、内容审核、举报与审计等能力在开启后仍可正常工作。",
      calloutEncryptTitle: "部署者须知",
      calloutEncryptBody:
        "密钥（MESSAGE_KEK）须与 JWT_SECRET 独立配置并离线备份；丢失密钥将导致历史密文无法恢复。完整环境变量与 KEK 轮换说明见仓库 README「8.4 消息落库加密」。",
      secMessageEncryptH3: "如何开启（自托管）",
      secMessageEncryptP3: "在 linkx-server/.env.prod 或 .env.local 中配置：",
      secMessageEncryptP4:
        "重启后端后，Snail Job 会分批将历史明文转为密文；KEK 轮换可通过 MESSAGE_KEK_LEGACY_MAP 保留旧密钥用于解密。",
      faqQ5: "消息在数据库里是明文吗？",
      faqA5:
        "默认情况下消息以应用层可读形式落库；自托管部署者可选择开启 AES-256-GCM 落库加密（见上文「消息落库加密」）。无论是否开启，传输层均使用 HTTPS / WSS；落库加密不是端到端加密，服务端在业务需要时仍可解密内容用于审核与审计。",
      secLinkmateTitle: "灵伴 Agent",
      secLinkmateP1:
        "LinkX 1.0.1 起，客户端内置「灵伴」AI 助手，并支持 Agent 代操模式：在 LLM 返回函数调用时，由客户端在本地执行导航、打开会话、发送消息等操作（需用户授权与全局开关开启）。",
      secLinkmateH1: "能力范围",
      secLinkmateL1: "会话与导航：切换导航、打开指定聊天、搜索联系人等。",
      secLinkmateL2: "消息代发：在已确认上下文中代为输入并发送消息（受权限与风控约束）。",
      secLinkmateL3: "设置与偏好：读取或调整部分客户端设置（如通知开关）。",
      secLinkmateH2: "管理端策略",
      secLinkmateP2:
        "运营人员可在管理端配置灵伴 Agent 全局开关、群聊 AI 默认接入策略，并在「系统设置 → 灵伴」中统一管理。企业可按需关闭代操能力，仅保留对话问答。",
      calloutLinkmateTitle: "安全提示",
      calloutLinkmateBody:
        "Agent 代操在客户端沙箱内执行，不会直接暴露服务端密钥；敏感操作仍需用户确认。生产环境请结合 RBAC 与风控规则审慎开放。",
      secUpdatesTitle: "版本与更新",
      secUpdatesP1:
        "LinkX 桌面客户端支持启动时自动检查更新。版本信息与更新说明由管理端「版本发布」维护，客户端通过 GET /app/version 拉取，无需在客户端硬编码文案。",
      secUpdatesH1: "管理端发布流程",
      secUpdatesL1: "在管理端「版本发布」创建草稿，填写版本号、渠道、平台与更新说明（releaseNotes）。",
      secUpdatesL2: "上传 Windows 安装包（或填写 MinIO / CDN 下载地址与 SHA-256）。",
      secUpdatesL3: "发布后立即对客户端生效；旧版同平台记录自动归档。",
      secUpdatesL3b: "官网下载：配置 site-config.js 的 apiBaseUrl 后，首页与版本日志通过 GET /app/version 获取 R2 公网直链（可在 installerDirectUrl 配置兜底地址）。",
      secUpdatesH2: "客户端行为",
      secUpdatesL4: "有新版本：Electron 在后台静默下载安装包；可选更新下载完成后提示「立即安装」，强制更新则自动静默安装并重启。",
      secUpdatesL5: "已是最新：若当前版本有发布说明且用户未读过，展示「本次更新」弹窗（currentReleaseNotes）。",
      secUpdatesL6: "手动检查：设置 → 关于 →「检查更新」，或侧栏更多菜单中的检查更新入口。",
      secUpdatesH3: "自托管发布脚本",
      secUpdatesP2:
        "仓库提供 linkx-client/scripts/publish-release.mjs，可在打包后自动上传安装包并调用管理端 API 创建/发布版本记录。详见仓库 README「9.2 桌面客户端」。",
      faqQ6: "客户端如何获取新版本？",
      faqA6:
        "桌面端启动时会自动请求 /app/version 检查更新并后台下载；也可在「设置 → 关于」手动检查。更新说明由管理端版本发布填写，官网 changelog 与客户端弹窗内容应保持一致。",
    },
    en: {
      secOverviewP1:
        "LinkX is an enterprise instant messaging (IM) stack with a separated frontend and backend: desktop client, admin console, and a monolithic API service for team communication, collaboration, and operations.",
      secOverviewP2:
        "Windows desktop (v1.0.1) is available today. v1.1.0 will ship Linux desktop and Android mobile clients; v1.2.0 will focus on LinkMate knowledge base & agent policies, local search & message sync, and short-video recommendations with an admin ops console. macOS and iOS remain on the roadmap.",
      secArchL5: "Optional at-rest message encryption (AES-256-GCM, off by default; not E2EE)",
      secMessageEncryptTitle: "Message At-Rest Encryption",
      secMessageEncryptP1:
        "The LinkX server can encrypt IM message content with AES-256-GCM before writing to MySQL. This is off by default and transparent to clients—apps still use HTTPS / WSS with no client changes.",
      secMessageEncryptH1: "Scope",
      secMessageEncryptL1: "Chat message body and quote text (im_message)",
      secMessageEncryptL2: "Moments posts, locations, and comments (moments_*)",
      secMessageEncryptH2: "Not the same as end-to-end encryption (E2EE)",
      secMessageEncryptP2:
        "At-rest encryption uses server-held keys and application-layer crypto—it protects storage, not E2EE. Admin features such as sensitive-word filtering, review, reports, and audit still work when enabled.",
      calloutEncryptTitle: "For self-hosted operators",
      calloutEncryptBody:
        "MESSAGE_KEK must be separate from JWT_SECRET and backed up offline. Losing the key makes historical ciphertext unrecoverable. See repository README section 8.4 for full variables and KEK rotation.",
      secMessageEncryptH3: "How to enable (self-hosted)",
      secMessageEncryptP3: "In linkx-server/.env.prod or .env.local:",
      secMessageEncryptP4:
        "After restart, Snail Job re-encrypts legacy plaintext in batches. Use MESSAGE_KEK_LEGACY_MAP during KEK rotation to keep old keys for decryption.",
      faqQ5: "Are messages stored in plaintext in the database?",
      faqA5:
        "By default, message content is stored in a form the server can read at the application layer. Self-hosted operators may enable AES-256-GCM at-rest encryption (see Message At-Rest Encryption above). Transport always uses HTTPS / WSS. At-rest encryption is not E2EE—the server can still decrypt for review and audit when required.",
      secLinkmateTitle: "LinkMate Agent",
      secLinkmateP1:
        "Since LinkX 1.0.1, the client includes LinkMate AI with an agent hands-on mode: when the LLM returns tool calls, the client executes navigation, opening chats, sending messages, and more locally (subject to user consent and the global toggle).",
      secLinkmateH1: "Capabilities",
      secLinkmateL1: "Sessions & navigation: switch views, open chats, search contacts.",
      secLinkmateL2: "Message actions: draft and send messages in confirmed context (subject to permissions and risk controls).",
      secLinkmateL3: "Settings: read or adjust selected client preferences such as notification toggles.",
      secLinkmateH2: "Admin policies",
      secLinkmateP2:
        "Operators configure the LinkMate Agent global toggle and default group AI policy under Admin → System → LinkMate. Enterprises may disable hands-on mode and keep Q&A only.",
      calloutLinkmateTitle: "Security note",
      calloutLinkmateBody:
        "Agent actions run in the client sandbox without exposing server secrets; sensitive steps still require user confirmation. Combine with RBAC and risk rules in production.",
      secUpdatesTitle: "Versioning & Updates",
      secUpdatesP1:
        "The LinkX desktop client checks for updates on startup. Release metadata and notes are maintained in Admin → Version releases and fetched via GET /app/version—no hard-coded copy in the client.",
      secUpdatesH1: "Admin release workflow",
      secUpdatesL1: "Create a draft under Version releases with version, channel, platform, and release notes.",
      secUpdatesL2: "Upload the Windows installer (or set MinIO / CDN URL and SHA-256).",
      secUpdatesL3: "Publishing takes effect immediately; previous releases for the same platform are archived.",
      secUpdatesL3b:
        "Website download: set apiBaseUrl in site-config.js; the homepage and changelog use GET /app/version for the R2 public installer URL (optional installerDirectUrl fallback).",
      secUpdatesH2: "Client behavior",
      secUpdatesL4: "When an update exists: Electron downloads silently in the background; optional updates prompt to install, forced updates install silently and restart.",
      secUpdatesL5: "When up to date: show the What's New dialog if currentReleaseNotes exist and the user has not dismissed it.",
      secUpdatesL6: "Manual check: Settings → About → Check for updates, or the sidebar more menu.",
      secUpdatesH3: "Self-hosted publish script",
      secUpdatesP2:
        "Use linkx-client/scripts/publish-release.mjs after building to upload the installer and publish via the admin API. See repository README section 9.2.",
      faqQ6: "How does the client get new versions?",
      faqA6:
        "On startup the desktop app calls /app/version and downloads updates in the background; you can also check manually under Settings → About. Keep admin release notes aligned with the website changelog and in-app dialogs.",
    },
  };

  const contentDict = contentI18n[lang];
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.dataset.i18n;
    const text = contentDict[key];
    if (text) el.textContent = text;
  });

  document.querySelectorAll(".hero__lang-option").forEach((opt) => {
    opt.classList.toggle("is-active", opt.dataset.lang === lang);
    opt.addEventListener("click", () => {
      const nextLang = opt.dataset.lang;
      const url = new URL(window.location.href);
      if (nextLang === "en") url.searchParams.set("lang", "en");
      else url.searchParams.delete("lang");
      window.location.href = url.toString();
    });
  });

  const navDocs = document.getElementById("navDocs");
  if (navDocs) {
    navDocs.setAttribute("href", "docs.html" + (lang === "en" ? "?lang=en" : ""));
  }
  const navChangelog = document.getElementById("navChangelog");
  if (navChangelog) {
    navChangelog.setAttribute("href", "changelog.html" + (lang === "en" ? "?lang=en" : ""));
  }
  const navJoinUs = document.getElementById("navJoinUs");
  if (navJoinUs) {
    navJoinUs.setAttribute("href", "join.html" + (lang === "en" ? "?lang=en" : ""));
  }
})();
