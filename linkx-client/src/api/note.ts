import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface Note {
  id: string
  title: string
  content: string
  /** 类型：note(普通笔记) / image(图片收藏) / link(链接收藏) / file(文件收藏) */
  type?: string
  createTime: string
  updateTime: string
}

export interface SaveNotePayload {
  title?: string
  content: string
  /** 类型：note / image / link / file */
  type?: string
}

/**
 * 获取笔记列表
 */
export function listNotes() {
  return apiClient.get<never, ApiResult<Note[]>>('/notes')
}

/**
 * 获取单条笔记
 */
export function getNote(noteId: string) {
  return apiClient.get<never, ApiResult<Note>>(`/notes/${noteId}`)
}

/**
 * 创建笔记
 */
export function createNote(payload: SaveNotePayload) {
  return apiClient.post<never, ApiResult<Note>>('/notes', payload)
}

/**
 * 更新笔记
 */
export function updateNote(noteId: string, payload: SaveNotePayload) {
  return apiClient.put<never, ApiResult<Note>>(`/notes/${noteId}`, payload)
}

/**
 * 删除笔记
 */
export function deleteNote(noteId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/notes/${noteId}`)
}
