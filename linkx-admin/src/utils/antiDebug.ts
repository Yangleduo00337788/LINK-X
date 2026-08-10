/**
 * 作者：yangleduo
 */
type StopAntiDebug = () => void

let activeStop: StopAntiDebug | null = null

function blockShortcut(event: KeyboardEvent) {
  const key = event.key?.toUpperCase()
  if (key === 'F12') {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  if (event.ctrlKey && event.shiftKey && (key === 'I' || key === 'J' || key === 'C')) {
    event.preventDefault()
    event.stopPropagation()
  }
  if (event.ctrlKey && key === 'U') {
    event.preventDefault()
    event.stopPropagation()
  }
}

function blockContextMenu(event: MouseEvent) {
  event.preventDefault()
}

function detectDevtoolsOpen(): boolean {
  const threshold = 160
  return (
    window.outerWidth - window.innerWidth > threshold ||
    window.outerHeight - window.innerHeight > threshold
  )
}

export function startAntiDebug(): StopAntiDebug {
  if (activeStop) {
    activeStop()
  }

  const onKeyDown = (event: KeyboardEvent) => blockShortcut(event)
  const onContextMenu = (event: MouseEvent) => blockContextMenu(event)
  window.addEventListener('keydown', onKeyDown, true)
  window.addEventListener('contextmenu', onContextMenu, true)

  const timer = window.setInterval(() => {
    if (detectDevtoolsOpen()) {
      document.body.innerHTML =
        '<div style="display:flex;align-items:center;justify-content:center;height:100vh;font-size:18px;color:#c0392b;background:#111;">开发者工具已被禁用</div>'
      window.clearInterval(timer)
    }
  }, 1000)

  const noop = () => undefined
  const methods = ['log', 'debug', 'info', 'warn', 'error', 'table', 'trace'] as const
  const original = new Map<string, (...args: unknown[]) => void>()
  methods.forEach((method) => {
    const current = console[method] as (...args: unknown[]) => void
    original.set(method, current)
    ;(console as unknown as Record<string, (...args: unknown[]) => void>)[method] = noop
  })

  const stop: StopAntiDebug = () => {
    window.removeEventListener('keydown', onKeyDown, true)
    window.removeEventListener('contextmenu', onContextMenu, true)
    window.clearInterval(timer)
    methods.forEach((method) => {
      const prev = original.get(method)
      if (prev) {
        ;(console as unknown as Record<string, (...args: unknown[]) => void>)[method] = prev
      }
    })
    activeStop = null
  }

  activeStop = stop
  return stop
}

export function stopAntiDebug() {
  activeStop?.()
}
