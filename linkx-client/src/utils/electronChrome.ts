/** Win32 是否使用系统原生窗口边框（非无边框圆角） */
export function useNativeWindowFrame(): boolean {
  return !!window.electronAPI?.useNativeWindowFrame
}
