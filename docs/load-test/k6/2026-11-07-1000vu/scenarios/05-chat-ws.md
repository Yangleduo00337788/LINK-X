<!-- 作者：yangleduo -->
# chat-ws · IM 长连接 / WebSocket

导出：`linkx-server/k6/results/chat-ws-20261107-135643.json`  
判定 / Verdict：**Pass**（`ws_pong_ok` 96.3% &gt; 90%，`ws_errors=0`）

未开 `K6_WS_SEND`，只握手 + ping/pong。

## 档位 / Load profile

恒定 1000 VU × 2m。单连接 hold `K6_WS_HOLD_MS=25000`。全部 VU 共用 `SHARED_DEVICE_ID=k6-shared-session`。

## 检查点 / Checks

| 检查 | 通过 | 失败 |
|------|-----:|-----:|
| ws handshake 101 | 5000 | 574 |
| received pong | 5000 | 574 |
| setup 登录 | 3 | 0 |
| **合计** | **10003** | **1148**（89.7%） |

检查点低于 90%，但自定义阈值看的是 `ws_pong_ok` 与 `ws_errors`。

## WebSocket

| 中文 | English | 值 |
|------|---------|-----|
| 会话 | ws_sessions | 5574 |
| 发出/收到消息 | ws_msgs_sent / received | 15000 / 15000 |
| pong 成功率 | ws_pong_ok | **96.31%**（15000 / 15574） |
| 错误 | ws_errors | **0** |
| 建连 p50 / p95 / max | ws_connect_ms | 127 / **617** / 905 ms |
| 会话时长 med | ws_session_duration | 25106 ms |

HTTP 仅 setup 登录 2 次，p95 263 ms。

握手失败与缺 pong 数量一致：新连接顶掉旧 device 会话，或 1000 路同时连时有一部分未完成 101。服务端没有把错误计数打到 `ws_errors`。

后端日志约 13:53 出现 **1000** 条 `WARN ImWebSocketAuthHandler … Connection reset`（连接被对端重置），无 ERROR。这就是「少量 IM 连接被重置」。
