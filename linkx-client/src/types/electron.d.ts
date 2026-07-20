// 空 export 使本文件成为模块，避免全局污染
export {}

// 扩展全局 Window 接口，声明 Electron preload 注入的 API
declare global {
  interface Window {
    // preload 通过 contextBridge 暴露的对象，Web 环境下为 undefined
    electronAPI?: {
      minimize: () => Promise<void>           // 最小化当前窗口
      maximize: () => Promise<void>           // 最大化/还原窗口
      close: () => Promise<void>              // 关闭窗口
      openMoments: () => void                 // 打开友链独立窗口
      openMomentsText: () => void            // 打开友链-发布文字独立窗口
      openMomentsMedia: () => void           // 打开友链-发布图片/视频独立窗口
      openNoteEditor: () => void             // 打开笔记编辑器独立窗口
      openRegister?: () => void               // 打开注册独立窗口
      isMaximized: () => Promise<boolean>     // 查询是否最大化
      isPinned: () => Promise<boolean>       // 查询是否置顶
      togglePin: () => Promise<boolean>     // 切换窗口置顶
      // 订阅最大化状态变化，返回取消订阅函数
      onMaximizedChange: (callback: (maximized: boolean) => void) => () => void
      setAutoStart?: (enabled: boolean) => Promise<boolean>
      getAutoStart?: () => Promise<boolean>
      getDesktopPrefs?: () => Promise<{
        minimizeToTray: boolean
        openOnStartup: 'main' | 'tray'
        language: 'zh-CN' | 'en-US'
      }>
      setDesktopPrefs?: (prefs: {
        minimizeToTray?: boolean
        openOnStartup?: 'main' | 'tray'
        language?: 'zh-CN' | 'en-US'
      }) => Promise<{
        minimizeToTray: boolean
        openOnStartup: 'main' | 'tray'
        language: 'zh-CN' | 'en-US'
      }>
      notifyThemeChange?: (theme: 'light' | 'dark') => void
      setWindowMode?: (mode: 'login' | 'main') => Promise<void>
      pickDownloadPath?: () => Promise<string | null>
      openDownloadPath?: (customPath?: string) => Promise<boolean>
      clearAppCache?: () => Promise<{ ok: boolean; message?: string }>
      getDownloadPath?: () => Promise<string>
      getShortcuts?: () => Promise<{ toggleWindow: string; lock: string }>
      setShortcuts?: (payload: { toggleWindow?: string; lock?: string }) => Promise<boolean>
      onShortcutLock?: (callback: () => void) => () => void
      isElectron?: boolean
      hasNativeTitleBarOverlay?: boolean
      /** 屏幕截图，返回截图数据或 null */
      captureScreen?: () => Promise<{ dataURL: string; width: number; height: number } | null>
      /** 通过 IP 获取地理位置，返回位置字符串或 null */
      fetchIPLocation?: () => Promise<string | null>
      /** 发布成功后通知友链列表窗口刷新 */
      notifyMomentsPublished?: () => void
      /** 订阅友链列表刷新，返回取消订阅函数 */
      onMomentsRefresh?: (callback: () => void) => () => void
      /** 弹出系统桌面通知 */
      showNotification?: (payload: { title?: string; body?: string }) => Promise<boolean>
      /** 订阅应用内 toast 兜底 */
      onInAppToast?: (callback: (data: { title?: string; body?: string }) => void) => () => void
      /** OS 级加密存储（Token、锁屏 PIN 等敏感数据） */
      secureStorage?: {
        isAvailable: () => Promise<boolean>
        get: (key: string) => Promise<string | null>
        set: (key: string, value: string) => Promise<boolean>
        remove: (key: string) => Promise<boolean>
      }
    }
  }
}
