# LinkX E2E（Playwright）

覆盖 API 冒烟、管理端登录/灵伴配置、客户端登录与 Agent 开关的端到端自动化。

## 前置条件

1. 启动 `linkx-server`（默认 `8080`）
2. 启动管理端：`cd linkx-admin && npm run dev`（**固定 `5174`**）
3. 启动客户端：`cd linkx-client && npm run dev`（**固定 `5173`**）
4. 执行 Flyway 迁移（含 `V122__linkmate_agent_group_ai_defaults.sql`）
5. 测试环境建议 **关闭图形验证码**（`CAPTCHA_ENABLED=false`）
6. 准备测试账号（也可写入 `linkx-server/.env.local` 的 `E2E_*` 变量）：
   - 管理端：有 `admin:setting:view` 权限，**未启用 TOTP**
   - 客户端：可正常登录的普通用户

> **端口注意（Windows）**：客户端 Vite 默认监听 `[::1]:5173`，E2E 请使用 `http://localhost:5173`（已在 `playwright.config.ts` 配置）。若 `5173` 被占用导致客户端改绑到 `5174`，会与管理端冲突，请先释放端口。

## 配置

```bash
cp e2e/.env.example e2e/.env
# 编辑 e2e/.env 填入账号密码
```

PowerShell 也可直接设置环境变量后运行：

```powershell
$env:E2E_ADMIN_USER='admin'
$env:E2E_ADMIN_PASSWORD='***'
$env:E2E_CLIENT_USER='user1'
$env:E2E_CLIENT_PASSWORD='***'
npm run test:e2e
```

`loadLocalEnv.ts` 会自动读取 `linkx-server/.env.local` 中以 `E2E_` 开头的变量。

## 安装浏览器

```bash
npm run test:e2e:install
```

## 运行

```bash
# 全部 E2E
npm run test:e2e

# 分项目
npm run test:e2e:api      # 仅 API 冒烟（无需 UI 服务）
npm run test:e2e:admin    # 管理端
npm run test:e2e:client   # 客户端

# 带界面调试
npx playwright test --ui
```

## 用例说明

| 文件 | 覆盖点 |
|------|--------|
| `api/smoke.spec.ts` | `/health`、`/auth/config`、`/admin/auth/config` |
| `admin/login-smoke.spec.ts` | 登录进控制台、未登录跳转登录页 |
| `admin/linkmate-settings.spec.ts` | 深度思考推断、群 AI 区块、Agent 开关联动 |
| `client/session-smoke.spec.ts` | 登录页表单、UI 登录后会话列表 |
| `client/linkmate-agent.spec.ts` | `agentEnabled` 开关与代操按钮 |

客户端 Agent 用例通过 mock `/linkmate/status` 验证 UI，无需反复改管理端配置。

## 报告

失败后可查看：

```bash
npx playwright show-report
```
