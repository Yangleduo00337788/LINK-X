<!-- 作者：yangleduo -->
# 500 VU 社交套件 / Social suite 500 VU

| 中文 | English | 值 |
|------|---------|-----|
| 日期 | Date | 2026-11-07 18:18–19:47 |
| 档位 | Profile | `stress` 阈值 + 脚本固定爬坡 **1m→500 / 1m 保持 / 30s 降到 0**（约 2m30s） |
| 结果 | Result | **6/10 场景拿到有效数据**；k6 阈值 5 过 5 不过。群读/群资产把本机 HTTP 连接器打满后，会议与网盘卡在登录 |
| 后端 | Backend | k6 profile，`:18080` / `:18081`，限流关 |

本轮把上次规划的本轮+下轮一次打完：群聊、会议、网盘、朋友圈赞评/发帖、好友、短视频互动、笔记日历收藏。不要和 11-06 的 7 场景 `run-all` 混在一起。

跑法：`python prep-k6-social.py` 后 `.\run-social.ps1 -Profile stress -ExportSummary`。不要设进程环境 `K6_VUS`/`K6_DURATION`，k6 会当成 CLI option **瞬间拉满 500 VU**，把 Tomcat 打拒连。

## 总表 / Summary

取**有业务流量**的一场。群发消息是 19:38 补跑；群读是 18:18 正场。

| 场景 Scenario | 中文 | 峰值 VU | 请求/会话 | 失败率 Failure | p95 | k6 阈值 | 业务 |
|---------------|------|--------:|----------:|---------------:|----:|---------|------|
| group-load | 群列表/详情/成员/公告 | 500 | 2123 HTTP | **34%** | **60 s** | Fail | 连接器打满；成功响应其实 ~9 ms |
| group-write | 群 WS 发文本 | 500 | 91543 连接 | HTTP 0% | 握手 p95 **1.74 s** | **Pass** | **Pass**，ack **100%** |
| group-extra | 邀请/公告/群资产 | 500 | 723 HTTP | **99.86%** | 60 s | Fail | 未打到业务，请求卡死 |
| conference-load | 会议信令 | 500 | setup 登录 | — | — | Fail | 登录 60 s 超时，未进场景 |
| cloud-load | 网盘 | 500 | setup 登录 | — | — | Fail | 同上（另修了 `__ITER` 不能在 setup 用） |
| moments-load | 朋友圈列表/赞/评 | 500 | 149971 | **0%** | 462 ms | **Pass** | **Pass** |
| moments-publish | 朋友圈发纯文本 | 500 | 73449 | **0%** | 253 ms | **Pass** | **Pass**（库里会留下大量 k6 帖） |
| friend-load | 好友列表/搜索/申请 | 500 | 292567 | **3.10%** | 134 ms | **Pass** | 列表搜索全过；申请撞唯一约束 |
| short-video-interact | 短视频点赞/评论/播放 | 500 | 140737 | **0%** | 745 ms | **Pass** | **Pass** |
| workspace-load | 笔记/日历/收藏 | 500 | 160034 | **0%** | 686 ms | **Pass** | **Pass** |

阈值（stress）：`http_req_failed < 5%`，`p(95)<2000` / `p(99)<4000`。`group-write`：`ws_send_ack_ok > 50%`，`ws_errors count<50000`。

## 分项 / Detail

### 群读 group-load

爬坡是对的（`group_load` 3 stages，不是瞬时 500）。0 次完整迭代：VU 卡在 60 s HTTP 超时。`http_req_failed=34%`（722/2123）。

`expected_response:true` 的 p95 只有 **9.4 ms**。能进应用的群列表/成员/公告很快；打满的是 Tomcat 线程和 accept 队列，和 1000 VU `client-bootstrap` 同类。

群消息历史这轮几乎没打到（`group_messages` 计数 0）：迭代在更前面的 `/group/list`、`/info` 就堵住了。

### 群发 group-write（补跑）

握手 101 **91543/91543**，send ack **91543/91543**。`ws_errors=14033` 来自短连接狂连/断，和私聊 `chat-write` 一样，阈值 5 万以内。IM 写群会话在 500 VU 业务上是通的。

### 群资产 / 会议 / 网盘

`group-extra` 登录成功后 500 个 VU 打 `/group/{id}/assets`，**0 成功**，几乎全 60 s 超时。随后会议、网盘 setup 登录同样 60 s。单实例 JVM 刚扛完群发，15 s 间隔不够恢复连接器。

`cloud-load.js` 曾在 `setup()` 里读 `__ITER`（k6 在 setup 没有这个变量），已改成传入 vu/iter。这轮补跑没走到上传，卡在登录。

### 朋友圈 / 好友 / 短视频 / 工作台

这四条是本轮最干净的容量数据：失败率 0%（好友申请除外），p95 都在 1.5 s 标签阈值内。

好友 `POST /friend/request` 对同一陌生人打了 9070 次，HTTP 非 200，正好对应整场 **3.10%** 失败率；列表和搜索 70874 次全过。这是唯一约束，不是慢 SQL。

发帖 73448 条会留在 `k6` 库，清理时按 `k6 pub` 内容删即可。

## 结论 / Conclusions

1. **朋友圈读写、好友读、短视频互动、笔记日历收藏在 500 VU 健康。**
2. **群发消息 500 VU 能 ack。** 扇出目前只有预种的十来个成员，不是大群。
3. **群 REST 读和群资产在 500 并发下先打满本机 HTTP 连接器**，不是 MySQL 先垮。成功请求仍然是毫秒级。
4. **会议信令和网盘这一轮没有有效容量数字。** 要单独跑，并且前后留空窗，不要跟在群 500 VU 后面。
5. **不要用 k6 原生 `K6_VUS` 打社交套件。** 会覆盖脚本爬坡，瞬间 500 全连，立刻拒连。

原始 JSON（gitignore，含 token）：`linkx-server/k6/results/*-20261107-18*.json`、`*-20261107-193850.json` 等。套件日志 `social-suite-20261107-181832.log`、`social-rerun-20261107-193850.log`。
