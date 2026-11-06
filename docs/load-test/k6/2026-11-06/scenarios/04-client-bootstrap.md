<!-- 作者：yangleduo -->
# client-bootstrap · 客户端冷启动 / app cold start

脚本 `client-bootstrap.js`。模拟打开 App 后的一批读请求。默认 0→20 VU，约 2 分 40 秒。本次流量最大。

导出：`linkx-server/k6/results/client-bootstrap-20261106-221002.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 执行器 | executor | ramping-vus |
| 阶段 | stages | 20s→20，2m 保持 20，20s→0 |
| 峰值 VU | vus_max | 20 |

## 覆盖接口 / APIs

| 路径 Path | 标签 | 中文 | English |
|-----------|------|------|---------|
| `GET /app/homepage` | `app_homepage` | 首页编排 | Homepage |
| `GET /app/version` | `app_version` | 检查更新 | Version check |
| `GET /user/me` | `user_me` | 当前用户 | Current user |
| `GET /chat/sessions` | `chat_sessions` | 会话列表 | Conversations |
| `GET /chat/unread-total` | `chat_unread_total` | 未读总数 | Unread total |
| `GET /friend/list` | `friend_list` | 好友列表 | Friends |
| `GET /notifications/unread` | `notifications_unread` | 未读通知 | Notifications |
| `GET /moments?limit=20` | `moments_list` | 朋友圈 | Moments |
| `GET /short-video?limit=10` | `short_video_list` | 短视频发现流 | Short-video discover |

每迭代 9 个业务 GET（另加 setup 登录）。3302 迭代 × 9 ≈ 29718，与 29720 请求一致。

## 检查点 / Checks

下列每项 3302 通过、0 失败（另 setup 登录 3 次）：

homepage / version / user/me / sessions / unread-total / friend list / notifications / moments / short-video，各 status 200 + api ok。

合计 / Total：**59439 / 59439**

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | **29720** |
| RPS | rate | **184.91** |
| 失败率 | http_req_failed | 0% |
| 迭代 | iterations | 3302（20.54 /s） |
| 下行 | data_received | 110 954 109 B（约 **105.8 MB**） |
| 上行 | data_sent | 15 415 119 B（约 14.7 MB） |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 全请求 http_req_duration | 5.99 | 3.84 | 20.39 | **23.03** | 373.88 | 0 |
| 会话列表 `chat_sessions` | 3.59 | 3.56 | 4.16 | **4.49** | 18.50 | 0.39 |
| 朋友圈 `moments_list` | 4.21 | 4.15 | 4.83 | **5.15** | 60.77 | 0.47 |
| TTFB waiting | 5.33 | 3.31 | 19.20 | 22.52 | 372.28 | 0 |
| 收包 receiving | 0.66 | 0.49 | 1.79 | 2.35 | 4.61 | 0 |
| 发包 sending | 0.01 | 0 | 0 | 0 | 3.86 | 0 |
| 建连 connecting | 0.01 | 0 | 0 | 0 | 3.35 | 0 |
| 阻塞 blocked | 0.01 | 0 | 0 | 0 | 3.35 | 0 |
| TLS | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 850.33 | 853.59 | 1178.16 | 1212.26 | 1634.99 | 451.28 |

阈值：全请求 p95 `< 800`；`chat_sessions` p95 `< 800`；`moments_list` p95 `< 1200`。全部达标。

## 判定 / Verdict

通过 / **Pass**。打开 App 的读路径在 20 VU 下稳定，p95 23 ms。max ~374 ms 为长尾。
