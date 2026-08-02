# LinkX 管理端数据库设计书

> 与 Flyway 迁移对齐 · 管理端相关：`V5`–`V47`（及 `V8`–`V9` 运行时配置）

## 1. 设计原则

- 复用客户端 RBAC：`sys_role`, `sys_permission`, `sys_role_permission`, `sys_user_role`
- 管理端菜单独立：`sys_admin_menu`, `sys_admin_role_menu`
- 审计复用：`sys_audit_log`（操作）、`sys_login_audit`（登录）
- 不单独建管理员表：管理端账号为 `sys_user` + 管理角色
- 版本信息在 `sys_runtime_setting`，无 `sys_admin_version` 表

## 2. 核心表

### 2.1 菜单与权限

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_admin_menu` | V5, V33, V45 | 树形菜单/按钮/API 节点 |
| `sys_admin_role_menu` | V5, V28–V31, V47 | 角色 ↔ 菜单 |
| `sys_permission` | V5+ | 权限码（含 `admin:*`） |
| `sys_role_permission` | V5, V28–V31, V41, V47 | 角色 ↔ 权限 |

### 2.2 组织与数据权限

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_dept` | V36 | 部门树 |
| `sys_role_dept` | V38 | 自定义组织数据范围 |
| `sys_role.data_scope` | V36 | 全部/本人/本部门及下级/自定义 |

### 2.3 审计与风控

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_audit_log` | 既有 | 操作审计 |
| `sys_login_audit` | 既有 | 登录审计 + IP |
| `sys_risk_event` | V20 | 风险事件 |
| `sys_admin_export_job` | V43 | 异步导出任务 |

### 2.4 内容与治理

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_feedback` | V7 | 反馈（`reply` 字段，无独立回复表） |
| `sys_review_task` | V14 | 审核/举报/敏感词入审 |
| `sys_sensitive_word` | V14 | 敏感词库 |

### 2.5 运营

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_admin_notice` | V16–V18 | 公告 |
| `sys_banner` | V23 | Banner |
| `sys_ops_recommend` | V40 | 推荐位 |
| `sys_ops_activity` | V40 | 活动 |

### 2.6 配置

| 表 | 迁移 | 说明 |
|----|------|------|
| `sys_runtime_setting` | V8, V9, V42, V46 | 注册/登录/密码/邮件/客户端/管理端配置 |
| 关键字段 | V34, V46 | `force_update`, `min_supported_version`, `app_channel`, `feedback_sla_hours`, `sensitive_filter_enabled`, `support_email/phone` |

### 2.7 设备与安全

| 表 | 迁移 | 说明 |
|----|------|------|
| 设备会话 | 既有 + V26, V39 | 踢下线、封禁、绑定 |
| 用户黑名单 | V21 | 黑名单 |
| TOTP | V25 | 管理端双因素 |

## 3. 种子角色 ID

| ID | role_code | 说明 |
|----|-----------|------|
| 1001 | `admin` | 超管，通配 `*` |
| 1003 | `ops_admin` | 运营 |
| 1004 | `audit_admin` | 审核 |
| 1005 | `security_admin` | 安全 |
| 1006 | `readonly_observer` | 只读 |

权限绑定修复：**V47**（推荐/活动/限流/重置密码等）

## 4. 未建表（文档曾建议）

| 表 | 状态 |
|----|------|
| `sys_admin_permission` | 复用 `sys_permission` |
| `sys_feedback_reply` | 复用 `sys_feedback.reply` |
| `sys_admin_version` | 字段在 runtime setting |
| `sys_admin_dashboard_snapshot` | ❌ 未做 |
| `sys_admin_statistic_snapshot` | ❌ 未做 |

## 5. 迁移索引（管理端）

```
V5   菜单与权限种子
V8–9 运行时配置
V10  版本菜单隐藏
V14  审核与敏感词
V16–18 公告
V19  统计
V20  风险事件
V21  黑名单
V23  Banner
V25  TOTP
V26–27 设备管理
V28–31 运营/审核/安全/只读角色
V32  重置密码
V33  菜单排序
V34  强更
V36–38 部门与数据权限
V39  设备封禁绑定
V40  推荐/活动
V41  角色分配权限
V42  敏感词总开关/客服
V43  异步导出
V44  IP 限流控制台
V45  举报菜单
V46  反馈 SLA
V47  角色权限补齐
```

## 6. 维护说明

新增管理端能力时：

1. 先查是否可复用现有表
2. 新表需：主键、状态、审计字段、`deleted`
3. 同步 `sys_permission` + `sys_admin_menu` + 角色绑定
4. 补 Flyway 与 `schema.sql` 测试种子
