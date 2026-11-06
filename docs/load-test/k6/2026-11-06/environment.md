<!-- 作者：yangleduo -->
# 压测环境 / Test environment

本次 **没有** 打正在开发的 `local` 进程，也没有打 `prod`。后端用独立 profile `k6` 启动。

## 为什么独立 env / Why a dedicated env

业务限流在高 VU 下会返回 **HTTP 429**。关限流只能发生在压测档：`RATE_LIMIT_ENABLED=false`。生产启动校验会拒绝该开关。

## 启动参数 / Startup

| 中文 | English | 值 |
|------|---------|-----|
| Spring 配置 | Spring profile | `k6` |
| 配置文件 | Env file | `linkx-server/.env.k6`（叠在 `.env.local` 上） |
| API 端口 | HTTP port | `18080`（本机 8080 已被其他 Java 进程占用） |
| IM 端口 | WebSocket port | `18081` |
| API 根路径 | API base | `http://127.0.0.1:18080/api` |
| WS 地址 | WS URL | `ws://127.0.0.1:18081/ws` |
| WS Origin | WS Origin | `http://127.0.0.1:5173` |
| Redis 库 | Redis DB | `15`（与开发库 0 隔离会话/限流键） |
| MySQL | MySQL | 同一 `linkx` 库，复用压测账号 |
| 验证码 | Captcha | 关闭 / disabled |
| 接口限流 | Rate limit | 关闭 / disabled |
| Snail Job | Job client | 关闭 / disabled |
| 压测账号 | Username | `k6_load_test` |
| k6 版本 | k6 version | v2.3.0 windows/amd64 |
| 后端 | Backend | Spring Boot 3.5 / JDK 21，`Started LinkXServerApplication in 14.402 seconds` |

启动命令：

```powershell
cd linkx-server
$env:SPRING_PROFILES_ACTIVE = "k6"
mvn -DskipTests spring-boot:run
```

```powershell
cd linkx-server/k6
.\run-all.ps1 -Profile load -ExportSummary
```

## 压了什么 / What was stressed

| 通道 Channel | 默认地址 Default | 本次实际 Actual | 用途 Purpose |
|--------------|------------------|-----------------|--------------|
| REST | `:8080/api` | `:18080/api` | 认证、会话、好友、朋友圈、短视频等读接口 |
| WebSocket | `:8081/ws` | `:18081/ws` | 长连接、心跳 ping/pong |

背后会打到 Spring Boot、Netty IM、MySQL、Redis。默认读多写少。

## 未覆盖 / Out of scope

- 桌面客户端 / Electron 渲染
- 管理后台
- 文件上传与 MinIO 大流量（本次 MinIO `:9000` 未连通，不影响读压测）
- 音视频会议
- 发消息写库（需 `K6_WS_SEND=1`）
- 生产环境真实限流与验证码
- 聊天历史消息（账号无会话）

## 套件文件 / Artifact files

| 类型 | 路径 |
|------|------|
| 套件日志 | `linkx-server/k6/results/full-suite-20261106-220721.log` |
| smoke | `smoke-20261106-220721.json` |
| auth-load | `auth-load-20261106-220733.json` |
| chat-read | `chat-read-20261106-220846.json` |
| client-bootstrap | `client-bootstrap-20261106-221002.json` |
| short-video-read | `short-video-read-20261106-221246.json` |
| chat-ws | `chat-ws-20261106-221358.json` |
| mixed | `mixed-20261106-221650.json` |

以上 JSON 的 `setup_data` 含 JWT，仅本地保留。
