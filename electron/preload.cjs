const { contextBridge, ipcRenderer } = require('electron')

const MAX_CHANGED = 'window-maximized-changed'

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  openMoments: () => ipcRenderer.send('window-open-moments'),
  openNoteEditor: () => ipcRenderer.send('window-open-note-editor'),
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
  isElectron: true
})