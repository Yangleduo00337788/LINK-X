/**
 * 作者：yangleduo
 */
/**
 * 设计 Token 的脚本侧引用。
 * 模板中 n-icon 等 props 无法直接写 CSS 变量时，通过本对象引用。
 * 圆角/颜色变更请同步修改 assets/styles.css 与 styles/ui-components.css。
 */

/** 映射 styles.css 中定义的 CSS 变量名 */
export const lxVar = {
  accent: 'var(--lx-accent)',
  accentHover: 'var(--lx-accent-hover)',
  accentDeep: 'var(--lx-accent-deep)',
  accentLight: 'var(--lx-accent-light)',
  accentSoft: 'var(--lx-accent-soft)',
  accentBgSoft: 'var(--lx-accent-bg-soft)',
  textPrimary: 'var(--lx-text-primary)',
  textMuted: 'var(--lx-text-muted)',
  textSecondary: 'var(--lx-text-secondary)',
  textBody: 'var(--lx-text-body)',
  textOnAccent: 'var(--lx-text-on-accent)',
  borderStrong: 'var(--lx-border-strong)',
  divider: 'var(--lx-divider)',
  success: 'var(--lx-success)',
  successStrong: 'var(--lx-success-strong)',
  danger: 'var(--lx-danger)',
  dangerHover: 'var(--lx-danger-hover)',
  dangerDeep: 'var(--lx-danger-deep)',
  warning: 'var(--lx-warning)',
  confAccent: 'var(--lx-conf-accent)',
  confBg: 'var(--lx-conf-bg)',
  confBgDeep: 'var(--lx-conf-bg-deep)',
  confBgVoid: 'var(--lx-conf-bg-void)',
  confSurface: 'var(--lx-conf-surface)',
  slate: 'var(--lx-slate)',
  slateMuted: 'var(--lx-slate-muted)',
  bgSoft: 'var(--lx-bg-soft)',
  bgLogo: 'var(--lx-bg-logo)',
  loginInk: 'var(--lx-login-ink)',
  loginMuted: 'var(--lx-login-muted)',
  loginIcon: 'var(--lx-login-icon)',
  loginBorder: 'var(--lx-login-border)',
  captionClose: 'var(--lx-caption-close)',
  bgWindow: 'var(--lx-bg-window)',
  bgPanel: 'var(--lx-bg-panel)',
  bgInput: 'var(--lx-bg-input)',
  bgCard: 'var(--lx-bg-card)',
  black: 'var(--lx-black)',
  radius2xs: 'var(--lx-radius-2xs)',
  radiusXs: 'var(--lx-radius-xs)',
  radiusSm: 'var(--lx-radius-sm)',
  radius: 'var(--lx-radius)',
  radiusXl: 'var(--lx-radius-xl)',
  radiusLg: 'var(--lx-radius-lg)',
  radiusCard: 'var(--lx-radius-card)',
  radius2xl: 'var(--lx-radius-2xl)',
  radius3xl: 'var(--lx-radius-3xl)',
  radius4xl: 'var(--lx-radius-4xl)',
  radiusPill: 'var(--lx-radius-pill)',
  radiusHair: 'var(--lx-radius-hair)',
  windowRadius: 'var(--lx-window-radius)',
  avatarRadius: 'var(--lx-avatar-radius)',
  font2xs: 'var(--lx-font-2xs)',
  fontXs: 'var(--lx-font-xs)',
  fontSm: 'var(--lx-font-sm)',
  fontMd: 'var(--lx-font-md)',
  font: 'var(--lx-font)',
  fontLg: 'var(--lx-font-lg)',
  fontXl: 'var(--lx-font-xl)',
  font2xl: 'var(--lx-font-2xl)',
  font3xl: 'var(--lx-font-3xl)',
  font4xl: 'var(--lx-font-4xl)',
  font5xl: 'var(--lx-font-5xl)',
  space2xs: 'var(--lx-space-2xs)',
  spaceXs: 'var(--lx-space-xs)',
  spaceSm: 'var(--lx-space-sm)',
  space: 'var(--lx-space)',
  spaceMd: 'var(--lx-space-md)',
  spaceLg: 'var(--lx-space-lg)',
  spaceXl: 'var(--lx-space-xl)',
  space2xl: 'var(--lx-space-2xl)',
  space3xl: 'var(--lx-space-3xl)',
  space4xl: 'var(--lx-space-4xl)',
  space5xl: 'var(--lx-space-5xl)',
  spaceSection: 'var(--lx-space-section)',
  space6xl: 'var(--lx-space-6xl)',
  sizeControl: 'var(--lx-size-control)',
  sizeControlLg: 'var(--lx-size-control-lg)',
  sizeWinBar: 'var(--lx-size-win-bar)',
  leadingTight: 'var(--lx-leading-tight)',
  leading: 'var(--lx-leading)',
  leadingNormal: 'var(--lx-leading-normal)',
  leadingRelaxed: 'var(--lx-leading-relaxed)',
  zDropdown: 'var(--lx-z-dropdown)',
  zModal: 'var(--lx-z-modal)',
  zDialog: 'var(--lx-z-dialog)',
  zToast: 'var(--lx-z-toast)',
  zCall: 'var(--lx-z-call)',
  duration: 'var(--lx-duration)',
  durationMd: 'var(--lx-duration-md)',
  durationSlow: 'var(--lx-duration-slow)',
  shadowSoft: 'var(--lx-shadow-soft)',
  shadowCard: 'var(--lx-shadow-card)',
  shadowDropdown: 'var(--lx-shadow-dropdown)',
  shadowModal: 'var(--lx-shadow-modal)',
  shadowFloat: 'var(--lx-shadow-float)',
  bgHover: 'var(--lx-bg-hover)',
  bgBubbleSelf: 'var(--lx-bg-bubble-self)',
  loginBgGradient: 'var(--lx-login-bg-gradient)',
  loginCardGradient: 'var(--lx-login-card-gradient)',
  loginPanelGradient: 'var(--lx-login-panel-gradient)',
  chatWallpaperDefault: 'var(--lx-chat-wallpaper-default)',
  chatWallpaperPurple: 'var(--lx-chat-wallpaper-purple)',
  chatWallpaperOrange: 'var(--lx-chat-wallpaper-orange)',
  callGradient: 'var(--lx-call-gradient)',
  videoSurfaceGradient: 'var(--lx-video-surface-gradient)',
  confPanel: 'var(--lx-conf-panel)',
  confRoomGradient: 'var(--lx-conf-room-gradient)',
  previewVoid: 'var(--lx-preview-void)',
  packetMainGradient: 'var(--lx-packet-main-gradient)',
  packetBubbleGradient: 'var(--lx-packet-bubble-gradient)',
  packetMutedGradient: 'var(--lx-packet-muted-gradient)',
  watermarkGradient: 'var(--lx-watermark-gradient)',
  watermarkGradientLight: 'var(--lx-watermark-gradient-light)',
  gradientRainbow: 'var(--lx-gradient-rainbow)',
  fileApkGradient: 'var(--lx-file-apk-gradient)',
  eventBlue: 'var(--lx-event-blue)',
  eventRed: 'var(--lx-event-red)',
  eventPurple: 'var(--lx-event-purple)',
  eventGreen: 'var(--lx-event-green)',
  captionCloseHover: 'var(--lx-caption-close-hover)',
  officialBorder: 'var(--lx-official-border)',
  officialBorderSoft: 'var(--lx-official-border-soft)'
} as const

