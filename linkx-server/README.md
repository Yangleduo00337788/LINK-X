<!-- 作者：yangleduo -->
# LinkX Server

LinkX 单体后端服务（Spring Boot 3.5 + Netty WebSocket + MyBatis-Flex + Flyway）。

完整说明见仓库根目录 **[README.md](../README.md)**。

## 环境要求

- JDK 21
- Maven 3.8+
- Docker（本地 MySQL / Redis / MinIO）

## 快速启动

### 1. 启动中间件

```bash
docker-compose up -d
```

### 2. 配置环境变量

```bash
# Windows
copy .env.local.example .env.local

# Linux / macOS
cp .env.local.example .env.local
```

至少填写：`JWT_SECRET`（≥32 字符）、`DB_PASSWORD`、`REDIS_PASSWORD`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`CORS_ALLOWED_ORIGINS`。

模板说明见 `.env.local.example`、`.env.prod.example`、`.env.docker.example`。

### 3. 启动服务

**IDE：** 运行 `com.linkx.server.LinkXServerApplication`（模块 SDK 须为 JDK 21）

**命令行：**

```bash
mvn spring-boot:run
```

| 端点 | 地址 |
|------|------|
| API | http://localhost:8080/api |
| Swagger | http://localhost:8080/api/swagger-ui.html |
| WebSocket | ws://localhost:8081/ws |

## 构建与测试

```bash
mvn test                        # 单元测试
mvn -DskipTests package         # 产出 target/linkx-server-1.0.0-SNAPSHOT.jar
```

## 数据库迁移

在 `src/main/resources/db/migration/` 新增 `V{n}__描述.sql`，由 Flyway 自动执行。**禁止**直接修改生产库表结构。

## 配置说明

- 业务参数通过 `.env.local` / `.env.prod` / `.env.docker` 注入，不写死 `application.yml`
- 消息落库加密（可选）：见根 README **[8.4 消息落库加密](../README.md#84-消息落库加密)**
- IP 归属地数据：见 `src/main/resources/ip2region/README.md`

## 常用命令

| 命令 | 说明 |
|------|------|
| `docker-compose up -d` | 启动中间件 |
| `docker-compose down` | 停止中间件 |
| `mvn spring-boot:run` | 开发启动 |
