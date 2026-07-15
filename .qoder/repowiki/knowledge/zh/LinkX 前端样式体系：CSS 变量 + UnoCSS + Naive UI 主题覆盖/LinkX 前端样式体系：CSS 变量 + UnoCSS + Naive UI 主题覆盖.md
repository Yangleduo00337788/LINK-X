---
kind: frontend_style
name: LinkX 前端样式体系：CSS 变量 + UnoCSS + Naive UI 主题覆盖
category: frontend_style
scope:
    - '**'
source_files:
    - linkx-client/src/assets/styles.css
    - linkx-client/src/theme/vars.ts
    - linkx-client/src/utils/themeSync.ts
    - linkx-client/src/AppRoot.vue
    - linkx-client/src/components/settings/settings-common.css
    - linkx-client/src/components/overlay/overlay-common.css
    - linkx-client/uno.config.ts
    - linkx-client/package.json
---

## 1. 系统概览
LinkX 桌面客户端采用「CSS 自定义属性设计令牌 + UnoCSS 原子类 + Naive UI 组件库」三层组合的样式方案。全局设计令牌通过 `:root` 与 `[data-theme='dark']` 双主题 CSS 变量集中管理，业务组件以 Vue SFC `<style>` 为主、辅以少量共享 CSS 文件；构建期由 UnoCSS 提供原子化工具类，运行时通过 Naive UI ConfigProvider 注入品牌色与圆角等主题覆盖。

## 2. 关键文件与包
- 设计令牌入口：`linkx-client/src/assets/styles.css`（定义所有 `--lx-*` 变量及暗色覆盖）
- 脚本侧 Token 引用：`linkx-client/src/theme/vars.ts`（导出 `lxVar` 常量与 `naiveThemeColors`）
- 主题同步工具：`linkx-client/src/utils/themeSync.ts`（写入 `data-theme`、跨窗口同步、通知 Electron 主进程）
- 应用根节点主题挂载点：`linkx-client/src/AppRoot.vue`（`n-config-provider` + `themeOverrides` + `applyDocumentTheme`）
- 共享样式片段：`src/components/settings/settings-common.css`、`src/components/overlay/overlay-common.css`
- UnoCSS 配置：`linkx-client/uno.config.ts`（仅启用 `presetUno()`）
- 依赖声明：`package.json` 中 `naive-ui`、`unocss`、`@vicons/ionicons5` 等

## 3. 架构与约定
### 3.1 设计令牌分层
- **CSS 层**：`styles.css` 按「背景层级 / 品牌色 / 文字 / 边框分割线 / 功能色 / 圆角阴影 / 渐变」分组声明 `--lx-*` 变量，并通过 `[data-theme='dark']` 覆盖实现暗色模式。
- **脚本层**：`theme/vars.ts` 将常用 CSS 变量名映射为只读常量 `lxVar`，供模板中无法直接写 CSS 变量的场景（如 `n-icon` props）使用；同时导出 `naiveThemeColors` 静态色值，保证 Naive UI 的 `primaryColor` 与 `--lx-accent` 一致。
- **运行时层**：`AppRoot.vue` 根据 Pinia store 中的 `theme` 计算 `naiveTheme = darkTheme | null`，并传入 `n-config-provider` 的 `themeOverrides`，从而让 Naive UI 组件跟随全局主题。

### 3.2 主题切换机制
- 通过 `utils/themeSync.ts` 的 `applyDocumentTheme(theme)` 在 `document.documentElement` 上设置 `data-theme='light'|'dark'`，驱动 CSS 变量切换。
- 同一份 `localStorage` 键 `linkx-app` 被多窗口共享，`initCrossWindowThemeSync` 监听 `storage` 事件实现跨窗口主题同步。
- 通过 `notifyElectronTheme` 经 IPC 通知 Electron 主进程，使原生标题栏/材质与渲染进程主题保持一致。

### 3.3 组件库与原子类协作策略
- **Naive UI**：作为主要 UI 骨架，通过 `ConfigProvider.themeOverrides` 统一注入品牌主色、圆角等；对下拉菜单、Popover、Modal 等浮层组件在全局 `styles.css` 中以 `!important` 覆盖默认样式，确保视觉一致性。
- **UnoCSS**：项目仅启用 `presetUno()`，未做额外扩展；实际业务中几乎不使用其原子类，而是走传统 CSS 变量 + 组件级 `<style>` 的路径，UnoCSS 在此更多是“已集成但未深度使用”的状态。
- **共享 CSS 片段**：`settings-common.css`、`overlay-common.css` 提取跨页面复用的布局与卡片样式，避免在每个 SFC 中重复书写。

### 3.4 命名与组织约定
- 所有设计令牌以 `--lx-` 前缀命名，语义化分组清晰。
- 组件内样式优先使用 scoped `<style>`，仅在多处复用且无作用域需求时抽取到 `components/<area>/xxx-common.css`。
- 需要穿透 Naive UI 内部 DOM 时使用 `:deep()` 选择器，配合 `!important` 强制覆盖。
- 颜色相关逻辑一律从 `theme/vars.ts` 或 CSS 变量读取，禁止在组件中硬编码色值。

## 4. 开发者应遵循的规则
1. **新增/修改颜色**：只在 `src/assets/styles.css` 的 `:root` 或 `[data-theme='dark']` 块中调整 `--lx-*` 变量，不要直接在组件里写十六进制色值。
2. **脚本侧引用颜色**：如需在 JS/TS 中拿到当前主题色，使用 `theme/vars.ts` 导出的 `lxVar` 或 `naiveThemeColors`，保持与 CSS 变量一致。
3. **覆盖 Naive UI 样式**：优先通过 `ConfigProvider.themeOverrides` 注入；若需覆盖浮层/内部 DOM，在 `styles.css` 中使用 `:deep()` + `!important`，并尽量限定在特定 class 下以避免全局污染。
4. **主题切换**：通过 Pinia store 的 `theme` 字段驱动，不要在组件内直接操作 `documentElement`；新增跨窗口同步需求时复用 `themeSync.ts` 的 `initCrossWindowThemeSync`。
5. **UnoCSS 使用**：当前项目未启用自定义 preset，不建议引入大量原子类；如需新增预设，请在 `uno.config.ts` 中集中配置而非散落在各组件中。
6. **图标与字体**：图标统一来自 `@vicons/ionicons5`，字体栈已在 `styles.css` 中固定为 PingFang SC / Microsoft YaHei 系列，新增字体请在此处维护。