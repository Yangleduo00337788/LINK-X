/**
 * 作者：yangleduo
 */
/**
 * LinkX 客户端构建版本号与发布渠道。
 * 用于"检查更新"接口的 current / channel 参数；服务端比对后返回 hasUpdate / forceUpdate。
 * <p>
 * 与后端 linkx.app.version / linkx.app.channel 默认值保持一致。
 * 升级客户端构建时，需要同步修改此处与服务端配置。
 * </p>
 */
export const APP_CLIENT_VERSION = '1.0.1'

/** 客户端订阅渠道：stable | beta | dev */
export const APP_CLIENT_CHANNEL = 'stable'

export type AppClientPlatform = 'windows' | 'macos' | 'linux'

/** 当前客户端平台（Electron 优先） */
export function getClientPlatform(): AppClientPlatform {
  const fromElectron = window.electronAPI?.getPlatform?.()
  if (fromElectron === 'windows' || fromElectron === 'macos' || fromElectron === 'linux') {
    return fromElectron
  }
  const ua = navigator.userAgent
  if (/Win/i.test(ua)) return 'windows'
  if (/Mac/i.test(ua)) return 'macos'
  return 'linux'
}
