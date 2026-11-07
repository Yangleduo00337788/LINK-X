<!-- 作者：yangleduo -->
# client-bootstrap · 客户端冷启动 / app cold start

导出：`linkx-server/k6/results/client-bootstrap-20261107-134740.json`  
判定 / Verdict：**Pass**（失败率 1.45% &lt; 5%，p95 706 ms &lt; 2000 ms）

本场景是 1000 VU 里**最先打出排队和拒连**的。

## 档位 / Load profile

恒定 1000 VU × 2m。每迭代 9 个业务 GET。

## 覆盖接口 / APIs

homepage / version / user/me / sessions / unread-total / friend list / notifications / moments / short-video。

## 检查点 / Checks

| 检查（各 status+api） | 通过约 | 失败约 |
|------------------------|------:|------:|
| homepage | 32194 | 579 |
| version | 32244 | 529 |
| user/me | 32245 | 528 |
| sessions | 32245 | 528 |
| unread-total | 32247 | 526 |
| friend list | 32267 | 506 |
| notifications | 32334 | 439 |
| moments | 32424 | 349 |
| short-video | 32482 | 291 |
| **合计** | **581367** | **8550**（98.55%） |

失败集中在后半段 Windows `dial tcp 127.0.0.1:18080: connectex: … actively refused`。Java 进程未退出，RSS ≈ 900 MB；健康检查随后仍返回 UP。

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 294959 |
| RPS | rate | 2393 |
| 失败率 | http_req_failed | **1.45%** |
| 迭代 | iterations | 32773（266 /s） |
| 下行 | data_received | 1 085 801 849 B（约 1.01 GB） |

## 耗时 / Duration（ms）

| 指标 | avg | med | p90 | p95 | p99 | max |
|------|----:|----:|----:|----:|----:|----:|
| http_req_duration | 322 | 284 | 625 | **706** | 769 | 1864 |
| chat_sessions | 284 | 287 | 313 | **322** | 365 | 811 |
| moments_list | 287 | 288 | 315 | **324** | 385 | 731 |
| iteration_duration | 3707 | 3721 | 4079 | 4155 | 4551 | 5571 |

200 VU 时全请求 p95 仅 28 ms。1000 VU 下 p95 进到数百毫秒，说明 **在途请求已经堵住 Tomcat 线程**，不是 SQL 突然变成 700 ms（分标签 p95 仍约 320 ms，失败样本把全量分位拉高）。
