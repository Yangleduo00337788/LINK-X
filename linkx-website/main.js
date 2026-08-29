(function () {
  const hero = document.getElementById("hero");
  const canvas = document.getElementById("heroMask");
  if (!hero || !canvas) return;

  const canHover = window.matchMedia("(hover: hover)").matches;
  if (!canHover) return;

  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const MASK = "246, 241, 234";
  const R_START = 8;
  const R_END = 128;
  const R_VARY = 0.45;
  const LIFETIME = 520;
  const STAMP_STEP = 12;
  const MAX_STAMPS = 160;
  const DPR = Math.min(window.devicePixelRatio || 1, 2);

  let w = 0;
  let h = 0;

  function resize() {
    const rect = hero.getBoundingClientRect();
    w = rect.width;
    h = rect.height;
    canvas.width = Math.round(w * DPR);
    canvas.height = Math.round(h * DPR);
    canvas.style.width = w + "px";
    canvas.style.height = h + "px";
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "rgb(" + MASK + ")";
    ctx.fillRect(0, 0, w, h);
  }

  resize();
  window.addEventListener("resize", resize);

  const stamps = [];
  let lastX = null;
  let lastY = null;

  function addStamp(x, y) {
    if (stamps.length >= MAX_STAMPS) stamps.shift();
    stamps.push({
      x,
      y,
      born: performance.now(),
      seed: Math.random() * Math.PI * 2,
      rmax: R_END * (1 - R_VARY + Math.random() * R_VARY),
    });
  }

  function stampAlong(x, y) {
    if (lastX === null) {
      addStamp(x, y);
    } else {
      const dx = x - lastX;
      const dy = y - lastY;
      const dist = Math.hypot(dx, dy);
      const steps = Math.max(1, Math.ceil(dist / STAMP_STEP));
      for (let i = 1; i <= steps; i++) {
        addStamp(lastX + (dx * i) / steps, lastY + (dy * i) / steps);
      }
    }
    lastX = x;
    lastY = y;
  }

  function carveInk(x, y, r, alpha, seed) {
    const g = ctx.createRadialGradient(x, y, r * 0.25, x, y, r);
    g.addColorStop(0, "rgba(0, 0, 0, " + 0.95 * alpha + ")");
    g.addColorStop(0.55, "rgba(0, 0, 0, " + 0.88 * alpha + ")");
    g.addColorStop(1, "rgba(0, 0, 0, 0)");
    ctx.fillStyle = g;
    ctx.beginPath();
    const segs = 32;
    for (let i = 0; i <= segs; i++) {
      const a = (i / segs) * Math.PI * 2;
      const wob =
        0.78 +
        0.14 * Math.sin(a * 3 + seed) +
        0.08 * Math.sin(a * 7 + seed * 2.1) +
        0.05 * Math.sin(a * 13 + seed * 0.7);
      const rr = r * wob;
      const px = x + Math.cos(a) * rr;
      const py = y + Math.sin(a) * rr;
      if (i === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fill();
  }

  let running = false;

  function loop() {
    const now = performance.now();

    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "rgb(" + MASK + ")";
    ctx.fillRect(0, 0, w, h);

    ctx.globalCompositeOperation = "destination-out";
    for (let i = stamps.length - 1; i >= 0; i--) {
      const t = (now - stamps[i].born) / LIFETIME;
      if (t >= 1) {
        stamps.splice(i, 1);
        continue;
      }
      const ease = 1 - Math.pow(1 - t, 3);
      const r = R_START + (stamps[i].rmax - R_START) * ease;
      const alpha = 1 - t * t;
      carveInk(stamps[i].x, stamps[i].y, r, alpha, stamps[i].seed);
    }

    if (stamps.length) {
      requestAnimationFrame(loop);
    } else {
      running = false;
    }
  }

  function start() {
    if (!running) {
      running = true;
      requestAnimationFrame(loop);
    }
  }

  hero.addEventListener("mouseenter", function (e) {
    const rect = hero.getBoundingClientRect();
    lastX = e.clientX - rect.left;
    lastY = e.clientY - rect.top;
    stampAlong(lastX, lastY);
    start();
  });

  hero.addEventListener("mousemove", function (e) {
    const rect = hero.getBoundingClientRect();
    stampAlong(e.clientX - rect.left, e.clientY - rect.top);
    start();
  });

  hero.addEventListener("mouseleave", function () {
    lastX = null;
    lastY = null;
  });
})();

(function () {
  const switcher = document.querySelector(".platform-switch");
  const panel = document.getElementById("platformPanel");
  const logo = document.getElementById("platformLogo");
  const nameEl = document.getElementById("platformName");
  const versionEl = document.getElementById("platformVersion");
  const downloadEl = document.getElementById("platformDownload");
  const comingEl = document.getElementById("platformComing");
  if (!switcher || !panel || !logo || !nameEl || !versionEl || !downloadEl || !comingEl) return;

  const options = Array.from(switcher.querySelectorAll(".platform-switch__option"));

  const platforms = {
    linux: {
      icon: "assets/icon-linux.svg",
      name: { zh: "Linux", en: "Linux" },
      comingSoon: true,
    },
    macos: {
      icon: "assets/icon-macos.svg",
      name: { zh: "macOS", en: "macOS" },
      comingSoon: true,
    },
    windows: {
      icon: "assets/icon-windows.svg",
      name: { zh: "Windows", en: "Windows" },
      version: { zh: "v1.0.1 · x64 · 安装包", en: "v1.0.1 · x64 · Installer" },
    },
    android: {
      icon: "assets/icon-android.svg",
      name: { zh: "Android", en: "Android" },
      comingSoon: true,
    },
    ios: {
      icon: "assets/icon-ios.svg",
      name: { zh: "iOS", en: "iOS" },
      comingSoon: true,
    },
  };

  function getLang() {
    const active = document.querySelector(".hero__lang-option.is-active");
    return active?.dataset.lang === "en" ? "en" : "zh";
  }

  function getComingSoonText() {
    const el = document.querySelector("[data-i18n='comingSoon']");
    return el?.textContent?.trim() || "敬请期待";
  }

  function applyPlatform(platform) {
    const info = platforms[platform] || platforms.windows;
    const lang = getLang();
    logo.src = info.icon;
    nameEl.textContent = info.name[lang];

    if (info.comingSoon) {
      panel.classList.add("is-coming-soon");
      versionEl.textContent = getComingSoonText();
      comingEl.hidden = false;
    } else {
      panel.classList.remove("is-coming-soon");
      versionEl.textContent = info.version[lang];
      const installer =
        window.LinkXAppDownload && window.LinkXAppDownload.installerUrl
          ? window.LinkXAppDownload.installerUrl(platform === "windows" ? "windows" : "windows")
          : "";
      if (installer) {
        downloadEl.href = installer;
        downloadEl.removeAttribute("download");
      }
      comingEl.hidden = true;
    }

    options.forEach((opt) => {
      const active = opt.dataset.platform === platform;
      opt.classList.toggle("is-active", active);
      opt.setAttribute("aria-pressed", active ? "true" : "false");
    });
  }

  options.forEach((opt) => {
    opt.addEventListener("click", () => applyPlatform(opt.dataset.platform));
  });

  document.querySelectorAll(".hero__lang-option").forEach((opt) => {
    opt.addEventListener("click", () => {
      const active = switcher.querySelector(".platform-switch__option.is-active");
      if (active) applyPlatform(active.dataset.platform);
    });
  });

  applyPlatform("windows");
})();

(function () {
  const titles = document.querySelectorAll(".card__text h3");
  if (!titles.length || !("IntersectionObserver" in window)) return;

  const CHAR_DELAY = 130;

  titles.forEach((h3) => {
    const text = h3.textContent;
    h3.textContent = "";
    for (const c of text) {
      const span = document.createElement("span");
      span.className = "char";
      span.textContent = c;
      h3.appendChild(span);
    }
    const cursor = document.createElement("span");
    cursor.className = "cursor";
    cursor.setAttribute("aria-hidden", "true");
    h3.appendChild(cursor);
  });

  function animateType(h3) {
    if (h3.dataset.typed === "1") return;
    h3.dataset.typed = "1";
    h3.classList.add("is-active");
    const chars = h3.querySelectorAll(".char");
    chars.forEach((char, i) => {
      setTimeout(() => char.classList.add("is-typed"), i * CHAR_DELAY);
    });
    setTimeout(() => h3.classList.add("is-done"), chars.length * CHAR_DELAY + 120);
  }

  function snapShow(h3) {
    if (h3.dataset.typed === "1") return;
    h3.dataset.typed = "1";
    h3.classList.add("is-active", "is-done");
    h3.querySelectorAll(".char").forEach((c) => c.classList.add("is-typed"));
  }

  const obs = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) animateType(entry.target);
      });
    },
    { threshold: 0.25 }
  );
  titles.forEach((h3) => obs.observe(h3));

  window.addEventListener(
    "scroll",
    () => {
      const cut = window.innerHeight * 0.5;
      titles.forEach((h3) => {
        if (h3.dataset.typed === "1") return;
        if (h3.getBoundingClientRect().bottom < cut) snapShow(h3);
      });
    },
    { passive: true }
  );
})();

