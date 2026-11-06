<!-- 作者：yangleduo -->
# chat-read · 聊天读 / chat read

脚本 `chat-read.js`。`setup()` 登录一次，VU 复用 token。默认 0→10 VU，约 75 s。

导出：`linkx-server/k6/results/chat-read-20261106-220846.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 执行器 | executor | ramping-vus |
| 阶段 | stages | 15s→10，45s 保持 10，15s→0 |
| 峰值 VU | vus_max | 10 |

## 覆盖接口 / APIs

| 路径 Path | 标签 | 中文 | English | 本次是否打到 |
|-----------|------|------|---------|--------------|
| `GET /chat/sessions` | `chat_sessions` | 会话列表 | Conversation list | 是 / yes |
| `GET /chat/sessions/{id}/messages` | `chat_messages` | 历史消息 | Message history | **否 / no** |
| `GET /chat/sessions/{id}/unread` | `chat_session_unread` | 会话未读 | Session unread | **否 / no** |
| `GET /friend/list` | `friend_list` | 好友列表 | Friend list | 是 / yes |

`setup_data.conversationId` 为空：`k6_load_test` 在 `im_conversation_member` 中无会话。

## 检查点 / Checks

| 检查 Check | 通过 | 失败 |
|-------------|-----:|-----:|
| login（setup）三项 | 1×3 | 0 |
| sessions status 200 / api ok | 918 × 2 | 0 |
| friend list status 200 / api ok | 918 × 2 | 0 |
| **合计 Total** | **3675** | **0** |

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 1838 |
| RPS | rate | 24.40 |
| 失败率 | http_req_failed | 0% |
| 迭代 | iterations | 918（12.19 /s） |
| 下行 | data_received | 6 021 207 B（约 5.74 MB） |
| 上行 | data_sent | 943 251 B（约 0.90 MB） |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 全请求 http_req_duration | 3.91 | 3.78 | 4.33 | **4.54** | 268.81 | 0.36 |
| 会话列表 `chat_sessions` | 3.91 | 4.00 | 4.39 | **4.56** | 10.08 | 0.36 |
| 历史消息 `chat_messages` | 0 | 0 | 0 | 0 | 0 | 0 |
| TTFB waiting | 3.04 | 3.06 | 3.90 | 4.16 | 268.05 | 0.33 |
| 收包 receiving | 0.86 | 0.63 | 2.36 | 2.75 | 5.06 | 0 |
| 发包 sending | 0.01 | 0 | 0 | 0 | 3.75 | 0 |
| 建连 connecting | 0.01 | 0 | 0 | 0 | 1.06 | 0 |
| 阻塞 blocked | 0.01 | 0 | 0 | 0 | 1.06 | 0 |
| TLS | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 657.29 | 647.55 | 931.41 | 968.41 | 1007.02 | 307.94 |

阈值：全请求 p95 `< 800`；`chat_sessions` p95 `< 600`；`chat_messages` p95 `< 1000`。全部达标。max 268 ms 为长尾，不是平台。

## 判定 / Verdict

通过 / **Pass**。若要压历史消息，给 `k6_load_test` 建私聊会话，或设 `K6_CONVERSATION_ID`。
