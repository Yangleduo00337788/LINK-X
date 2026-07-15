---
kind: configuration_system
name: LinkX 配置系统：Spring Boot + Vite 双端分层配置
category: configuration_system
scope:
    - '**'
source_files:
    - linkx-server/src/main/resources/application.yml
    - linkx-server/src/main/resources/application-local.yml
    - linkx-server/src/main/java/com/linkx/server/config/LinkxProperties.java
    - linkx-server/src/main/java/com/linkx/server/config/MinioConfig.java
    - linkx-client/.env.example
    - linkx-client/vite.config.ts
    - linkx-client/src/utils/tokenStorage.ts
---

## 1. 整体方案

LinkX 采用前后端分离的分层配置体系：后端基于 Spring Boot 的 application.yml + @ConfigurationProperties，前端基于 Vite 的 .env.* 环境变量。两者通过环境变量名约定与部署脚本配合，实现多环境切换。

## 2. 后端配置（Spring Boot）

- 核心配置文件
  - linkx-server/src/main/resources/application.yml：全局默认配置，所有敏感项通过 ${ENV_VAR:default} 占位符注入，支持运行时覆盖。
  - linkx-server/src/main/resources/application-local.yml：本地开发覆盖配置，已加入 .gitignore，由 application-local.yml.example 复制而来。
  - spring.profiles.active=${SPRING_PROFILES_ACTIVE:local} 控制激活 profile。

- 类型安全绑定
  - com.linkx.server.config.LinkxProperties：使用 @ConfigurationProperties(prefix = "linkx") 将 linkx.* 节点映射为强类型对象，包含子配置类 Jwt、Auth、Cors、Security、Minio、Im。
  - MinioConfig：从 LinkxProperties.Minio 读取 MinIO 连接信息并初始化 MinioClient Bean，启动时自动检查/创建 bucket。

- 关键配置项
  - 数据库：spring.datasource.url/username/password，Redis：spring.data.redis.*，均通过环境变量注入。
  - JWT：linkx.jwt.secret/access-expire/refresh-expire。
  - 认证与限流：linkx.auth.captcha-enabled/login-max-attempts/rate-limit-*。
  - CORS：linkx.cors.allowed-origins。
  - IM WebSocket：linkx.im.websocket-port=8081（独立于 HTTP 8080）。
  - MinIO：linkx.minio.endpoint/access-key/secret-key/bucket-name/max-file-size。

## 3. 前端配置（Vite + Electron）

- 环境变量文件
  - linkx-client/.env.example：提供 VITE_API_BASE_URL（HTTP API，含 /api context-path）和 VITE_WS_BASE_URL（Netty WebSocket，端口 8081）两个关键变量。
  - Vite 构建时自动加载 .env / .env.development / .env.production / .env.electron 等文件，变量以 VITE_ 前缀暴露给客户端代码。

- 构建期配置
  - vite.config.ts：定义 Electron 模式（mode === 'electron'）、插件链（Vue + UnoCSS + vite-plugin-electron）、分包策略（naive-ui、vue-vendor 手动 chunk），以及 preload 脚本拷贝逻辑。
  - package.json 中 scripts 区分 dev（纯 Web）与 electron:dev（Electron 渲染进程 + 主进程），build 与 electron:build 分别对应 Web 打包与桌面应用打包（electron-builder）。

- 运行时持久化
  - src/utils/tokenStorage.ts：封装 token 存储，优先使用 Electron 主进程提供的 window.electronAPI.secureStorage（操作系统级安全存储），不可用时回退到 localStorage，键名前缀 linkx:token: 避免冲突。

## 4. 架构与约定

- 后端：application.yml 统一入口，敏感值走环境变量；自定义配置集中在 linkx.* 命名空间并通过 @ConfigurationProperties 绑定。
- 前端：环境变量以 VITE_ 前缀暴露；Electron 模式通过 --mode electron 切换；token 存储优先 OS 安全存储，降级 localStorage。
- 部署：docker-compose.yml（服务端）+ electron-builder，通过环境变量注入数据库、Redis、MinIO、JWT 等外部依赖地址与凭据。

## 5. 开发者规范

1. 新增配置项：在 application.yml 的 linkx.* 下声明，并在 LinkxProperties 对应子类中添加字段，保持默认值合理。
2. 敏感信息：一律使用 ${ENV_VAR} 占位符，禁止硬编码；本地开发通过 application-local.yml 覆盖，该文件不得提交。
3. 前端环境变量：新增 VITE_* 变量后同步更新 .env.example，确保新成员可快速搭建。
4. Electron 安全存储：涉及用户凭证必须通过 tokenStorage.ts 存取，不要直接操作 localStorage。
5. 多环境切换：后端通过 SPRING_PROFILES_ACTIVE 切换，前端通过 Vite --mode 参数切换，不要在代码中写死环境判断。