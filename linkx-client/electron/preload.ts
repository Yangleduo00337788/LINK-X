import { contextBridge, ipcRenderer, desktopCapturer } from 'electron'

type WinAction = 'minimize' | 'maximize' | 'close'

const channels = {
  minimize: 'window-minimize',
  maximize: 'window-maximize',
  close: 'window-close'
} as const

const invokeChannels = {
  minimize: 'window:minimize',
  maximize: 'window:maximize',
  close: 'window:close'
} as const

function windowAction(action: WinAction) {
  ipcRenderer.send(channels[action])
  void ipcRenderer.invoke(invokeChannels[action]).catch(() => {
    /* send 已处理时 invoke 可忽略 */
  })
}

// 屏幕截图 API
async function captureScreen(): Promise<{ dataURL: string; width: number; height: number } | null> {
  try {
    const sources = await desktopCapturer.getSources({
      types: ['screen'],
      thumbnailSize: { width: 1920, height: 1080 }
    })
    if (sources.length === 0) return null
    const source = sources[0]
    return {
      dataURL: source.thumbnail.toDataURL(),
      width: source.thumbnail.getSize().width,
      height: source.thumbnail.getSize().height
    }
  } catch (e) {
    console.error('截图失败:', e)
    return null
  }
}

const api = {
  minimize: () => windowAction('minimize'),
  maximize: () => windowAction('maximize'),
  close: () => windowAction('close'),
  isElectron: true as const,
  captureScreen,
  secureStorage: {
    isAvailable: () => ipcRenderer.invoke('secure-storage:is-available'),
    get: (key: string) => ipcRenderer.invoke('secure-storage:get', key),
    set: (key: string, value: string) => ipcRenderer.invoke('secure-storage:set', key, value),
    remove: (key: string) => ipcRenderer.invoke('secure-storage:remove', key)
  }
}

contextBridge.exposeInMainWorld('electronAPI', api)