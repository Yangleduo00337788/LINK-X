/**
 * 作者：yangleduo
 */
/** Win11 / Electron titleBarOverlay 同款 Segoe Fluent Icons 字模 */
export const WIN_CAPTION_GLYPH = {
  minimize: '\uE921',
  maximize: '\uE922',
  restore: '\uE923',
  close: '\uE8BB',
  pin: '\uE718',
  pinned: '\uE840'
} as const

export function isWindowsElectron(): boolean {
  return window.electronAPI?.getPlatform?.() === 'windows'
}
