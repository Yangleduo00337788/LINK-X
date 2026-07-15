---
kind: external_dependency
name: Redis 7.2 缓存与会话存储
slug: redis
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

用于 Refresh Token 可吊销存储及登录限流等能力。通过 spring-boot-starter-data-redis 接入，密码通过 REDIS_PASSWORD 环境变量注入；Docker 容器启用 AOF 持久化。默认端口 6379，数据库索引 0。