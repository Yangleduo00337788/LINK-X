import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { createDiscreteApi, darkTheme } from 'naive-ui'
import type { ApiResult } from '@/types/api'

const { message } = createDiscreteApi(['message'], {
  configProviderProps: { theme: darkTheme },
})

const TOKEN_KEY = 'linkx_admin_access_token'
const REFRESH_KEY = 'linkx_admin_refresh_token'

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null
  try {
    const { data } = await axios.post<ApiResult<{ accessToken: string; refreshToken: string }>>(
      `${import.meta.env.VITE_API_BASE_URL || '/api'}/admin/auth/refresh`,
      { refreshToken },
    )
    if (data.code === 200 && data.data?.accessToken) {
      setTokens(data.data.accessToken, data.data.refreshToken || refreshToken)
      return data.data.accessToken
    }
  } catch {
    /* fall through */
  }
  clearTokens()
  return null
}

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      message.error(payload.message || '请求失败')
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return response
  },
  async (error: AxiosError<ApiResult>) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    if (status === 401 && original && !original._retry && !original.url?.includes('/admin/auth/login')) {
      original._retry = true
      refreshing = refreshing || refreshAccessToken().finally(() => { refreshing = null })
      const token = await refreshing
      if (token) {
        original.headers = { ...original.headers, Authorization: `Bearer ${token}` }
        return request(original)
      }
      if (!window.location.pathname.includes('/login')) {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    if (status !== 401) message.error(msg)
    return Promise.reject(error)
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>) {
  const { data } = await request.get<ApiResult<T>>(url, { params })
  return data.data
}

export async function post<T>(url: string, body?: unknown) {
  const { data } = await request.post<ApiResult<T>>(url, body)
  return data.data
}

export async function put<T>(url: string, body?: unknown) {
  const { data } = await request.put<ApiResult<T>>(url, body)
  return data.data
}

export async function del<T>(url: string) {
  const { data } = await request.delete<ApiResult<T>>(url)
  return data.data
}

export default request
