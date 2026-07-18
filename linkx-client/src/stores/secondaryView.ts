/**
 * 次级视图 Store
 * 管理主界面右侧或次级区域当前展示的收藏项或本地文件
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入收藏项类型
import type { FavoriteItem } from '../types'
// 导入本地文件项类型（来自 files Store）
import type { LocalFileItem } from '../stores/files'

// 定义并导出 secondaryView Store
export const useSecondaryViewStore = defineStore('secondaryView', {
  // 初始状态：两个互斥的「当前激活项」，同一时刻通常只有一个非 null
  state: () => ({
    activeFavorite: null as FavoriteItem | null, // 当前预览的收藏内容
    activeFile: null as LocalFileItem | null     // 当前预览的本地文件
  })
})
