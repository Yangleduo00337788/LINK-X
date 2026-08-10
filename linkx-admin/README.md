<!-- 作者：yangleduo -->
# LinkX Admin

LinkX 管理端前端（Vue 3 + TypeScript + Vite + Naive UI）。

## 开发

```bash
npm install
npm run dev
```

默认地址：http://127.0.0.1:5174  
API 代理：`/api` → `http://127.0.0.1:8080`

## 构建

```bash
npm run build
```

## 登录说明

使用具备 `admin` 或 `super_admin` 角色的账号登录。  
需先启动 `linkx-server`，并确保 Flyway 已执行 `V5__admin_menu_and_permissions.sql`。
