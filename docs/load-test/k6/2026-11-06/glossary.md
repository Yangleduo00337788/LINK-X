<!-- 作者：yangleduo -->
# 指标词典 / Metric glossary

## 执行 / Execution

| k6 字段 | 中文 | English |
|---------|------|---------|
| `vus` | 当前虚拟用户数 | Current virtual users |
| `vus_max` | 峰值虚拟用户数 | Maximum virtual users |
| `iterations` | 脚本循环次数 | Completed script iterations |
| `iteration_duration` | 单次迭代总时长（含 sleep） | Full iteration time including sleep |
| `checks` | 断言通过 / 失败 | Assertion passes / fails |

## HTTP

| k6 字段 | 中文 | English |
|---------|------|---------|
| `http_reqs` | HTTP 请求总数 | Total HTTP requests |
| `http_reqs.rate` | 每秒请求数 | Requests per second (RPS) |
| `http_req_failed` | HTTP 失败率 | HTTP failure rate |
| `http_req_duration` | 整段请求耗时 | End-to-end request duration |
| `http_req_duration{expected_response:true}` | 仅成功响应的耗时 | Duration of expected (successful) responses |
| `http_req_blocked` | 等待空闲连接 | Time blocked waiting for a TCP connection |
| `http_req_connecting` | TCP 建连 | TCP connect time |
| `http_req_tls_handshaking` | TLS 握手 | TLS handshake time |
| `http_req_sending` | 发送请求体 | Time sending the request |
| `http_req_waiting` | 等待首字节（TTFB） | Time to first byte |
| `http_req_receiving` | 接收响应体 | Time receiving the response |
| `data_received` | 下行字节 | Bytes received |
| `data_sent` | 上行字节 | Bytes sent |

## 分位与统计 / Statistics

| k6 字段 | 中文 | English |
|---------|------|---------|
| `avg` | 平均值 | Average |
| `med` | 中位数（p50） | Median (p50) |
| `min` | 最小值 | Minimum |
| `max` | 最大值 | Maximum |
| `p(90)` | 90 分位 | 90th percentile |
| `p(95)` | 95 分位 | 95th percentile |
| `p(99)` | 99 分位 | 99th percentile |
| `count` | 计数 | Count |
| `rate` | 速率或比率 | Rate |

本次部分导出 JSON **没有 p(99) 数值**（阈值仍配置了 `p(99)<1500`）。有则写出，无则标 `—`。

## WebSocket

| k6 字段 | 中文 | English |
|---------|------|---------|
| `ws_sessions` | WebSocket 连接数 | WebSocket sessions opened |
| `ws_connecting` | 内置握手耗时 | Built-in connecting duration |
| `ws_connect_ms` | 自定义连上耗时 | Custom connect latency (ms) |
| `ws_session_duration` | 连接保持时长 | How long a session stayed open |
| `ws_msgs_sent` | 发送帧数 | Messages sent |
| `ws_msgs_received` | 接收帧数 | Messages received |
| `ws_pong_ok` | ping 收到 pong 的比例 | Pong success rate |
| `ws_errors` | WS 错误次数 | WebSocket error count |

## 场景脚本 / Scenario scripts

| 脚本 | 中文 | English |
|------|------|---------|
| `smoke.js` | 公开冒烟 | Public smoke |
| `auth-load.js` | 认证 / 登录压测 | Auth / login load |
| `chat-read.js` | 聊天读链路 | Chat read path |
| `client-bootstrap.js` | 客户端冷启动读 | App cold-start reads |
| `short-video-read.js` | 短视频读 | Short-video reads |
| `chat-ws.js` | IM 长连接 | IM WebSocket |
| `mixed.js` | 健康探针 + 认证读 | Health probe + authenticated reads |

## 接口标签 / Request name tags

| tag `name:` | 中文 | English |
|-------------|------|---------|
| `health` | 健康检查 | Health |
| `health_live` | 存活探针 | Liveness |
| `auth_config` | 认证配置 | Auth config |
| `auth_login` | 登录 | Login |
| `user_me` | 当前用户 | Current user |
| `chat_unread_total` | 未读总数 | Unread total |
| `chat_sessions` | 会话列表 | Conversation list |
| `chat_messages` | 历史消息 | Message history |
| `chat_session_unread` | 会话未读 | Session unread |
| `friend_list` | 好友列表 | Friend list |
| `app_homepage` | 客户端首页 | App homepage |
| `app_version` | 版本检查 | Version check |
| `notifications_unread` | 未读通知 | Unread notifications |
| `moments_list` | 朋友圈列表 | Moments feed |
| `short_video_list` | 短视频发现流 | Short-video discover |
| `short_video_hot` | 短视频热榜 | Short-video hot |
| `short_video_topics_hot` | 热门话题 | Hot topics |
| `im_ws` | IM WebSocket | IM WebSocket |
