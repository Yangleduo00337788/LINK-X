# LinkX 全项目测试指南

## 金字塔

```
        UI E2E (Playwright: client + admin)
      API / 集成 (MockMvc + Admin*IT + WS E2E)
    单元 (JUnit / Vitest)
  安全 / SCA / 压测 (CodeQL, OSV, npm audit, k6, ZAP)
```

## 命令矩阵

| 模块 | 命令 | 说明 |
|------|------|------|
| Server 全量 | `cd linkx-server && mvn test` | JUnit 全量 |
| Server + 覆盖率 | `cd linkx-server && mvn verify` | JaCoCo line≥52%（冲 60%→100%，见 [COVERAGE.md](./COVERAGE.md)） |
| Client unit | `cd linkx-client && npm test` | Vitest |
| Client coverage | `cd linkx-client && npm run test:coverage` | line≥40% |
| Client E2E | `cd linkx-client && npm run test:e2e` | Playwright |
| Admin coverage | `cd linkx-admin && npm run test:coverage` | line≥40%（不含 views E2E 面） |
| 端点矩阵 B | `node linkx-server/perf/k6/scripts/endpoint-test-matrix.mjs` | CI fail-under 0.70 |
| Admin E2E | `cd linkx-admin && npm run test:e2e` | Playwright（API mock） |
| Admin live E2E | `ADMIN_USER=… ADMIN_PASS=… npm run test:e2e:live` | 真实后端 |
| Admin 五角色抽检 | `npm run test:role-smoke:live`（需凭证） | 见 [ADMIN_FIVE_ROLE_CHECKLIST.md](./ADMIN_FIVE_ROLE_CHECKLIST.md) |
| k6 热路径 | 见 `linkx-server/perf/k6/README.md` | |
| k6 全 API | 同上，OpenAPI 生成 endpoints | |
| ZAP / 公开面 | `bash linkx-server/perf/zap/check-public-urls.sh` | DAST 前置 curl |

## CI：按变更持续跑

见 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)。

| 事件 | 行为 |
|------|------|
| push `main`/`master` | **全量**跑所有 job |
| pull_request | `dorny/paths-filter` 按路径分流 |

| Filter | 路径 | Jobs |
|--------|------|------|
| `server` | `linkx-server/**`、`.github/workflows/**` | `server-tests`、`load-smoke` |
| `client` | `linkx-client/**` | `client-unit`、`client-e2e` |
| `admin` | `linkx-admin/**` | `admin-unit`、`admin-e2e` |
| `security` | server、lockfile、`perf/zap/**`、workflow、SECURITY.md | `dependency-scan`、`zap-baseline` |

汇总 job **`CI OK`**（`ci-ok`）：只要求「本 PR 应跑」的 job 成功。分支保护建议将 **`CI OK`** 设为 required check。

## Nightly 压测

见 [`.github/workflows/nightly-load.yml`](../../.github/workflows/nightly-load.yml)：

- 每天定时：校验 k6 脚本；若配置了 `LOAD_BASE_URL` / `LOAD_USER` / `LOAD_PASS`，自动跑 **hot-path** + **全 API 只读**
- secrets 缺失：压测 job 跳过（不失败）
- 手动 `workflow_dispatch`：可选 hot-path / 含写全 API（`INCLUDE_MUTATING=1`）

## 文档

- [CODE_REVIEW.md](./CODE_REVIEW.md) — 审查结论
- [SECURITY.md](./SECURITY.md) — 安全测试
- [../../linkx-server/perf/k6/README.md](../../linkx-server/perf/k6/README.md) — 压测
- [../../linkx-server/perf/zap/README.md](../../linkx-server/perf/zap/README.md) — ZAP

## CI Jobs 一览

- `detect-changes` → 路径分流
- `server-tests` → `mvn verify`
- `client-unit` → `npm run test:coverage`
- `client-e2e` / `admin-e2e`
- `dependency-scan` → CodeQL + OSV + npm audit
- `load-smoke` → k6 脚本静态校验
- `zap-baseline` → Redis + test-profile 服务 + 公开面 curl + ZAP baseline
- `ci-ok` → 汇总门禁
