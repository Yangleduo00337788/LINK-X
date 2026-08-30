<!-- 作者：yangleduo -->
# LinkX Client

LinkX 跨平台桌面 IM 客户端（Vue 3 + TypeScript + Electron + Pinia + Naive UI）。

完整说明见仓库根目录 **[README.md](../README.md)**。

## 环境要求

- Node.js 18+（推荐 20 / 22）
- 已启动 `linkx-server`（REST `8080`、WebSocket `8081`）

## 配置

| 文件 | 场景 |
|------|------|
| `.env` | 本地开发（参考 `.env.example`） |
| `.env.electron` | 打包安装包（参考 `.env.electron.example`） |

```bash
copy .env.example .env          # Windows；Linux/macOS 用 cp
```

关键变量：`VITE_API_BASE_URL`、`VITE_WS_BASE_URL`；可选 `VITE_LEGAL_PAGE_BASE_URL`、`VITE_HELP_PAGE_BASE_URL`。

## 开发

```bash
npm install
npm run electron:dev            # Electron 桌面模式（推荐）
npm run dev                     # 纯浏览器调试（Vite :5173）
```

## 构建

```bash
npm run electron:build          # Windows 安装包 → release/installer/
npm run electron:build:mac      # macOS DMG
npm run electron:build:linux    # Linux AppImage
```

打包前须配置 `.env.electron` 中的线上 API / WS 地址。详见根 README **[九、构建与部署 → 9.2](../README.md#92-桌面客户端)**。

## 常用命令

| 命令 | 说明 |
|------|------|
| `npm run installer:assets` | 生成安装向导图标、许可协议等资源 |
| `npm run installer:dev` | 开发调试安装向导 |
| `npm run clean:release` | 清理 `release/` 构建产物 |
| `npm run format:check` | Prettier 检查 |
| `npm run test` | Vitest 单元测试 |
| `npm run test:e2e` | Playwright E2E（见 `e2e/README.md`） |

## 版本与更新

- 构建版本号：`src/utils/appVersion.ts` 中的 `APP_CLIENT_VERSION`（发版时与 `package.json` 同步）
- 启动时自动检查更新（`startupVersion.ts`），Electron 后台静默下载；更新说明由管理端版本发布配置
- 打包后可用 `scripts/publish-release.mjs` 上传安装包并发布至管理端

### 版本规划

与根目录 [CHANGELOG.md](../CHANGELOG.md) 保持一致：

| 版本 | 主题 |
|------|------|
| **1.1.0** | Linux 桌面端（`electron:build:linux`） |
| **1.2.0** | 灵伴知识库与 Agent 策略、本地搜索与消息同步、短视频推荐与运营 |

当前已发布：**1.0.1**（Windows 桌面端）。

## UI 与样式

新增或改版页面须遵循 Design Token（`--lx-*`）与公共组件规范，详见根 README **[8.5 客户端 UI 与样式规范](../README.md#85-客户端-ui-与样式规范)**。
