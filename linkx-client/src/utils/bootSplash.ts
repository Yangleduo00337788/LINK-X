/**
 * 作者：yangleduo
 */
let dismissed = false

/** Vue 首屏（登录页）绘制完成后移除静态启动占位，并通知 Electron 可展示窗口 */
export function dismissBootSplash() {
  if (dismissed || typeof document === 'undefined') return
  dismissed = true
  document.documentElement.classList.add('app-ready')
  window.setTimeout(() => {
    document.getElementById('boot-splash')?.remove()
  }, 200)
  window.electronAPI?.notifyWindowReady?.()
}

export function scheduleDismissBootSplash() {
  if (typeof window === 'undefined' || dismissed) return
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      dismissBootSplash()
    })
  })
}

export function showBootSplashError(message: string) {
  if (typeof document === 'undefined') return
  const splash = document.getElementById('boot-splash')
  if (!splash) return
  splash.innerHTML = `<div class="boot-error">${message}</div>`
}
