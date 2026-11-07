<!-- 作者：yangleduo -->
# auth-load · 登录突发 / login burst

导出：`linkx-server/k6/results/auth-load-20261107-135854.json`  
判定 / Verdict：**Fail**（exit 99）

同一账号 1000 VU 反复 `POST /auth/login`（BCrypt cost 12）。这是 CPU 压测，不是业务读容量。

## 档位 / Load profile

恒定 1000 VU × 2m。

## 覆盖接口 / APIs

| 路径 | 标签 |
|------|------|
| `POST /auth/login` | `auth_login` |
| `GET /user/me` | `user_me` |
| `GET /chat/unread-total` | `chat_unread_total` |

## 检查点 / Checks

| 检查 | 通过 | 失败 |
|------|-----:|-----:|
| login status / api / token | 4955 ×3 | 578 ×3 |
| user/me status / api | 4955 ×2 | 0 |
| unread-total status / api | 4955 ×2 | 0 |
| **合计** | **34685** | **1734**（95.24%） |

登录失败后该迭代不再打后面两个接口，所以 me/unread 失败为 0。

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 15443 |
| RPS | rate | **114** |
| 失败率 | http_req_failed | 3.74%（仍 &lt; 5%） |
| 迭代 | iterations | 5533（41 /s） |

## 耗时 / Duration（ms）

| 指标 | avg | med | p90 | p95 | p99 | max |
|------|----:|----:|----:|----:|----:|----:|
| http_req_duration | 8027 | 6083 | 19145 | **22223** | 26469 | 30539 |
| auth_login | 13168 | 13298 | 23475 | **25083** | 29427 | 30539 |
| iteration_duration | 23342 | 23739 | 36909 | 45485 | 48479 | 49644 |

未过阈值：`p(95)<2000`、`p(99)<4000`、`auth_login p(95)<1200`。登录 p95 **25 s**，CPU 已饱和。

MySQL：`Slow_queries=0`，语句 `SUM_ERRORS=0`。登录审计写入最长约 0.7 s（`INSERT sys_login_audit` avg 61 ms / max 735 ms），不是 10 秒级慢查询。这就是「登录哈希把 CPU 打满」，不是慢 SQL。
