<!-- 作者：yangleduo -->
# chat-read · 聊天读 / chat read

导出：`linkx-server/k6/results/chat-read-20261107-134534.json`  
判定 / Verdict：**Pass**（exit 0）

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 意图 stages | Intended | 1m→1000，2m 保持，30s→0 |
| 实际 | Actual | 恒定 **1000 VU × 2m** |
| vus_max | vus_max | 1000 |

## 覆盖接口 / APIs

| 路径 | 标签 | 打到 |
|------|------|------|
| `GET /chat/sessions` | `chat_sessions` | 是 |
| `GET /chat/sessions/{id}/messages` | `chat_messages` | **否**（无 conversationId） |
| `GET /friend/list` | `friend_list` | 是 |

## 检查点 / Checks

| 检查 | 通过 | 失败 |
|------|-----:|-----:|
| login setup ×3 | 1×3 | 0 |
| sessions status 200 / api ok | 181625 ×2 | 671 ×2 |
| friend list status 200 / api ok | 181692 ×2 | 604 ×2 |
| **合计** | **726637** | **2550**（99.65%） |

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 364594 |
| RPS | rate | **2998** |
| 失败率 | http_req_failed | **0.35%**（1275 / 364594） |
| 迭代 | iterations | 182296（1499 /s） |
| 下行 | data_received | 1 189 928 254 B（约 1.11 GB） |
| 上行 | data_sent | 186 382 937 B（约 178 MB） |

## 耗时 / Duration（ms）

| 指标 | avg | med | p90 | p95 | p99 | max |
|------|----:|----:|----:|----:|----:|----:|
| http_req_duration | 3.85 | 3.17 | 4.51 | **5.82** | 13.52 | 554 |
| chat_sessions | 3.96 | 3.14 | 4.51 | **6.03** | 14.82 | 408 |
| chat_messages | 0 | 0 | 0 | 0 | 0 | 0 |
| iteration_duration | 660 | 660 | 939 | 974 | 1002 | 1417 |

阈值 p95&lt;2000 / p99&lt;4000 / `chat_sessions` p95&lt;600 均过。相对 200 VU p95 4.50 ms 几乎仍是空载。
