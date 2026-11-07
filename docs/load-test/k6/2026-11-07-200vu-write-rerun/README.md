<!-- 作者：yangleduo -->
# 200 VU 写路径重跑 / Write-path 200 VU rerun

| 中文 | English | 值 |
|------|---------|-----|
| 日期 | Date | 2026-11-07 16:37–16:43 |
| 档位 | Profile | 恒定 **200 VU × 1m** |
| 会话 | Conversation | `476565679037829120`（字符串传递，不再 `Number()`） |
| 结果 | Result | 业务上 **通话 / 红包 / 发消息 ack / 历史 / 上传** 已打通；注册仍被 BCrypt 拖死；上传全成功但 p95 慢 |

本轮针对上一轮误判做了修正后再打。

## 五个问题怎么处理 / Answers

1. **通话：是。** 雪花 ID 用 JS `Number()` 会丢精度。已改成 JSON 字符串，invite **99.2%** 成功（11307/11402）。
2. **红包：同一原因。** 对上真实会话后，余额 / 充值 / 红包 **5159/5159 全过**。k6 退出码 99 只是整场 p95 超过 `load` 档 800ms，不是业务失败。
3. **消息风暴：上一轮没关。** `RATE_LIMIT_ENABLED=false` 只关 HTTP 限流，风暴仍是 10 秒 30 条。本轮 k6 档与限流开关绑定，风暴旁路。发消息 ack **44195/44195**。
4. **注册：不是代码 bug。** 验证码已按 `k6w{vu}x{iter}` 种进 Redis。失败是 **BCrypt cost 12 把 CPU 打满**，大量请求卡到 60s 超时。和登录哈希是同一类容量问题，不是漏洞。
5. **上传：MinIO 后来用本机已有 `minio/minio:latest` compose 起来了**（指定 RELEASE 标签才会去拉仓库被 denied）。桶 `linkx` 已建。200 VU 上传 **失败率 0%**，p95 **12.6 s**（阈值 4s，k6 退出 99）。

## 总表 / Summary

| 场景 Scenario | 中文 | 请求 | 失败率 Failure | p95 | k6 阈值 | 业务 |
|---------------|------|-----:|---------------:|----:|---------|------|
| chat-history | 历史消息 | 32904 | **0%** | 14 ms | Pass | Pass |
| chat-write | WS 发文本写库 | 44195 连接 | ack **100%** | 握手 429 ms | Fail（`ws_errors` 计数>200） | **Pass** |
| call-load | 通话 invite+cancel | 22710 | **0.42%** | 411 ms | Pass | Pass |
| pay-load | 余额/充值/红包 | 15478 | **0%** | 2124 ms | Fail（p95>800ms） | **Pass** |
| register-load | 注册写库 | 566 | **35.3%** | 60 s | Fail | Fail（CPU） |
| upload-chat | 附件→对象存储 | 1494 | **0%** | 12.6 s | Fail（p95>4s） | **Pass** |

## 分项 / Detail

### 发消息

握手 101 与 send ack 全过。`ws_errors=4703` 来自短连接狂连/断，不是风暴 429。阈值 `ws_errors count<200` 对 200 VU 过紧。

### 通话

invite 失败 95 条（0.42%），有 callId 的 cancel **全过**。已压到真实信令，不是「一路互斥打满」。

### 支付

红包走同一私聊会话，金额 0.01。p95 高是同一用户行上并发冻余额，不是对错会话。

### 注册

成功 366，失败 200。成功请求 p50 约 5.4s。总 RPS 仅 7.6。验证码已覆盖；瓶颈是哈希。

### 上传 / 对象存储

WSL `docker compose -f docker-compose.yml -f docker-compose.k6-minio.yml up -d minio`，镜像用本机 `minio/minio:latest`。探测上传返回 200 且对象在 `linkx` 桶。

200 VU × 1m：`upload status 200` **1493/1493**，`http_req_failed=0`。平均 8.0 s，p95 12.6 s，约 21 RPS。慢在单机 JVM + 本机 Docker MinIO 串行写，不是上传失败。
