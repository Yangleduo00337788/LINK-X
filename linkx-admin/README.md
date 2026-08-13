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
