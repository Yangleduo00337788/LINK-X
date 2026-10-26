/**
 * 作者：yangleduo
 */
export type AppearancePreset = 'mixed' | 'light' | 'dark'
export type LayoutMode = 'side' | 'top' | 'mix'
export type FormContainerStyle = 'drawer' | 'modal'

export interface ThemeColorPreset {
  key: string
  color: string
}

export const THEME_COLOR_PRESETS: ThemeColorPreset[] = [
  { key: 'wechat', color: '#07C160' },
  { key: 'dust', color: '#f5222d' },
  { key: 'volcano', color: '#fa541c' },
  { key: 'sunset', color: '#fa8c16' },
  { key: 'gold', color: '#faad14' },
  { key: 'cyan', color: '#13c2c2' },
  { key: 'green', color: '#52c41a' },
  { key: 'geekblue', color: '#2f54eb' },
  { key: 'blue', color: '#1890ff' },
  { key: 'purple', color: '#722ed1' },
]

/** WeUI / 微信公众平台后台品牌绿 */
export const DEFAULT_PRIMARY_COLOR = '#07C160'
/** 深色侧栏（公众平台经典炭灰，区别于 Ant Design Pro 的 #001529） */
export const DARK_SIDER_COLOR = '#2e2e2e'
