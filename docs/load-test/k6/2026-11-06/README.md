<!-- 作者：yangleduo -->
# LinkX k6 压测报告 / Load Test Report

| 中文 | English | 值 / Value |
|------|---------|------------|
| 报告日期 | Report date | 2026-11-06 |
| 档位 | Profile | `load` |
| 结果 | Result | **7/7 通过 / all passed** |
| 墙钟耗时 | Wall time | 约 11 分钟 / ~11 min |
| 开始时间 | Started at | 2026-11-06 22:07:21 |
| 套件日志 | Suite log | `linkx-server/k6/results/full-suite-20261106-220721.log` |

本目录是本次上线前 k6 全量压测的书面报告。原始 k6 `--summary-export` JSON 仍在 `linkx-server/k6/results/`（含登录 token，**不要入库、不要转发**）。

## 目录 / Contents

| 文件 | 说明 |
|------|------|
| [environment.md](./environment.md) | 压测环境、独立 k6 env、未覆盖项 |
| [glossary.md](./glossary.md) | 指标中英对照词典 |
| [scenarios/01-smoke.md](./scenarios/01-smoke.md) | 公开冒烟 |
| [scenarios/02-auth-load.md](./scenarios/02-auth-load.md) | 登录链路 |
| [scenarios/03-chat-read.md](./scenarios/03-chat-read.md) | 聊天读 |
| [scenarios/04-client-bootstrap.md](./scenarios/04-client-bootstrap.md) | 客户端冷启动 |
| [scenarios/05-short-video-read.md](./scenarios/05-short-video-read.md) | 短视频读 |
| [scenarios/06-chat-ws.md](./scenarios/06-chat-ws.md) | IM WebSocket |
| [scenarios/07-mixed.md](./scenarios/07-mixed.md) | 混合读 |

## 总表 / Summary

| 场景 Scenario | VU 峰值 | HTTP 请求 Requests | 失败率 Failed | p50 | p95 | max | RPS | 检查点 Checks |
|---------------|--------:|-------------------:|--------------:|----:|----:|----:|----:|--------------|
| smoke | 1 | 20 | 0% | 3.74 ms | 6.35 ms | 9.76 ms | 1.98 | 40/40 |
| auth-load | 10 | 1407 | 0% | 4.83 ms | 428.05 ms | 462.29 ms | 19.86 | 3283/3283 |
| chat-read | 10 | 1838 | 0% | 3.78 ms | 4.54 ms | 268.81 ms | 24.40 | 3675/3675 |
| client-bootstrap | 20 | 29720 | 0% | 3.84 ms | 23.03 ms | 373.88 ms | 184.91 | 59439/59439 |
| short-video-read | 8 | 1628 | 0% | 12.12 ms | 23.91 ms | 374.26 ms | 23.10 | 3255/3255 |
| chat-ws | 30 | 2（仅 setup） | 0% | — | WS 握手 13.95 ms | 229 ms | — | 331/331 |
| mixed | 17 | 5782 | 0% | 3.97 ms | 4.88 ms | 367.55 ms | 60.28 | 2423/2423 |

WebSocket 补充 / WebSocket extras：`ws_sessions=182`，`ws_pong_ok=100%`，`ws_errors=0`。

## 结论 / Conclusions

1. **读链路健康。** 会话、首页、朋友圈、短视频 p95 大多 5–25 ms，HTTP 失败率 0%。
2. **登录最慢。** `auth_login` p95 ≈ 436 ms，主要是 BCrypt，不是 SQL 读路径排队。
3. **IM 长连接稳定。** 182 条连接，ping/pong 全过，无 WS 错误。默认未发聊天消息。
4. **缺口。** 账号 `k6_load_test` 无会话，`chat-read` 未打到历史消息接口。
5. **未覆盖。** 发消息写库、上传、音视频会议、管理端、真实生产限流。

## 阈值 / Thresholds（本次全部达标）

| 指标 Metric | 默认 Default | stress 档 |
|-------------|--------------|-----------|
| `http_req_failed` | `< 1%` | `< 5%` |
| `http_req_duration` p95 / p99 | `< 800 ms` / `< 1500 ms` | `< 2000 ms` / `< 4000 ms` |
| `ws_pong_ok` | `> 90%` | 同左 |
| `ws_errors` | `count < 80` | `count < 500` |

k6 v2 summary JSON 中 `"p(95)<800": false` 表示**未击穿阈值**（threshold not failed）。
