# LinkX

> 企业级即时通讯与协同平台 — 桌面客户端

LinkX 是一款基于 **Vue 3 + Electron** 构建的跨平台即时通讯桌面应用，提供消息、联系人、收藏、文件、X友圈等核心模块的前端交互演示，后续将对接 Java 后端服务。

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
| 消息 | 单聊 / 群聊会话列表、消息收发、引用回复、文件/图片/链接消息 |
| 联系人 | 好友分组、好友/群通知、联系人资料卡 |
| 收藏 | 笔记、图片、链接、文件收藏展示 |
| 文件 | 最近文件、按类型筛选 |
| X友圈 | 独立窗口浏览动态（Electron） |
| 设置 | 通用、外观、通知、隐私等配置面板 |
| 锁屏 | 客户端锁屏保护 |
| 群聊扩展 | 群文件、群相册、群精华、群公告、语音/视频通话（演示） |

> 当前版本以前端 Mock 数据驱动，登录与消息收发均为本地模拟。

---

## 技术架构

```
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
│              api/client.ts（后端 API 占位层）           │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
              Java 后端服务（规划中，默认 :8080）
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

### 1. 安装依赖

```bash
npm install
```

### 2. Web 开发模式

```bash
npm run dev
```

浏览器访问 Vite 开发服务器地址（默认 `http://localhost:5173`）。

### 3. Electron 桌面开发模式

```bash
npm run electron:dev
```

### 4. 生产构建

```bash
# Web 构建
npm run build

# Electron 打包
npm run electron:build
```

---

## 项目结构

```
LinkX/
├── electron/                  # Electron 主进程
│   ├── main.ts                # 窗口创建、IPC 注册
│   └── preload.cjs            # 预加载脚本（CJS，注入 electronAPI）
├── src/
│   ├── api/
│   │   └── client.ts          # 后端 API 占位层
│   ├── assets/
│   │   └── styles.css         # 全局 Design Tokens + 公共工具类
│   ├── theme/
│   │   └── vars.ts            # JS/Naive UI 引用的主题常量
│   ├── components/            # Vue 组件
│   │   ├── chat/              # 聊天相关弹窗与抽屉
│   │   ├── contacts/          # 联系人通知
│   │   └── overlay/           # 叠加页容器
│   ├── data/
│   │   └── mockData.ts        # Mock 演示数据
│   ├── stores/                # Pinia 状态仓库
│   │   ├── app.ts             # 应用核心状态（导航、会话、消息、登录）
│   │   ├── chatModals.ts      # 聊天弹窗/抽屉开关
│   │   ├── overlay.ts         # 叠加页栈
│   │   ├── settings.ts        # 设置弹窗
│   │   └── secondaryView.ts   # 二级视图选中项
│   ├── types/                 # TypeScript 类型定义
│   ├── App.vue                # 根视图路由分发
│   ├── AppRoot.vue            # Naive UI 主题配置
│   └── main.ts                # 应用入口
├── index.html
├── vite.config.ts
├── uno.config.ts
├── tsconfig.json
└── package.json
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
| `useSecondaryViewStore` | `stores/secondaryView.ts` | 频道/应用/收藏选中项、菜单抽屉 |

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
| `openMoments()` | 打开 X友圈独立窗口 |
| `openNoteEditor()` | 打开笔记编辑器独立窗口 |
| `onMaximizedChange(cb)` | 监听最大化状态变化 |

主窗口采用 **无边框 + Windows Mica/Acrylic** 原生材质。

---

## 后端对接

API 占位层位于 `src/api/client.ts`，通过环境变量配置后端地址：

```bash
# .env
VITE_API_BASE_URL=http://localhost:8080
```

提供 `apiGet<T>` 与 `apiPost<T>` 两个基础方法，后续对接 Java 服务时在此扩展 WebSocket 与业务接口。

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

打包产物输出目录：`release/`（由 electron-builder 配置）。

---

## 依赖版本清单

以下为项目当前锁定的主要依赖版本（`npm ls --depth=0` 实测）：

### 生产依赖

| 包名 | 版本 | 用途 |
|------|------|------|
| vue | 3.5.39 | 前端框架 |
| pinia | 2.3.1 | 状态管理 |
| naive-ui | 2.44.1 | UI 组件库 |
| uuid | 14.0.1 | 唯一标识生成 |
| @vicons/fa | 0.12.0 | Font Awesome 图标 |
| @vicons/ionicons5 | 0.12.0 | Ionicons5 图标 |
| @vicons/material | 0.12.0 | Material 图标 |

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
| @types/uuid | 10.0.0 | uuid 类型声明 |

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

- [ ] 对接 Java 后端 REST API 与 WebSocket 实时消息
- [ ] 实现真实用户认证与 Token 管理
- [ ] 文件上传/下载与消息持久化
- [ ] 完善 X友圈、笔记编辑器业务逻辑
- [ ] 添加单元测试与 E2E 测试
- [ ] CI/CD 流水线集成

---

## 许可证

本项目为私有项目（`private: true`），未经授权不得对外分发。
