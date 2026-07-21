import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface LocationPlace {
  name: string
  address: string
  lat?: number
  lon?: number
}

export function searchPlaces(q: string, limit = 8) {
  return apiClient.get<never, ApiResult<LocationPlace[]>>('/location/search', {
    params: { q, limit }
  })
}
