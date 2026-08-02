# 反馈自动分流规则引擎 — 评估结论

> 评估日期：2026-08-02 · 关联：`V46` 反馈 SLA、`AdminFeedbackServiceImpl`

## 1. 现状（已具备）

| 能力 | 实现 |
|------|------|
| SLA 小时数配置 | `sys_runtime_setting.feedback_sla_hours`，管理端「系统配置 → 客户端」可改 |
| 超时判定 | `pending` 且 `createTime <= now - SLA` → `overdue=true` |
| 超时筛选 | `GET /admin/feedback?overdueOnly=true` |
| 仪表盘待办 | `overdueFeedback` 计数 |
| 人工处置 | 回复 / 关闭 / 重开，状态 `pending → replied → closed` |

**结论：** 运营侧「超时可见、可筛、可统计」已闭环，满足当前 M6 目标。

## 2. 缺口（自动分流尚未做）

| 项 | 说明 |
|----|------|
| 处理人字段 | `sys_feedback` **无** `assignee_id`（审核任务 `sys_review_task` 有） |
| 分流规则 | 无按类型/关键词/负载/班次的规则配置 |
| 自动派单 | 新建反馈时无自动写入 assignee |
| 升级策略 | 超时后无自动改派、通知上级、或升优先级 |
| 规则引擎表 | 无 `sys_feedback_dispatch_rule` 等配置表 |
| 管理端 UI | 无规则 CRUD、无「我的工单」视图 |

## 3. 方案对比

### 方案 A：暂不实施（推荐）

- **理由：** SLA 已覆盖「发现超时」；团队规模小阶段人工认领成本可接受。
- **成本：** 0 开发量。
- **风险：** 工单量大时人工分拣效率低（可等业务量触发再立项）。

### 方案 B：最小派单（M7 轻量，约 3–5 人日）

1. `sys_feedback` 增加 `assignee_id`、`assigned_at`
2. 管理端列表支持「指派给我 / 未指派」筛选 + 手动指派下拉
3. 可选：按 `type` 的静态映射（JSON 配置，非完整规则引擎）

### 方案 C：完整规则引擎（第二版，约 2–3 周）

1. 规则表：条件（type/关键词/渠道）+ 动作（指派角色/用户/队列）
2. 定时任务：扫描超时 → 升级/通知（邮件/站内/SSE）
3. 与部门、`ops_admin` 值班表联动
4. 审计：指派/改派记入 `sys_audit_log`

## 4. 决策

| 维度 | 结论 |
|------|------|
| **当前迭代** | **不做**（方案 A） |
| **触发条件** | 待处理反馈常态 >50/天，或平均首次响应 >SLA 的 50% |
| **下一里程碑** | 若立项，优先 **方案 B**，验证指派流程后再考虑方案 C |
| **依赖** | 方案 B 仅需 Flyway + `AdminFeedbackController`；方案 C 需通知通道与规则 UI |

## 5. 若实施方案 B 的任务拆解（备忘）

1. `V48__feedback_assignee.sql`：`assignee_id`、`assigned_at`
2. `PUT /admin/feedback/{id}/assign` + 权限 `admin:feedback:assign`
3. 列表筛选 `assigneeId` / `unassignedOnly`
4. `FeedbackListView`：指派列 + 筛选
5. 单元测试 + `AdminFeedbackServiceTest` 补充

---

**批准建议：** 维持 SLA 运营流程；将自动分流列入 **第二版 backlog**（见 `docs/admin/ADMIN_V2_BACKLOG.md`），不在当前迭代排期。
