# LinkX 管理端缺口清单（M6 残余 + 第二版）

> 对照主文档 `管理端开发文档.md` · 更新：2026-08-03 · 盘点基准：**Flyway V5–V55**

## 图例

| 标记 | 含义 |
|------|------|
| ✅ | 已落地 |
| ⚠️ | 部分/合并实现 |
| ❌ | 未做 / 暂缓 |
| 🔄 | 持续回归 |

---

## A. 当前迭代（验收）

| # | 项 | 状态 | 说明 |
|---|-----|------|------|
| A1 | 五角色生产抽检 | 🔄 | 自动化已齐；live 需配置凭证，见 [ADMIN_FIVE_ROLE_CHECKLIST.md](../testing/ADMIN_FIVE_ROLE_CHECKLIST.md) |
| A2 | M5 操作留痕/联调回归 | 🔄 | IT + 冒烟矩阵 |
| A3 | ESLint + Prettier | ✅ | `linkx-admin` `npm run lint` / `format:check`；**CI 已接入** |
| A4 | API 集合 | ✅ | `linkx-admin.postman_collection.json` |
| A5 | 文档拆分 | ✅ | 本目录三份规格书 |
| A6 | 文档同步至 V54 | ✅ | 主文档 + 本清单 + 五角色 checklist |

---

## B. V48–V54 已交付（原 M6 残余 / 第二版候选中部分已提前落地）

| 迁移 | 能力 | 状态 | 前后端 |
|------|------|------|--------|
| V48 | 独立版本管理 CRUD + 发布流 | ✅ | `AdminVersionController`、`VersionListView` |
| V49 | 群公告审核专页 | ✅ | `/admin/announcement-reviews` |
| V50 | 审核独立下架内容 | ✅ | `admin:review:delete-content` |
| V51 | 反馈指派 + 轻量自动分流 | ✅ | `assignee_id`、分流规则 CRUD、`mineOnly` |
| V52 | 异常访问专页 | ✅ | `/admin/abnormal-access` |
| V53 | 首页运营编排 | ✅ | `/admin/homepage-orchestration` |
| V54 | 反馈多轮回复表 | ✅ | `sys_feedback_reply`、详情页回复线程 |
| V55 | 反馈超时升级/改派 | ✅ | `FeedbackEscalationTask`、配置开关、列表升级标记 |

---

## C. M6 残余（可选增强）

| # | 项 | 状态 | 建议 |
|---|-----|------|------|
| C1 | 反馈超时升级/改派 | ✅ V55 | 默认关闭；见 [FEEDBACK_AUTO_DISPATCH_EVALUATION.md](./FEEDBACK_AUTO_DISPATCH_EVALUATION.md) |
| C2 | 完整规则引擎 | ❌ | 条件+动作+升级+值班表；业务量大再立项 |
| C3 | 复杂风控模型配置 | ❌ | 第二版，见 [ADMIN_V2_BACKLOG.md](./ADMIN_V2_BACKLOG.md) |

---

## D. 有意合并（非缺口）

| 项 | 现网形态 |
|----|----------|
| 举报页 | `/admin/reports` 复用 `ReviewListView` |
| 配置 API | 分类 PUT，无单一 `PUT /admin/settings` |
| 版本运行时 | `settings/client` 与 `sys_app_version` 并存；发布流走版本管理 |

---

## E. 部分能力（⚠️）

| 项 | 缺口说明 |
|----|----------|
| 审核风险等级标记 | 在风险事件侧，审核列表无独立等级 UI |
| `admin:role:view` 等 | 种子有，前端未单独用 |
| 群公告审核 | 已有专页，与朋友圈/评论共用审核组件 |

---

## F. 第二版 backlog（❌ 暂缓）

见 [ADMIN_V2_BACKLOG.md](./ADMIN_V2_BACKLOG.md)：

- 复杂风控模型配置
- 复杂审批流
- 高级 BI 看板 / 快照表 / 实时大屏
- 多租户
- 移动端管理后台
- 反馈完整规则引擎（升级/改派/通知）

---

## G. 交付物对照

| 交付物 | 状态 |
|--------|------|
| Swagger/OpenAPI | ✅ `openapi.json` |
| Postman/Apifox | ✅ 本目录 collection |
| 五角色 checklist | ✅ |
| 接口规格书 | ✅ `ADMIN_API_SPEC.md` |
| 数据库设计书 | ✅ `ADMIN_DATABASE_DESIGN.md` |
| 权限码字典 | ✅ 主文档 §25 + V48–V54 增量 |
| CI lint/format | ✅ `.github/workflows/ci.yml` admin-unit |
