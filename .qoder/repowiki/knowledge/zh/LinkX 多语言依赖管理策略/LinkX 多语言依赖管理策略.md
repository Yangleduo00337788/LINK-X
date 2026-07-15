---
kind: dependency_management
name: LinkX 多语言依赖管理策略
category: dependency_management
scope:
    - '**'
source_files:
    - linkx-client/package.json
    - linkx-client/package-lock.json
    - linkx-server/pom.xml
---

本仓库采用多语言、多模块的依赖管理方案，前端与后端各自使用其生态的标准工具链，并通过锁定文件保证构建可复现性。

## 1. 前端（linkx-client）— npm + package-lock.json
- 包管理器：npm（lockfileVersion=3），通过 package-lock.json 锁定所有子依赖版本，确保 CI/本地一致。
- 源镜像：从 lock 文件中大量 https://registry.npmmirror.com/... 可知，团队默认使用淘宝镜像加速下载，可通过 .npmrc 或环境变量统一配置。
- 依赖分类：dependencies 为运行时依赖（Vue3、Pinia、Naive UI、Axios、图标库等）；devDependencies 为构建与开发期依赖（Vite、TypeScript、electron-builder、UnoCSS 等）。
- 打包产物：Electron 应用通过 electron-builder 在 release/ 目录输出 Windows NSIS、macOS DMG、Linux AppImage 安装包。
- 无 vendoring：未使用 pnpm/yarn workspaces 或私有 npm registry，也未见 .npmrc 中的 NODE_AUTH_TOKEN 等认证配置。

## 2. 后端（linkx-server）— Maven + Spring Boot Parent
- 构建系统：Maven，继承 spring-boot-starter-parent:3.3.0，统一管理 Spring 生态版本。
- 版本集中化：通过 <properties> 声明 mybatis-flex.version、jjwt.version、netty.version 等关键第三方版本，避免散落在各 <dependency> 中。
- 依赖范围：runtime 用于 MySQL Connector/J、JWT impl/jackson 实现类，不进入编译期 classpath；provided 用于 Servlet API，由运行容器提供；optional=true 用于 Lombok，仅注解处理需要。
- 插件：maven-compiler-plugin 指定 Java 21；spring-boot-maven-plugin 排除 Lombok 参与打包。
- 无私有仓库：未见 settings.xml 或 <repositories>/<pluginRepositories> 自定义镜像，默认走 Maven Central。

## 3. 架构约定与约束
- 版本策略：前端使用 ^ 语义化版本，允许小版本升级；后端关键三方件通过 <properties> 集中管控。
- 锁定文件：前端提交 package-lock.json；后端不提交 target/，由 CI 生成。
- 私有包：当前未发现私有 npm registry 或 Maven 私服配置，如需接入需补充 .npmrc / settings.xml。
- 安全扫描：未引入 npm audit 或 OWASP dependency-check 插件，可在 CI 阶段补充。
- 更新流程：建议先改 package.json/pom.xml，再执行 npm i / mvn dependency:resolve 刷新锁文件后提交。

## 4. 开发者应遵循的规则
1. 新增依赖前先评估必要性，优先复用已有 starter（如 Spring Boot Starter），避免重复引入。
2. 统一版本来源：后端新增三方件时，将其版本放入 <properties>；前端尽量保持与现有 ^ 风格一致。
3. 提交锁定文件：修改 package.json 后必须提交 package-lock.json；后端变更 pom.xml 后在 CI 中验证 mvn clean install。
4. 敏感信息不入仓：若后续引入私有 npm/Maven 仓库，请将 token 写入 CI 变量而非代码。
5. 定期审计：建议在流水线中加入 npm audit --production 与 Maven 依赖漏洞扫描。