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
      navGroupTech: "技术",
      navArchitecture: "技术架构",
      navMessageEncrypt: "消息落库加密",
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
      navGroupTech: "Technical",
      navArchitecture: "Architecture",
      navMessageEncrypt: "Message Encryption",
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
    },
    en: {
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
