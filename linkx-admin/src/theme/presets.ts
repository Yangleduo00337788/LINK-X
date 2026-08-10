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

export const DEFAULT_PRIMARY_COLOR = '#1890ff'
