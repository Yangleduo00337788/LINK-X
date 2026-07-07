export {}

declare global {
  interface Window {
    electronAPI?: {
      minimize: () => Promise<void>
      maximize: () => Promise<void>
      close: () => Promise<void>
      openMoments: () => void
      isMaximized: () => Promise<boolean>
      onMaximizedChange: (callback: (maximized: boolean) => void) => () => void
      isElectron?: boolean
    }
  }
}