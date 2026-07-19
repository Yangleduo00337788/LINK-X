const { contextBridge, ipcRenderer } = require('electron')

const MAX_CHANGED = 'window-maximized-changed'

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  openMoments: () => ipcRenderer.send('window-open-moments'),
  openMomentsText: () => ipcRenderer.send('window-open-moments-text'),
  openMomentsMedia: () => ipcRenderer.send('window-open-moments-media'),
  openNoteEditor: () => ipcRenderer.send('window-open-note-editor'),
  openRegister: () => ipcRenderer.send('window-open-register'),
  isMaximized: () => ipcRenderer.invoke('window:is-maximized'),
  isPinned: () => ipcRenderer.invoke('window:is-pinned'),
  togglePin: () => ipcRenderer.invoke('window:toggle-pin'),
  onMaximizedChange: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, maximized) => callback(!!maximized)
    ipcRenderer.on(MAX_CHANGED, listener)
    return () => ipcRenderer.removeListener(MAX_CHANGED, listener)
  },
  setAutoStart: enabled => ipcRenderer.invoke('app:set-auto-start', enabled),
  getAutoStart: () => ipcRenderer.invoke('app:get-auto-start'),
  notifyThemeChange: theme => ipcRenderer.send('theme-changed', theme),
  setWindowMode: mode => ipcRenderer.invoke('window:set-mode', mode),
  isElectron: true,
  secureStorage: {
    isAvailable: () => ipcRenderer.invoke('secure-storage:is-available'),
    get: key => ipcRenderer.invoke('secure-storage:get', key),
    set: (key, value) => ipcRenderer.invoke('secure-storage:set', key, value),
    remove: key => ipcRenderer.invoke('secure-storage:remove', key)
  }
})