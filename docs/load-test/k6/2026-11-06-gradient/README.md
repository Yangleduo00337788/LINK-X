<!-- 作者：yangleduo -->
# 梯度压测 / Gradient load（50 → 100 → 150 → 200 VU）

| 中文 | English | 值 |
|------|---------|-----|
| 日期 | Date | 2026-11-06 22:27–22:42 |
| 档位 | Profile | `gradient` |
| 阶梯 | Stages | 20s 升 + 45s 保持：50 / 100 / 150 / 200，再 30s 降到 0 |
| 单场时长 | Per scenario | 约 4m50s |
| 结果 | Result | 3/3 通过，失败率 0% |
| 后端 | Backend | 仍为 k6 profile，`:18080` / `:18081`，限流关闭 |

相对上一轮 `load`（最高 30 VU），本轮把读链路和 IM 拉到 **200 VU**。

## 总表 / Summary vs previous load

| 场景 | 上次 VU | 本次峰值 | HTTP 请求 | RPS | 失败率 | p50 | p95 | p99 | max |
|------|--------:|--------:|----------:|----:|------:|----:|----:|----:|----:|
| chat-read | 10 | **200** | 102242 | 352 | 0% | 3.24 ms | **4.50 ms** | 6.02 ms | 388 ms |
| client-bootstrap | 20 | **200** | 349706 | **1200** | 0% | 3.95 ms | **27.79 ms** | 43.87 ms | 416 ms |
| chat-ws | 30 | **200** | 2（setup） | — | 0% | 握手 7 ms | **9 ms** | 18.5 ms | 30 ms |

### chat-ws 补充

| 中文 | English | 值 |
|------|---------|-----|
| 连接数 | ws_sessions | 1426 |
| ping/pong | ws_pong_ok | 100%（4155/4155） |
| WS 错误 | ws_errors | 0 |
| 握手 101 | checks | 1309/1309 |
| 降载打断 | interrupted iterations | 118（25s hold 被 graceful stop 掐断，属预期） |

### 接口标签 p95

| 场景 | 标签 | p95 |
|------|------|-----|
| chat-read | `chat_sessions` | 4.44 ms |
| client-bootstrap | `chat_sessions` | 7.04 ms |
| client-bootstrap | `moments_list` | 7.74 ms |

## 和 load 档对比 / Compared with load

| | load | gradient 200 VU |
|--|------|-----------------|
| chat-read p95 | 4.54 ms | 4.50 ms（几乎不变） |
| client-bootstrap p95 | 23.0 ms | 27.8 ms（略升） |
| client-bootstrap RPS | 185 | 1200 |
| WS 峰值连接 | 30 | 200 并发，pong 仍 100% |

## 结论 / Conclusions

1. **200 VU 仍未打出拐点。** 读接口 p95 仍远低于 800 ms / 2000 ms 阈值，失败率 0。
2. **连接池够用。** k6 档 Hikari 最大 40：单次请求 3–7 ms，连接周转快，没有打满排队。
3. **冷启动读** 在 200 VU 下约 **1200 RPS**，p99 44 ms，max 416 ms 仍是长尾不是平台。
4. **IM** 200 条并发长连接稳定；降载阶段的 interrupted 是 hold=25s 被掐掉，不是服务端断连（`ws_errors=0`）。
5. 若要找拐点，需要再往上（例如 400–800 VU）、打开 `K6_WS_SEND`、或给账号补会话打历史消息。不要用 `auth-load` 做超高 VU（会变成 BCrypt CPU 压测）。

原始文件：`linkx-server/k6/results/gradient-suite-20261106-222741.log` 及对应 `*-20261106-222741.json` / `223234` / `223730`。
