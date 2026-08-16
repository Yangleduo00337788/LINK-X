/** 主界面窗口最小尺寸，与 electron/main.ts 保持一致 */
export const MAIN_WINDOW_MIN_WIDTH = 960
export const MAIN_WINDOW_MIN_HEIGHT = 640

export type WindowResizeEdge = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'

export type WindowBounds = {
  x: number
  y: number
  width: number
  height: number
}

export function cursorForResizeEdge(edge: WindowResizeEdge): string {
  switch (edge) {
    case 'n':
    case 's':
      return 'ns-resize'
    case 'e':
    case 'w':
      return 'ew-resize'
    case 'ne':
    case 'sw':
      return 'nesw-resize'
    case 'nw':
    case 'se':
      return 'nwse-resize'
    default:
      return 'default'
  }
}

export function computeResizedBounds(
  start: WindowBounds,
  dx: number,
  dy: number,
  edge: WindowResizeEdge,
  minW = MAIN_WINDOW_MIN_WIDTH,
  minH = MAIN_WINDOW_MIN_HEIGHT
): WindowBounds {
  let { x, y, width, height } = start

  if (edge.includes('e')) {
    width = Math.max(minW, start.width + dx)
  }
  if (edge.includes('w')) {
    const nextWidth = Math.max(minW, start.width - dx)
    x = start.x + (start.width - nextWidth)
    width = nextWidth
  }
  if (edge.includes('s')) {
    height = Math.max(minH, start.height + dy)
  }
  if (edge.includes('n')) {
    const nextHeight = Math.max(minH, start.height - dy)
    y = start.y + (start.height - nextHeight)
    height = nextHeight
  }

  return {
    x: Math.round(x),
    y: Math.round(y),
    width: Math.round(width),
    height: Math.round(height)
  }
}

export function startWindowEdgeResize(
  event: MouseEvent,
  edge: WindowResizeEdge,
  getBounds: () => Promise<WindowBounds>,
  setBounds: (bounds: WindowBounds) => Promise<void>
): void {
  if (event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()

  const startPoint = { x: event.screenX, y: event.screenY }
  let startBounds: WindowBounds | null = null
  let raf = 0
  let pending: WindowBounds | null = null

  const flush = () => {
    raf = 0
    if (!pending) return
    const next = pending
    pending = null
    void setBounds(next)
  }

  const schedule = (bounds: WindowBounds) => {
    pending = bounds
    if (!raf) {
      raf = requestAnimationFrame(flush)
    }
  }

  const onMove = (ev: MouseEvent) => {
    if (!startBounds) return
    const dx = ev.screenX - startPoint.x
    const dy = ev.screenY - startPoint.y
    schedule(computeResizedBounds(startBounds, dx, dy, edge))
  }

  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    if (pending) {
      void setBounds(pending)
      pending = null
    }
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.body.style.cursor = cursorForResizeEdge(edge)
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)

  void getBounds().then(bounds => {
    startBounds = bounds
  })
}
