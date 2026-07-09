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
  bgInput: 'var(--lx-bg-input)',             // 输入框背景
  bgCard: 'var(--lx-bg-card)'                // 卡片背景
} as const // 只读常量，防止运行时被修改

/**
 * Naive UI ConfigProvider themeOverrides 使用的静态色值。
 * primaryColor 须与 :root --lx-accent 保持一致。
 */
export const naiveThemeColors = {
  primaryColor: '#12b7f5',        // 主色
  primaryColorHover: '#39c2f6',   // 悬停主色
  primaryColorPressed: '#12b7f5', // 按下主色
  borderRadius: '9px'             // 全局圆角，与 --lx-radius 一致
} as const
