import { contextBridge, ipcRenderer } from 'electron'

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

const api = {
  minimize: () => windowAction('minimize'),
  maximize: () => windowAction('maximize'),
  close: () => windowAction('close'),
  isElectron: true as const
}

contextBridge.exposeInMainWorld('electronAPI', api)