/** 需要传给 Naive / 图表等「只能吃纯色」API 时的静态色（与 CSS Token 同源） */
export const lxColorHex = {
  accent: '#12b7f5',
  accentHover: '#39c2f6',
  success: '#52c41a',
  successStrong: '#07c160',
  danger: '#fa5151',
  dangerHover: '#ff4d4f',
  warning: '#faad14',
  confAccent: '#006eff',
  confBg: '#1f1f1f',
  confSurface: '#1f2329',
  iconImage: '#ff8800',
  slate: '#64748b',
  slateMuted: '#94a3b8',
  slateSoft: '#f1f5f9',
  bgLogo: '#f0f4f8',
  brandPurple: '#a855f7',
  captionClose: '#e81123',
  loginIcon: '#9aa3b2',
  loginBorder: '#c5d0dc',
  textPrimary: '#1a1a1a',
  textBody: '#333333',
  textPrimaryDark: '#e5e5e5',
  textSecondaryDark: '#a3a3a3',
  textSecondaryLight: '#8f959e',
  bgCard: '#ffffff',
  bgCardDark: '#262626',
  bgWindow: '#f5f5f5',
  bgWindowDark: '#1a1a1a',
  white: '#ffffff',
  packetGold: '#f5b041',
  packetGoldDeep: '#f39c12',
  packetTextCream: '#fff8e7',
  packetTextParchment: '#fff8dc',
  confVoice: '#12b76a',
  confVoiceText: '#0f8a52',
  confVideo: '#1a6bff',
  confVideoText: '#1a56db',
  confMeeting: '#7c3aed',
  confMeetingText: '#6d28d9',
  confGold: '#ffb454',
  confGoldLight: '#ffcf87',
  confLinkBlue: '#5b8def',
  fileFolder: '#f5a623',
  fileFolderBg: '#fff4d6',
  filePdfBg: '#fdecea',
  fileDoc: '#2b579a',
  fileDocBg: '#e8f0fe',
  fileXls: '#1d6f42',
  fileXlsBg: '#e8f5ee',
  filePpt: '#c43e1c',
  filePptBg: '#fde8e1',
  fileZip: '#8e44ad',
  fileZipBg: '#f3e8fa',
  fileMedia: '#722ed1',
  fileMediaBg: '#f3e8ff',
  eventBlue: '#3370ff',
  eventRed: '#f54a45',
  eventPurple: '#7b61ff',
  eventGreen: '#00b578',
  captionCloseHover: '#f1707a',
  noteBlue: '#2563eb',
  noteOrange: '#f97316',
  noteAmber: '#f59e0b'
} as const

