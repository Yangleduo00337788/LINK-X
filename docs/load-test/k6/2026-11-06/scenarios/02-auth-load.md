<!-- 作者：yangleduo -->
# auth-load · 登录链路 / login path

脚本 `auth-load.js`。每个 VU 迭代都会登录（打 BCrypt）。默认 load：0→10 VU，约 70 s。

导出：`linkx-server/k6/results/auth-load-20261106-220733.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 执行器 | executor | ramping-vus |
| 阶段 | stages | 20s→10，40s 保持 10，10s→0 |
| 峰值 VU | vus_max | 10 |

## 覆盖接口 / APIs

| 路径 Path | 标签 name | 中文 | English |
|-----------|-----------|------|---------|
| `POST /auth/login` | `auth_login` | 登录 | Login |
| `GET /user/me` | `user_me` | 当前用户 | Current user |
| `GET /chat/unread-total` | `chat_unread_total` | 未读总数 | Unread total |

## 检查点 / Checks

每项 469 次，失败 0：

- login status 200
- login api code 200
- login has accessToken
- user/me status 200
- user/me api ok
- unread-total status 200
- unread-total api ok

合计 / Total：**3283 / 3283**

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 1407 |
| 每秒请求 | RPS | 19.86 |
| 失败率 | http_req_failed | 0% |
| 迭代 | iterations | 469（6.62 /s） |
| 下行 | data_received | 5 467 323 B（约 5.21 MB） |
| 上行 | data_sent | 1 142 153 B（约 1.09 MB） |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 全请求 http_req_duration | 141.65 | 4.83 | 421.40 | **428.05** | 462.29 | 0.53 |
| 登录 `auth_login` | 415.51 | 416.28 | 430.29 | **435.84** | 462.29 | 367.74 |
| TTFB http_req_waiting | 140.88 | 4.27 | 420.91 | 427.03 | 461.40 | 0.52 |
| 收包 receiving | 0.75 | 0.57 | 1.94 | 2.69 | 5.37 | 0 |
| 发包 sending | 0.02 | 0 | 0 | 0 | 2.86 | 0 |
| 建连 connecting | 0.01 | 0 | 0 | 0 | 1.14 | 0 |
| 阻塞 blocked | 0.02 | 0 | 0 | 0 | 1.95 | 0 |
| TLS | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 1178.24 | 1176.20 | 1381.20 | 1404.49 | 1444.22 | 920.13 |

登录专用阈值：`auth_login` p95 `< 1200 ms`（实测 436 ms，通过）。

## 说明 / Notes

全请求 p95 被登录拉高。中位数 4.83 ms 说明 `/user/me`、未读仍很快。登录成本来自 **BCrypt cost=12**，不是连接池打满。

## 判定 / Verdict

通过 / **Pass**。
