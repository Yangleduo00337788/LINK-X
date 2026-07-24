# LinkX

> 企业级即时通讯与协同平台 — 桌面客户端

LinkX 是一款基于 **Vue 3 + Electron** 构建的跨平台即时通讯桌面应用，配套 **Spring Boot** 后端，提供消息、联系人、朋友圈、日历、红包等完整 IM 能力。

---

## 目录

- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [状态管理](#状态管理)
- [Electron 能力](#electron-能力)
- [后端对接](#后端对接)
- [构建与打包](#构建与打包)
- [依赖版本清单](#依赖版本清单)
- [开发规范](#开发规范)
- [后续规划](#后续规划)

---

## 功能特性

| 模块 | 说明 |
|------|------|
| 消息 | 单聊 / 群聊会话列表、消息收发、引用回复、编辑/撤回/转发、文件/图片/语音/链接消息 |
| 会议 | 多人音视频会议（创建/加入/离开/静音/主持人管理/WebRTC 信令） |
| 联系人 | 好友分组、好友/群通知、联系人资料卡 |
| 收藏 | 笔记、图片、链接、文件收藏展示 |
| 文件 | 个人网盘（上传/文件夹/分享）、聊天与群文件聚合、按类型筛选 |
| 友链 | 独立窗口浏览动态、发布与搜索（Electron） |
| 设置 | 通用、外观、通知、隐私等配置面板 |
| 锁屏 | 客户端锁屏保护 |
| 群聊扩展 | 群文件、群相册、群精华、群公告、语音/视频通话（WebRTC） |

> 核心模块已对接 Java 后端（REST + WebSocket）：认证、好友、单聊/群聊、朋友圈、日历、笔记/收藏、个人网盘、红包、通知、WebRTC 通话等。

---

## 认证与安全

| 能力 | 说明 |
|------|------|
| 双 Token | Access Token（2h）+ Refresh Token（7d），Refresh 存 Redis 可吊销 |
| 自动登录 | 勾选「自动登录」后，启动时用 Refresh Token 换票，不存明文密码 |
| 验证码 | 图形验证码（4 位字母数字），注册/登录均可配置开关 |
| 限流 / 锁定 | 登录 IP 限流、连续失败 5 次锁定 15 分钟 |
| 安全存储 | Electron 下 Token / 锁屏 PIN 使用 `safeStorage` 加密落盘 |
| 401 统一 | HTTP 401 + `{ code: 401, message, data: null }`，前端自动 Refresh 重试 |

### 后端环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `JWT_SECRET` | 是 | JWT 签名密钥（≥32 字符），勿写入 `application.yml` |
| `DB_PASSWORD` | 是 | MySQL 密码 |
| `REDIS_PASSWORD` | 是 | Redis 密码 |
| `CAPTCHA_ENABLED` | 否 | 是否启用验证码，默认 `true` |
| `REQUIRE_HTTPS` | 否 | 是否强制 HTTPS，生产建议 `true` |
| `SPRING_PROFILES_ACTIVE` | 否 | 默认 `local`；决定加载 `.env.local` 或 `.env.prod` |

本地：维护 `linkx-server/.env.local`（可参考 `.env.local.example`）。
生产：维护 `.env.prod` 或注入同名系统环境变量。主配置仅 `application.yml` 一份。

### 前端环境变量

```bash
# linkx-client/.env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=ws://localhost:8081
```

---

## 技术架构

```text
┌─────────────────────────────────────────────────────┐
│                   Electron 主进程                    │
│         窗口管理 · IPC · 多窗口 · 系统材质            │
├─────────────────────────────────────────────────────┤
│                   Preload (CJS)                      │
│              contextBridge → electronAPI             │
├─────────────────────────────────────────────────────┤
│                   Vue 3 渲染进程                      │
│    AppRoot → App → AppShell / LoginView / 独立窗口    │
├──────────────┬──────────────┬───────────────────────┤
│  Naive UI    │   UnoCSS     │   Pinia 状态管理       │
├──────────────┴──────────────┴───────────────────────┤
│                                                     │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
              Java 后端服务（Spring Boot，默认 :8080 + WS :8081）
```

---

## 环境要求

| 工具 | 版本要求 |
|------|----------|
| Node.js | >= 18.x（推荐 22.x） |
| npm | >= 9.x |
| 操作系统 | Windows 10+ / macOS / Linux |

---

## 快速开始

本项目分为前端（`linkx-client`）和后端（`linkx-server`）两部分。

### 前端开发

```bash
cd linkx-client
npm install
```

#### Web 开发模式

```bash
npm run dev
```

#### Electron 桌面开发模式

```bash
npm run electron:dev
```

### 后端开发

```bash
cd linkx-server
# 一键启动中间件 (MySQL, Redis, MinIO)
docker-compose up -d
```
然后通过 IDE 运行 `LinkXServerApplication` 启动后端服务。

---

## 项目结构

```text
LinkX/
├── linkx-client/              # 前端工程 (Vue 3 + Electron)
│   ├── electron/              # Electron 主进程
│   ├── src/                   # Vue 3 渲染进程
│   ├── package.json
│   └── vite.config.ts
├── linkx-server/              # 后端工程 (Spring Boot 单体)
│   ├── docker-compose.yml     # 中间件容器编排
│   ├── pom.xml
│   └── src/main/java/         # Java 源码
└── README.md
```

---

## 状态管理

项目使用 **Pinia** 作为全局状态管理方案，替代原有的 composable 模块级 `ref` 方案。

| Store | 文件 | 职责 |
|-------|------|------|
| `useAppStore` | `stores/app.ts` | 导航、会话列表、消息、用户资料、登录/锁屏/主题 |
| `useChatModalsStore` | `stores/chatModals.ts` | 聊天相关弹窗与抽屉的开关状态 |
| `useOverlayStore` | `stores/overlay.ts` | 叠加页栈（设置、隐私、文件预览等） |
| `useSettingsStore` | `stores/settings.ts` | 设置弹窗可见性与当前 Tab |
| `useSecondaryViewStore` | `stores/secondaryView.ts` | 收藏/本地文件选中项 |

### 使用示例

```typescript
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const { navKey, currentSession } = storeToRefs(appStore)
const { setNav, sendMessage } = appStore
```

> 组件中读取响应式状态时请使用 `storeToRefs`；调用 action 时直接解构 store 实例。

---

## 样式与设计规范

项目采用 **CSS Design Tokens** 统一管理视觉变量，定义于 `src/assets/styles.css`。

### Token 分类

| 类别 | 变量示例 | 说明 |
|------|----------|------|
| 背景 | `--lx-bg-panel` / `--lx-bg-card` / `--lx-bg-input` | 面板、卡片、输入框背景 |
| 品牌色 | `--lx-accent` / `--lx-accent-hover` | 主色 `#12b7f5`（已统一，废弃 `#0099ff`） |
| 文字 | `--lx-text` / `--lx-text-body` / `--lx-text-muted` | 主文字 / 正文 / 次要文字 |
| 边框 | `--lx-border` / `--lx-divider` | 边框与分割线 |
| 圆角 | `--lx-radius` | 全局 9px |
| 阴影 | `--lx-shadow-soft` / `--lx-shadow-dropdown` | 卡片与下拉阴影 |

### 暗色主题

通过 `data-theme="dark"` 切换，`useAppStore().toggleTheme()` 会自动同步到 `<html>` 元素。

### 公共工具类

| 类名 | 用途 |
|------|------|
| `.lx-search-input` | 搜索栏 Naive Input 统一样式 |
| `.lx-icon-btn` | 32px 圆角图标按钮 |
| `.lx-text-muted` | 次要文字色 |
| `.lx-bg-panel` / `.lx-bg-card` | 背景色快捷类 |

### 脚本中使用

模板内 `n-icon` 等 props 引用 CSS 变量时，使用 `src/theme/vars.ts` 中的 `lxVar` 对象；Naive UI `themeOverrides` 使用 `naiveThemeColors`（须与 `--lx-accent` 保持一致）。

---

## Electron 能力

通过 `window.electronAPI` 暴露以下接口：

| 方法 | 说明 |
|------|------|
| `minimize()` | 最小化窗口 |
| `maximize()` | 最大化 / 还原窗口 |
| `close()` | 关闭窗口 |
| `togglePin()` | 切换窗口置顶 |
| `isMaximized()` | 查询是否最大化 |
| `isPinned()` | 查询是否置顶 |
| `openMoments()` | 打开友链独立窗口 |
| `openNoteEditor()` | 打开笔记编辑器独立窗口 |
| `setWindowMode(mode)` | 切换登录/主界面窗口尺寸 |
| `secureStorage` | OS 级加密存储（Token、锁屏 PIN） |
| `onMaximizedChange(cb)` | 监听最大化状态变化 |

主窗口采用 **无边框 + Windows Mica/Acrylic** 原生材质。

---

## 后端对接

前后端为独立工程，通过环境变量配置地址（参见 `linkx-client/.env.example`）：

```bash
# linkx-client/.env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=ws://localhost:8081
```

| 通道 | 默认地址 | 说明 |
|------|----------|------|
| REST API | `http://localhost:8080/api` | 所有 HTTP 接口（含 `/auth`、`/chat`、`/group` 等） |
| WebSocket | `ws://localhost:8081/ws` | 即时消息收发（Netty 独立端口） |
| Swagger | `http://localhost:8080/api/swagger-ui.html` | API 文档 |

### 已对接模块

- **认证**：登录 / 注册 / Refresh Token / 邮箱找回密码
- **聊天**：会话列表、历史消息、文件上传、WebSocket 实时消息
- **好友 / 群聊**：搜索、申请、群管理、群邀请
- **朋友圈 / 日历 / 笔记**：完整 CRUD
- **红包 / 余额 / 通知 / 反馈**：业务接口已接通
- **通话**：`/call/invite|accept|reject|cancel|hangup|signal` + WebSocket 实时信令，WebRTC 单聊音视频
- **会议**：`/conference/create|join|leave|end|info|active|mute|remove|transfer-host|signal|audio|video` + WebSocket 会议信令，多人音视频会议
- **合规**：用户数据导出（GDPR）、账号注销数据清除、过期数据自动清理
- **安全**：敏感词过滤（DFA 自动机）、用户黑名单、操作审计、登录风控、超大群消息风暴控制

### 本地开发

```bash
# 后端
cd linkx-server && docker-compose up -d
# IDE 运行 LinkXServerApplication（端口 8080 + 8081）

# 前端
cd linkx-client && npm install && npm run electron:dev
```

生产部署时在 `.env.prod` 配置 CORS 与 HTTPS：

```env
CORS_ALLOWED_ORIGINS=https://your-domain.com
REQUIRE_HTTPS=true
```

---

## 构建与打包

| 命令 | 说明 |
|------|------|
| `npm run dev` | Vite Web 开发服务器 |
| `npm run electron:dev` | Electron + Vite 热更新开发 |
| `npm run build` | TypeScript 检查 + Web 生产构建 |
| `npm run electron:build` | 完整 Electron 应用打包 |
| `npm run preview` | 预览 Web 构建产物 |
| `npm run electron:preview` | 预览 Electron 构建产物 |
| `npm run test` | 运行前端 Vitest 单元测试 |
| `npm run test:e2e` | 构建并在 Chromium 中运行 Playwright E2E 冒烟测试 |
| `npm run format` | 使用 Prettier 格式化前端代码 |
| `npm run format:check` | 校验前端代码格式，无修改 |

首次运行 E2E 测试前，请执行 `npx playwright install chromium` 安装浏览器。打包产物输出目录：`release/`（由 electron-builder 配置）。

---

## 依赖版本清单

以下为项目当前锁定的主要依赖版本（`npm ls --depth=0` 实测）：

### 生产依赖

| 包名 | 版本 | 用途 |
|------|------|------|
| vue | 3.5.39 | 前端框架 |
| pinia | 2.3.1 | 状态管理 |
| naive-ui | 2.44.1 | UI 组件库 |
| @vicons/ionicons5 | 0.12.0 | Ionicons5 图标 |

### 开发依赖

| 包名 | 版本 | 用途 |
|------|------|------|
| vite | 5.4.21 | 构建工具 |
| @vitejs/plugin-vue | 5.2.4 | Vue SFC 支持 |
| typescript | 5.9.3 | 类型系统 |
| vue-tsc | 2.2.12 | Vue TypeScript 检查 |
| unocss | 0.59.4 | 原子化 CSS |
| electron | 31.7.7 | 桌面运行时 |
| electron-builder | 24.13.3 | 应用打包 |
| vite-plugin-electron | 0.28.8 | Electron Vite 集成 |
| vite-plugin-electron-renderer | 0.14.7 | 渲染进程适配 |

### 运行时

| 工具 | 版本 |
|------|------|
| Node.js | 22.22.0（开发环境实测） |

---

## 开发规范

1. **状态管理**：全局共享状态统一放入 `src/stores/`，禁止在 composable 中使用模块级 `ref` 管理全局状态。
2. **组件通信**：父子组件使用 props / emit；跨组件状态使用 Pinia Store。
3. **类型安全**：所有业务实体类型定义在 `src/types/index.ts`，开启 TypeScript 严格模式。
4. **样式**：优先使用 UnoCSS 原子类；组件 scoped 样式遵循 BEM 命名习惯。
5. **Electron 安全**：渲染进程禁止开启 `nodeIntegration`；通过 preload + contextBridge 暴露有限 API。
6. **提交规范**：遵循 Conventional Commits，提交信息使用中文描述。

---

## 后续规划

- [x] 实现用户认证与双 Token 管理
- [x] 对接 WebSocket 实时消息（Netty，文本/图片/文件/语音）
- [x] 文件上传/下载与消息持久化（MinIO + MySQL）
- [x] 个人网盘（文件夹 / 分享 / 配额）
- [x] 完善友链业务逻辑（视频、编辑、分页搜索、嵌套回复）
- [x] 完善笔记编辑器媒体能力（图片/附件/语音/位置）
- [x] 添加单元测试（前端 Vitest / 后端 JUnit）
- [x] E2E 测试
- [x] CI/CD 流水线集成
- [ ] Prettier 代码格式化工具接入
- [x] 多人音视频会议
- [x] 合规与数据治理（数据导出/账号注销/过期清理）
- [x] 超大群消息风暴控制
- [x] 敏感词过滤集成
- [x] IM 主链路端到端测试
- [x] WebSocket 实时链路异常测试
- [x] 异常路径测试

---

## 许可证

本项目为私有项目（`private: true`），未经授权不得对外分发。
