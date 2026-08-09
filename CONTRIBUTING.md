# 贡献指南

感谢你对 LinkX 的关注。本文说明如何参与本仓库的开发协作。

> 本仓库为私有项目。外部协作者须先获得仓库访问权限，方可提交代码。

---

## 一、开发流程

```text
Fork / 拉分支 → 本地开发 → 自测通过 → 提交 PR → Code Review → 合并
```

| 步骤 | 说明 |
|------|------|
| 1. 同步主分支 | `git pull origin master` |
| 2. 创建功能分支 | 从 `master` 切出，命名见下文 |
| 3. 开发与自测 | 确保相关子工程可正常启动 |
| 4. 提交代码 | 遵循提交信息规范 |
| 5. 发起 Pull Request | 目标分支为 `master`，填写变更说明 |

---

## 二、分支命名

| 类型 | 格式 | 示例 |
|------|------|------|
| 新功能 | `feat/<简述>` | `feat/group-announcement` |
| 缺陷修复 | `fix/<简述>` | `fix/ws-reconnect` |
| 重构 | `refactor/<简述>` | `refactor/chat-store` |
| 文档 | `docs/<简述>` | `docs/readme-update` |
|  chores | `chore/<简述>` | `chore/deps-bump` |

---

## 三、提交信息规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)，**描述使用中文**。

```text
<type>(<scope>): <subject>

[optional body]
```

| type | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `docs` | 文档变更 |
| `style` | 格式调整（不影响逻辑） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `chore` | 构建 / 工具 / 依赖 |

**scope 示例：** `client`、`admin`、`server`

**示例：**

```text
feat(client): 支持群公告置顶展示
fix(server): 修复 WebSocket 断线重连后消息乱序
docs: 更新 README 快速上手章节
```

---

## 四、代码规范

### 4.1 通用原则

- 变更范围最小化，不做无关重构
- 禁止提交密钥、`.env.local`、`.env.prod` 等敏感文件
- 禁止将 `node_modules/`、`target/`、`dist/` 等构建产物入库

### 4.2 linkx-server（Java）

| 项 | 规范 |
|----|------|
| JDK | 21 |
| 包结构 | `controller` → `service` → `mapper`，管理端接口放 `controller/admin` |
| 数据库变更 | 仅通过 Flyway：`db/migration/V{n}__描述.sql` |
| 配置 | 业务值写入 `.env.*`，不写死 `application.yml` |
| 注释 | 复杂业务逻辑补充说明，避免无意义注释 |

### 4.3 linkx-client / linkx-admin（前端）

| 项 | 规范 |
|----|------|
| 状态管理 | 全局状态使用 Pinia，放 `src/stores/` |
| 类型 | 业务类型集中在 `types/`，开启 TypeScript 严格模式 |
| 样式 | 优先 UnoCSS / Design Token；组件 scoped 样式保持 BEM 习惯 |
| 国际化 | 用户可见文案走 i18n，避免硬编码中文（管理端已接入 vue-i18n） |
| Electron | 禁止开启 `nodeIntegration`，仅通过 Preload 暴露 API |

### 4.4 格式化

```bash
# 客户端
cd linkx-client && npm run format:check

# 管理端
cd linkx-admin && npm run lint && npm run format:check
```

---

## 五、自测要求

提交 PR 前，请至少完成与变更范围对应的验证：

| 变更范围 | 最低验证 |
|----------|----------|
| `linkx-server` | `mvn -DskipTests compile` 通过；相关接口手动或 Swagger 验证 |
| `linkx-client` | `npm run dev` 或 `npm run electron:dev` 可启动；涉及页面手动走通 |
| `linkx-admin` | `npm run dev` 可启动；涉及页面与权限手动走通 |
| 数据库迁移 | 本地空库 / 存量库各验证一次 Flyway 迁移 |

---

## 六、Pull Request 说明模板

```markdown
## 变更类型
- [ ] 新功能
- [ ] 缺陷修复
- [ ] 重构
- [ ] 文档

## 变更说明
（简要描述做了什么、为什么做）

## 影响范围
- [ ] linkx-server
- [ ] linkx-client
- [ ] linkx-admin

## 自测情况
（列出已验证的场景）

## 关联 Issue
（如有，填写 Gitee Issue 编号）
```

---

## 七、问题反馈

- **缺陷 / 需求**：在 Gitee 仓库提交 [Issue](https://gitee.com/yangleduo7788/link-x/issues)
- **安全问题**：请勿公开 Issue，通过仓库维护者私下联系

---

再次感谢你的贡献。
