---
kind: error_handling
name: LinkX 前后端统一错误处理体系
category: error_handling
scope:
    - '**'
source_files:
    - linkx-server/src/main/java/com/linkx/server/exception/CustomException.java
    - linkx-server/src/main/java/com/linkx/server/exception/GlobalExceptionHandler.java
    - linkx-server/src/main/java/com/linkx/server/common/Result.java
    - linkx-server/src/main/java/com/linkx/server/config/SecurityHeadersFilter.java
    - linkx-server/src/main/java/com/linkx/server/config/interceptor/LoginInterceptor.java
    - linkx-client/src/api/client.ts
    - linkx-client/src/types/auth.ts
---

## 一、整体方案概述

LinkX 在后端采用「自定义业务异常 + 全局异常处理器 + 统一响应体」的 Spring Boot 标准模式，在前端通过 Axios 拦截器集中处理 401/403 等网络与业务错误，并实现自动刷新令牌。前后端通过 `Result<T>` 的 `code` 字段约定错误语义，形成端到端的错误协议。

## 二、后端（Spring Boot）

### 2.1 核心组件
- **CustomException**：业务异常基类，携带 `Integer code` 与消息，默认 code=500；构造器支持显式指定 HTTP 风格码（400/401/403/429 等）。
- **GlobalExceptionHandler**：`@RestControllerAdvice` 全局捕获三类异常：
  - `CustomException` → 按 `mapStatus(code)` 映射到对应 `HttpStatus`，返回 `Result.error(code, message)`。
  - `MethodArgumentNotValidException` / `BindException` → 提取第一个校验错误信息，返回 400。
  - 兜底 `Exception` → 记录 ERROR 日志，返回 500 固定提示「系统内部繁忙，请稍后再试」。
- **Result<T>**：统一响应体 `{ code, message, data }`，提供 `success()` / `success(data)` / `error(code, msg)` / `error(msg)` 工厂方法，作为所有接口的唯一返回结构。

### 2.2 过滤器层错误输出
`SecurityHeadersFilter` 在请求进入 Controller 之前进行 HTTPS 强制检查，不满足条件时直接写入 `Result.error(403, "请使用 HTTPS 访问")`，绕过 MVC 层。

### 2.3 调用方式
- Service/Controller 中业务校验失败时 `throw new CustomException(401, "未登录或登录已过期")`。
- 参数解析异常处（如 `NumberFormatException`）也包装为 `CustomException(400, "无效 ID")`。
- 非业务性非法参数直接使用 `IllegalArgumentException`，由全局处理器兜底返回 500。

### 2.4 关键文件
- `linkx-server/src/main/java/com/linkx/server/exception/CustomException.java`
- `linkx-server/src/main/java/com/linkx/server/exception/GlobalExceptionHandler.java`
- `linkx-server/src/main/java/com/linkx/server/common/Result.java`
- `linkx-server/src/main/java/com/linkx/server/config/SecurityHeadersFilter.java`
- `linkx-server/src/main/java/com/linkx/server/config/interceptor/LoginInterceptor.java`（在拦截器中抛 `CustomException`）

## 三、前端（Electron + Vue3）

### 3.1 Axios 拦截器策略
- **请求拦截器**：自动从 `tokenStorage` 读取 `accessToken` 注入 `Authorization: Bearer ...`。
- **响应拦截器**：
  - 成功路径：解包 `response.data`，调用方直接拿到 `ApiResult<T>`。
  - 失败路径：若 `status === 401` 或 `data.code === 401`，触发 `processUnauthorized`。

### 3.2 401 自动刷新流程
- 使用 `refreshing` 标志与 `refreshQueue` 队列防止并发重复刷新。
- 先尝试 `/auth/refresh` 获取新 token 对，成功后重放原请求并清空队列。
- 刷新失败则清理本地 token、重置 app store 状态并拒绝 Promise。
- 对 `/auth/refresh`、`/auth/login`、`/auth/register` 接口跳过刷新逻辑，直接走登出。

### 3.3 关键文件
- `linkx-client/src/api/client.ts`（Axios 实例、拦截器、刷新逻辑）
- `linkx-client/src/types/auth.ts`（`ApiResult<T>` 类型定义）
- `linkx-client/src/utils/tokenStorage.ts`（token 持久化）

## 四、架构与约定

| 层面 | 约定 | 说明 |
|------|------|------|
| 错误码 | 复用 HTTP 语义码（400/401/403/429/500） | 后端 `mapStatus` 直接映射，前端以 `code` 判断业务态 |
| 响应体 | 全部走 `Result<T>` | 成功 `code=200`，失败 `data=null`，message 面向用户 |
| 异常传播 | Service 抛 `CustomException`，Controller 不吞异常 | 由 `GlobalExceptionHandler` 统一收敛 |
| 安全层 | Filter 早于 MVC 层输出 JSON 错误 | 避免进入控制器即阻断（如 HTTPS 强制） |
| 前端鉴权 | 401 自动刷新一次，失败则登出 | 通过 `_retry` 标记防重入，队列保证顺序 |

## 五、开发者规则

1. **业务错误一律抛 `CustomException`**，不要手动 `return Result.error(...)` 从 Controller 分支返回，让全局处理器统一格式化。
2. **错误码优先使用 HTTP 语义码**：400 参数错、401 未认证、403 无权限、429 限流、500 未知异常；仅在需要区分子场景时才扩展自定义码。
3. **不要在 Service 层 catch 后吞掉异常**，应向上抛出，由全局处理器记录日志并返回友好提示。
4. **前端只依赖 `ApiResult.code` 判断业务态**，不要直接读 `response.status`；401 交由拦截器处理，业务代码无需感知。
5. **新增安全/前置校验**放在 Filter 或 Interceptor 中，通过 `Result.error` 快速返回，避免污染业务逻辑。
6. **刷新令牌期间阻塞并发请求**，利用 `refreshQueue` 等待而非各自重试，避免雪崩。