<!-- 作者：yangleduo -->
# mixed · 混合读 / mixed

导出：`linkx-server/k6/results/mixed-20261107-135439.json`  
判定 / Verdict：**Pass**（0 失败）

`mixed.js` 在 `K6_VUS` 覆盖 scenarios 后必须有 `default`。本轮补了：每 20 个 VU 打一次 health，其余走认证读。

## 档位 / Load profile

恒定 1000 VU × 2m。

## 覆盖接口 / APIs

| 路径 | 说明 |
|------|------|
| `GET /health` `GET /health/live` | 公开探针（约 1/20 VU） |
| `GET /user/me` | 当前用户 |
| `GET /chat/sessions` | 会话列表 |
| `GET /chat/unread-total` | 未读 |
| `GET /friend/list` | 好友 |
| `GET /moments?limit=10` | 朋友圈 |
| 历史消息 | 未打到（无会话） |

## 检查点 / Checks

| 检查 | 通过 | 失败 |
|------|-----:|-----:|
| user/me ok | 103772 | 0 |
| sessions ok | 103772 | 0 |
| health / live | 3000 ×2 | 0 |
| setup 登录 | 3 | 0 |
| **合计** | **213547** | **0** |

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | **524862** |
| RPS | rate | **4306** |
| 失败率 | http_req_failed | **0%** |
| 迭代 | iterations | 106772（876 /s） |
| 下行 | data_received | 1 750 779 695 B（约 1.63 GB） |

## 耗时 / Duration（ms）

| 指标 | avg | med | p90 | p95 | p99 | max |
|------|----:|----:|----:|----:|----:|----:|
| http_req_duration | 20.1 | 15.9 | 35.0 | **41.6** | 68.3 | 323 |
| iteration_duration | 1129 | 1113 | 1531 | 1616 | 2016 | 2649 |

单迭代含 `sleep(0.5+rand)` 或 health `sleep(2)`，在途连接远少于 bootstrap 的 9 路连发，所以 1000 VU 仍 0 失败、p95 42 ms。
