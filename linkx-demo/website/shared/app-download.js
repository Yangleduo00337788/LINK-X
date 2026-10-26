/**
 * 作者：yangleduo
 */
/**
 * 首页 / 版本日志页下载：Windows 单链；Linux 悬停展示 AppImage / deb。
 */
(function () {
  var cachedInstallerUrls = Object.create(null);
  var cachedLinuxPackages = null;

  function apiBase() {
    var raw = (window.SiteConfig && window.SiteConfig.apiBaseUrl) || "";
    return raw.replace(/\/$/, "");
  }

  function configDirectUrl() {
    var raw = window.SiteConfig && window.SiteConfig.installerDirectUrl;
    return raw ? String(raw).trim() : "";
  }

  function configLinuxDownloads() {
    var cfg = window.SiteConfig && window.SiteConfig.linuxDirectUrls;
    if (!cfg) return {};
    return {
      appimage: cfg.appimage ? String(cfg.appimage).trim() : "",
      deb: cfg.deb ? String(cfg.deb).trim() : "",
    };
  }

  function installerApiUrl(platform, channel, format) {
    var base = apiBase();
    if (!base) return "";
    var params = new URLSearchParams({
      platform: platform || "windows",
      channel: channel || "stable",
    });
    if (format) params.set("format", format);
    return base + "/app/installer?" + params.toString();
  }

  function installerUrl(platform, channel) {
    var key = platform || "windows";
    if (cachedInstallerUrls[key]) return cachedInstallerUrls[key];
    var direct = configDirectUrl();
    if (direct) return direct;
    return installerApiUrl(key, channel);
  }

  function fetchInstallerUrl(platform, channel) {
    var key = platform || "windows";
    var ch = channel || "stable";
    if (cachedInstallerUrls[key]) {
      return Promise.resolve(cachedInstallerUrls[key]);
    }

    var direct = configDirectUrl();
    var base = apiBase();
    if (!base) {
      if (direct) {
        cachedInstallerUrls[key] = direct;
        return Promise.resolve(direct);
      }
      return Promise.resolve("");
    }

    var params = new URLSearchParams({
      current: "0.0.0",
      channel: ch,
      platform: key,
    });

    return fetch(base + "/app/version?" + params.toString(), {
      headers: { Accept: "application/json" },
      mode: "cors",
      cache: "no-store",
    })
      .then(function (res) {
        return res.json();
      })
      .then(function (body) {
        var url =
          body && body.code === 200 && body.data && body.data.downloadUrl
            ? String(body.data.downloadUrl).trim()
            : "";
        if (url) {
          cachedInstallerUrls[key] = url;
          return url;
        }
        if (direct) {
          cachedInstallerUrls[key] = direct;
          return direct;
        }
        return installerApiUrl(key, ch);
      })
      .catch(function () {
        if (direct) {
          cachedInstallerUrls[key] = direct;
          return direct;
        }
        return installerApiUrl(key, ch);
      });
  }

  function mapPackagesToUrls(packages) {
    var out = { appimage: "", deb: "" };
    if (!packages || !packages.length) return out;
    packages.forEach(function (pkg) {
      var fmt = (pkg.packageFormat || "").toLowerCase();
      var url = (pkg.downloadUrl || "").trim();
      if (!url) return;
      if (fmt === "appimage") out.appimage = url;
      if (fmt === "deb") out.deb = url;
    });
    return out;
  }

  function fetchLinuxPackages() {
    if (cachedLinuxPackages) {
      return Promise.resolve(cachedLinuxPackages);
    }
    var fallback = configLinuxDownloads();
    var base = apiBase();
    if (!base) {
      cachedLinuxPackages = fallback;
      return Promise.resolve(fallback);
    }
    var params = new URLSearchParams({
      current: "0.0.0",
      channel: "stable",
      platform: "linux",
    });
    return fetch(base + "/app/version?" + params.toString(), {
      headers: { Accept: "application/json" },
      mode: "cors",
      cache: "no-store",
    })
      .then(function (res) {
        return res.json();
      })
      .then(function (body) {
        var urls = mapPackagesToUrls(body && body.code === 200 && body.data ? body.data.packages : []);
        if (!urls.appimage && body && body.data && body.data.downloadUrl) {
          var primary = String(body.data.downloadUrl).trim();
          var name = ((body.data.packageFileName || "") + "").toLowerCase();
          if (name.endsWith(".deb")) urls.deb = primary;
          else urls.appimage = primary;
        }
        if (!urls.appimage) urls.appimage = fallback.appimage || "";
        if (!urls.deb) urls.deb = fallback.deb || "";
        cachedLinuxPackages = urls;
        return urls;
      })
      .catch(function () {
        cachedLinuxPackages = fallback;
        return fallback;
      });
  }

  function applyLink(el, url) {
    if (!el || !url) return;
    el.href = url;
    el.removeAttribute("download");
    el.setAttribute("rel", "noopener noreferrer");
  }

  function bindInstallerDownload(el, platform) {
    if (!el) return;
    var ch = "stable";
    var p = platform || "windows";
    applyLink(el, installerUrl(p, ch));
    fetchInstallerUrl(p, ch).then(function (url) {
      applyLink(el, url);
    });
  }

  function bindLinuxMenuLinks(menuEl, urls) {
    if (!menuEl) return;
    var appimageEl = menuEl.querySelector('[data-format="appimage"]');
    var debEl = menuEl.querySelector('[data-format="deb"]');
    applyLink(appimageEl, urls.appimage || installerApiUrl("linux", "stable", "appimage"));
    applyLink(debEl, urls.deb || installerApiUrl("linux", "stable", "deb"));
  }

  function applyPlatformDownload(platform, elements) {
    if (!elements) return;
    var singleEl = elements.singleEl;
    var groupEl = elements.groupEl;
    var menuEl = elements.menuEl;
    if (!singleEl || !groupEl || !menuEl) return;

    var actionsEl = singleEl.parentElement;

    if (platform === "linux") {
      if (actionsEl) actionsEl.classList.add("is-linux-download");
      singleEl.hidden = true;
      groupEl.hidden = false;
      var fallback = configLinuxDownloads();
      bindLinuxMenuLinks(menuEl, fallback);
      fetchLinuxPackages().then(function (urls) {
        bindLinuxMenuLinks(menuEl, urls);
      });
      return;
    }

    if (actionsEl) actionsEl.classList.remove("is-linux-download");
    groupEl.hidden = true;
    singleEl.hidden = false;
    if (platform === "windows") {
      bindInstallerDownload(singleEl, "windows");
    }
  }

  function refreshVersionLabel(versionEl, platform) {
    var base = apiBase();
    if (!base || !versionEl) return;
    var params = new URLSearchParams({
      current: "0.0.0",
      channel: "stable",
      platform: platform || "windows",
    });
    fetch(base + "/app/version?" + params.toString(), {
      headers: { Accept: "application/json" },
      mode: "cors",
    })
      .then(function (res) {
        return res.json();
      })
      .then(function (body) {
        if (body.code !== 200 || !body.data || !body.data.version) return;
        var lang = new URLSearchParams(window.location.search).get("lang") === "en" ? "en" : "zh";
        var suffix = lang === "en" ? "Installer" : "安装包";
        versionEl.textContent = "v" + body.data.version + " · x64 · " + suffix;
      })
      .catch(function () {
        /* 静默：保留 HTML 默认版本文案 */
      });
  }

  window.LinkXAppDownload = {
    apiBase: apiBase,
    installerUrl: installerUrl,
    fetchInstallerUrl: fetchInstallerUrl,
    fetchLinuxPackages: fetchLinuxPackages,
    bindInstallerDownload: bindInstallerDownload,
    applyPlatformDownload: applyPlatformDownload,
    refreshVersionLabel: refreshVersionLabel,
  };

  document.addEventListener("DOMContentLoaded", function () {
    refreshVersionLabel(document.getElementById("platformVersion"), "windows");
    refreshVersionLabel(document.getElementById("changelogPlatformVersion"), "windows");
  });
})();
