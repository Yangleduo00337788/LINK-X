---
kind: logging_system
name: 基于 Lombok Slf4j + Spring Boot 默认 Logback 的轻量日志体系
category: logging_system
scope:
    - '**'
source_files:
    - linkx-server/src/main/java/com/linkx/server/LinkXServerApplication.java
    - linkx-server/src/main/java/com/linkx/server/exception/GlobalExceptionHandler.java
    - linkx-server/src/main/resources/application.yml
    - linkx-server/pom.xml
    - linkx-client/electron/main.ts
---

## 系统概述
LinkX 后端采用 Spring Boot 3.3 内置的 Logback 作为日志实现，通过 Lombok 的 `@Slf4j` 注解在各组件中注入 `log` 实例；前端 Electron/Vue 客户端仅使用原生 `console.log`，未引入结构化日志框架。

## 后端（Java）
- **日志门面与实现**：Spring Boot Starter Web 依赖传递引入 SLF4J API 与 Logback，项目未显式声明 logback-classic 或 log4j2 依赖，完全沿用 Spring Boot 默认配置。
- **注入方式**：所有需要日志的类统一使用 `@Slf4j` 注解，通过 Lombok 在编译期生成 `private final Logger log = LoggerFactory.getLogger(...)`，调用形式为 `log.info/warn/error/debug(...)`。
- **日志级别使用现状**：
  - `info`：服务启动、MinIO Bucket 创建、JWT Secret 校验通过等正常流程信息。
  - `warn`：业务异常（GlobalExceptionHandler）、WebSocket 鉴权失败/通道异常、JWT Secret 强度不足警告。
  - `error`：系统内部异常堆栈、序列化 WS 帧失败、安全错误（JWT_SECRET 缺失/格式无效）。
  - `debug`：IM 连接数统计、WebSocket 握手完成等调试信息。
- **全局异常处理中的日志**：`GlobalExceptionHandler` 对自定义异常记录 warn，对未知异常记录 error 并附带完整堆栈，便于线上排查。
- **配置文件**：`application.yml` 中未发现任何 `logging.*` 相关配置项，全部沿用 Spring Boot 默认行为（控制台输出、INFO 级别、UTF-8 编码）。
- **安全审计日志**：`SysLoginAudit` 实体及对应 Mapper 存在，但未见将登录审计事件写入日志的代码，审计数据目前仅落库。

## 前端（Electron + Vue3）
- Electron 主进程 `electron/main.ts` 中使用 `console.log('[electron] ...')` 打印调试信息，无独立日志模块封装。
- 渲染进程未发现集中式日志工具，业务代码以 UI 交互为主，未观察到结构化日志输出。

## 架构与约定
- 日志框架选择遵循 Spring Boot 零配置原则，未引入第三方日志桥接或自定义 Appender。
- 日志级别划分清晰：业务可预期问题用 warn，不可恢复错误用 error，运行期诊断用 debug，关键路径用 info。
- 尚未建立统一的日志字段规范（如 traceId、userId、sessionId 等 MDC 上下文），也未配置文件滚动策略或异步输出。

## 开发者应遵循的规则
1. 新增类如需日志，优先使用 `@Slf4j` 注解，避免手动 `LoggerFactory.getLogger`。
2. 按语义选择级别：`info` 记录重要业务流程节点，`warn` 记录可恢复的业务异常，`error` 记录需人工介入的错误并附带异常堆栈，`debug` 仅用于开发/测试环境诊断。
3. 敏感信息（密码、密钥、用户隐私）不得直接写入日志；如需记录用户上下文，建议使用脱敏后的 ID。
4. 当前无全局 traceId 注入，跨服务/跨线程追踪能力有限，后续可在拦截器/过滤器中结合 MDC 补充链路标识。
5. 生产环境建议通过环境变量或外部配置中心覆盖 `logging.level.*` 与 `logging.file.*` 等属性，启用文件滚动输出。