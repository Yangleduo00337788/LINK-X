/**
 * 作者：yangleduo
 */
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_WS_BASE_URL?: string
  /** 官网根地址（默认 https://mars-studio.asia） */
  readonly VITE_LEGAL_PAGE_BASE_URL?: string
  /** 帮助中心根地址（默认 https://mars-studio.asia/help） */
  readonly VITE_HELP_PAGE_BASE_URL?: string
  /** WebRTC ICE 服务器 JSON 数组（可含 TURN） */
  readonly VITE_ICE_SERVERS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
