import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface Note {
  id: string
  title: string
  content: string
  type?: string
  createTime: string
  updateTime: string
}

export interface SaveNotePayload {
  title?: string
  content: string
  type?: string
}

export interface NoteFileUploadResult {
  url: string
  fileKey: string
  fileName?: string
  fileSize?: number
  contentType?: string
}

export function listNotes() {
  return apiClient.get<never, ApiResult<Note[]>>('/notes')
}

export function getNote(noteId: string) {
  return apiClient.get<never, ApiResult<Note>>(`/notes/${noteId}`)
}

export function createNote(payload: SaveNotePayload) {
  return apiClient.post<never, ApiResult<Note>>('/notes', payload)
}

export function updateNote(noteId: string, payload: SaveNotePayload) {
  return apiClient.put<never, ApiResult<Note>>(`/notes/${noteId}`, payload)
}

export function deleteNote(noteId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/notes/${noteId}`)
}

export function uploadNoteFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<NoteFileUploadResult>>('/notes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function resolveNoteMediaUrl(key: string) {
  return apiClient.get<never, ApiResult<string>>('/notes/media-url', {
    params: { key }
  })
}
