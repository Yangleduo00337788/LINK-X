/**
 * 聊天图片展示地址缓存：虚拟列表滚动回收节点后，再次挂载时直接复用 URL，避免占位闪烁。
 */
const cache = new Map<string, string>()
const MAX = 400

export function getChatImageDisplayCache(messageId: string): string | undefined {
  return cache.get(messageId)
}

export function setChatImageDisplayCache(messageId: string, src: string): void {
  const id = messageId?.trim()
  const url = src?.trim()
  if (!id || !url) return
  if (cache.has(id)) {
    cache.delete(id)
  } else if (cache.size >= MAX) {
    const oldest = cache.keys().next().value
    if (oldest) cache.delete(oldest)
  }
  cache.set(id, url)
}
