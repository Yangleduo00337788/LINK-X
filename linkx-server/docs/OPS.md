# LinkX 运维：备份 / Flyway / Docker 应用

## 1. 备份与恢复

中间件起来后，在 `linkx-server` 目录执行（Git Bash / WSL / Linux）：

```bash
chmod +x scripts/*.sh
./scripts/backup.sh
```

产物目录：`backups/<时间戳>/`

| 文件 | 内容 |
|------|------|
| `mysql_linkx.sql.gz` | MySQL 逻辑备份 |
| `redis_dump.rdb` | Redis RDB |
| `minio/` | 对象存储镜像 |
| `SHA256SUMS` | 校验和（若系统有 sha256sum） |

恢复（会覆盖现网数据）：

```bash
./scripts/restore.sh backups/20260730_120000
# 提示时输入 YES
```

建议：

- 生产用 cron / 计划任务每日跑 `backup.sh`，异地再拷一份
- `BACKUP_KEEP` 默认保留最近 7 份；可 `BACKUP_KEEP=14 ./scripts/backup.sh`
- 网络名默认 `linkx-server_linkx-net`；若 compose 项目名不同，设 `COMPOSE_NETWORK=...`

## 2. Flyway Schema 迁移

- 迁移脚本：`src/main/resources/db/migration/V*.sql`
- 存量库首次启动：`baseline-on-migrate=true`，记为版本 **1**，再执行 **V2+**
- Docker 空库仍可用 `init.sql` 初始化全量表；应用启动时 Flyway 只补增量
- 测试（H2）关闭 Flyway，继续用 `schema.sql`
- 遗留 `SchemaPatchRunner` **默认关闭**；勿再往里加补丁

新增字段/表：

1. 写 `V4__your_change.sql`
2. 本地/预发启动应用验证 `flyway_schema_history`
3. 同步更新 `init.sql`（给全新 docker volume 用）与测试 `schema.sql`（如需要）

## 3. 应用 Dockerfile

仅中间件（原行为）：

```bash
docker compose up -d
```

连同应用一起构建启动：

```bash
cp .env.docker.example .env.docker
# 填写 JWT_SECRET、DB_PASSWORD、REDIS_PASSWORD、MINIO_* 等
docker compose --profile app up -d --build
```

单独构建镜像：

```bash
docker build -t linkx-server:local .
```

应用暴露：`8080`（HTTP `/api`）、`8081`（IM WebSocket）。健康检查探测本机 `8080`。
