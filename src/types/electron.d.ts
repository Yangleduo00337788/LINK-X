export {}

declare global {
  interface Window {
    electronAPI?: {
      minimize: () => Promise<void>
      maximize: () => Promise<void>
      close: () => Promise<void>
      openMoments: () => void
      openNoteEditor: () => void
      isMaximized: () => Promise<boolean>
      isPinned: () => Promise<boolean>
      togglePin: () => Promise<boolean>
      onMaximizedChange: (callback: (maximized: boolean) => void) => () => void
      setAutoStart?: (enabled: boolean) => Promise<boolean>
      getAutoStart?: () => Promise<boolean>
      notifyThemeChange?: (theme: 'light' | 'dark') => void
      setWindowMode?: (mode: 'login' | 'main') => Promise<void>
      isElectron?: boolean
    }
  }
}