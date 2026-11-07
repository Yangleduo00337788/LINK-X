<!-- 作者：yangleduo -->
# 1000 VU 压测 / 1000 VU stress

| 中文 | English | 值 |
|------|---------|-----|
| 日期 | Date | 2026-11-07 13:45–14:01 |
| 档位 | Profile | `stress` + `K6_VUS=1000` `K6_DURATION=2m` |
| 实际执行 | Actual executor | **恒定 1000 VU × 2 分钟**（k6 原生 `K6_VUS`/`K6_DURATION` 覆盖了脚本里的 ramping stages） |
| 结果 | Result | **5/6 通过**；`auth-load` 阈值失败 |
| 后端 | Backend | k6 profile，`:18080` / `:18081`，限流关闭 |

相对梯度档峰值 200 VU，本轮把所有场景一次性拉到 **1000 VU**。

## 总表 / Summary

| 场景 | 上次峰值 | 本次 VU | HTTP 请求 | RPS | 失败率 | p50 | p95 | p99 | max | 判定 |
|------|--------:|--------:|----------:|----:|------:|----:|----:|----:|----:|------|
| chat-read | 200 | **1000** | 364594 | **2998** | 0.35% | 3.17 ms | **5.82 ms** | 13.52 ms | 554 ms | Pass |
| client-bootstrap | 200 | **1000** | 294959 | 2393 | **1.45%** | 284 ms | **706 ms** | 769 ms | 1864 ms | Pass |
| short-video-read | 8（load） | **1000** | 128498 | 1050 | **1.69%** | 694 ms | **1272 ms** | 1443 ms | 1792 ms | Pass |
| mixed | 15（load） | **1000** | 524862 | **4306** | **0%** | 15.9 ms | **41.6 ms** | 68.3 ms | 323 ms | Pass |
| chat-ws | 200 | **1000** | 2（setup） | — | 0% | 握手 127 ms | **617 ms** | 826 ms | 905 ms | Pass |
| auth-load | 10 | **1000** | 15443 | 114 | 3.74% | 6083 ms | **22223 ms** | 26469 ms | 30539 ms | **Fail** |

阈值（stress）：`http_req_failed < 5%`，全请求 `p(95)<2000` / `p(99)<4000`。`auth-load` 另有 `auth_login p(95)<1200`。

## chat-ws 补充

| 中文 | English | 值 |
|------|---------|-----|
| 连接数 | ws_sessions | 5574 |
| 握手 101 | checks | 5000 / 5574（89.7%） |
| ping/pong | ws_pong_ok | **96.3%**（15000 pass / 574 fail） |
| WS 错误 | ws_errors | **0** |
| 会话时长 med | ws_session_duration | 25.1 s（hold≈25 s） |

握手失败与未收到 pong 同为 574：1000 路并发挤在同一 `SHARED_DEVICE_ID` 上会互相顶掉，外加本机 accept 队列抖动。阈值 `ws_pong_ok > 90%` 仍过。

## 拐点 / Knee

1. **chat-read 仍远没打满。** 1000 VU ≈ 3000 RPS，p95 5.8 ms（200 VU 时 4.5 ms），失败率 0.35%。
2. **冷启动读出现排队。** bootstrap p95 从 200 VU 的 28 ms 跳到 **706 ms**，失败率 1.45%。过程中出现过 `connectex: connection refused`（Windows 在 Tomcat 线程/全连接队列满时直接拒连），随后进程未退出，健康检查仍为 UP。
3. **短视频列表** p95 **1403 ms**，贴近脚本 `short_video_list p(95)<1500` 的边；失败率 1.69%。
4. **mixed 在后端恢复后 0 失败、4306 RPS、p95 42 ms**——单迭代 sleep 更长，在途请求更少，没打满连接器。
5. **登录（BCrypt cost 12）是 CPU 拐点。** 1000 VU 同时 `POST /auth/login`：login p95 **25.1 s**，整体 p95 22.2 s，阈值失败。不要用 auth-load 衡量读链路容量。

## 和 200 VU 对比 / vs gradient 200

| | gradient 200 | 1000 VU |
|--|--------------|---------|
| chat-read p95 | 4.50 ms | 5.82 ms |
| chat-read RPS | 352 | 2998 |
| bootstrap p95 | 27.8 ms | **706 ms** |
| bootstrap RPS | 1200 | 2393 |
| bootstrap 失败率 | 0% | 1.45% |
| WS 峰值 | 200，pong 100% | 1000，pong 96.3%，握手失败 574 |

