# LinkX 管理端接口规格书

> 与现网实现对齐 · 盘点基准：`linkx-server` `controller/admin/*` · OpenAPI：`linkx-server/perf/k6/openapi.json`  
> Postman/Apifox 集合：`linkx-admin.postman_collection.json`（见 [API_COLLECTION_README.md](./API_COLLECTION_README.md)）

## 1. 通用约定

| 项 | 约定 |
|----|------|
| 前缀 | `/api/admin/**`（网关后统一加 `/api`） |
| 鉴权 | `Authorization: Bearer <accessToken>` |
| 响应 | `Result<T>`：`{ code, message, data }` |
| 分页列表 | `{ items, page, size, total }` |
| 列表查询 | `page`, `size`, `keyword`, `status`, `startTime`, `endTime`, `sortBy`, `sortOrder` |
| 高危操作 | `@RequireStepUp`：TOTP / 邮箱二次验证 |
| 写操作审计 | 关键写接口记入 `sys_audit_log` |

## 2. 控制器与路径映射

| Controller | 基础路径 | 说明 |
|------------|----------|------|
| `AdminAuthController` | `/admin/auth` | 登录、刷新、菜单、权限、TOTP、个人资料 |
| `AdminDashboardController` | `/admin/dashboard` | 摘要、趋势、实时、待办 |
| `AdminEventsController` | `/admin/events` | SSE 实时事件流 |
| `AdminUserController` | `/admin/users` | 用户 CRUD、冻封、设备、登录记录、导出 |
| `AdminRoleController` | `/admin/roles` | 角色、菜单/权限/用户绑定 |
| `AdminPermissionController` | `/admin/permissions` | 权限点 CRUD |
| `AdminMenuController` | `/admin/menus` | 菜单树 CRUD、排序 |
| `AdminDeptController` | `/admin/depts` | 部门树 CRUD |
| `AdminAuditLogController` | `/admin/audit-logs` | 操作日志 |
| `AdminLoginLogController` | `/admin/login-logs` | 登录日志（含 IP 归属地） |
| `AdminRiskEventController` | `/admin/risk-events` | 风险事件处置、导出、批量 |
| `AdminRateLimitController` | `/admin/rate-limits` | IP 限流控制台（`V44`） |
| `AdminFeedbackController` | `/admin/feedback` | 反馈列表、回复、关闭、SLA 筛选 |
| `AdminReviewController` | `/admin/reviews` | 内容审核（含 `sourceType=report` 举报） |
| `AdminSensitiveWordController` | `/admin/sensitive-words` | 敏感词 |
| `AdminBlacklistController` | `/admin/blacklist` | 黑名单 |
| `AdminDeviceController` | `/admin/devices` | 设备踢下线、封禁、导出 |
| `AdminNoticeController` | `/admin/notices` | 公告、收件箱 |
| `AdminBannerController` | `/admin/banners` | Banner |
| `AdminRecommendController` | `/admin/recommends` | 推荐位（`V40`） |
| `AdminActivityController` | `/admin/activities` | 活动（`V40`） |
| `AdminSettingController` | `/admin/settings` | 分类配置（register/login/password/client/mail/admin） |
| `AdminStatisticsController` | `/admin/statistics` | 统计、热力图、群活跃、导出 |
| `AdminExportJobController` | `/admin/export-jobs` | 异步导出任务（`V43`） |

## 3. 核心接口清单

### 3.1 认证

- `POST /admin/auth/login` · `POST /admin/auth/logout` · `POST /admin/auth/refresh`
- `GET /admin/auth/me` · `GET /admin/auth/menus` · `GET /admin/auth/permissions`
- TOTP：`/admin/auth/totp/*` · `PUT /admin/auth/profile`

### 3.2 仪表盘

- `GET /admin/dashboard/summary` · `trends` · `realtime` · `pending-tasks`
- 摘要字段含：`pendingReports`, `overdueFeedback`, `todaySensitiveHits`, `todayRiskBlocks`, `dau/wau/mau`

### 3.3 用户

- `GET/PUT /admin/users` · `GET /admin/users/{id}`
- `POST .../freeze|unfreeze|ban|unban|reset-password`
- `GET .../devices` · `.../logins` · `GET .../export`
- 异步导出：`POST /admin/export-jobs` + `GET .../{id}` + `.../download`

### 3.4 RBAC

- 角色：`GET/POST/PUT/DELETE /admin/roles` · `GET/PUT .../{id}/menus|permissions|users`
- 权限：`GET/POST/PUT/DELETE /admin/permissions`
- 菜单：`GET/POST/PUT/DELETE /admin/menus` · `POST /admin/menus/reorder`
- 部门：`GET/POST/PUT/DELETE /admin/depts`

### 3.5 治理

- 反馈：`GET /admin/feedback`（`overdueOnly`）· `POST .../reply|close|reopen`
- 审核：`GET /admin/reviews`（`sourceType`, `targetType`, `riskLevel`）· `approve|reject|batch`
- 举报：前端 `/admin/reports` 复用 reviews API，`sourceType=report`（`V45`）

### 3.6 运营与配置

- 公告/ Banner / 推荐 / 活动：标准 CRUD + `publish|unpublish`
- 配置：`GET /admin/settings` · `PUT /admin/settings/{register|login|password|client|mail|mail-templates|admin}`
- 客户端版本：走 `settings/client`（`forceUpdate`, `minSupportedVersion`, `appChannel` 灰度）
- **无**独立 `/admin/versions/**`（已并入 settings）

### 3.7 统计

- `overview` · `users` · `content` · `risk` · `feedback` · `groups` · `activity-heatmap` · `export`

## 4. 未实现（文档草案有、现网无）

| 路径 | 说明 |
|------|------|
| `POST/PUT/DELETE /admin/versions/**` | 版本走 `settings/client` |
| `GET /admin/settings/public|security|upload|notification` | 已拆分为分类 PUT |
| `AdminVersionController` | 未建 |

## 5. 权限码

完整字典见主文档 §25；种子角色：`admin`/`super_admin`、`ops_admin`、`audit_admin`、`security_admin`、`readonly_observer`（`V47` 绑定修复）。

## 6. 联调与验收

- 五角色抽检：[../testing/ADMIN_FIVE_ROLE_CHECKLIST.md](../testing/ADMIN_FIVE_ROLE_CHECKLIST.md)
- 后端 IT：`AdminRoleSmokeIT`, `AdminM6FeaturesIT`, `AdminRolePermissionIT`
- 前端矩阵：`linkx-admin/src/acceptance/roleSmokeMatrix.ts`
