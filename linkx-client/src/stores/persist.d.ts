/**
 * Pinia 持久化插件类型扩展声明
 * 为 defineStore 的 options 增加 persist 配置项的类型支持
 */

// 引入 pinia-plugin-persistedstate 插件，确保模块声明合并生效
import 'pinia-plugin-persistedstate'

// 扩展 Pinia 核心模块的类型定义
declare module 'pinia' {
  // 扩展 Store 定义选项基类，添加 persist 可选配置
  export interface DefineStoreOptionsBase<S, Store> {
    // persist 可为布尔值（全量持久化）或对象（指定 key 与 paths）
    persist?: boolean | {
      key?: string      // localStorage 存储键名
      paths?: string[]  // 需要持久化的 state 字段路径列表
    }
  }
}
