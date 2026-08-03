# 管理端五角色生产抽检清单

> 对齐 `管理端开发文档` §37.4、`linkx-admin/src/acceptance/roleSmokeMatrix.ts`、`AdminRoleSmokeIT`

## 角色与账号准备

| 角色 | role_code | 建议测试账号 | 说明 |
|------|-----------|--------------|------|
| 超级管理员 | `super_admin` / `admin` | 生产超管 | 全菜单、`*` 权限 |
| 运营管理员 | `ops_admin` | 独立测试号 | 反馈/公告/统计/推荐/活动 |
| 审核管理员 | `audit_admin` | 独立测试号 | 审核/举报/敏感词/风控处置 |
| 安全管理员 | `security_admin` | 独立测试号 | 日志/设备/黑名单/IP 限流 |
| 只读观察员 | `readonly_observer` | 独立测试号 | 只读列表与导出，写操作 403 |

**注意：** 每个角色使用独立账号，勿与真实运营账号混用。抽检前确认 Flyway 已执行至 **V54+**。

---

## 自动化（推荐先做）

### 1. 后端集成冒烟（本地/CI）

```bash
cd linkx-server
mvn -Dtest=AdminRoleSmokeIT,AdminRolePermissionIT,AdminRateLimitIT test
```

### 2. 前端矩阵单元测试

```bash
cd linkx-admin
npm test -- src/acceptance/roleSmokeMatrix.spec.ts
```

### 3. 生产/联调 API 抽检

```bash
cd linkx-admin
# 方式 A：JSON 凭证
export ADMIN_API_BASE=https://your-api.example.com/api
export ROLE_SMOKE_CREDENTIALS='[
  {"roleCode":"super_admin","username":"admin","password":"***"},
  {"roleCode":"ops_admin","username":"ops","password":"***"},
  {"roleCode":"audit_admin","username":"audit","password":"***"},
  {"roleCode":"security_admin","username":"security","password":"***"},
  {"roleCode":"readonly_observer","username":"readonly","password":"***"}
]'
npm run test:role-smoke:live

# 方式 B：分项环境变量
export ADMIN_SMOKE_OPS_USER=ops ADMIN_SMOKE_OPS_PASS=***
# ... 其他角色同理
npm run test:role-smoke:live
```

### 4. 浏览器 Live E2E（可选）

```bash
cd linkx-admin
ADMIN_USER=admin ADMIN_PASS=*** npm run test:e2e:live
```

---

## 人工抽检表（可选；自动化已覆盖时可跳过）

> 矩阵单测 + `AdminRoleSmokeIT` 等已通过时，本节仅作生产环境疑难排查参考。

### 超级管理员

- [ ] 登录成功，侧边栏含：系统配置、菜单管理、角色管理、IP 限流、**版本管理**
- [ ] 可进入 `/admin/settings` 并保存配置
- [ ] 可进入 `/admin/menus` 并看到菜单树
- [ ] 可进入 `/admin/rate-limits` 并加载列表
- [ ] 可进入 `/admin/versions` 并 CRUD/发布版本
- [ ] 冻结用户 / 发布公告等写操作成功（非 403）

### 运营管理员 (`ops_admin`)

- [ ] 可见：仪表盘、反馈、**反馈分流规则**、公告、统计、推荐、活动、**首页编排**
- [ ] **不可见**：系统配置、风险事件、IP 限流、菜单管理
- [ ] 可回复反馈、可手动指派、可新建公告
- [ ] 可进入 `/admin/feedback-dispatch-rules` 并管理规则
- [ ] 冻结用户 → **403**
- [ ] 修改系统配置 → **403**

### 审核管理员 (`audit_admin`)

- [ ] 可见：违规内容、用户举报、**群公告审核**、敏感词、风险事件、设备、**异常访问**
- [ ] **不可见**：公告管理、统计分析、系统配置、IP 限流
- [ ] 可审核通过/驳回、可**下架内容**（有权限时）
- [ ] 新建公告 → **403**

### 安全管理员 (`security_admin`)

- [ ] 可见：操作/登录日志、风险事件、设备、黑名单、IP 限流、**异常访问**
- [ ] **不可见**：反馈管理、公告管理
- [ ] 可处置风险、可解除限流
- [ ] 回复反馈 → **403**

### 只读观察员 (`readonly_observer`)

- [ ] 可见：仪表盘、用户、设备、统计、日志（只读）
- [ ] **不可见**：系统配置、黑名单、公告、IP 限流
- [ ] 可导出用户/设备列表
- [ ] 冻结用户 / 踢设备 / 新建公告 → **403**

---

## 常见权限漂移点（`V54` 后重点看）

| 现象 | 可能原因 |
|------|----------|
| 看得见菜单，接口 403 | `sys_admin_role_menu` 有菜单但 `sys_role_permission` 缺权限码 |
| 运营看不到推荐/活动/首页编排 | `ops_admin` 未绑定 `admin:recommend:*` / `admin:activity:*` / `admin:homepage:*` |
| 运营看不到分流规则 | `ops_admin` 未绑定 `admin:feedback-dispatch-rule:*` |
| 审核看不到群公告/异常访问 | `audit_admin` 未绑定 `announcement-review` 菜单或 `admin:abnormal-access:list` |
| 安全看不到 IP 限流 | `security_admin` 未绑定 `admin:rate-limit:*` |
| 只读能踢设备 | `readonly_observer` 误绑 `admin:device:kick` |

修复后执行：`rbacService.evictUserCache(userId)` 或重新登录。

---

## 抽检记录模板

| 日期 | 环境 | 执行人 | 自动化结果 | 人工结果 | 备注 |
|------|------|--------|------------|----------|------|
| YYYY-MM-DD | prod/staging | | IT ✓/✗ | 5/5 ✓ | |
