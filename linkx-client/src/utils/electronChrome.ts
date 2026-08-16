/** Win32 主界面是否使用系统原生边框（登录窗为自绘顶栏） */
export function useNativeWindowFrame(): boolean {
  return document.documentElement.classList.contains('lx-native-frame')
}

export function syncDesktopChromeMode(loggedIn: boolean) {
  if (!window.electronAPI?.isElectron) return
  const isWin32 = window.electronAPI.getPlatform?.() === 'windows'
  document.documentElement.classList.toggle('lx-native-frame', isWin32 && loggedIn)
}
