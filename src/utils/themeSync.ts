export const APP_STORAGE_KEY = 'linkx-app'

export type ThemeMode = 'light' | 'dark'

export function applyDocumentTheme(theme: ThemeMode) {
  document.documentElement.setAttribute('data-theme', theme)
}

export function notifyElectronTheme(theme: ThemeMode) {
  window.electronAPI?.notifyThemeChange?.(theme)
}

/** 跨窗口主题同步（localStorage storage 事件） */
export function initCrossWindowThemeSync(onThemeChange: (theme: ThemeMode) => void) {
  window.addEventListener('storage', e => {
    if (e.key !== APP_STORAGE_KEY || !e.newValue) return
    try {
      const parsed = JSON.parse(e.newValue) as { theme?: ThemeMode }
      if (parsed.theme === 'light' || parsed.theme === 'dark') {
        onThemeChange(parsed.theme)
      }
    } catch {
      /* ignore */
    }
  })
}
