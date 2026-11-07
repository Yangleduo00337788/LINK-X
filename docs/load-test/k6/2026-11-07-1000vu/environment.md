<!-- 作者：yangleduo -->
# 压测环境 / Test environment

未打 `local` / `prod`。后端 `SPRING_PROFILES_ACTIVE=k6`。本轮开始前本机 Redis `127.0.0.1:6379` 已停，在 WSL Ubuntu 用 `redis-server --requirepass …` 拉起后再启后端。

## 启动参数 / Startup

| 中文 | English | 值 |
|------|---------|-----|
| Spring 配置 | Spring profile | `k6` |
| 配置文件 | Env file | `linkx-server/.env.k6` 叠 `.env.local` |
| API 端口 | HTTP port | `18080` |
| IM 端口 | WebSocket port | `18081` |
| Redis | Redis | `127.0.0.1:6379` DB **15** |
| Hikari | Pool | max 40 / min idle 10 |
| Lettuce | Redis pool | max active 32 |
| 限流 | Rate limit | `RATE_LIMIT_ENABLED=false` |
| 验证码 | Captcha | 关闭 |
| 压测账号 | Username | `k6_load_test`（无会话，历史消息未打到） |
| k6 | k6 | v2.3.0 windows/amd64 |
| 后端 | Backend | Spring Boot 3.5 / JDK 21，约 9.3 s 启动 |
| JVM RSS | Java working set | 压测中约 **900 MB** |

```powershell
cd linkx-server
$env:SPRING_PROFILES_ACTIVE = "k6"
mvn -q spring-boot:run
```

```powershell
cd linkx-server/k6
.\run.ps1 -Scenario chat-read -Profile stress -Vus 1000 -Duration 2m -ExportSummary
```

注意：`K6_VUS` / `K6_DURATION` 同时是 k6 **进程级** 选项。传 `-Vus 1000` 会变成恒定 VU，而不是 `config.js` 里 ≥500 的 1m 爬升。`mixed.js` 原先没有 `default` 函数，覆盖 scenarios 后会直接报 `function 'default' not found`；本轮已补 `export default`。

## 压了什么 / What was stressed

| 通道 | 地址 | 用途 |
|------|------|------|
| REST | `http://127.0.0.1:18080/api` | 冷启动读、会话、好友、朋友圈、短视频、登录 |
| WebSocket | `ws://127.0.0.1:18081/ws` | 握手 + ping/pong（未 `K6_WS_SEND`） |

MinIO `:9000` 本轮未启动，对象存储探针失败，不影响这些读/IM 场景。

## 事后排查数据源 / Post-run evidence

| 中文 | English | 来源 |
|------|---------|------|
| k6 汇总 | k6 summary | `linkx-server/k6/results/*-20261107-*.json` |
| 后端日志 | Backend log | k6 profile 进程控制台（`ERROR=0`，`Connection reset`×1000） |
| MySQL 慢查询计数 | Slow_queries | `performance_schema.global_status` = **0** |
| 语句摘要 | Statement digest | `events_statements_summary_by_digest`（schema=`linkx`） |
| 慢查询日志文件 | Slow query log file | `DESKTOP-QENU66B-slow.log` 最后一条在压测开始前（13:26），内容属其他库 |
