# LinkX k6 API 压测

基于 [Grafana k6](https://k6.io/) 的 HTTP API 负载测试，覆盖健康检查、登录、聊天读链路等。

> 发消息走 WebSocket（`:8081/ws`），本套件压测 REST API（`:8080/api`）。SQL 直连压测见 `../scripts/benchmark-sql.py`。

## 前置条件

1. 本机已安装 k6（`k6 version`）
2. `linkx-server` 已启动（`http://127.0.0.1:8080/api`）
3. 压测账号已存在，且 **图形验证码已关闭**（`CAPTCHA_ENABLED=false`）
4. **限流白名单**：`run.ps1` 会自动执行 `prep-k6-load.py`，将 `127.0.0.1` 加入 Redis 白名单；否则高频请求会返回 **429**

```powershell
# 手动准备（可选）
python prep-k6-load.py
```

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|:----:|--------|------|
| `K6_BASE_URL` | 否 | `http://127.0.0.1:8080/api` | API 根路径 |
| `K6_USERNAME` | 是* | — | 压测账号 |
| `K6_PASSWORD` | 是* | — | 压测密码 |
| `K6_CONVERSATION_ID` | 否 | 自动取第一个会话 | 固定会话 ID |

\* `smoke.js` 无需账号

## 快速开始

```powershell
cd linkx-server/k6

# 1. 公开接口冒烟（无需登录）
k6 run smoke.js

# 2. 认证压测
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 auth-load.js

# 3. 聊天读链路（推荐）
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 chat-read.js

# 4. 混合场景
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 mixed.js

# 5. 短视频读链路
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 short-video-read.js
```

或使用封装脚本（PowerShell，自动读取 `k6.env`）：

```powershell
copy k6.env.example k6.env   # 首次配置账号
.\run.ps1 -Scenario smoke
.\run.ps1 -Scenario chat-read -ExportSummary

# 全量（smoke + auth-load + chat-read + mixed，约 4 分钟）
.\run-all.ps1 -ExportSummary
```

## 场景说明

| 脚本 | VU / 时长 | 覆盖接口 |
|------|-----------|----------|
| `smoke.js` | 1 VU × 10s | `/health`、`/auth/config` |
| `auth-load.js` | 0→10 VU，70s | `/auth/login`、`/user/me`、`/chat/unread-total` |
| `chat-read.js` | 0→10 VU，75s | `/chat/sessions`、`/messages`、`/friend/list` |
| `short-video-read.js` | 0→8 VU，70s | `/short-video`、`/short-video/hot`、`/short-video/topics/hot` |
| `mixed.js` | 公开 2 VU + 认证 15 VU | 健康探针 + 混合读请求 |

## 输出与阈值

默认阈值（可在各脚本 `options.thresholds` 调整）：

- `http_req_failed < 1%`
- `p(95) < 800ms`，`p(99) < 1500ms`

导出 JSON 报告：

```powershell
k6 run --summary-export=results/summary.json -e K6_USERNAME=... -e K6_PASSWORD=... chat-read.js
```

## 目录结构

```text
k6/
├── lib/
│   ├── config.js    # 环境变量与阈值
│   └── client.js    # 登录、请求封装
├── data/
│   └── users.example.json
├── smoke.js
├── auth-load.js
├── chat-read.js
├── mixed.js
├── prep-k6-load.py   # 压测前：白名单 + 清理限流计数
└── run.ps1
```

## 注意事项

- 压测会触发登录风控与限流，建议在**本地/测试环境**运行，勿对生产账号高频登录。
- `run.ps1` 会在每次运行前将 `127.0.0.1` 加入限流白名单；压测结束后可在管理端移除白名单。
- 共享 token 场景（`chat-read`）使用固定 `X-Device-Id: k6-shared-session`，与 `setup()` 登录设备一致。
- 多用户压测可复制 `data/users.example.json` 为 `users.json` 并扩展脚本（当前默认单账号）。
- 若登录返回 400/403，检查验证码开关、账号锁定状态与密码策略。
