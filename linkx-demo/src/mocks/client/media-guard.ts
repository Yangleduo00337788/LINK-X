/**
 * 作者：yangleduo
 */
/** 把误指向真实后端 / 坏 blob 的图片纠正到本地 demo-media */
export function installClientMediaUrlGuard() {
  const origin = () => window.location.origin

  function rewrite(url: string): string {
    return url
      .replace(/^https?:\/\/127\.0\.0\.1:8080/i, origin())
      .replace(/^https?:\/\/localhost:8080/i, origin())
  }

  function recover(el: HTMLImageElement | HTMLVideoElement) {
    const src = el.currentSrc || el.src || ''
    if (!src) return
    const next = rewrite(src)
    if (next !== src) {
      el.src = next
      return
    }
    if (!(el instanceof HTMLImageElement)) return
    if (src.startsWith('blob:') || src.startsWith('file:')) {
      const name = (el.alt || '').trim()
      el.src = /\.jpe?g$/i.test(name) ? `/media/chat/${name}` : '/media/chat/chat-photo-01.jpg'
    }
  }

  window.addEventListener(
    'error',
    e => {
      const t = e.target
      if (t instanceof HTMLImageElement || t instanceof HTMLVideoElement) recover(t)
    },
    true
  )
}
