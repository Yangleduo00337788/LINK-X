<!-- 作者：yangleduo -->
# LinkX Admin

LinkX 管理端前端（Vue 3 + TypeScript + Vite + Naive UI + ECharts）。

完整说明见仓库根目录 **[README.md](../README.md)**，快速上手见 **[6.6 启动管理后台](../README.md#66-启动管理后台可选)**。

## 环境要求

- Node.js 18+
- 已启动 `linkx-server`（`http://127.0.0.1:8080`）

## 配置

复制环境变量模板（可选，开发默认通过 Vite 代理访问后端）：

```bash
copy .env.example .env          # Windows；Linux/macOS 用 cp
```

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 默认 `/api`（开发代理至 `8080`） |
| `VITE_API_DIRECT_URL` | 可选，大文件上传直连接后端 |

## 开发

```bash
npm install
npm run dev
```

默认地址：http://127.0.0.1:5174  
API 代理：`/api` → `http://127.0.0.1:8080`

## 构建

```bash
npm run build                   # 输出 dist/
```

## 代码检查

```bash
npm run lint
npm run format:check
```

## 登录说明

使用具备 `admin` 或 `super_admin` 角色的账号登录。  
需先启动 `linkx-server`，并确保 Flyway 已执行 `V5__admin_menu_and_permissions.sql`。

## 版本发布

在「版本发布」菜单维护客户端安装包与 **更新说明**（`releaseNotes`）：

1. 新建草稿 → 填写版本号、渠道、平台、更新说明、下载地址 / SHA-256
2. 发布后对客户端立即生效（`GET /app/version`）
3. 同步更新官网 `linkx-website/shared/changelog-data.js` 与根目录 `CHANGELOG.md`

也可使用 `linkx-client/scripts/publish-release.mjs` 在打包后自动上传并发布。

### 版本规划

与根目录 [CHANGELOG.md](../CHANGELOG.md) 保持一致：

| 版本 | 主题 |
|------|------|
| **1.1.0** | Linux 桌面端、Android 移动端 |
| **1.2.0** | 灵伴知识库与 Agent 策略、本地搜索与消息同步、短视频推荐与运营 |
