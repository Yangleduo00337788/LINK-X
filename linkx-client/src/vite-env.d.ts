/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_WS_BASE_URL?: string
  /** WebRTC ICE 服务器 JSON 数组（可含 TURN） */
  readonly VITE_ICE_SERVERS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
