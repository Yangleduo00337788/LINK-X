/**
 * 作者：yangleduo
 */
/**
 * 首页 / 版本日志页 Windows 下载：优先 R2 公网直链（/app/version），兜底 site-config 或 /app/installer。
 */
(function () {
  var cachedInstallerUrls = Object.create(null);

  function apiBase() {
    var raw = (window.SiteConfig && window.SiteConfig.apiBaseUrl) || "";
    return raw.replace(/\/$/, "");
  }

  function configDirectUrl() {
    var raw = window.SiteConfig && window.SiteConfig.installerDirectUrl;
    return raw ? String(raw).trim() : "";
  }

  function installerApiUrl(platform, channel) {
    var base = apiBase();
    if (!base) return "";
    var params = new URLSearchParams({
      platform: platform || "windows",
      channel: channel || "stable",
    });
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

  function bindInstallerDownload(el, platform) {
    if (!el) return;
    var ch = "stable";
    var p = platform || "windows";

    function apply(url) {
      if (!url) return;
      el.href = url;
      el.removeAttribute("download");
      el.setAttribute("rel", "noopener noreferrer");
    }

    apply(installerUrl(p, ch));
    fetchInstallerUrl(p, ch).then(apply);
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
    bindInstallerDownload: bindInstallerDownload,
    refreshVersionLabel: refreshVersionLabel,
  };

  document.addEventListener("DOMContentLoaded", function () {
    bindInstallerDownload(document.getElementById("platformDownload"), "windows");
    bindInstallerDownload(document.getElementById("changelogPlatformDownload"), "windows");
    refreshVersionLabel(document.getElementById("platformVersion"), "windows");
    refreshVersionLabel(document.getElementById("changelogPlatformVersion"), "windows");
  });
})();
