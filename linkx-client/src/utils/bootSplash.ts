/**
 * 作者：yangleduo
 */
let windowReadyNotified = false

/** 通知 Electron 主进程：渲染层首帧后可展示窗口 */
export function notifyElectronWindowReady() {
  if (windowReadyNotified || typeof window === 'undefined') return
  windowReadyNotified = true
  window.electronAPI?.notifyWindowReady?.()
}

/** 双 RAF 后通知，尽量等首屏绘制完成 */
export function scheduleNotifyElectronWindowReady() {
  if (typeof window === 'undefined' || windowReadyNotified) return
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      notifyElectronWindowReady()
    })
  })
}

export function reportBootError(message: string) {
  console.error('[boot]', message)
}
