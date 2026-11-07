<!-- 作者：yangleduo -->
# short-video-read · 短视频读 / short-video read

导出：`linkx-server/k6/results/short-video-read-20261107-134947.json`  
判定 / Verdict：**Pass**

## 档位 / Load profile

恒定 1000 VU × 2m。紧接 bootstrap 拒连之后，后端已恢复接听。

## 覆盖接口 / APIs

| 路径 | 标签 |
|------|------|
| `GET /short-video?limit=10` | `short_video_list` |
| `GET /short-video/hot?limit=5` | `short_video_hot` |
| `GET /short-video/topics/hot?limit=5` | `short_video_topics_hot` |

## 检查点 / Checks

| 检查 | 通过 | 失败 |
|------|-----:|-----:|
| list status/api | 42089 ×2 | 743 ×2 |
| hot status/api | 42102 ×2 | 730 ×2 |
| topics status/api | 42128 ×2 | 704 ×2 |
| **合计** | **252641** | **4354**（98.31%） |

## HTTP

| 中文 | English | 值 |
|------|---------|-----|
| 请求总数 | http_reqs | 128498 |
| RPS | rate | 1050 |
| 失败率 | http_req_failed | **1.69%** |
| 迭代 | iterations | 42832（350 /s） |
| 下行 | data_received | 674 798 641 B（约 644 MB） |

## 耗时 / Duration（ms）

| 指标 | avg | med | p90 | p95 | p99 | max |
|------|----:|----:|----:|----:|----:|----:|
| http_req_duration | 688 | 694 | 1094 | **1272** | 1443 | 1792 |
| short_video_list | 923 | 896 | 1336 | **1403** | 1517 | 1792 |
| iteration_duration | 2824 | 2897 | 3188 | 3254 | 3357 | 3528 |

`short_video_list` p95 **1403 ms**，阈值 `&lt;1500` 仍过，余量很小。这是本轮读接口里最重的一条。
