/// <reference types="vite/client" /> // 引入 Vite 客户端类型（import.meta.env、静态资源等）

/** Vite 环境变量类型扩展，对应 .env 中以 VITE_ 开头的变量 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string // 后端 API 基地址，README 规划用，当前 client.ts 仍写死 localhost
}

/** import.meta 对象类型 */
interface ImportMeta {
  readonly env: ImportMetaEnv // 通过 import.meta.env 访问环境变量
}
