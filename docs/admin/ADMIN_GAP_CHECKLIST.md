# LinkX 管理端缺口清单（M6 残余 + 第二版）

> 对照主文档 `管理端开发文档.md` · 更新：2026-08-02

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
| A1 | 五角色生产抽检 | 🔄 | 自动化已齐，见 [ADMIN_FIVE_ROLE_CHECKLIST.md](../testing/ADMIN_FIVE_ROLE_CHECKLIST.md) |
| A2 | M5 操作留痕/联调回归 | 🔄 | IT + 冒烟矩阵 |
| A3 | ESLint + Prettier | ✅ | `linkx-admin` `npm run lint` / `format:check` |
| A4 | API 集合 | ✅ | `linkx-admin.postman_collection.json` |
| A5 | 文档拆分 | ✅ | 本目录三份规格书 |

---

## B. M6 残余（可选增强）

| # | 项 | 状态 | 建议 |
|---|-----|------|------|
| B1 | 反馈自动分流 | ❌ | **暂不实施**，见 [FEEDBACK_AUTO_DISPATCH_EVALUATION.md](./FEEDBACK_AUTO_DISPATCH_EVALUATION.md) |
| B2 | 反馈最小指派（方案 B） | ❌ | 业务量大时再立项 |
| B3 | 风险事件归属地 | ✅ | ip2region 读时解析 |
| B4 | 举报独立入口 | ✅ | V45 |
| B5 | 反馈 SLA | ✅ | V46 |

---

## C. 有意合并（非缺口）

| 项 | 现网形态 |
|----|----------|
| 版本管理 | `settings/client`，无 `/admin/versions` |
| 举报页 | `/admin/reports` 复用 `ReviewListView` |
| 反馈详情 | 列表内抽屉，无独立路由 |
| 配置 API | 分类 PUT，无单一 `PUT /admin/settings` |
| 反馈回复表 | `sys_feedback.reply` 字段 |

---

## D. 部分能力（⚠️）

| 项 | 缺口说明 |
|----|----------|
| 群公告审核 | 模型支持 `announcement`，无独立页 |
| 删除内容 | 驳回处置为主 |
| 审核风险等级标记 | 在风险事件侧 |
| 异常访问记录 | 登录失败 + 风险事件覆盖部分 |
| 首页运营编排 | Banner/公告部分覆盖 |
| `admin:role:view` 等 | 种子有，前端未单独用 |

---

## E. 第二版 backlog（❌ 暂缓）

见 [ADMIN_V2_BACKLOG.md](./ADMIN_V2_BACKLOG.md)：

- 复杂风控模型配置
- 复杂审批流
- 高级 BI 看板
- 多租户
- 移动端管理后台
- 统计/仪表盘快照表
- 独立版本发布流水线

---

## F. 交付物对照

| 交付物 | 状态 |
|--------|------|
| Swagger/OpenAPI | ✅ `openapi.json` |
| Postman/Apifox | ✅ 本目录 collection |
| 五角色 checklist | ✅ |
| 接口规格书 | ✅ `ADMIN_API_SPEC.md` |
| 数据库设计书 | ✅ `ADMIN_DATABASE_DESIGN.md` |
| 权限码字典 | ✅ 主文档 §25 |
