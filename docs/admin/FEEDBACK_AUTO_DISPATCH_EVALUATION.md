# 反馈自动分流规则引擎 — 评估结论

> 评估日期：2026-08-03（修订）· 关联：`V46` SLA、`V51` 轻量分流、`V54` 多轮回复、**`V55` 超时升级**

## 1. 现状（已具备）

| 能力 | 实现 |
|------|------|
| SLA 小时数配置 | `sys_runtime_setting.feedback_sla_hours`，管理端「系统配置 → 客户端」可改 |
| 超时判定 | `pending` 且 `createTime <= now - SLA` → `overdue=true` |
| 超时筛选 | `GET /admin/feedback?overdueOnly=true` |
| 仪表盘待办 | `overdueFeedback` 计数 |
| 人工处置 | 回复 / 关闭 / 重开，状态 `pending → replied → closed` |
| **处理人指派** | `sys_feedback.assignee_id`、`PUT /admin/feedback/{id}/assign`（`V51`） |
| **轻量自动分流** | `sys_feedback_dispatch_rule`：按 `type` / `keyword` + 优先级匹配（`V51`） |
| **我的工单** | `GET /admin/feedback?mineOnly=true`（`V51`） |
| **分流规则 UI** | `/admin/feedback-dispatch-rules` CRUD（`V51`） |
| **多轮回复** | `sys_feedback_reply` + 详情页回复线程（`V54`） |
| **超时升级/改派** | `V55`：`FeedbackEscalationTask` 每小时扫描；无处理人自动分流、可选改派；SSE `feedback_escalated` + 审计 `FEEDBACK_ESCALATE` |
| **升级筛选** | `GET /admin/feedback?escalatedOnly=true`；列表展示 `escalated` / `escalationCount` |
| **升级配置** | `feedback_escalation_enabled` / `auto_reassign` / `interval_hours`，管理端「系统配置 → 客户端」 |

**结论：** 运营侧「超时可见、可筛、可统计、可指派、可自动初分、可定时升级」已闭环。

## 2. 仍缺能力（完整版规则引擎）

| 项 | 说明 |
|----|------|
| 负载/班次分流 | 无按值班表、队列深度、轮询指派 |
| 条件+动作可视化 | 当前仅 type/keyword 静态规则，无条件组合器 |
| 上级角色通知 | 升级仅 SSE 广播 + 审计，无按角色邮件/站内催办上级 |

## 3. 方案对比

### 方案 A：SLA + 轻量分流（**已交付**）

- SLA、指派、分流规则、多轮回复、超时升级（V55）。

### 方案 B：超时升级/改派（**V55 已落地**）

1. 定时任务扫描 `pending` 且超过 SLA、且距上次升级超过间隔
2. 动作：无 assignee 自动分流 → 可选按规则改派 → SSE 通知管理端
3. 审计：记入 `sys_audit_log`（`FEEDBACK_ESCALATE`）

### 方案 C：完整规则引擎（第二版 backlog）

1. 规则表扩展：条件组合 + 动作（指派/升级/通知）
2. 与部门、值班表联动
3. 规则命中模拟与统计

## 4. 决策（2026-08-03 更新）

| 维度 | 结论 |
|------|------|
| **当前迭代** | **方案 B 已立项并交付（V55）** |
| **默认行为** | 升级默认关闭，需运营在系统配置中手动开启 |
| **下一里程碑** | 若工单量继续增长，考虑方案 C 或上级角色催办 |

## 5. 实现备忘（V55）

- 迁移：`V55__feedback_escalation.sql`
- 服务：`FeedbackEscalationService` + `FeedbackEscalationTask`（cron `0 15 * * * *`）
- 配置：`LinkxProperties.App` + `sys_runtime_setting` 三字段
- 前端：系统配置开关 + 反馈列表「已升级」标记与筛选

---

**批准建议：** 生产启用前在测试环境验证分流规则与升级间隔；默认保持关闭，由运营按需开启。
