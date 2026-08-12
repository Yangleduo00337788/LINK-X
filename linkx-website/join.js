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
      joinTitle: "加入我们",
      joinSubtitle:
        "LinkX 是开源的企业级即时通讯项目，欢迎开发者、设计师与产品爱好者一起共建。无论你是提交代码、完善文档还是反馈问题，都是对社区的重要贡献。",
      navWelcome: "欢迎加入",
      navWays: "参与方式",
      navContribute: "代码贡献",
      navGroupMore: "更多",
      navIssues: "问题反馈",
      navContact: "联系我们",
      secWelcomeTitle: "欢迎加入 LinkX 社区",
      secWelcomeP1:
        "LinkX 由 Yangleduo 发起并维护，采用 MIT 开源协议。项目涵盖桌面客户端、运营管理后台与 Spring Boot 后端，适合学习现代 IM 架构、全栈开发与团队协作实践。",
      cardOpenTitle: "开源开放",
      cardOpenBody: "源码托管于 Gitee，欢迎 Fork、Star 与 Pull Request。",
      cardLearnTitle: "技术成长",
      cardLearnBody: "涵盖 Vue 3、Electron、Netty WebSocket、WebRTC 等主流技术栈。",
      cardTeamTitle: "协作共建",
      cardTeamBody: "Issue 讨论、代码评审与文档共建，与社区一起成长。",
      cardImpactTitle: "真实场景",
      cardImpactBody: "面向企业 IM 真实需求，功能完整、可本地部署联调。",
      secWaysTitle: "你可以这样参与",
      secWaysL1: "代码开发：修复 Bug、实现新功能、优化性能与体验。",
      secWaysL2: "文档完善：补充使用说明、架构文档、部署指南与 FAQ。",
      secWaysL3: "测试反馈：在不同平台安装体验，提交复现步骤与改进建议。",
      secWaysL4: "设计贡献：UI/UX 优化、图标与视觉资源、交互原型建议。",
      secWaysL5: "社区运营：回答问题、整理 Issue、推广项目与撰写技术文章。",
      secWaysP1: "没有编程经验也可以参与——清晰的 Bug 报告、文档纠错和功能建议同样非常有价值。",
      secContributeTitle: "代码贡献流程",
      secContributeL1: "访问 Gitee 仓库，Fork 项目到你的账号。",
      secContributeL2: "克隆 Fork 后的仓库到本地，创建功能分支（如 feature/xxx 或 fix/xxx）。",
      secContributeL3: "按照项目规范完成开发与自测，确保现有功能不受影响。",
      secContributeL4: "提交 Pull Request，描述变更内容与测试情况，等待维护者 Review。",
      secContributeL5: "根据 Review 意见修改后合并，你的贡献将出现在 CHANGELOG 与贡献者列表中。",
      calloutContributeTitle: "提交规范",
      calloutContributeBody:
        "Commit 信息请简洁明确；PR 尽量保持单一职责、变更范围可控。涉及数据库变更请通过 Flyway 脚本管理，勿手改生产库。",
      secContributeH1: "子项目说明",
      thProject: "目录",
      thStack: "技术栈",
      thEntry: "本地启动",
      secIssuesTitle: "问题反馈",
      secIssuesP1: "遇到 Bug 或有功能建议，请通过 Gitee Issues 提交，便于跟踪与协作。",
      secIssuesL1: "描述问题现象与期望行为，附上截图或录屏（如适用）。",
      secIssuesL2: "说明复现步骤、操作系统与 LinkX 版本号。",
      secIssuesL3: "安全问题请勿公开 Issue，请通过下方联系方式私下反馈。",
      secIssuesLink: "前往 Gitee Issues →",
      secContactTitle: "联系我们",
      secContactP1: "如有合作意向、深度参与或一般咨询，可通过以下方式联系：",
      secContactL1: "项目仓库：",
      secContactL2: "联系开发者 QQ：",
      secContactL3: "客户端内：「设置」→「关于」→ 问题反馈",
    },
    en: {
      navProduct: "Product",
      navDocs: "Docs",
      navChangelog: "Changelog",
      navJoinUs: "Join Us",
      joinTitle: "Join Us",
      joinSubtitle:
        "LinkX is an open-source enterprise IM project. Developers, designers, and enthusiasts are welcome to build together — code, docs, and feedback all matter.",
      navWelcome: "Welcome",
      navWays: "Ways to Help",
      navContribute: "Contributing",
      navGroupMore: "More",
      navIssues: "Issues",
      navContact: "Contact",
      secWelcomeTitle: "Welcome to the LinkX Community",
      secWelcomeP1:
        "LinkX is maintained by Yangleduo under the MIT license. It includes a desktop client, admin console, and Spring Boot backend — ideal for learning modern IM architecture and full-stack development.",
      cardOpenTitle: "Open Source",
      cardOpenBody: "Hosted on Gitee — Fork, Star, and submit Pull Requests.",
      cardLearnTitle: "Learn & Grow",
      cardLearnBody: "Vue 3, Electron, Netty WebSocket, WebRTC, and more.",
      cardTeamTitle: "Collaborate",
      cardTeamBody: "Issue discussions, code review, and docs — grow with the community.",
      cardImpactTitle: "Real-World IM",
      cardImpactBody: "Enterprise-grade features you can deploy and test locally.",
      secWaysTitle: "How You Can Help",
      secWaysL1: "Code: fix bugs, add features, improve performance and UX.",
      secWaysL2: "Docs: guides, architecture notes, deployment, and FAQ.",
      secWaysL3: "Testing: try builds on your platform and report steps to reproduce.",
      secWaysL4: "Design: UI/UX, icons, visuals, and interaction ideas.",
      secWaysL5: "Community: answer questions, triage issues, write articles.",
      secWaysP1: "No coding required — clear bug reports, doc fixes, and ideas are valuable too.",
      secContributeTitle: "Contribution Workflow",
      secContributeL1: "Fork the Gitee repository to your account.",
      secContributeL2: "Clone your fork, create a branch (e.g. feature/xxx or fix/xxx).",
      secContributeL3: "Develop and self-test following project conventions.",
      secContributeL4: "Open a Pull Request with a clear description and test notes.",
      secContributeL5: "Address review feedback; your work lands in CHANGELOG and credits.",
      calloutContributeTitle: "Guidelines",
      calloutContributeBody:
        "Keep commits focused; PRs should do one thing. Database changes go through Flyway migrations — never edit production DB by hand.",
      secContributeH1: "Subprojects",
      thProject: "Directory",
      thStack: "Stack",
      thEntry: "Local dev",
      secIssuesTitle: "Report Issues",
      secIssuesP1: "File bugs and feature requests on Gitee Issues for tracking.",
      secIssuesL1: "Describe actual vs expected behavior; attach screenshots if helpful.",
      secIssuesL2: "Include repro steps, OS, and LinkX version.",
      secIssuesL3: "Do not post security issues publicly — contact us privately below.",
      secIssuesLink: "Go to Gitee Issues →",
      secContactTitle: "Contact",
      secContactP1: "For partnership, deeper involvement, or general questions:",
      secContactL1: "Repository:",
      secContactL2: "Developer QQ:",
      secContactL3: 'In app: Settings → About → Feedback',
    },
  };

  document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
  const dict = i18n[lang];

  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const text = dict[el.dataset.i18n];
    if (text !== undefined) {
      if (el.tagName === "A" && el.getAttribute("href")) return;
      el.textContent = text;
    }
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

  function langHref(path) {
    return path + (lang === "en" ? "?lang=en" : "");
  }

  ["navDocs", "navChangelog", "navJoinUs"].forEach((id) => {
    const el = document.getElementById(id);
    if (!el) return;
    const paths = { navDocs: "docs.html", navChangelog: "changelog.html", navJoinUs: "join.html" };
    el.setAttribute("href", langHref(paths[id]));
  });
})();
