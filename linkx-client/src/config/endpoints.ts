/**
 * 前端 API / WS 基址统一入口，避免 localhost 兜底散落。
 * 生产务必通过 VITE_API_BASE_URL / VITE_WS_BASE_URL 注入。
 */
const trimSlash = (url: string) => url.replace(/\/$/, '')

export const API_BASE_URL = trimSlash(
  import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api'
)

export const WS_BASE_URL = trimSlash(
  import.meta.env.VITE_WS_BASE_URL || 'ws://127.0.0.1:8081'
)
