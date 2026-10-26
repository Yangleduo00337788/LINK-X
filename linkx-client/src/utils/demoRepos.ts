/**
 * 作者：yangleduo
 */
export const GITEE_REPO_URL = 'https://gitee.com/yangleduo7788/link-x'
export const GITHUB_REPO_URL = 'https://github.com/Yangleduo00337788/LINK-X'
export const DEMO_ADMIN_PATH = '/admin/'

export function openDemoRepo(url: string) {
  if (typeof window.electronAPI?.openExternal === 'function') {
    void window.electronAPI.openExternal(url)
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

export function openDemoAdmin() {
  try {
    if (window.top && window.top !== window) {
      window.top.location.assign(DEMO_ADMIN_PATH)
      return
    }
  } catch {
    /* iframe 跨域时退回当前窗口 */
  }
  window.location.assign(DEMO_ADMIN_PATH)
}
