/**
 * 个人网盘 Store（对接 /cloud）
 */
import { defineStore } from 'pinia'
import * as driveApi from '../api/drive'
import type { DriveActivityVO, DriveItemVO, DriveStorageVO } from '../api/drive'
import { formatFileSize } from '../utils/file'

export const useDriveStore = defineStore('drive', {
  state: () => ({
    folderId: null as string | null,
    items: [] as DriveItemVO[],
    breadcrumb: [] as DriveItemVO[],
    storage: null as DriveStorageVO | null,
    selectedIds: [] as string[],
    selectedKindMap: {} as Record<string, 'file' | 'folder'>,
    detailItem: null as DriveItemVO | null,
    activities: [] as DriveActivityVO[],
    loading: false,
    uploading: false,
    initialized: false
  }),

  getters: {
    selectedItems(state): { kind: 'file' | 'folder'; id: string }[] {
      return state.selectedIds.map(id => ({
        kind: state.selectedKindMap[id] || 'file',
        id
      }))
    },
    usedLabel(state): string {
      return state.storage ? formatFileSize(state.storage.usedBytes) : '0 B'
    },
    quotaLabel(state): string {
      return state.storage ? formatFileSize(state.storage.quotaBytes) : '20 GB'
    }
  },

  actions: {
    async refreshAll(keyword?: string) {
      await Promise.all([this.fetchItems(keyword), this.fetchStorage(), this.fetchBreadcrumb()])
      this.initialized = true
    },

    async fetchItems(keyword?: string) {
      this.loading = true
      try {
        const res = await driveApi.listDriveItems(this.folderId, keyword)
        if (res.code === 200 && res.data) {
          this.items = res.data
        }
      } finally {
        this.loading = false
      }
    },

    async fetchStorage() {
      const res = await driveApi.getDriveStorage()
      if (res.code === 200 && res.data) {
        this.storage = res.data
      }
    },

    async fetchBreadcrumb() {
      if (!this.folderId) {
        this.breadcrumb = []
        return
      }
      const res = await driveApi.getDriveBreadcrumb(this.folderId)
      if (res.code === 200 && res.data) {
        this.breadcrumb = res.data
      }
    },

    async enterFolder(folderId: string | null) {
      this.folderId = folderId
      this.selectedIds = []
      this.selectedKindMap = {}
      this.detailItem = null
      await this.refreshAll()
    },

    toggleSelect(item: DriveItemVO) {
      const idx = this.selectedIds.indexOf(item.id)
      if (idx >= 0) {
        this.selectedIds.splice(idx, 1)
        delete this.selectedKindMap[item.id]
      } else {
        this.selectedIds.push(item.id)
        this.selectedKindMap[item.id] = item.kind
      }
    },

    selectAll() {
      this.selectedIds = this.items.map(i => i.id)
      this.selectedKindMap = Object.fromEntries(this.items.map(i => [i.id, i.kind]))
    },

    clearSelection() {
      this.selectedIds = []
      this.selectedKindMap = {}
    },

    async openDetail(item: DriveItemVO) {
      if (item.kind === 'folder') {
        this.detailItem = item
        this.activities = []
        return
      }
      const res = await driveApi.getDriveFile(item.id)
      if (res.code === 200 && res.data) {
        this.detailItem = res.data
      } else {
        this.detailItem = item
      }
      await this.fetchActivities(item.id)
    },

    async fetchActivities(fileId?: string) {
      const res = await driveApi.listDriveActivities(fileId, 30)
      if (res.code === 200 && res.data) {
        this.activities = res.data
      }
    },

    async uploadFiles(files: FileList | File[]) {
      const list = Array.from(files)
      if (!list.length) return
      this.uploading = true
      try {
        for (const file of list) {
          const res = await driveApi.uploadDriveFile(file, this.folderId)
          if (res.code !== 200) {
            throw new Error(res.message || '上传失败')
          }
        }
        await this.refreshAll()
      } finally {
        this.uploading = false
      }
    },

    async createFolder(name: string) {
      const res = await driveApi.createDriveFolder(name, this.folderId)
      if (res.code !== 200) throw new Error(res.message || '创建失败')
      await this.refreshAll()
      return res.data
    },

    async expandStorage() {
      const res = await driveApi.expandDriveStorage()
      if (res.code !== 200) throw new Error(res.message || '扩容失败')
      this.storage = res.data
      return res.data
    },

    async deleteSelected() {
      if (!this.selectedIds.length) return
      const res = await driveApi.batchDeleteDriveItems(this.selectedItems)
      if (res.code !== 200) throw new Error(res.message || '删除失败')
      if (this.detailItem && this.selectedIds.includes(this.detailItem.id)) {
        this.detailItem = null
      }
      this.clearSelection()
      await this.refreshAll()
    },

    async deleteOne(item: DriveItemVO) {
      const res =
        item.kind === 'folder'
          ? await driveApi.deleteDriveFolder(item.id)
          : await driveApi.deleteDriveFile(item.id)
      if (res.code !== 200) throw new Error(res.message || '删除失败')
      if (this.detailItem?.id === item.id) this.detailItem = null
      await this.refreshAll()
    },

    async moveSelected(targetFolderId: string | null) {
      if (!this.selectedIds.length) return
      const res = await driveApi.batchMoveDriveItems(this.selectedItems, targetFolderId)
      if (res.code !== 200) throw new Error(res.message || '移动失败')
      this.clearSelection()
      await this.refreshAll()
    },

    async renameItem(item: DriveItemVO, name: string) {
      const res =
        item.kind === 'folder'
          ? await driveApi.updateDriveFolder(item.id, { name })
          : await driveApi.updateDriveFile(item.id, { name })
      if (res.code !== 200) throw new Error(res.message || '重命名失败')
      await this.refreshAll()
      if (this.detailItem?.id === item.id && res.data) {
        this.detailItem = res.data
      }
    },

    async updateDescription(fileId: string, description: string) {
      const res = await driveApi.updateDriveFile(fileId, { description })
      if (res.code !== 200) throw new Error(res.message || '保存失败')
      if (this.detailItem?.id === fileId && res.data) this.detailItem = res.data
    },

    async addTag(fileId: string, tagName: string) {
      const res = await driveApi.addDriveTag(fileId, tagName)
      if (res.code !== 200) throw new Error(res.message || '添加标签失败')
      if (this.detailItem?.id === fileId) {
        this.detailItem = { ...this.detailItem, tags: res.data || [] }
      }
      await this.fetchActivities(fileId)
    },

    async removeTag(fileId: string, tagName: string) {
      const res = await driveApi.removeDriveTag(fileId, tagName)
      if (res.code !== 200) throw new Error(res.message || '移除标签失败')
      if (this.detailItem?.id === fileId) {
        this.detailItem = { ...this.detailItem, tags: res.data || [] }
      }
    },

    async createShare(item: DriveItemVO, opts?: { password?: string; expireHours?: number }) {
      const res = await driveApi.createDriveShare({
        shareType: item.kind,
        targetId: item.id,
        password: opts?.password,
        expireHours: opts?.expireHours
      })
      if (res.code !== 200 || !res.data) throw new Error(res.message || '创建分享失败')
      if (item.kind === 'file') await this.fetchActivities(item.id)
      return res.data
    }
  }
})
