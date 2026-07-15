import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface Note {
  id: string
  title: string
  content: string
  createTime: string
  updateTime: string
}

export interface SaveNotePayload {
  title?: string
  content: string
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
