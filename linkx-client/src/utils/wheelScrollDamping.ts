/**
 * 全局滚轮/触控板垂直滚动阻尼，降低快速滑动时的速度，便于阅读。
 */
const WHEEL_SCROLL_FACTOR = 0.8
const WHEEL_MAX_STEP_RATIO = 0.2
const WHEEL_MIN_STEP_PX = 26

let installed = false

function canScrollY(el: HTMLElement, deltaY: number): boolean {
  if (el.scrollHeight <= el.clientHeight + 1) return false
  if (deltaY < 0) return el.scrollTop > 0
  if (deltaY > 0) return el.scrollTop + el.clientHeight < el.scrollHeight - 1
  return false
}

function findScrollableY(target: EventTarget | null): HTMLElement | null {
  let node = target instanceof Element ? (target as HTMLElement) : null
  while (node && node !== document.body && node !== document.documentElement) {
    const style = window.getComputedStyle(node)
    const overflowY = style.overflowY
    if (overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay') {
      return node
    }
    node = node.parentElement
  }
  const root = document.scrollingElement
  return root instanceof HTMLElement ? root : null
}

function onWheel(e: WheelEvent) {
  if (e.defaultPrevented || e.ctrlKey) return
  if (e.deltaY === 0 || Math.abs(e.deltaY) < Math.abs(e.deltaX)) return

  const el = findScrollableY(e.target)
  if (!el || !canScrollY(el, e.deltaY)) return

  e.preventDefault()
  // vueuc VirtualList 会在 .v-vl 上再次处理 wheel 并叠加滚动，导致聊天区比设置页更快
  e.stopPropagation()
  const maxStep = Math.max(WHEEL_MIN_STEP_PX, el.clientHeight * WHEEL_MAX_STEP_RATIO)
  const step = Math.sign(e.deltaY) * Math.min(Math.abs(e.deltaY) * WHEEL_SCROLL_FACTOR, maxStep)
  el.scrollTop += step
}

/** 在应用入口调用一次即可 */
export function installGlobalWheelScrollDamping(): void {
  if (installed || typeof document === 'undefined') return
  installed = true
  document.addEventListener('wheel', onWheel, { passive: false, capture: true })
}
