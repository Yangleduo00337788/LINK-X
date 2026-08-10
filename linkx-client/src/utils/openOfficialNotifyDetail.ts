/**
 * 作者：yangleduo
 */
/**
 * 打开 LinkX 官方通知详情页。
 * - Electron：独立子窗口（需 preload 已注册 IPC）
 * - 其它环境：路由跳转；浏览器可另开标签，失败则同页打开
 */
import router from '../router'

export function openOfficialNotifyDetail(notifId: string) {
  const id = String(notifId || '').trim()
  if (!id) return

  const path = `/official-notify/${encodeURIComponent(id)}`

  if (typeof window.electronAPI?.openOfficialNotifyDetail === 'function') {
    window.electronAPI.openOfficialNotifyDetail(id)
    return
  }

  if (window.electronAPI?.isElectron) {
    sessionStorage.setItem('official-detail-in-main', '1')
    void router.push(path)
    return
  }

  const base = window.location.href.split('#')[0]
  const url = `${base}#${path}`
  const opened = window.open(url, '_blank', 'noopener,noreferrer')
  if (!opened) {
    sessionStorage.setItem('official-detail-in-main', '1')
    void router.push(path)
  }
}
