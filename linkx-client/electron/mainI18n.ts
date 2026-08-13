/**
 * Electron 主进程 UI 文案（托盘、原生对话框、IPC 返回消息）。
 * 语言与 desktop-prefs.json 中的 language 同步。
 */
export type MainLocale = 'zh-CN' | 'en-US'

const catalogs: Record<MainLocale, Record<string, string>> = {
  'zh-CN': {
    trayShow: '显示主窗口',
    trayQuit: '退出',
    pickImagesTitle: '选择图片',
    cacheCleared: '缓存已清理',
    cacheClearFail: '清理失败',
    downloadFail: '下载失败',
    downloadFailStatus: '下载失败 ({status})',
    downloadMissingContent: '缺少下载内容',
    downloadBadUrl: '下载地址格式错误',
    downloadUntrustedSource: '仅允许下载本应用可信源的文件',
    downloadUnsupportedUrl: '不支持的下载地址，请由渲染进程传入文件数据',
    downloadSavedNoAutoOpen: '文件已保存；出于安全考虑未自动打开可执行文件',
    downloadHttpsOnly: '仅支持 HTTPS 下载地址',
    downloadInvalidUrl: '无效的下载地址',
    downloadNotWhitelisted: '下载源不在允许的白名单内',
    downloadInstallerOnly: '仅允许下载安装包格式(.exe/.msi/.dmg/.AppImage)',
    downloadInstallerReady: '已下载，请在打开的文件夹中手动运行安装包',
    downloadInstallerFail: '下载安装失败',
    downloadChecksumMismatch: '安装包校验失败，请重新下载或联系管理员',
    screenshotAllow: '允许',
    screenshotCancel: '取消',
    screenshotTitle: '屏幕截图授权',
    screenshotMessage: 'LinkX 请求进行屏幕截图，是否允许？',
    screenshotDetail: '截图将用于发送给聊天对象。'
  },
  'en-US': {
    trayShow: 'Show LinkX',
    trayQuit: 'Quit',
    pickImagesTitle: 'Select images',
    cacheCleared: 'Cache cleared',
    cacheClearFail: 'Failed to clear cache',
    downloadFail: 'Download failed',
    downloadFailStatus: 'Download failed ({status})',
    downloadMissingContent: 'No download content',
    downloadBadUrl: 'Invalid download URL',
    downloadUntrustedSource: 'Only trusted app sources are allowed',
    downloadUnsupportedUrl: 'Unsupported URL. Pass file data from the renderer.',
    downloadSavedNoAutoOpen: 'File saved. Executable files are not opened automatically for security.',
    downloadHttpsOnly: 'Only HTTPS download URLs are supported',
    downloadInvalidUrl: 'Invalid download URL',
    downloadNotWhitelisted: 'Download source is not on the allowlist',
    downloadInstallerOnly: 'Only installer formats are allowed (.exe/.msi/.dmg/.AppImage)',
    downloadInstallerReady: 'Downloaded. Run the installer from the opened folder.',
    downloadInstallerFail: 'Failed to download installer',
    downloadChecksumMismatch: 'Installer checksum mismatch. Please retry or contact support.',
    screenshotAllow: 'Allow',
    screenshotCancel: 'Cancel',
    screenshotTitle: 'Screen capture permission',
    screenshotMessage: 'LinkX wants to capture your screen. Allow?',
    screenshotDetail: 'The screenshot will be sent in chat.'
  }
}

export function mainT(locale: MainLocale, key: string, params?: Record<string, string | number>): string {
  const catalog = catalogs[locale] || catalogs['zh-CN']
  let text = catalog[key] ?? catalogs['zh-CN'][key] ?? key
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v))
    }
  }
  return text
}