## 问题清单 / Issues found

压测相关故障就这三类。k6 数字当时已写入分场景文档；**SQL / 后端日志排查后补记如下。**

| # | 中文 | English | 证据 Evidence | 严重程度 |
|---|------|---------|---------------|----------|
| 1 | HTTP 连接器被打满 | HTTP connector saturated | bootstrap / short-video 出现 `connection refused`（连接被拒绝），失败率 1.45% / 1.69%；Java 进程未退出 | 中：读接口排队、部分请求进不了应用 |
| 2 | 登录哈希把 CPU 打满 | Login hashing saturated CPU | `auth-load` 唯一 Fail；login p95 **25.1 s**（BCrypt cost 12，同一账号 1000 VU） | 高：该场景阈值失败 |
| 3 | 少量 IM 连接被重置 | Some IM connections reset | 握手 574/5574 失败；pong 96.3%；后端 `ImWebSocketAuthHandler` **1000** 条 `Connection reset`（约 13:53）；k6 `ws_errors=0` | 低：长连接仍能维持 |

### 没有出现的 / Did not occur

| 中文 | English | 值 |
|------|---------|-----|
| 官方慢查询 | Slow queries（`long_query_time=10s`） | **0** |
| SQL 执行错误 | SQL errors（`SUM_ERRORS`） | **0** |
| 应用 ERROR 日志 | Application ERROR | **0** |
| 连接池耗尽 | Hikari timeout / pool exhausted | **0** 条日志 |
| 内存耗尽 / 崩溃 | OOM / process crash | 无；RSS ≈ 900 MB，health 仍 UP |
| 客户端异常断开（MySQL） | Aborted clients | 0 |

读 SQL 平均 **0.2–0.4 ms**，最长约 40–80 ms。超过 500 ms 的都是登录审计写入（`sys_login_audit` / `sys_device_session` / `sys_audit_log`），属于问题 2 的写路径长尾，不是慢 SELECT。

### 非本次压测打出来的 / Not caused by this load

| 中文 | English | 说明 |
|------|---------|------|
| MinIO 连不上 | Object storage probe fail | 启动时 `:9000` 未开，不影响读/IM |
| Prometheus 标签不一致 | Meter tag mismatch | 监控配置，不是接口失败 |

## 处置 / Disposition

**不当代码 bug 修，也不是生产安全漏洞。**  
*Not application bugs and not production vulnerabilities. No business-logic patch required.*

三类现象都是 **单实例 JVM 被同一时刻打满** 的容量边界（BCrypt 慢哈希、同设备顶号还是产品设计）。

| 问题 | 要不要改代码 | 生产怎么做 |
|------|--------------|------------|
| HTTP 连接器打满 | 否 | **加机器 / 多实例** 扛真人并发；限流防刷，不能拿 429 替代扩容 |
| 登录哈希打满 CPU | **否**（不要降 BCrypt cost） | 生产 **必须开限流**；登录吞吐靠核数/实例，不按 1000 VU 同账号对打规划 |
| IM 连接重置 | 否（同设备踢旧连接保留） | 真实多设备在线再靠多实例；压测应用不同 `deviceId` 即可 |

限流和多实例职责不同：限流挡滥用，多实例挡正常高峰。限流开了不等于一台 JVM 能抗 1000 人同时打开 App。

## 结论 / Conclusions

1. **读会话/好友在 1000 VU 仍健康**；Hikari 40 够周转（单次 3–6 ms）。
2. **冷启动 9 路并行读** 在 1000 VU 打到本机 Tomcat 默认线程（约 200）和 accept 队列，出现拒连与 p95 数百毫秒。这是连接器/本机环回，不是 MySQL 先垮。
3. **短视频发现流** 是读场景里最重的 SQL/序列化，1000 VU 已接近 1.5 s 阈值。
4. **IM 1000 长连接能维持**（`ws_errors=0`），但共享 deviceId 会踢连接；要测纯连接容量应每 VU 独立 device。
5. **登录接口不能按 1000 VU 当业务容量。** 需要的是独立用户池 + 限流打开的真实模型，而不是同一账号 BCrypt 对打。
6. **本次不改业务代码。** 上线检查限流为开，流量上来再水平扩展。

原始 JSON（gitignore，含 token，勿提交）：`linkx-server/k6/results/*-20261107-*.json`，中断日志 `vu1000-suite-20261107-134534.log`。