/** 聊天壁纸 inline style（与 --lx-chat-wallpaper-* 对应） */
export const lxChatWallpaperBg = {
  default: 'var(--lx-chat-wallpaper-default)',
  purple: 'var(--lx-chat-wallpaper-purple)',
  orange: 'var(--lx-chat-wallpaper-orange)'
} as const

/** 日历事件色板（JS 内联样式用） */
export const lxEventColors = [
  lxColorHex.eventBlue,
  lxColorHex.eventRed,
  lxColorHex.iconImage,
  lxColorHex.eventPurple,
  lxColorHex.eventGreen,
  lxColorHex.accent
] as const

/** 文件类型色标（JS 内联样式用） */
export const lxFileTypeHex = {
  folder: { color: lxColorHex.fileFolder, bg: lxColorHex.fileFolderBg },
  pdf: { color: lxColorHex.danger, bg: lxColorHex.filePdfBg },
  doc: { color: lxColorHex.fileDoc, bg: lxColorHex.fileDocBg },
  xls: { color: lxColorHex.fileXls, bg: lxColorHex.fileXlsBg },
  ppt: { color: lxColorHex.filePpt, bg: lxColorHex.filePptBg },
  zip: { color: lxColorHex.fileZip, bg: lxColorHex.fileZipBg },
  media: { color: lxColorHex.fileMedia, bg: lxColorHex.fileMediaBg }
} as const

/**
 * Naive UI ConfigProvider themeOverrides 使用的静态色值。
 * 所有颜色须与 :root CSS 变量保持一致。
 */
export const naiveThemeColors = {
  primaryColor: '#12b7f5',
  primaryColorHover: '#39c2f6',
  primaryColorPressed: '#12b7f5',
  errorColor: '#fa5151',
  errorColorHover: '#ff4d4f',
  errorColorPressed: '#fa5151',
  borderRadius: '9px'
} as const

/** 圆角分级（与 styles.css 一致，供 JS 侧引用） */
export const lxRadius = {
  xxs: '4px',
  xs: '6px',
  sm: '8px',
  md: '9px',
  lg: '12px',
  xl: '10px',
  xxl: '16px',
  avatar: '12px',
  window: '20px'
} as const

/** 字号分级 */
export const lxFont = {
  '2xs': '10px',
  xs: '11px',
  sm: '12px',
  md: '13px',
  base: '14px',
  lg: '15px',
  xl: '16px',
  '2xl': '17px',
  '3xl': '18px',
  '4xl': '20px',
  '5xl': '22px',
  '6xl': '28px',
  '7xl': '32px',
  '8xl': '34px',
  display: '24px'
} as const

/** 间距分级 */
export const lxSpace = {
  hair: '1px',
  '2xs': '2px',
  xs: '4px',
  sm: '6px',
  base: '8px',
  md: '10px',
  lg: '12px',
  xl: '14px',
  '2xl': '16px',
  '3xl': '20px',
  '4xl': '24px',
  '5xl': '32px',
  '6xl-minus': '36px',
  section: '40px',
  '6xl': '48px',
  block: '54px',
  'block-lg': '60px',
  'block-xl': '64px'
} as const

/** 行高 */
export const lxLeading = {
  none: '1',
  tight: '1.2',
  snug: '1.3',
  base: '1.4',
  normal: '1.5',
  relaxed: '1.6',
  loose: '1.7'
} as const

/** 层级（与 styles.css 数值一致，避免叠层回归） */
export const lxZ = {
  base: 0,
  raised: 1,
  dropdown: 10,
  sticky: 20,
  dock: 30,
  fab: 50,
  header: 100,
  overlay: 400,
  modal: 500,
  popover: 999,
  toast: 1000,
  dialog: 2200,
  critical: 10050,
  call: 12000,
  lock: 30000
} as const

/** 动效时长 */
export const lxDuration = {
  instant: '0.05s',
  caption: '83ms',
  faster: '0.1s',
  fast: '0.12s',
  base: '0.15s',
  md: '0.2s',
  slow: '0.3s',
  slower: '0.4s',
  slowest: '0.55s',
  emphasis: '0.7s'
} as const

/** 控件尺寸 */
export const lxSize = {
  control: '38px',
  controlLg: '40px',
  winBar: '40px'
} as const
