<!-- 作者：yangleduo -->
# smoke · 公开冒烟 / public smoke

脚本 `smoke.js`。无需登录。阈值用 `THRESHOLDS_SMOKE`：`http_req_failed < 5%`，`p(95) < 2000 ms`。

导出：`linkx-server/k6/results/smoke-20261106-220721.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 虚拟用户 | VUs | 1 |
| 时长 | duration | 10 s |
| 峰值 VU | vus_max | 1 |

## 覆盖接口 / APIs

| 路径 Path | 中文 | English |
|-----------|------|---------|
| `GET /health` | 公开健康检查 | Public health |
| `GET /auth/config` | 认证配置 | Auth config |

## 检查点 / Checks

| 检查 Check | 通过 Passes | 失败 Fails |
|-------------|------------:|----------:|
| health status 200 | 10 | 0 |
| health api ok | 10 | 0 |
| auth config status 200 | 10 | 0 |
| auth config api ok | 10 | 0 |
| **合计 Total** | **40** | **0** |

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs count | 20 |
| 每秒请求 | http_reqs rate | 1.98 /s |
| 失败率 | http_req_failed | 0%（0 failed / 20） |
| 迭代次数 | iterations | 10 |
| 迭代速率 | iteration rate | 0.99 /s |
| 下行 | data_received | 69340 B（约 67.7 KB） |
| 上行 | data_sent | 1670 B |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med (p50) | p90 | p95 | max | min |
|-------------|----:|----------:|----:|----:|----:|----:|
| 整段请求 http_req_duration | 3.87 | 3.74 | 5.48 | **6.35** | 9.76 | 1.55 |
| 等服务器 http_req_waiting (TTFB) | 2.86 | 2.81 | 5.14 | 5.71 | 8.79 | 0.41 |
| 收包 http_req_receiving | 1.01 | 0.91 | 2.02 | 2.18 | 3.10 | 0 |
| 发包 http_req_sending | 0 | 0 | 0 | 0 | 0 | 0 |
| 建连 http_req_connecting | 0.05 | 0 | 0 | 0.05 | 1.03 | 0 |
| 阻塞 http_req_blocked | 0.05 | 0 | 0 | 0.05 | 1.03 | 0 |
| TLS http_req_tls_handshaking | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 1008.95 | 1007.46 | 1011.43 | 1014.67 | 1017.90 | 1007.13 |

## 判定 / Verdict

通过 / **Pass**。公开探针无失败，p95 6.35 ms。
