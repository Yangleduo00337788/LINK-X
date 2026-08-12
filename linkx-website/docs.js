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
