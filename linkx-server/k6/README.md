# LinkX k6 压测

上线前用 [Grafana k6](https://k6.io/) 打 REST（`:8080/api`）和 IM WebSocket（`:8081/ws`）。

压测必须走独立的 **`k6` profile**（`.env.k6`），不要打正在开发的 `local`，更不要打 `prod`。大量 VU 在业务限流下会 429，关限流只能发生在压测档。

> 发消息走 WebSocket。`chat-ws.js` 默认只握手 + ping/pong；加 `K6_WS_SEND=1` 才会写聊天记录。SQL 直连压测见 `../scripts/benchmark-sql.py`。

## 后端：独立压测 env

```powershell
cd linkx-server
copy .env.k6.example .env.k6

# 现有 .env.local 需有 RATE_LIMIT_ENABLED=true（给日常开发用）
# IDE：Active profiles = k6
# 或命令行：
$env:SPRING_PROFILES_ACTIVE="k6"
mvn spring-boot:run
```

启动日志应出现 `loaded env file: ...\.env.k6 (profile=k6`，以及「压测档：RATE_LIMIT_ENABLED=false」。

`.env.k6` 叠在 `.env.local` 上：JWT/DB/MinIO 继承本地密钥，只覆盖：

| 项 | k6 档 | local / prod |
|----|--------|----------------|
| `RATE_LIMIT_ENABLED` | `false`（关 429） | `true`（生产启动校验强制 true） |
| `CAPTCHA_ENABLED` | `false` | local 可关；prod 开 |
| `REDIS_DATABASE` | `15` | `0` |

不要对生产用户库做登录爆破或 `K6_WS_SEND=1` 的写压测。

若必须对「仍开着限流」的预发打，才用 `python prep-k6-load.py --ip 出口IP` 加白名单；k6 档已关限流，不必改 Redis 白名单。

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|:----:|--------|------|
| `K6_BASE_URL` | 否 | `http://127.0.0.1:8080/api` | API 根路径 |
| `K6_WS_URL` | 否 | `ws://127.0.0.1:8081/ws` | IM WebSocket |
| `K6_WS_ORIGIN` | 否 | `http://127.0.0.1:5173` | WS Origin（须在 CORS 白名单） |
| `K6_USERNAME` | 是* | — | 压测账号 |
| `K6_PASSWORD` | 是* | — | 压测密码 |
| `K6_CONVERSATION_ID` | 否 | 自动取第一个会话 | 固定会话 ID |
| `K6_PROFILE` | 否 | `load` | `smoke` / `load` / `soak` / `stress` |
| `K6_VUS` / `K6_DURATION` | 否 | — | 覆盖档位的 VU 与持续时长 |
| `K6_WS_SEND` | 否 | 关闭 | `1` 时 WS 场景发送文本消息 |

\* `smoke.js` 无需账号

## 快速开始

```powershell
cd linkx-server/k6
copy k6.env.example k6.env   # 首次填写账号

# 公开接口冒烟（无需登录）
.\run.ps1 -Scenario smoke

# 上线前推荐：客户端冷启动读链路
.\run.ps1 -Scenario client-bootstrap -Profile load -ExportSummary

# IM 长连接
.\run.ps1 -Scenario chat-ws -Profile load

# 全量（约数分钟，soak/stress 会更长）
.\run-all.ps1 -Profile load -ExportSummary
```

Linux / macOS：

```bash
chmod +x run.sh
./run.sh smoke
K6_PROFILE=load ./run.sh client-bootstrap
```

直接调用 k6：

```powershell
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 client-bootstrap.js
k6 run -e K6_PROFILE=soak -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 chat-read.js
k6 run -e K6_USERNAME=你的账号 -e K6_PASSWORD=你的密码 chat-ws.js
```

## 场景说明

| 脚本 | 默认 load 档 | 覆盖 |
|------|----------------|------|
| `smoke.js` | 1 VU × 10s | `/health`、`/auth/config` |
| `auth-load.js` | 0→10 VU | 登录、`/user/me`、未读数（会打登录接口，勿对生产跑高档） |
| `chat-read.js` | 0→10 VU | 会话列表、历史消息、好友 |
| `client-bootstrap.js` | 0→20 VU | 首页、版本、资料、会话、通知、朋友圈、短视频 |
| `short-video-read.js` | 0→8 VU | 发现流 / 热榜 / 话题 |
| `chat-ws.js` | 0→30 VU | WS 握手、ping/pong；可选发消息 |
| `mixed.js` | 探针 2 VU + 认证读 | 健康检查 + 混合读 |

## 档位（`K6_PROFILE`）

| 档位 | 用途 | 规模（可用 `K6_VUS` 覆盖） |
|------|------|---------------------------|
| `smoke` | 脚本能否跑通 | 约 2 VU，几十秒 |
| `load` | 上线前常规压测 | 脚本默认 VU，约 1–3 分钟 |
| `soak` | 稳定性 | 20 VU × 10 分钟（`K6_DURATION` 可改） |
| `stress` | 找拐点 | 升到 100 VU |

例：`.\run.ps1 -Scenario client-bootstrap -Profile soak` 或 `-Vus 50 -Duration 3m`。

## 输出与阈值

默认阈值（`stress` 会放宽）：

- `http_req_failed < 1%`
- `p(95) < 800ms`，`p(99) < 1500ms`
- `chat-ws`：`ws_pong_ok > 90%`

```powershell
k6 run --summary-export=results/summary.json -e K6_USERNAME=... -e K6_PASSWORD=... client-bootstrap.js
```

报告在 `results/`（已 gitignore）。

## 建议顺序

1. `smoke`：服务活着
2. `client-bootstrap` + `chat-read`：读链路
3. `chat-ws`：连接数与心跳
4. 需要时再 `auth-load`（登录风控敏感）
5. 预发环境用 `soak`，再视容量跑 `stress`

## 注意事项

- 只打 **k6 profile** 启动的后端，不要打生产。
- k6 档已关限流时不必跑 `prep-k6-load.py`。
- 共享 token 场景使用固定 `X-Device-Id: k6-shared-session`，WebSocket `deviceId` 与之相同。
- 多用户可复制 `data/users.example.json` 为 `users.json` 后自行扩展脚本（当前默认单账号）。
- 若登录 400/403，检查是否仍走了 local/prod（验证码、锁定仍在）。
- 若 WS 不是 101：核对 Origin 白名单、token、以及登录设备 ID。
