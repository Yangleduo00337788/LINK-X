/**
 * 作者：yangleduo
 */
import { contextBridge, ipcRenderer } from 'electron'

const installer: InstallerApi = {
  getDefaults: () => ipcRenderer.invoke('installer:get-defaults'),
  browseDirectory: (current: string) => ipcRenderer.invoke('installer:browse-directory', current),
  startInstall: (options: InstallerStartOptions) => ipcRenderer.invoke('installer:start', options),
  launchApp: () => ipcRenderer.invoke('installer:launch-app'),
  close: () => ipcRenderer.send('installer:close'),
  minimize: () => ipcRenderer.send('installer:minimize'),
  openExternal: (url: string) => ipcRenderer.send('installer:open-external', url),
  openLegal: (kind: 'service' | 'privacy') => ipcRenderer.send('installer:open-legal', kind),
  onProgress: (callback: (progress: InstallerProgress) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, progress: InstallerProgress) => {
      callback(progress)
    }
    ipcRenderer.on('installer:progress', handler)
    return () => ipcRenderer.removeListener('installer:progress', handler)
  }
}

contextBridge.exposeInMainWorld('installer', installer)
