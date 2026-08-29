/**
 * 作者：yangleduo
 */
/**
 * 参考模板。实际使用请编辑 shared/site-config.js（已内置按域名自动选择逻辑）。
 * 部署 mars-studio.asia 前请把其中的 PRODUCTION_API_BASE_URL 改成线上 linkx-server。
 */
(function () {
  var PRODUCTION_API_BASE_URL = "https://api.mars-studio.asia/api";
  window.SiteConfig = {
    apiBaseUrl: PRODUCTION_API_BASE_URL,
    isLocalPreview: false,
    installerDirectUrl:
      "https://pub-xxxx.r2.dev/releases/YYYY/MM/DD/LinkX-Installer-x.y.z.exe",
  };
})();
