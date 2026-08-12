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
      blogTitle: "LinkX Blog",
      blogSubtitle: "深入 LinkX 的技术实现：系统架构、模块划分，以及消息从发送到实时送达的完整链路。",
      navOverview: "架构概览",
      navModules: "模块组成",
      navChannels: "通信通道",
      navGroupMessage: "消息链路",
      navSend: "消息发送",
      navPush: "实时推送",
      navSync: "多端同步",
      navSignaling: "通话信令",
      secOverviewTitle: "架构概览",
      secOverviewP1:
        "LinkX 采用前后端分离的三层架构：展现层（客户端 / 管理端）通过 HTTP 与 WebSocket 接入后端，服务层由 Spring Boot 单体承载业务逻辑，数据层使用 MySQL、Redis 与 MinIO 分别负责持久化、缓存与文件存储。",
      cardLayer1Title: "展现层",
      cardLayer1Body: "linkx-client（Electron + Vue 3）与 linkx-admin（Vue 3 管理后台）负责用户交互与界面渲染。",
      cardLayer2Title: "接入层",
      cardLayer2Body: "HTTP REST :8080/api 处理业务请求；Netty WebSocket :8081/ws 负责实时推送。",
      cardLayer3Title: "服务层",
      cardLayer3Body: "认证鉴权、IM 核心、好友群组、文件网盘、风控审计等模块统一部署在 linkx-server。",
      cardLayer4Title: "数据层",
      cardLayer4Body: "MySQL 存储业务数据，Redis 缓存 Token 与在线状态，MinIO 存储图片与文件对象。",
      secOverviewDiagram:
        "展现层          接入层                 服务层                    数据层\n─────────      ─────────────        ──────────────           ─────────────\nlinkx-client → HTTP  :8080/api  →  Spring Boot 3.5    →   MySQL 8.4\nlinkx-admin  → WS    :8081/ws   →  Netty WebSocket    →   Redis 7.2\n                                    MyBatis-Flex             MinIO\n                                    Flyway 迁移",
      secModulesTitle: "模块组成",
      thModule: "模块",
      thTech: "技术栈",
      thResponsibility: "职责",
      tdClient: "单聊/群聊 UI、WebSocket 客户端、WebRTC 通话、文件网盘",
      tdAdmin: "RBAC 权限、内容审核、风控策略、统计大屏",
      tdServer: "REST API、消息路由、在线状态、对象存储对接",
      secModulesP1:
        "服务端按业务域划分 Controller / Service / Mapper，消息写入 MySQL 后通过 Netty 通道推送给在线用户。管理端与客户端共享同一套后端 API，权限通过 JWT + RBAC 隔离。",
      secChannelsTitle: "通信通道",
      secChannelsP1: "LinkX 刻意将「请求-响应」与「实时推送」拆分为两条通道，兼顾可靠性与低延迟。",
      thChannel: "通道",
      thAddress: "地址",
      thPurpose: "典型场景",
      tdHttp: "登录注册、拉取历史消息、好友/群聊管理、文件上传",
      tdWs: "新消息推送、已读回执、在线状态、通话/会议信令",
      calloutChannelsTitle: "设计原则",
      calloutChannelsBody:
        "历史消息与分页查询走 HTTP，保证可重试与幂等；实时事件走 WebSocket，减少轮询开销。客户端登录成功后两条通道同时可用。",
      secSendTitle: "消息发送流程",
      secSendP1: "用户发送一条聊天消息时，链路如下：",
      secSendL1:
        "客户端将消息封装为 REST 请求（文本、图片元数据或文件引用），携带 Access Token 调用 POST /api/message/send。",
      secSendL2: "服务端校验 Token、会话权限与敏感词，将消息持久化至 MySQL，并生成全局唯一 messageId。",
      secSendL3: "若消息含附件，文件已预先上传至 MinIO，消息体仅保存对象 key 与元数据。",
      secSendL4: "持久化成功后，服务端通过 Netty 将消息事件推送给会话内在线成员（单聊对方、群聊全体成员）。",
      secSendL5: "发送方客户端收到 HTTP 响应后更新本地消息状态（发送中 → 已发送），并等待对端已读回执。",
      secPushTitle: "实时推送机制",
      secPushP1: "客户端登录后自动建立 WebSocket 长连接，并在 Header 或首帧携带 JWT 完成鉴权绑定。",
      secPushH1: "连接生命周期",
      secPushL1: "建立连接：登录成功 → 连接 ws://host:8081/ws → 服务端将 Channel 与用户 ID 绑定。",
      secPushL2: "心跳保活：客户端定时发送 ping，服务端响应 pong，超时未心跳则断开并清理在线状态。",
      secPushL3: "断线重连：网络波动时客户端指数退避重连，重连后通过 HTTP 增量拉取离线期间的消息。",
      secPushH2: "推送事件类型",
      secPushL4: "NEW_MESSAGE — 新聊天消息",
      secPushL5: "MESSAGE_READ — 已读回执",
      secPushL6: "ONLINE_STATUS — 好友上线/下线",
      secPushL7: "CALL_SIGNAL — 音视频通话信令",
      secSyncTitle: "多端同步与离线消息",
      secSyncP1: "同一账号可在多台设备同时登录。每条消息以服务端时间戳与 messageId 排序，各端通过增量同步保持一致。",
      secSyncH1: "在线同步",
      secSyncP2: "任一在线设备收到 WebSocket 推送后，其他在线设备同样会收到推送（多端同时响铃/展示）。已读状态变更也会广播到所有在线端。",
      secSyncH2: "离线补全",
      secSyncP3:
        "设备离线期间的消息保存在 MySQL。重新连接后，客户端调用 GET /api/message/sync?since={lastMessageId} 拉取增量，合并入本地会话列表，再恢复 WebSocket 实时接收。",
      calloutSyncTitle: "一致性保证",
      calloutSyncBody:
        "以服务端落库为唯一事实来源（Single Source of Truth）。客户端本地缓存仅作展示优化，冲突时以服务端数据为准。",
      secSignalingTitle: "通话与会议信令",
      secSignalingP1: "音视频媒体流通过 WebRTC 在终端之间直连传输，服务端仅转发信令（SDP / ICE Candidate），不中转音视频数据。",
      secSignalingL1: "主叫方通过 REST 创建通话会话，服务端向被叫方 WebSocket 推送 CALL_INVITE。",
      secSignalingL2: "被叫接听后，双方经 WebSocket 交换 SDP Offer/Answer 与 ICE Candidate。",
      secSignalingL3: "WebRTC 连接建立，音视频 P2P 传输；挂断时发送 CALL_HANGUP 释放资源。",
      secSignalingP2: "多人 Mesh 会议沿用同一信令通道，每位参与者与其他成员建立独立 PeerConnection，适合小规模协作场景。",
    },
    en: {
      navProduct: "Product",
      navDocs: "Docs",
      navChangelog: "Changelog",
      navJoinUs: "Join Us",
      blogTitle: "LinkX Blog",
      blogSubtitle:
        "A deep dive into LinkX — system architecture, module layout, and the full message delivery pipeline.",
      navOverview: "Overview",
      navModules: "Modules",
      navChannels: "Channels",
      navGroupMessage: "Messaging",
      navSend: "Sending",
      navPush: "Real-Time Push",
      navSync: "Multi-Device Sync",
      navSignaling: "Call Signaling",
      secOverviewTitle: "Architecture Overview",
      secOverviewP1:
        "LinkX uses a layered architecture: presentation (client/admin) connects via HTTP and WebSocket; a Spring Boot monolith handles business logic; MySQL, Redis, and MinIO handle persistence, cache, and files.",
      cardLayer1Title: "Presentation",
      cardLayer1Body: "linkx-client (Electron + Vue 3) and linkx-admin (Vue 3) render the user interface.",
      cardLayer2Title: "Gateway",
      cardLayer2Body: "HTTP REST :8080/api for requests; Netty WebSocket :8081/ws for real-time events.",
      cardLayer3Title: "Service",
      cardLayer3Body: "Auth, IM core, friends/groups, cloud drive, risk control — all in linkx-server.",
      cardLayer4Title: "Data",
      cardLayer4Body: "MySQL for business data, Redis for tokens and online status, MinIO for file objects.",
      secOverviewDiagram:
        "Presentation    Gateway                  Service                   Data\n─────────────  ─────────────        ──────────────           ─────────────\nlinkx-client → HTTP  :8080/api  →  Spring Boot 3.5    →   MySQL 8.4\nlinkx-admin  → WS    :8081/ws   →  Netty WebSocket    →   Redis 7.2\n                                    MyBatis-Flex             MinIO\n                                    Flyway migrations",
      secModulesTitle: "Module Layout",
      thModule: "Module",
      thTech: "Stack",
      thResponsibility: "Responsibility",
      tdClient: "Chat UI, WebSocket client, WebRTC calls, cloud drive",
      tdAdmin: "RBAC, content review, risk policies, analytics dashboard",
      tdServer: "REST API, message routing, online status, object storage",
      secModulesP1:
        "The server is organized by domain (Controller / Service / Mapper). Messages are persisted to MySQL then pushed via Netty. Client and admin share the same API with JWT + RBAC isolation.",
      secChannelsTitle: "Communication Channels",
      secChannelsP1: "LinkX separates request-response from real-time push for reliability and low latency.",
      thChannel: "Channel",
      thAddress: "Address",
      thPurpose: "Typical Use",
      tdHttp: "Login, history fetch, friends/groups, file upload",
      tdWs: "New messages, read receipts, online status, call signaling",
      calloutChannelsTitle: "Design Principle",
      calloutChannelsBody:
        "History and pagination use HTTP (retryable, idempotent). Real-time events use WebSocket. Both channels are available after login.",
      secSendTitle: "Message Send Flow",
      secSendP1: "When a user sends a chat message:",
      secSendL1:
        "Client wraps the message in a REST call with Access Token → POST /api/message/send.",
      secSendL2: "Server validates token, permissions, and sensitive words; persists to MySQL with a unique messageId.",
      secSendL3: "Attachments are pre-uploaded to MinIO; the message body stores only object keys and metadata.",
      secSendL4: "After persistence, Netty pushes the event to online session members.",
      secSendL5: "Sender updates local state (sending → sent) and awaits read receipts.",
      secPushTitle: "Real-Time Push",
      secPushP1: "After login, the client opens a WebSocket and authenticates with JWT in the header or first frame.",
      secPushH1: "Connection Lifecycle",
      secPushL1: "Connect → ws://host:8081/ws → server binds Channel to user ID.",
      secPushL2: "Heartbeat: client ping / server pong; timeout clears online status.",
      secPushL3: "Reconnect with exponential backoff; HTTP incremental sync for offline messages.",
      secPushH2: "Event Types",
      secPushL4: "NEW_MESSAGE — new chat message",
      secPushL5: "MESSAGE_READ — read receipt",
      secPushL6: "ONLINE_STATUS — friend online/offline",
      secPushL7: "CALL_SIGNAL — audio/video call signaling",
      secSyncTitle: "Multi-Device & Offline Sync",
      secSyncP1: "One account can be logged in on multiple devices. Messages are ordered by server timestamp and messageId.",
      secSyncH1: "Online Sync",
      secSyncP2: "All online devices receive WebSocket push. Read status changes are broadcast to every online endpoint.",
      secSyncH2: "Offline Catch-Up",
      secSyncP3:
        "Offline messages stay in MySQL. On reconnect, GET /api/message/sync?since={lastMessageId} fetches increments before resuming WebSocket.",
      calloutSyncTitle: "Consistency",
      calloutSyncBody: "Server persistence is the single source of truth. Local cache is for display; server wins on conflict.",
      secSignalingTitle: "Call & Meeting Signaling",
      secSignalingP1: "Media flows over WebRTC peer-to-peer; the server only relays signaling (SDP / ICE).",
      secSignalingL1: "Caller creates session via REST; server pushes CALL_INVITE to callee via WebSocket.",
      secSignalingL2: "On accept, both sides exchange SDP Offer/Answer and ICE Candidates over WebSocket.",
      secSignalingL3: "WebRTC connects; hang up sends CALL_HANGUP to release resources.",
      secSignalingP2: "Mesh meetings reuse the same signaling; each participant builds PeerConnections to others.",
    },
  };

  document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
  const dict = i18n[lang];
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const text = dict[el.dataset.i18n];
    if (text !== undefined) el.textContent = text;
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
