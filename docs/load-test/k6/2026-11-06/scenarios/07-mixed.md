<!-- 作者：yangleduo -->
# mixed · 探针 + 认证读 / probe + authenticated read

脚本 `mixed.js`。公开健康探针与认证读并行。认证流复用 setup token（不再每轮登录）。

导出：`linkx-server/k6/results/mixed-20261106-221650.json`

## 档位 / Load profile

| 场景 Scenario | 执行器 executor | 配置 |
|---------------|-----------------|------|
| `public_probe` | constant-vus | 2 VU × 90 s，开始即跑 |
| `authenticated` | ramping-vus | 5s 后 15s→15，60s 保持，15s→0 |

峰值合计 vus_max = **17**。

## 覆盖接口 / APIs

| 路径 Path | 中文 | English |
|-----------|------|---------|
| `GET /health` | 健康检查 | Health |
| `GET /health/live` | 存活探针 | Liveness |
| `GET /user/me` | 当前用户 | Current user |
| `GET /chat/sessions` | 会话列表 | Conversations |
| `GET /chat/unread-total` | 未读总数 | Unread total |
| `GET /friend/list` | 好友列表 | Friends |
| `GET /moments?limit=10` | 朋友圈 | Moments |
| `GET .../messages` | 历史消息 | Messages（本次无会话，跳过） |

## 检查点 / Checks

| 检查 Check | 通过 | 失败 |
|-------------|-----:|-----:|
| setup 登录三项 | 3 | 0 |
| health ok | 90 | 0 |
| live ok | 90 | 0 |
| user/me ok | 1120 | 0 |
| sessions ok | 1120 | 0 |
| **合计 Total** | **2423** | **0** |

未读/好友/朋友圈有请求，脚本未对它们单独 `check`。

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 5782 |
| RPS | rate | 60.28 |
| 失败率 | http_req_failed | 0% |
| 迭代 | iterations | 1210（12.62 /s） |
| 下行 | data_received | 19 287 519 B（约 18.4 MB） |
| 上行 | data_sent | 2 893 693 B（约 2.76 MB） |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 全请求 http_req_duration | 3.96 | 3.97 | 4.62 | **4.88** | 367.55 | 0.37 |
| TTFB waiting | 3.23 | 3.36 | 4.19 | 4.44 | 366.49 | 0 |
| 收包 receiving | 0.72 | 0.51 | 1.98 | 2.68 | 4.06 | 0 |
| 发包 sending | 0.01 | 0 | 0 | 0 | 3.57 | 0 |
| 建连 connecting | 0.01 | 0 | 0 | 0 | 3.21 | 0 |
| 阻塞 blocked | 0.01 | 0 | 0 | 0 | 3.21 | 0 |
| TLS | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 1087.45 | 1041.76 | 1477.85 | 2002.82 | 2004.86 | 521.54 |

探针迭代含 `sleep(2)`，认证流含 0.5–1.5 s sleep，故 iteration_duration 会出现 ~2 s。

## 判定 / Verdict

通过 / **Pass**。混合读 p95 4.88 ms，失败率 0%。
