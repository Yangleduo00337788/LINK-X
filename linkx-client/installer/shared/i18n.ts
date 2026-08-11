/**
 * 安装/卸载向导文案（主进程与渲染进程共用）。
 */
export type InstallerLocale = 'zh-CN' | 'en-US'

const catalogs: Record<InstallerLocale, Record<string, string>> = {
  'zh-CN': {
    minimize: '最小化',
    close: '关闭',
    browseDirectory: '浏览目录',
    apiUnavailable: '安装程序接口不可用',
    uninstallApiUnavailable: '卸载程序接口不可用',
    preparingInstall: '准备安装...',
    preparingUninstall: '准备卸载...',
    installingProgress: '正在安装 LinkX，请稍候... {percent}%',
    uninstallingProgress: '正在卸载 LinkX，请稍候... {percent}%',
    installFail: '安装失败',
    uninstallFail: '卸载失败',
    installNow: '立即安装',
    uninstallNow: '立即卸载',
    agreePrefix: '阅读并同意',
    userAgreement: '用户协议',
    privacyPolicy: '隐私条款',
    customInstall: '自定义安装',
    addStartMenu: '添加到开始菜单',
    addDesktopShortcut: '添加到桌面快捷方式',
    autoStart: '开机自启动',
    launchAfter: '安装完成后启动',
    installComplete: '安装完成',
    uninstallComplete: '卸载完成',
    tryNow: '立即体验',
    done: '完成',
    exitInstallTitle: '退出安装',
    exitInstallMessage: '确定要退出安装 LinkX 程序吗？',
    exitUninstallTitle: '退出卸载',
    exitUninstallMessage: '确定要退出卸载 LinkX 程序吗？',
    exit: '退出',
    cancel: '取消',
    uninstallConfirm: '确定要卸载 LinkX 吗？',
    removeUserData: '同时删除本地用户数据（聊天记录缓存等）',
    installPath: '安装位置：{path}',
    copyingFile: '正在复制：{file}',
    creatingShortcuts: '创建快捷方式...',
    writingUninstallInfo: '写入卸载信息...',
    configuringAutostart: '配置启动项...',
    selectInstallDir: '选择安装目录',
    selectInstallDirRequired: '请选择安装目录',
    payloadNotFound: '未找到应用安装包，请先执行 npm run electron:build 生成 win-unpacked',
    payloadIncomplete: '安装包内容不完整，请重新下载安装程序或联系技术支持',
    shortcutFail: '创建快捷方式失败: {path}',
    uninstallerResourceMissing: '未找到卸载程序资源，请重新下载安装包',
    uninstallerMissing: '未找到卸载程序，请先执行 npm run electron:build 生成卸载程序',
    windowsOnlyInstall: 'LinkX 安装程序仅支持 Windows',
    windowsOnlyUninstall: 'LinkX 卸载程序仅支持 Windows',
    closingApp: '正在关闭 LinkX...',
    removingShortcuts: '正在移除快捷方式...',
    cleaningAutostart: '正在清理启动项...',
    removingRegistry: '正在移除注册表项...',
    cleaningUserData: '正在清理用户数据...',
    deletingFiles: '正在删除程序文件...',
    installDirNotFound: '未找到 LinkX 安装目录，可能已被卸载',
    pageTitleInstall: 'LinkX 安装向导',
    pageTitleUninstall: 'LinkX 卸载向导'
  },
  'en-US': {
    minimize: 'Minimize',
    close: 'Close',
    browseDirectory: 'Browse folder',
    apiUnavailable: 'Installer API is unavailable',
    uninstallApiUnavailable: 'Uninstaller API is unavailable',
    preparingInstall: 'Preparing installation...',
    preparingUninstall: 'Preparing uninstall...',
    installingProgress: 'Installing LinkX… {percent}%',
    uninstallingProgress: 'Uninstalling LinkX… {percent}%',
    installFail: 'Installation failed',
    uninstallFail: 'Uninstall failed',
    installNow: 'Install now',
    uninstallNow: 'Uninstall now',
    agreePrefix: 'I have read and agree to the',
    userAgreement: 'Terms of Service',
    privacyPolicy: 'Privacy Policy',
    customInstall: 'Custom installation',
    addStartMenu: 'Add to Start menu',
    addDesktopShortcut: 'Add desktop shortcut',
    autoStart: 'Launch at login',
    launchAfter: 'Launch after installation',
    installComplete: 'Installation complete',
    uninstallComplete: 'Uninstall complete',
    tryNow: 'Get started',
    done: 'Done',
    exitInstallTitle: 'Exit installer',
    exitInstallMessage: 'Exit the LinkX installer?',
    exitUninstallTitle: 'Exit uninstaller',
    exitUninstallMessage: 'Exit the LinkX uninstaller?',
    exit: 'Exit',
    cancel: 'Cancel',
    uninstallConfirm: 'Uninstall LinkX?',
    removeUserData: 'Also remove local user data (chat cache, etc.)',
    installPath: 'Install location: {path}',
    copyingFile: 'Copying: {file}',
    creatingShortcuts: 'Creating shortcuts...',
    writingUninstallInfo: 'Writing uninstall info...',
    configuringAutostart: 'Configuring launch at login...',
    selectInstallDir: 'Choose install folder',
    selectInstallDirRequired: 'Please choose an install folder',
    payloadNotFound: 'App payload not found. Run npm run electron:build first.',
    payloadIncomplete: 'Incomplete installer payload. Re-download or contact support.',
    shortcutFail: 'Failed to create shortcut: {path}',
    uninstallerResourceMissing: 'Uninstaller resources not found. Re-download the installer.',
    uninstallerMissing: 'Uninstaller not found. Run npm run electron:build first.',
    windowsOnlyInstall: 'LinkX installer supports Windows only',
    windowsOnlyUninstall: 'LinkX uninstaller supports Windows only',
    closingApp: 'Closing LinkX...',
    removingShortcuts: 'Removing shortcuts...',
    cleaningAutostart: 'Cleaning launch-at-login entry...',
    removingRegistry: 'Removing registry entries...',
    cleaningUserData: 'Cleaning user data...',
    deletingFiles: 'Deleting program files...',
    installDirNotFound: 'LinkX install folder not found. It may already be removed.',
    pageTitleInstall: 'LinkX Setup',
    pageTitleUninstall: 'LinkX Uninstaller'
  }
}

export function resolveInstallerLocale(locale?: string | null): InstallerLocale {
  if (!locale) return 'zh-CN'
  const normalized = locale.toLowerCase()
  if (normalized === 'en-us' || normalized.startsWith('en')) return 'en-US'
  return 'zh-CN'
}

export function installerT(
  locale: InstallerLocale,
  key: string,
  params?: Record<string, string | number>
): string {
  const catalog = catalogs[locale] || catalogs['zh-CN']
  let text = catalog[key] ?? catalogs['zh-CN'][key] ?? key
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v))
    }
  }
  return text
}
