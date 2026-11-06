<!-- 作者：yangleduo -->
# short-video-read · 短视频读 / short-video read

脚本 `short-video-read.js`。setup 登录后复用 token。默认 0→8 VU，约 70 s。

导出：`linkx-server/k6/results/short-video-read-20261106-221246.json`

## 档位 / Load profile

| 中文 | English | 值 |
|------|---------|-----|
| 执行器 | executor | ramping-vus |
| 阶段 | stages | 20s→8，40s 保持 8，10s→0 |
| 峰值 VU | vus_max | 8 |

## 覆盖接口 / APIs

| 路径 Path | 标签 | 中文 | English |
|-----------|------|------|---------|
| `GET /short-video?limit=10` | `short_video_list` | 发现流 | Discover feed |
| `GET /short-video/hot?limit=5` | `short_video_hot` | 热榜 | Hot list |
| `GET /short-video/topics/hot?limit=5` | `short_video_topics_hot` | 热门话题 | Hot topics |

## 检查点 / Checks

每项 542 通过、0 失败：list / hot / topics 的 status 200 与 api ok；setup 登录 3 次。

合计 / Total：**3255 / 3255**

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 1628 |
| RPS | rate | 23.10 |
| 失败率 | http_req_failed | 0% |
| 迭代 | iterations | 542（7.69 /s） |
| 下行 | data_received | 8 694 941 B（约 8.29 MB） |
| 上行 | data_sent | 855 575 B（约 0.82 MB） |

## 耗时 / Duration（ms）

| 指标 Metric | avg | med | p90 | p95 | max | min |
|-------------|----:|----:|----:|----:|----:|----:|
| 全请求 http_req_duration | 13.14 | 12.12 | 23.14 | **23.91** | 374.26 | 0.51 |
| 发现流 `short_video_list` | 22.36 | 22.42 | 24.30 | **24.96** | 37.27 | 16.26 |
| TTFB waiting | 12.35 | 11.55 | 22.68 | 23.46 | 374.26 | 0.50 |
| 收包 receiving | 0.76 | 0.53 | 2.01 | 2.80 | 4.26 | 0 |
| 发包 sending | 0.03 | 0 | 0 | 0.32 | 1.78 | 0 |
| 建连 connecting | 0.01 | 0 | 0 | 0 | 0.91 | 0 |
| 阻塞 blocked | 0.01 | 0 | 0 | 0 | 1.33 | 0 |
| TLS | 0 | 0 | 0 | 0 | 0 | 0 |
| 迭代 iteration_duration | 807.51 | 808.01 | 1001.28 | 1021.47 | 1044.34 | 537.99 |

阈值：全请求 p95 `< 800`；`short_video_list` p95 `< 1500`。达标。发现流中位 22 ms，比会话列表慢一档，仍远低于阈值。

## 判定 / Verdict

通过 / **Pass**。
