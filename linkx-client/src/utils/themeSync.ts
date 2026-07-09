// Pinia app store 持久化使用的 localStorage key，与 stores/app.ts persist.key 一致
export const APP_STORAGE_KEY = 'linkx-app'

// 主题模式字面量类型
export type ThemeMode = 'light' | 'dark'

/**
 * 将主题写入 document.documentElement 的 data-theme 属性。
 * styles.css 通过 [data-theme="dark"] 选择器切换暗色变量。
 */
export function applyDocumentTheme(theme: ThemeMode) {
  document.documentElement.setAttribute('data-theme', theme)
}

/**
 * 通知 Electron 主进程当前主题（用于原生标题栏/材质适配）。
 * Web 环境下 electronAPI 不存在，可选链安全跳过。
 */
export function notifyElectronTheme(theme: ThemeMode) {
  window.electronAPI?.notifyThemeChange?.(theme)
}

/**
 * 跨窗口主题同步：监听 storage 事件。
 * 当其他窗口修改 linkx-app 持久化数据时，本窗口跟随更新主题。
 *
 * @param onThemeChange 解析到新主题时的回调
 */
export function initCrossWindowThemeSync(onThemeChange: (theme: ThemeMode) => void) {
  window.addEventListener('storage', e => {
    // 仅处理 linkx-app 键的变化，且新值非空
    if (e.key !== APP_STORAGE_KEY || !e.newValue) return
    try {
      // 解析 Pinia 持久化的 JSON 结构
      const parsed = JSON.parse(e.newValue) as { theme?: ThemeMode }
      // 校验 theme 字段合法后再回调
      if (parsed.theme === 'light' || parsed.theme === 'dark') {
        onThemeChange(parsed.theme)
      }
    } catch {
      /* 解析失败忽略，避免脏数据导致崩溃 */
    }
  })
}
