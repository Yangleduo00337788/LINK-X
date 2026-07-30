/**
 * Swagger UI：中英文切换条 + 常用按钮中文映射。
 * 依赖 Cookie LINKX_LANG / URL ?lang=zh_CN|en（由后端 Locale 拦截器写入）。
 */
(function () {
  var ZH = {
    "Authorize": "授权",
    "Close": "关闭",
    "Try it out": "试一试",
    "Cancel": "取消",
    "Execute": "执行",
    "Clear": "清除",
    "Schemas": "数据模型",
    "Schemes": "协议",
    "Parameters": "参数",
    "Responses": "响应",
    "Response": "响应",
    "Request body": "请求体",
    "No parameters": "无参数",
    "Server response": "服务器响应",
    "Response headers": "响应头",
    "Response body": "响应体",
    "Download": "下载",
    "Available authorizations": "可用授权",
    "Logout": "退出登录",
    "Apply": "应用",
    "Value": "值",
    "Description": "说明",
    "Name": "名称",
    "Required": "必填",
    "Example Value": "示例值",
    "Schema": "结构",
    "Filter by tag": "按标签过滤",
    "Explore": "浏览",
    "Servers": "服务器"
  };

  function currentLang() {
    var m = document.cookie.match(/(?:^|;\s*)LINKX_LANG=([^;]+)/);
    if (m) {
      try {
        return decodeURIComponent(m[1]).toLowerCase().indexOf("en") === 0 ? "en" : "zh";
      } catch (e) { /* ignore */ }
    }
    var q = new URLSearchParams(location.search).get("lang");
    if (q && q.toLowerCase().indexOf("en") === 0) return "en";
    return "zh";
  }

  function switchLang(lang) {
    var url = new URL(location.href);
    url.searchParams.set("lang", lang === "en" ? "en" : "zh_CN");
    location.href = url.toString();
  }

  function mountBar() {
    if (document.getElementById("linkx-swagger-lang")) return;
    var bar = document.createElement("div");
    bar.id = "linkx-swagger-lang";
    bar.style.cssText = [
      "position:fixed", "top:10px", "right:16px", "z-index:99999",
      "display:flex", "gap:6px", "align-items:center",
      "font:13px/1.2 system-ui,sans-serif"
    ].join(";");
    var lang = currentLang();
    function btn(label, code) {
      var b = document.createElement("button");
      b.type = "button";
      b.textContent = label;
      b.style.cssText = [
        "cursor:pointer", "padding:6px 10px", "border-radius:6px",
        "border:1px solid #89bf04",
        lang === code ? "background:#89bf04;color:#fff" : "background:#fff;color:#3b4151"
      ].join(";");
      b.onclick = function () { switchLang(code); };
      return b;
    }
    bar.appendChild(btn("中文", "zh"));
    bar.appendChild(btn("English", "en"));
    document.body.appendChild(bar);
  }

  function translateNode(root) {
    if (currentLang() !== "zh") return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(function (n) {
      var t = n.nodeValue;
      if (!t || !t.trim()) return;
      var key = t.trim();
      if (ZH[key]) {
        n.nodeValue = t.replace(key, ZH[key]);
      }
    });
    root.querySelectorAll && root.querySelectorAll("[placeholder]").forEach(function (el) {
      var p = el.getAttribute("placeholder");
      if (ZH[p]) el.setAttribute("placeholder", ZH[p]);
    });
  }

  function boot() {
    mountBar();
    translateNode(document.body);
    var obs = new MutationObserver(function (mutations) {
      mutations.forEach(function (m) {
        m.addedNodes.forEach(function (n) {
          if (n.nodeType === 1) translateNode(n);
        });
      });
    });
    obs.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
