/**
 * 作者：yangleduo
 */
/** 内存会话标记：token 在 HttpOnly Cookie 中，JS 不可读，用此标记驱动路由与拦截器。 */
let sessionActive = false

export function isSessionActive(): boolean {
  return sessionActive
}

export function setSessionActive(active: boolean): void {
  sessionActive = active
}
