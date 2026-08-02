# LinkX 全项目覆盖率仪表盘（A + B + C）

更新日期：2026-08-02

## 目标

| 口径 | 目标 |
|------|------|
| A 行覆盖 | Server / Client / Admin 业务代码 LINE→100%（排除见下） |
| B 业务 | OpenAPI 每个 REST 至少 1 条成功路径自动化；关键路由 E2E；WS 发消息自动化 |
| C 种类 | 单元/集成、E2E、压测脚本、SCA/SAST、ZAP 公开面均进 CI，`CI OK` 门禁 |

### A 排除（不计入分母）

- Server：`**/entity/table/**`、`**/controller/dto/**`、`**/controller/vo/**`、`**/mapper/**`
- Client：`*.d.ts`、`types/**`、测试文件
- Admin Vitest Phase 3.1 仅统计：`format.ts` / `mediaUrl.ts` / `menuI18n.ts` / `roleSmokeMatrix.ts`（api/router/stores/i18n/views 在后续轮次纳入；views 由 Playwright 负责）

## 基线与当前门禁

| 模块 | 指标 | 基线实测 | 当前 CI 门禁 | 下一目标 |
|------|------|----------|--------------|----------|
| Server JaCoCo | LINE | 51.97% → **82.12%** | **80%**（3.2b） | **100%** |
| Server JaCoCo | INSTRUCTION | 49.90% → **80.42%** | **78%** | 90% → 100% |
| Client Vitest | LINE | 51.38% | **40%** | 70% → 100% |
| Admin Vitest | LINE（工具层） | ~98%（纳入文件） | **40%** | 扩大 include → 100% |
| B 端点矩阵 | 启发式 | **282/381（74.0%）** | **fail-under 0.70** | 0.85 → 1.00 |

### 工具

```bash
# A
cd linkx-server && mvn -B verify
cd linkx-client && npm run test:coverage
cd linkx-admin && npm run test:coverage

# B
cd linkx-server/perf/k6
node scripts/endpoint-test-matrix.mjs
node scripts/endpoint-test-matrix.mjs --fail-under 0.70
```

## 抬升记录

| 轮次 | Server line | Client line | Admin | B fail-under | 状态 |
|------|-------------|-------------|-------|--------------|------|
| 基线 | 52% | 51% | — | — | 已测 |
| 3.1a | 门禁 52% / instr 50% | 门禁 40% | 工具层 40% | 0.50 | **已落地** |
| 3.1b | **60.25%**，门禁 60% / instr 55% | 40% | 工具层 40% | 0.50 | **已落地** |
| 3.2a | **65.88%**，门禁 65% / instr 60% | 40% | 工具层 40% | **0.70**（实测 74%） | **已落地** |
| 3.2b | **82.12%**，门禁 80% / instr 78% | 40% | 工具层 40% | 0.85 | **已落地** |
| 3.3 | 100% | 100% | 100% | 1.00 | 待办（冻结） |

## Phase 2 首批（Auth / Health / User）

已有成功路径自动化：

- `AuthControllerTest`：register / login / captcha / **config** / **refresh** / **logout**
- `HealthControllerTest`：`/health` `/live` `/ready`
- `UserControllerTest`：`/user/me`
- `ClientHotPathSuccessIT`：me / friend / group / sessions / auth/config
- `ClientReadApiSuccessIT`：公开面 + 核心只读批测
- `ClientGetCatalogCoverageIT` / `AdminEndpointPathCatalogIT`：路径目录扫
- Admin `criticalViews.spec.ts`：views 全量存在性矩阵
- WS：`ImWebSocketLiveE2ETest` / `ImMainLinkE2ETest`（已有）

## C 种类（CI）

- `detect-changes` 分流 + **`CI OK`**
- Admin：`npm run test:coverage`
- Client：`npm run test:coverage`
- `load-smoke`：k6 静态校验 + **endpoint-test-matrix --fail-under 0.70**
- ZAP：公开面 curl 硬失败 + baseline 告警
- 压测本体：本机 k6（无公网不配 `LOAD_*`）

## 分支保护

建议 required check：**CI OK**

## 下一轮优先补测（冲 Server 80%）

按 JaCoCo 缺口（约需再覆盖 ~2700 LINE）：

1. `ChatServiceImpl` / `GroupServiceImpl` / `ConferenceServiceImpl`
2. `SysUserServiceImpl` / `FileStorageServiceImpl` / `MomentsServiceImpl`
3. `CallServiceImpl` / `ImMessagePushService` / `FavoriteServiceImpl`
4. Client/Admin Vitest 抬阈；B 矩阵补会议/云盘/通话成功路径 → 0.85
