---
kind: build_system
name: LinkX 构建与打包体系
category: build_system
scope:
    - '**'
source_files:
    - linkx-client/package.json
    - linkx-client/vite.config.ts
    - linkx-client/electron/main.ts
    - linkx-server/pom.xml
    - linkx-server/docker-compose.yml
---

## 1. 使用的构建系统与技术栈
- 前端（桌面客户端）：Vite + Vue3 + TypeScript，通过 `vite-plugin-electron` 将 Electron 主进程纳入 Vite 管线；使用 `electron-builder` 生成跨平台安装包。
- 后端（Spring Boot）：Maven + Spring Boot 3.3，基于 `spring-boot-maven-plugin` 打包为可执行 JAR。
- 本地开发环境编排：`docker-compose.yml` 一键拉起 MySQL、Redis、MinIO 三个依赖服务。
- 无 CI/CD 流水线、Makefile、Dockerfile 等自动化发布脚本，当前以本地命令驱动为主。

## 2. 关键文件与职责
- `linkx-client/package.json`
  - 定义 `dev` / `build` / `electron:dev` / `electron:build` 等 npm scripts。
  - `electron:build` 串联 `vue-tsc --noEmit && vite build --mode electron && electron-builder`，完成类型检查、Vite 打包与多平台分发。
  - `build` 字段配置 `electron-builder`：产物输出目录 `release`，Windows NSIS、macOS dmg、Linux AppImage 三端目标。
- `linkx-client/vite.config.ts`
  - 通过 `vite-plugin-electron` 指定 Electron 主进程入口 `electron/main.ts`，输出到 `dist-electron/main`。
  - 在 `electron` 模式下启用 `vite-plugin-electron-renderer`，并手动复制 CJS preload 脚本到 `dist-electron/preload`，避免 ESM 注入问题。
  - Rollup 层面拆分 `naive-ui`、`vue-vendor` 等 vendor chunk，控制体积。
- `linkx-client/electron/main.ts`
  - Electron 主进程逻辑：窗口管理、托盘、全局快捷键、IPC 桥接、安全存储（`safeStorage`）、自动启动等。
  - 通过 `process.env.VITE_DEV_SERVER_URL` 区分开发与生产加载 URL。
- `linkx-server/pom.xml`
  - 继承 `spring-boot-starter-parent:3.3.0`，Java 21，声明 MyBatis-Flex、Netty、JWT、MinIO 等依赖。
  - 构建插件：`maven-compiler-plugin` 指定 release=21，`spring-boot-maven-plugin` 排除 Lombok 后打包可执行 JAR。
- `linkx-server/docker-compose.yml`
  - 定义 `mysql`、`redis`、`minio` 三个服务，挂载数据卷与初始化 SQL，提供本地一键运行依赖的能力。

## 3. 架构与约定
- **双模块独立构建**：前后端各自维护独立的包管理与构建配置，互不耦合；根目录仅包含 `docker-compose.yml` 用于编排依赖服务。
- **Electron 构建模式**：通过 Vite mode `electron` 切换 Electron 专用插件链，渲染进程与主进程共用同一 Vite 配置，但主进程单独编译到 `dist-electron`。
- **预加载脚本策略**：CJS 形式的 `preload.cjs` 由自定义插件在构建时复制到 `dist-electron/preload`，主进程按候选路径动态解析，兼容开发与打包两种场景。
- **后端容器化依赖**：未对应用本身打镜像，而是用 docker-compose 拉起数据库/缓存/对象存储，配合 `init.sql` 与 `migrations/*.sql` 完成库表初始化。
- **版本策略**：前端 `package.json` 中 `version: "1.0.0"`，后端 `pom.xml` 中 `1.0.0-SNAPSHOT`，两者独立演进，未见统一版本号同步机制。

## 4. 开发者应遵循的规则
- **前端构建命令**
  - 开发：`npm run dev`（纯 Web）或 `npm run electron:dev`（Electron 调试）。
  - 打包：`npm run electron:build`，产物位于 `linkx-client/release`。
- **后端构建命令**
  - 清理+编译+打包：`mvn clean package`，产物位于 `linkx-server/target/linkx-server-1.0.0-SNAPSHOT.jar`。
  - 直接运行：`mvn spring-boot:run`。
- **本地依赖服务**
  - 在项目根目录执行 `docker compose up -d` 启动 MySQL/Redis/MinIO，再分别启动前后端即可联调。
- **Electron 构建注意事项**
  - 修改主进程入口需同步更新 `vite.config.ts` 中的 `entry` 与 `outDir`。
  - 新增 IPC 接口需在 `main.ts` 的 `registerWindowIpc` 中注册，保持命名空间前缀一致（如 `window:*`、`secure-storage:*`）。
  - 若引入新的 Node/Electron API，确保在 `electron-builder` 的 `files` 中包含对应资源。
- **后端依赖变更**
  - 所有第三方库版本集中在 `pom.xml` 的 `<properties>` 与 `<dependencies>` 中管理，升级时需关注 Spring Boot 3.x 兼容性。
  - 新增数据库结构变更请放在 `migrations/` 下，并在 `docker-compose.yml` 的 init 卷中确认被挂载。
- **无 CI/CD 现状**
  - 仓库未包含 GitHub Actions、Jenkinsfile、Makefile 或 Dockerfile；如需接入自动化，建议先在根目录补充 `Dockerfile` 与 `.github/workflows/build.yml`，并将前后端构建步骤合并为单一流水线。