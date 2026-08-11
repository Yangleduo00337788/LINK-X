/**
 * 作者：yangleduo
 */
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface LicenseBlock {
  kind: 'title' | 'section' | 'body' | 'footer' | 'gap'
  text?: string
}

interface InstallerDefaults {
  version: string
  defaultDir: string
  appExe: string
  locale?: string
}

interface InstallerStartOptions {
  installDir: string
  desktopShortcut: boolean
  startMenuShortcut: boolean
  autoStartOnBoot: boolean
  launchAfter: boolean
}

interface InstallerProgress {
  percent: number
  status: string
}

interface InstallerApi {
  getDefaults: () => Promise<InstallerDefaults>
  browseDirectory: (current: string) => Promise<string | null>
  startInstall: (options: InstallerStartOptions) => Promise<{ ok: boolean; message?: string }>
  launchApp: () => Promise<void>
  close: () => void
  minimize: () => void
  openExternal: (url: string) => void
  openLegal: (kind: 'service' | 'privacy') => void
  setWindowSize?: (width: number, height: number) => void
  onProgress: (callback: (progress: InstallerProgress) => void) => () => void
}

interface UninstallerDefaults {
  version: string
  installDir: string
  appName: string
  locale?: string
}

interface UninstallerStartOptions {
  removeUserData?: boolean
}

interface UninstallerApi {
  getDefaults: () => Promise<UninstallerDefaults>
  startUninstall: (options: UninstallerStartOptions) => Promise<{ ok: boolean; message?: string }>
  close: () => void
  minimize: () => void
  setWindowSize?: (width: number, height: number) => void
  onProgress: (callback: (progress: InstallerProgress) => void) => () => void
}

interface Window {
  installer?: InstallerApi
  uninstaller?: UninstallerApi
}
