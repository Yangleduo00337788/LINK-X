<!-- 作者：yangleduo -->
# 更新日志

本文件记录 LinkX 各版本的显著变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。

---

## [Unreleased]

---

## [1.0.1] - 2026-08-29

### 新增

- 灵伴 Agent 代操模式：支持 LLM 函数调用在客户端执行导航、发消息等操作
- 灵伴 Agent 全局开关与群 AI 默认策略（管理端可配置）
- 客户端 Design Token 体系（`--lx-*`）与 `ui-components.css` 公共样式
- 公共 UI 组件：`LxButton`、`LxIconButton`、`LxGroupCard`
- 客户端全站迁移至统一 Design Token（`migrate-*.mjs` 一次性脚本已移除）
- 服务端消息落库加密（AES-256-GCM，可选开启，见 README 8.4）

### 变更

- 客户端 Electron 升级至 **43**（`electron-builder` 26、`vite-plugin-electron` 1.x），开发态增加国内镜像与 `electron:install` 脚本
- 桌面端体积与性能优化：裁剪 Chromium 语言包、剔除 sourcemap、Markdown 高亮懒加载、托盘隐藏时降低渲染占用
- `linkx-client` 全站迁移至统一按钮、间距、圆角与场景色 Token
- 窗控与状态栏置顶统一 `.lx-win-caption-btn` 圆角块悬停交互
- `ui-components.css` 改由 `main.ts` 显式引入（修复 `@import` 顺序导致的样式未加载）
- README 与目录结构、客户端 UI 开发规范对齐
- 文档与 MIT 许可证说明对齐（README / CONTRIBUTING）
- 官网 `docs.html` 补充消息落库加密说明，与 README 8.4 对齐

---

## [1.0.0] - 2026-08-12

首个可本地完整联调的稳定基线，涵盖 IM 核心链路与管理运营能力。

### 新增 — 客户端（linkx-client）

- Electron 跨平台桌面客户端，支持 Web / 桌面双模式开发
- 单聊 / 群聊、多媒体消息、引用 / 编辑 / 撤回 / 转发
- 消息状态机、已读回执（可按隐私设置关闭）、在线状态与正在输入
- 朋友圈、日历、笔记编辑器（Markdown 块编辑）
- 会话内转发、引用回复样式
- 个人网盘、收藏、锁屏、系统浏览器打开外链
- 通知卡片流、日程提醒与官方通知详情
- WebRTC 单聊音视频与多人 Mesh 会议

### 新增 — 管理端（linkx-admin）

- RBAC 权限体系：用户 / 角色 / 部门 / 菜单
- 风控规则、风险事件、内容审核、反馈工单
- 统计大屏、BI 分析、系统监控
- 登录页视觉重构，品牌 Logo 统一

### 新增 — 服务端（linkx-server）

- Spring Boot 3.5 单体架构，REST + Netty WebSocket 双通道
- 双 Token 鉴权（Access + Refresh）、登录风控与图形验证码
- 好友 / 群聊 / 红包 / 通话 / 会议完整业务 API
- MinIO 对象存储、Flyway 数据库版本管理
- 敏感词 DFA 过滤、操作审计、用户数据导出与账号注销
- Snail Job 定时任务集成

### 技术栈

| 模块 | 核心版本 |
|------|----------|
| 后端 | JDK 21、Spring Boot 3.5.0、MyBatis-Flex 1.9.3、Netty 4.1.115 |
| 客户端 | Vue 3.5、Electron 43、Vite 5.4、Pinia 2.3 |
| 管理端 | Vue 3.5、Vite 8.1、ECharts 6.1、vue-i18n 9.14 |
| 中间件 | MySQL 8.4、Redis 7.2、MinIO 2024-05 |

---

## 版本说明

| 标记 | 含义 |
|------|------|
| `新增` | 新功能 |
| `变更` | 既有功能的变更 |
| `修复` | 缺陷修复 |
| `移除` | 已删除的功能或文件 |
| `安全` | 安全相关修复 |

[Unreleased]: https://gitee.com/yangleduo7788/link-x/compare/v1.0.0...master
[1.0.0]: https://gitee.com/yangleduo7788/link-x/releases/tag/v1.0.0
