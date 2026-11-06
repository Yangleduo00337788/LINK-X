<!-- 作者：yangleduo -->
# chat-ws · IM 长连接 / IM WebSocket

脚本 `chat-ws.js`。握手 + ping/pong。默认 **不发聊天消息**（未设 `K6_WS_SEND=1`）。load：0→30 VU，约 2 分 40 秒；单连接保持约 25 s。

导出：`linkx-server/k6/results/chat-ws-20261106-221358.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 执行器 | executor | ramping-vus |
| 阶段 | stages | 20s→30，2m 保持 30，20s→0 |
| 峰值 VU | vus_max | 30 |
| 连接保持 | K6_WS_HOLD_MS | 25000 |
| 发消息 | K6_WS_SEND | 关闭 / off |

## 覆盖行为 / Behavior

| 中文 | English |
|------|---------|
| `Sec-WebSocket-Protocol: linkx-access-token, <jwt>` | 子协议鉴权 |
| Origin `http://127.0.0.1:5173` | 须在 CORS 白名单 |
| `deviceId=k6-shared-session` | 与登录设备一致 |
| action `ping` → `pong` | 心跳 |
| 未发送 action `send` | 不写聊天库 |

## 检查点 / Checks

| 检查 Check | 通过 | 失败 |
|-------------|-----:|-----:|
| setup 登录三项 | 3 | 0 |
| ws handshake 101 | 164 | 0 |
| received pong | 164 | 0 |
| **合计 Total** | **331** | **0** |

## WebSocket

| 中文 | English | 值 |
|------|---------|-----|
| 连接数 | ws_sessions | **182** |
| 连接速率 | ws_sessions rate | 1.07 /s |
| 发送帧 | ws_msgs_sent | 526 |
| 接收帧 | ws_msgs_received | 526 |
| ping/pong 成功率 | ws_pong_ok | **100%**（526/526） |
| WS 错误 | ws_errors | **0** |
| 迭代 | iterations | 164 |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 自定义连上 ws_connect_ms | 10.24 | 9 | 12 | **13.95** | 229 | 4 |
| 内置握手 ws_connecting | 10.19 | 8.35 | 12.39 | **13.43** | 228.71 | 3.99 |
| 连接保持 ws_session_duration | 25010.96 | 25008.99 | 25013.19 | 25014.28 | 25229.10 | 25004.22 |
| 迭代 iteration_duration | 25211.35 | 25209.42 | 25213.58 | 25214.65 | 25429.31 | 25204.66 |

迭代约 25.2 s，与 hold 25 s + 收尾一致。

## HTTP（仅 setup）

setup 登录 + 拉会话，共 2 个 HTTP 请求，失败率 0%。该段 p95 365 ms 是登录 BCrypt，不代表 WS 路径。

| 中文 | English | 值 |
|------|---------|-----|
| HTTP 请求 | http_reqs | 2 |
| 失败率 | http_req_failed | 0% |
| 下行 / 上行 | data in/out | 154233 B / 122681 B |

阈值：`ws_pong_ok > 90%`、`ws_errors count < 80`、HTTP p95 `< 800`。全部达标。

## 判定 / Verdict

通过 / **Pass**。30 VU 长连接稳定。要压发消息需 `K6_WS_SEND=1` 且账号有会话。
