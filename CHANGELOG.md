# 更新日志

本文件记录 LinkX 各版本的显著变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。

---

## [Unreleased]

### 新增

- README 按腾讯开源文档规范重构，补充贡献指南与更新日志
- 仓库根目录 `assets/logo.png` 用于文档展示

### 变更

- 精简仓库结构：移除 CI 流水线、测试套件与独立文档目录（不影响客户端 / 管理端 / 服务端运行）

---

## [1.0.0] - 2026-08-09

首个可本地完整联调的稳定基线，涵盖 IM 核心链路与管理运营能力。

### 新增 — 客户端（linkx-client）

- Electron 跨平台桌面客户端，支持 Web / 桌面双模式开发
- 单聊 / 群聊、多媒体消息、引用 / 编辑 / 撤回 / 转发
- 消息状态机、已读回执（可按隐私设置关闭）、在线状态与正在输入
- 朋友圈、日历、笔记编辑器（Markdown 块编辑）
- QQ 风格转发、微信风格引用回复
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
| 客户端 | Vue 3.5、Electron 33、Vite 5.4、Pinia 2.3 |
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
