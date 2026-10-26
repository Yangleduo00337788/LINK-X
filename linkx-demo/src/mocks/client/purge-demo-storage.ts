/**
 * 作者：yangleduo
 */
/** 演示站启动时清掉旧会话缓存，避免 localStorage 里的 Alice/空官方会话盖住新 MOCK */
export function purgeDemoClientStorage() {
  if (import.meta.env.VITE_LINKX_DEMO !== '1') return
  try {
    const appKey = 'linkx-app'
    const raw = localStorage.getItem(appKey)
    if (raw) {
      const state = JSON.parse(raw) as Record<string, unknown>
      delete state.sessions
      delete state.currentSessionId
      state.isOffline = false
      localStorage.setItem(appKey, JSON.stringify(state))
    }
    sessionStorage.removeItem('linkx-app-msgs')
  } catch {
    /* ignore */
  }
}
