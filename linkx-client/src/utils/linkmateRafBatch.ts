/**
 * 作者：yangleduo
 */
/** 将高频字符串更新合并到下一帧，减少流式 delta 触发的重渲染 */
export function createStringRafBatcher(onFlush: (chunk: string) => void) {
  let pending = ''
  let scheduled = false

  const schedule = () => {
    if (scheduled) return
    scheduled = true
    requestAnimationFrame(() => {
      scheduled = false
      if (!pending) return
      const value = pending
      pending = ''
      onFlush(value)
    })
  }

  return {
    push(chunk: string) {
      pending += chunk
      schedule()
    },
    flush() {
      if (!pending) return
      const value = pending
      pending = ''
      onFlush(value)
    }
  }
}
