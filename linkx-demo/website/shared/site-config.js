/**
 * 作者：yangleduo
 */
/**
 * 官网 API 地址：按访问域名自动选择，避免生产站误连 127.0.0.1。
 * 部署 mars-studio.asia 前请确认 PRODUCTION_API_BASE_URL 为线上 linkx-server。
 */
(function () {
  /** 线上后端（须含 /api 前缀）；请改成你实际部署的 API 地址 */
  var PRODUCTION_API_BASE_URL = "https://api.mars-studio.asia/api";

  var host = (window.location.hostname || "").toLowerCase();

  function isLocalPreview() {
    if (host === "localhost" || host === "127.0.0.1") return true;
    if (/^192\.168\.\d+\.\d+$/.test(host)) return true;
    if (/^10\.\d+\.\d+\.\d+$/.test(host)) return true;
    return false;
  }

  function resolveApiBaseUrl() {
    if (isLocalPreview()) {
      return "http://127.0.0.1:8080/api";
    }
    if (host === "mars-studio.asia" || host === "www.mars-studio.asia") {
      return PRODUCTION_API_BASE_URL;
    }
    return PRODUCTION_API_BASE_URL;
  }

  window.SiteConfig = {
    apiBaseUrl: resolveApiBaseUrl(),
    isLocalPreview: isLocalPreview(),
    /** Windows 安装包 R2 公网直链（API 不可用时的兜底） */
    installerDirectUrl:
      "https://pub-b74f4fc9019d4f88978244b74e2627dc.r2.dev/releases/2026/08/29/LinkX-Installer-1.0.1.exe",
    /** Linux 安装包 R2 公网直链（API 不可用时的兜底） */
    linuxDirectUrls: {
      appimage:
        "https://pub-b74f4fc9019d4f88978244b74e2627dc.r2.dev/releases/2026/08/30/LinkX-1.0.1.appimage",
      deb: "https://pub-b74f4fc9019d4f88978244b74e2627dc.r2.dev/releases/2026/08/30/linkx_1.0.1_amd64.deb",
    },
  };
})();
