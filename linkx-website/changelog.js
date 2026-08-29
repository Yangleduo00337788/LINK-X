(function () {
  const data = window.ChangelogData;
  if (!data) return;

  const switcher = document.getElementById("changelogPlatformSwitch");
  const panel = document.getElementById("changelogPlatformPanel");
  const listEl = document.getElementById("changelogList");
  const logo = document.getElementById("changelogPlatformLogo");
  const nameEl = document.getElementById("changelogPlatformName");
  const versionEl = document.getElementById("changelogPlatformVersion");
  const downloadEl = document.getElementById("changelogPlatformDownload");
  const comingEl = document.getElementById("changelogPlatformComing");
  if (!switcher || !panel || !listEl || !logo || !nameEl || !versionEl || !downloadEl || !comingEl) return;

  const options = Array.from(switcher.querySelectorAll(".platform-switch__option"));
  const lang = new URLSearchParams(window.location.search).get("lang") === "en" ? "en" : "zh";

  const i18n = {
    zh: {
      navProduct: "产品",
      navDocs: "文档",
      navChangelog: "版本日志",
      navJoinUs: "加入我们",
      changelogTitle: "LinkX 版本日志",
      changelogSubtitle: "查看各平台最新版本与历史更新记录，了解每一次功能迭代与体验优化。",
      highlightsTitle: "版本亮点",
      downloadBtn: "下载",
      comingSoon: "敬请期待",
      emptyTitle: "暂无发布版本",
      emptyBody: "该平台客户端正在开发中，敬请期待后续更新。",
      unreleasedDate: "进行中",
      roadmapTitle: "版本规划",
      plannedBadge: "计划中",
    },
    en: {
      navProduct: "Product",
      navDocs: "Docs",
      navChangelog: "Changelog",
      navJoinUs: "Join Us",
      changelogTitle: "LinkX Changelog",
      changelogSubtitle: "Latest versions and release history for every platform — track each feature update and improvement.",
      highlightsTitle: "Release Highlights",
      downloadBtn: "Download",
      comingSoon: "Coming Soon",
      emptyTitle: "No Releases Yet",
      emptyBody: "The client for this platform is under development. Stay tuned for future updates.",
      unreleasedDate: "In progress",
      roadmapTitle: "Roadmap",
      plannedBadge: "Planned",
    },
  };

  const dict = i18n[lang];

  document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
  document.querySelectorAll("[data-i18n]").forEach(function (el) {
    const text = dict[el.dataset.i18n];
    if (text) el.textContent = text;
  });

  function langHref(path) {
    return path + (lang === "en" ? "?lang=en" : "");
  }

  document.querySelectorAll(".hero__lang-option").forEach(function (opt) {
    opt.classList.toggle("is-active", opt.dataset.lang === lang);
    opt.addEventListener("click", function () {
      const nextLang = opt.dataset.lang;
      const url = new URL(window.location.href);
      if (nextLang === "en") url.searchParams.set("lang", "en");
      else url.searchParams.delete("lang");
      window.location.href = url.toString();
    });
  });

  ["navDocs", "navChangelog"].forEach(function (id) {
    const el = document.getElementById(id);
    if (!el) return;
    const path = id === "navDocs" ? "docs.html" : "changelog.html";
    el.setAttribute("href", langHref(path));
  });
  const navJoinUs = document.getElementById("navJoinUs");
  if (navJoinUs) navJoinUs.setAttribute("href", langHref("join.html"));

  function renderRoadmap() {
    const wrap = document.getElementById("changelogRoadmap");
    if (!wrap || !data.roadmap || !data.roadmap.length) return;

    wrap.innerHTML =
      '<h2 class="changelog-roadmap__title">' +
      dict.roadmapTitle +
      "</h2>" +
      '<div class="changelog-roadmap__list">' +
      data.roadmap
        .map(function (item) {
          const sections = (item.sections || [])
            .map(function (section) {
              const items = (section.items[lang] || [])
                .map(function (line) {
                  return "<li>" + line + "</li>";
                })
                .join("");
              return (
                '<div class="changelog-section">' +
                "<h4>" +
                section.title[lang] +
                "</h4>" +
                "<ul>" +
                items +
                "</ul>" +
                "</div>"
              );
            })
            .join("");

          return (
            '<article class="changelog-release changelog-release--planned">' +
            '<div class="changelog-release__head">' +
            '<div class="changelog-release__title">' +
            '<span class="changelog-release__version">v' +
            item.version +
            "</span>" +
            '<span class="changelog-release__badge">' +
            item.status[lang] +
            "</span>" +
            "</div>" +
            "</div>" +
            '<p class="changelog-roadmap__summary">' +
            item.summary[lang] +
            "</p>" +
            sections +
            "</article>"
          );
        })
        .join("") +
      "</div>";
  }

  function renderHighlights() {
    const wrap = document.getElementById("changelogHighlights");
    if (!wrap || !data.highlights) return;

    wrap.innerHTML =
      '<h2 class="changelog-highlights__title" data-i18n="highlightsTitle">' +
      dict.highlightsTitle +
      "</h2>" +
      '<div class="changelog-highlights__list">' +
      data.highlights
        .map(function (item, index) {
          const reverse = item.reverse ? " changelog-highlight--reverse" : "";
          return (
            '<article class="changelog-highlight' +
            reverse +
            '">' +
            '<div class="changelog-highlight__art" aria-hidden="true">' +
            '<img src="' +
            item.image +
            '" alt="" loading="lazy" />' +
            "</div>" +
            '<div class="changelog-highlight__text">' +
            "<h3>" +
            item.title[lang] +
            "</h3>" +
            "<p>" +
            item.body[lang] +
            "</p>" +
            "</div>" +
            "</article>"
          );
        })
        .join("") +
      "</div>";
  }

  function renderReleases(platformKey) {
    const platform = data.platforms[platformKey];
    if (!platform) return;

    if (!platform.releases || !platform.releases.length) {
      listEl.innerHTML =
        '<div class="changelog-empty">' +
        '<img class="changelog-empty__icon" src="' +
        platform.icon +
        '" alt="" />' +
        "<h3>" +
        dict.emptyTitle +
        "</h3>" +
        "<p>" +
        dict.emptyBody +
        "</p>" +
        "</div>";
      return;
    }

    listEl.innerHTML = platform.releases
      .map(function (release) {
        const versionText =
          release.version === "Unreleased"
            ? lang === "zh"
              ? "未发布"
              : "Unreleased"
            : "v" + release.version;
        const dateText = release.date || dict.unreleasedDate;
        const badge = release.badge
          ? '<span class="changelog-release__badge">' + release.badge[lang] + "</span>"
          : "";
        const sections = (release.sections || [])
          .map(function (section) {
            const items = (section.items[lang] || [])
              .map(function (item) {
                return "<li>" + item + "</li>";
              })
              .join("");
            return (
              '<div class="changelog-section">' +
              "<h4>" +
              section.title[lang] +
              "</h4>" +
              "<ul>" +
              items +
              "</ul>" +
              "</div>"
            );
          })
          .join("");

        return (
          '<article class="changelog-release">' +
          '<div class="changelog-release__head">' +
          '<div class="changelog-release__title">' +
          '<span class="changelog-release__version">' +
          versionText +
          "</span>" +
          badge +
          "</div>" +
          '<time class="changelog-release__date">' +
          dateText +
          "</time>" +
          "</div>" +
          sections +
          "</article>"
        );
      })
      .join("");
  }

  function applyPlatform(platformKey) {
    const platform = data.platforms[platformKey] || data.platforms.windows;
    logo.src = platform.icon;
    nameEl.textContent = platform.name[lang];

    if (platform.comingSoon) {
      panel.classList.add("is-coming-soon");
      versionEl.textContent = dict.comingSoon;
      comingEl.hidden = false;
    } else {
      panel.classList.remove("is-coming-soon");
      versionEl.textContent = platform.versionLabel[lang];
      comingEl.hidden = true;
      const installer =
        window.LinkXAppDownload && window.LinkXAppDownload.installerUrl
          ? window.LinkXAppDownload.installerUrl(platformKey)
          : "";
      if (installer) {
        downloadEl.href = installer;
        downloadEl.removeAttribute("download");
      }
    }

    options.forEach(function (opt) {
      const active = opt.dataset.platform === platformKey;
      opt.classList.toggle("is-active", active);
      opt.setAttribute("aria-pressed", active ? "true" : "false");
    });

    renderReleases(platformKey);
  }

  options.forEach(function (opt) {
    opt.addEventListener("click", function () {
      applyPlatform(opt.dataset.platform);
    });
  });

  const initial = new URLSearchParams(window.location.search).get("platform");
  renderRoadmap();
  renderHighlights();
  applyPlatform(initial && data.platforms[initial] ? initial : "windows");
})();
