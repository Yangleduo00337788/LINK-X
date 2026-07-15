---
kind: external_dependency
name: MySQL 8.4 数据库
slug: mysql
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

项目持久化层使用 MySQL 8.4（通过 docker-compose 一键启动，默认 root/linkx_password，库名 linkx）。后端通过 Spring Boot JDBC + MyBatis-Flex 访问，字符集 utf8mb4、排序规则 utf8mb4_unicode_ci。初始化脚本位于 `init.sql`，增量迁移在 `migrations/` 目录，由 Docker 入口自动加载。连接参数通过环境变量 DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD 注入。