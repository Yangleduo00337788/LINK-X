/**
 * 设计 Token 的脚本侧引用。
 * 模板中 n-icon 等 props 无法直接写 CSS 变量时，通过本对象引用。
 */

/** 映射 styles.css 中定义的 CSS 变量名 */
export const lxVar = {
  accent: 'var(--lx-accent)',                 // 品牌主色
  accentHover: 'var(--lx-accent-hover)',     // 主色悬停态
  textMuted: 'var(--lx-text-muted)',         // 次要文字色
  textSecondary: 'var(--lx-text-secondary)', // 次级文字色
  textBody: 'var(--lx-text-body)',           // 正文文字色
  borderStrong: 'var(--lx-border-strong)',   // 强调边框色
  divider: 'var(--lx-divider)',              // 分割线色
  success: 'var(--lx-success)',              // 成功态色
  danger: 'var(--lx-danger)',                // 危险/错误态色
  dangerHover: 'var(--lx-danger-hover)',     // 危险色悬停态
  bgInput: 'var(--lx-bg-input)',             // 输入框背景
  bgCard: 'var(--lx-bg-card)'                // 卡片背景
} as const // 只读常量，防止运行时被修改

/**
 * Naive UI ConfigProvider themeOverrides 使用的静态色值。
 * 所有颜色须与 :root CSS 变量保持一致。
 */
export const naiveThemeColors = {
  primaryColor: '#12b7f5',        // 主色（与 --lx-accent 一致）
  primaryColorHover: '#39c2f6',   // 悬停主色
  primaryColorPressed: '#12b7f5', // 按下主色
  errorColor: '#fa5151',          // 错误色（与 --lx-danger 一致）
  errorColorHover: '#ff4d4f',     // 错误色悬停
  errorColorPressed: '#fa5151',   // 错误色按下
  borderRadius: '9px'             // 全局圆角，与 --lx-radius 一致
} as const
