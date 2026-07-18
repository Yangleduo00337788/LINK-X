/**
 * 全屏/半屏覆盖层 Store
 * 管理 overlay 页面栈与文件预览载荷
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入覆盖层页面类型
import type { OverlayPage } from '../types'

/** 文件预览 overlay 所需的数据载荷 */
export interface FilePreviewPayload {
  fileName: string   // 文件名
  fileUrl?: string   // 文件访问 URL（可选）
  fileSize?: string  // 人类可读的文件大小（可选）
  isImage?: boolean  // 是否为图片类型（可选）
}

// 定义并导出 overlay Store
export const useOverlayStore = defineStore('overlay', {
  // 初始状态
  state: () => ({
    stack: [] as OverlayPage[],                        // overlay 页面栈（后进先出）
    filePreview: null as FilePreviewPayload | null     // file-preview 页面的文件信息
  }),

  getters: {
    /** 栈顶页面，即当前显示的 overlay */
    currentPage(state): OverlayPage | null {
      return state.stack[state.stack.length - 1] ?? null
    },
    /** 是否有任意 overlay 打开 */
    isOpen(state): boolean {
      return state.stack.length > 0
    },
    /** 当前预览文件名（无预览时为空串） */
    overlayFileName(state): string {
      return state.filePreview?.fileName ?? ''
    }
  },

  actions: {
    /**
     * 打开指定 overlay 页面
     * @param page 页面标识
     * @param payload 可选附加数据（文件名或完整预览载荷）
     */
    open(
      page: OverlayPage,
      payload?: { fileName?: string; filePreview?: FilePreviewPayload }
    ) {
      // 打开文件预览时设置预览载荷
      if (page === 'file-preview') {
        this.filePreview =
          payload?.filePreview ??
          (payload?.fileName ? { fileName: payload.fileName } : null)
      }
      // 避免连续 push 相同页面（防止重复打开同一层）
      if (this.stack[this.stack.length - 1] !== page) {
        this.stack.push(page)
      }
    },

    /** 关闭栈顶 overlay，并清理关联的文件预览状态 */
    close() {
      const top = this.stack.pop() // 弹出栈顶
      if (top === 'file-preview') this.filePreview = null  // 清理预览载荷
    },

    /** 关闭所有 overlay 并重置关联状态 */
    closeAll() {
      this.stack = []
      this.filePreview = null
    }
  }
})
