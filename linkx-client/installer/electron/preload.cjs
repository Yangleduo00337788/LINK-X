/**
 * 作者：yangleduo
 */
const { contextBridge, ipcRenderer } = require('electron')

const installer = {
  getDefaults: () => ipcRenderer.invoke('installer:get-defaults'),
  browseDirectory: current => ipcRenderer.invoke('installer:browse-directory', current),
  startInstall: options => ipcRenderer.invoke('installer:start', options),
  launchApp: () => ipcRenderer.invoke('installer:launch-app'),
  close: () => ipcRenderer.send('installer:close'),
  minimize: () => ipcRenderer.send('installer:minimize'),
  openExternal: url => ipcRenderer.send('installer:open-external', url),
  openLegal: kind => ipcRenderer.send('installer:open-legal', kind),
  setWindowSize: (width, height) => ipcRenderer.send('installer:set-window-size', { width, height }),
  onProgress: callback => {
    const handler = (_event, progress) => {
      callback(progress)
    }
    ipcRenderer.on('installer:progress', handler)
    return () => ipcRenderer.removeListener('installer:progress', handler)
  }
}

const uninstaller = {
  getDefaults: () => ipcRenderer.invoke('uninstaller:get-defaults'),
  startUninstall: options => ipcRenderer.invoke('uninstaller:start', options),
  close: () => ipcRenderer.send('uninstaller:close'),
  minimize: () => ipcRenderer.send('uninstaller:minimize'),
  setWindowSize: (width, height) => ipcRenderer.send('uninstaller:set-window-size', { width, height }),
  onProgress: callback => {
    const handler = (_event, progress) => {
      callback(progress)
    }
    ipcRenderer.on('uninstaller:progress', handler)
    return () => ipcRenderer.removeListener('uninstaller:progress', handler)
  }
}

contextBridge.exposeInMainWorld('installer', installer)
contextBridge.exposeInMainWorld('uninstaller', uninstaller)
