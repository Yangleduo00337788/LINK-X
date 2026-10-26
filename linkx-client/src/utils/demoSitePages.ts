/**
 * 作者：yangleduo
 * 演示站把官网静态页挂在同源 /site（文档、协议、隐私等）。
 */
function demoOrigin(): string {
  try {
    return (window.top?.location ?? window.location).origin
  } catch {
    return window.location.origin
  }
}

export function demoWebsiteBaseUrl(): string | null {
  if (import.meta.env.VITE_LINKX_DEMO !== '1') return null
  return `${demoOrigin()}/site`
}
