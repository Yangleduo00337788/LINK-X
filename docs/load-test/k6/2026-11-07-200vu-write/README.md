<!-- 作者：yangleduo -->
# 200 VU 写路径 / Write-path 200 VU

| 中文 | English | 值 |
|------|---------|-----|
| 日期 | Date | 2026-11-07 14:43–14:50 |
| 档位 | Profile | 恒定 **200 VU × 1m**（`K6_VUS` 覆盖 stages） |
| 会话 | Conversation | `476565679037829120`（`k6_load_test` ↔ `k6_load_peer`） |
| 结果 | Result | **1/6 过阈值**（仅历史消息） |

本轮补上次没打到的：**历史消息、发消息写库、上传/对象存储、注册、演示支付、通话信令**。仍是 k6 profile，限流关。

后续修正与重跑见 [2026-11-07-200vu-write-rerun](../2026-11-07-200vu-write-rerun/)（雪花 ID 字符串、风暴旁路、MinIO 上传）。

## 总表 / Summary

| 场景 Scenario | 中文 | 请求/会话 | 失败率 Failure | p95 | 判定 |
|---------------|------|----------:|---------------:|----:|------|
| chat-history | 历史消息 | 33181 | **0.08%** | **11.3 ms** | **Pass** |
| chat-write | WS 发文本写库 | 4140 次连接 | ack **43%** | 握手 153 ms | Fail（ack 阈值 80%） |
| call-load | 通话 invite+cancel | 23881 | **99.99%** | 3.9 ms | Fail |
| pay-load | 余额查询/充值/红包 | 38788 | **33.3%** | 623 ms | Fail |
| register-load | 注册写库 | 12003 | **93.9%** | 1397 ms | Fail |
| upload-chat | 聊天附件→对象存储 | 19777 | **99.99%** | — | Fail |

## 分项说明 / What happened

### 历史消息 / Message history — 通过

`GET /chat/sessions/{id}/messages?limit=50`，约 **546 RPS**，p95 11 ms。读历史在 200 VU 很轻松。

### 发消息写库 / Chat send — 部分成功

握手 101 **100%**（4140/4140）。`send` ack 仅 **43%**（1784）。其余在 4s hold 内没等到 ack；`ws_errors` 计数很高（含业务 error 帧）。能写进库，但 200 路同时往同一会话塞消息已经挤兑。

### 通话 / Call signaling — 几乎全失败

`POST /call/invite` 在 200 并发打**同一私聊**时全部非 HTTP 200。信令接口有「一路通话」互斥，不是 SQL 慢（p95 4 ms）。要用多会话/多用户对才像真实通话容量。

### 支付 / Pay（演示通道，无支付宝微信）

| 检查 | 结果 |
|------|------|
| `GET /balance` | 12929 全过 |
| `POST /balance/recharge` 0.01 元 | 12929 全过 |
| `POST /red-packet` | **12929 全失败** |

充值写库没问题。红包失败拉高整场失败率 33%。同一会话 200 人同时发红包会撞业务限制。

### 注册 / Register

验证码预先写入 Redis，**没有打 QQ SMTP**（`.env.k6` 把 `MAIL_HOST` 指到 127.0.0.1:9，欢迎信失败不影响注册）。成功 **736**，失败 11267。注册带 BCrypt cost 12，200 VU 对打会大量超时/失败，和上次登录哈希是同一类 CPU 墙。成功的账号用户名为 `k6w{n}`，留在 `linkx` 库。

### 上传 / 对象存储 — 未真正打到 MinIO

本机 **MinIO `:9000` 没起来**：WSL Docker 没有 docker.sock；官方二进制 URL 返回 410；GitHub 下载约 20 KB/s，100 MB 要一小时级。上传几乎全失败，后半还有 Windows **临时端口耗尽**（`Only one usage of each socket address`）。

Redis / MySQL 本轮已在。缺的是 MinIO。

## 结论 / Conclusions

1. **历史消息 200 VU 健康。**  
2. **写路径（发消息、红包、注册、通话、上传）不能按「读接口那种 200 VU 全绿」来预期。** 同一会话互斥、BCrypt、对象存储未启动都会让失败率飙高。  
3. **演示充值可以扛 200 VU；发红包不行。**  
4. **对象存储这次没有压到。** 下次先把 MinIO 跑在 `127.0.0.1:9000`（admin/admin123，桶 `linkx`）再打 upload。  
5. 通话要多对用户，不要 200 VU 抢一个 call。

脚本：`chat-history.js` / `chat-write.js` / `upload-chat.js` / `register-load.js` / `pay-load.js` / `call-load.js`，预备 `prep-k6-write.py`。原始 JSON 在 `linkx-server/k6/results/*-20261107-144*.json`（勿提交 token）。