(function () {
  const sub = document.querySelector(".hero__subtitle");
  if (!sub) return;
  if (window.matchMedia("(max-width: 700px)").matches) return;
  const original = sub.textContent.trim();

  function type() {
    sub.style.whiteSpace = "nowrap";
    sub.textContent = original;
    const fullW = sub.getBoundingClientRect().width;
    if (fullW > 0) sub.style.width = Math.ceil(fullW) + "px";
    sub.textContent = "";
    sub.classList.add("is-typing");

    const chars = [];
    for (const ch of original) {
      const s = document.createElement("span");
      s.className = "char";
      s.textContent = ch;
      sub.appendChild(s);
      chars.push(s);
    }
    const caret = document.createElement("span");
    caret.className = "type-caret";
    caret.setAttribute("aria-hidden", "true");
    sub.appendChild(caret);

    const DELAY = 55;
    const START = 350;
    chars.forEach((c, i) => {
      setTimeout(() => c.classList.add("is-typed"), START + i * DELAY);
    });
    setTimeout(() => sub.classList.add("is-done"), START + chars.length * DELAY + 150);
  }

  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(type);
  } else {
    type();
  }
})();

(function () {
  const langSwitch = document.querySelector(".hero__lang");
  if (!langSwitch) return;
  const options = Array.from(langSwitch.querySelectorAll(".hero__lang-option"));

  const i18n = {
    zh: {
      navDocs: "文档",
      navChangelog: "版本日志",
      navProduct: "产品",
      navJoinUs: "加入我们",
      heroSubtitle: "面向团队的新一代企业级即时通讯方案，支持多端协同与实时消息，帮助你更高效地沟通、协作与管理。",
      featuresTitle: "为什么选择 LinkX",
      card1Title: "即时消息",
      card1Body: "单聊与群聊齐备，支持文本、图片、文件与语音，引用、编辑、撤回、转发一应俱全。",
      card2Title: "实时推送",
      card2Body: "HTTP 拉取历史消息，WebSocket 毫秒级推送，在线状态与消息送达实时同步。",
      card3Title: "音视频会议",
      card3Body: "WebRTC 单聊通话与多人 Mesh 会议，通话信令经 WebSocket 实时下发，协作无延迟。",
      card4Title: "文件与网盘",
      card4Body: "聊天文件、群文件/群相册与个人网盘统一接入 MinIO，团队资料集中沉淀、随时取用。",
      card5Title: "管理运营",
      card5Body: "用户权限、内容审核、风控策略与统计大屏，后台运营一站搞定。",
      downloadBtn: "下载",
      comingSoon: "敬请期待",
    },
    en: {
      navDocs: "Docs",
      navChangelog: "Changelog",
      navProduct: "Product",
      navJoinUs: "Join Us",
      heroSubtitle:
        "A next-generation enterprise IM solution for teams. Multi-end collaboration and real-time messaging help you communicate, collaborate, and manage more efficiently.",
      featuresTitle: "Why Choose LinkX",
      card1Title: "Instant Messaging",
      card1Body: "One-on-one and group chats with text, images, files, and voice — plus quote, edit, recall, and forward.",
      card2Title: "Real-Time Push",
      card2Body: "HTTP for history, WebSocket for millisecond delivery — online status and message sync stay in step.",
      card3Title: "Audio & Video",
      card3Body: "WebRTC one-on-one calls and multi-party Mesh conferences, with call signaling over WebSocket.",
      card4Title: "Files & Cloud Drive",
      card4Body: "Chat files, group albums, and personal cloud drive powered by MinIO — all assets in one place.",
      card5Title: "Admin & Operations",
      card5Body: "User permissions, content review, risk policies, and analytics dashboards — operations in one console.",
      downloadBtn: "Download",
      comingSoon: "Coming Soon",
    },
  };

  let lang = "zh";

  function applyLang(newLang) {
    lang = newLang;
    document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
    options.forEach((opt) => {
      opt.classList.toggle("is-active", opt.dataset.lang === lang);
    });
    const docsBtn = document.getElementById("docsBtn");
    if (docsBtn) {
      docsBtn.setAttribute("href", "docs.html" + (lang === "en" ? "?lang=en" : ""));
    }
    const navDocs = document.getElementById("navDocs");
    if (navDocs) {
      navDocs.setAttribute("href", "docs.html" + (lang === "en" ? "?lang=en" : ""));
    }
    const navChangelog = document.getElementById("navChangelog");
    if (navChangelog) {
      navChangelog.setAttribute("href", "changelog.html" + (lang === "en" ? "?lang=en" : ""));
    }
    const blogBtn = document.getElementById("blogBtn");
    if (blogBtn) {
      blogBtn.setAttribute("href", "blog.html" + (lang === "en" ? "?lang=en" : ""));
    }
    const navLinks = {
      navHome: "index.html",
      navLinkX: "index.html",
      navJoinUs: "join.html" + (lang === "en" ? "?lang=en" : ""),
    };
    Object.entries(navLinks).forEach(([id, href]) => {
      const el = document.getElementById(id);
      if (el) el.setAttribute("href", href);
    });
    const dict = i18n[lang];
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.dataset.i18n;
      const text = dict[key];
      if (text === undefined) return;
      if (el.classList.contains("hero__subtitle")) {
        el.classList.remove("is-typing", "is-done");
        el.style.width = "";
        el.style.whiteSpace = newLang === "zh" ? "nowrap" : "normal";
        el.textContent = text;
        return;
      }
      const isTitle = el.tagName === "H3" && el.closest(".card__text");
      if (!isTitle) {
        el.textContent = text;
        return;
      }
      const wasTyped = el.dataset.typed === "1";
      el.textContent = "";
      for (const c of text) {
        const span = document.createElement("span");
        span.className = "char";
        if (wasTyped) span.classList.add("is-typed");
        span.textContent = c;
        el.appendChild(span);
      }
      const cursor = document.createElement("span");
      cursor.className = "cursor";
      cursor.setAttribute("aria-hidden", "true");
      el.appendChild(cursor);
    });
  }

  options.forEach((opt) => {
    opt.addEventListener("click", () => applyLang(opt.dataset.lang));
  });
})();
