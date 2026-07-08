/** 供模板 / 脚本中 n-icon 等 props 使用的 CSS 变量引用 */
export const lxVar = {
  accent: 'var(--lx-accent)',
  accentHover: 'var(--lx-accent-hover)',
  textMuted: 'var(--lx-text-muted)',
  textSecondary: 'var(--lx-text-secondary)',
  textBody: 'var(--lx-text-body)',
  borderStrong: 'var(--lx-border-strong)',
  divider: 'var(--lx-divider)',
  success: 'var(--lx-success)',
  bgInput: 'var(--lx-bg-input)',
  bgCard: 'var(--lx-bg-card)'
} as const

/** Naive UI themeOverrides 使用的静态色值（须与 :root --lx-accent 保持一致） */
export const naiveThemeColors = {
  primaryColor: '#12b7f5',
  primaryColorHover: '#39c2f6',
  primaryColorPressed: '#12b7f5',
  borderRadius: '9px'
} as const
