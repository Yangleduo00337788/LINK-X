# LinkX 代码审查报告（全项目测试 Phase 1）

审查日期：2026-08-02  
范围：仓库现状 + 近期管理端改动（Device/User/Setting 视图、`AdminM6FeaturesIT`、CI）及测试修复。

## 结论摘要

现有后端集成测试与客户端 Vitest/Playwright 基线可用；审查未发现 P0 鉴权绕过。Phase 1 修复了 4 类测试漂移（TokenType null 语义、Health 探针响应形状、IM push Executor 类型、重置密码注册邮箱验证码）。建议通过 PR 模板与 CODEOWNERS 固化审查门禁。

## 问题分级

### P0（必修）

无。

### P1（建议尽快处理）

| ID | 位置 | 说明 |
|----|------|------|
| P1-1 | CI `server-tests` | 曾只跑约 33 个类，易漏回归；已计划改为 `mvn verify` |
| P1-2 | `HealthControllerTest` | 曾未继承 `BaseIntegrationTest`，在本机有密码 Redis 时上下文失败；已修复 |
| P1-3 | Admin 前端 | 关键页缺少浏览器 E2E；已接入 Playwright mock 冒烟 |

### P2（记录）

| ID | 位置 | 说明 |
|----|------|------|
| P2-1 | `TokenType.fromClaim(null)` | 现抛异常防类型绕过；测试已对齐，属正确安全加固 |
| P2-2 | `ImMessagePushService` | 运行时将 `Executor` 强转为 `ExecutorService`；单元测试需提供真实 `ExecutorService` |
| P2-3 | 公开 `/health` | 刻意不返回 mysql/redis 明细，降低信息面；详细状态仅内网 `/health/ready` DOWN 时可见 |
| P2-4 | 重置密码注册流 | 注册需邮箱验证码；测试须 `seedRegisterEmailCode` / `register()` 辅助方法 |

## 安全关注点（抽查）

- JWT：测试密钥仅测试环境；生产有 `ProductionSecurityValidator` / `ProductionSecretRulesTest`
- Admin：`@RequirePermission` + 前端路由矩阵与 `AdminRoleSmokeIT` / Vitest 对齐
- 限流：登录/重置/注册存在 RateLimit；Admin 有 `AdminRateLimitIT`
- 密钥：未见将生产密钥硬编码进仓库（审查基于公开配置与测试 profile）

## 审查清单（后续 PR 复用）

- [ ] 鉴权/权限变更是否有 IT 覆盖
- [ ] 写操作是否有幂等 / 限流
- [ ] 用户可见错误是否枚举账号
- [ ] Redis key / 验证码是否可被爆破清空
- [ ] 前端路由 `permission` 与后端权限码一致
