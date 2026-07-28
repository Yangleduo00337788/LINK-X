import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResult, TokenData } from '../types/auth'
import { parseJsonPreservingIds } from '../utils/parseJson'
import { clearTokens, getRefreshToken, getToken, isWebEnvironment, saveTokenPair } from '../utils/tokenStorage'
import { getDeviceName, getDeviceType, getOrCreateDeviceId } from '../utils/deviceId'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const apiClient = axios.create({
  baseURL,
  timeout: 10000,
  transformResponse: [
    data => {
      if (typeof data !== 'string' || !data) return data
      try {
        return parseJsonPreservingIds(data)
      } catch {
        return data
      }
    }
  ]
})

// Web 环境：携带 HttpOnly Cookie 完成鉴权（token 由后端 Cookie 管理，JS 不可读）；
// Electron 环境：走 Authorization Header + safeStorage，无需携带 Cookie，保持 withCredentials=false 避免跨域凭证问题。
apiClient.defaults.withCredentials = isWebEnvironment()

let refreshing = false
let refreshQueue: Array<(token: string | null) => void> = []

function isUnauthorized(error: AxiosError<ApiResult<unknown>>): boolean {
  return error.response?.status === 401 || error.response?.data?.code === 401
}

function applyDeviceHeaders(headers: InternalAxiosRequestConfig['headers']) {
  if (!headers) return
  headers['X-Device-Id'] = getOrCreateDeviceId()
  headers['X-Device-Name'] = getDeviceName()
  headers['X-Device-Type'] = getDeviceType()
}

async function redirectToLogin() {
  const { useAppStore } = await import('../stores/app')
  const { resetSessionUi } = await import('../utils/resetSessionUi')
  const appStore = useAppStore()
  resetSessionUi()
  await clearTokens()
  appStore.$patch({
    isLoggedIn: false,
    isLocked: false,
    isLoading: false,
    authInitializing: false
  })
}

async function processUnauthorized(config?: InternalAxiosRequestConfig) {
  const url = config?.url ?? ''
  if (url.includes('/auth/refresh') || url.includes('/auth/login') || url.includes('/auth/register')) {
    await clearTokens()
    return Promise.reject(new Error('未授权'))
  }

  if (refreshing) {
    return new Promise((resolve, reject) => {
      refreshQueue.push(token => {
        if (!token || !config) {
          reject(new Error('登录已过期'))
          return
        }
        // Web 环境依赖 Cookie 鉴权，不设 Authorization Header；Electron 设新 access token
        if (!isWebEnvironment()) {
          config.headers.Authorization = `Bearer ${token}`
        }
        applyDeviceHeaders(config.headers)
        resolve(apiClient(config))
      })
    })
  }

  refreshing = true
  try {
    const isWeb = isWebEnvironment()
    const refresh = await getRefreshToken()
    // Web 环境 refresh token 在 HttpOnly Cookie 中（本地不可读），仍尝试刷新；
    // Electron 环境无本地 refresh token 则直接登出。
    if (!refresh && !isWeb) {
      await redirectToLogin()
      return Promise.reject(new Error('登录已过期'))
    }

    const { data: res } = await axios.post<ApiResult<TokenData>>(
      `${baseURL}/auth/refresh`,
      { refreshToken: refresh },
      {
        timeout: 10000,
        // Web 环境携带 Cookie（refresh token 在 HttpOnly Cookie 中）；Electron 不需要
        withCredentials: isWeb,
        headers: {
          'X-Device-Id': getOrCreateDeviceId(),
          'X-Device-Name': getDeviceName(),
          'X-Device-Type': getDeviceType()
        }
      }
    )
    if (res.code !== 200 || !res.data) {
      await redirectToLogin()
      return Promise.reject(new Error(res.message || '登录已过期'))
    }

    // Web 环境 saveTokenPair 为 no-op（Cookie 由后端 Set-Cookie 管理）；Electron 落盘 safeStorage
    await saveTokenPair(res.data.accessToken, res.data.refreshToken)
    refreshQueue.forEach(cb => cb(res.data.accessToken))
    refreshQueue = []

    if (config) {
      // Web 环境依赖 Cookie 鉴权，不设 Authorization Header；Electron 设新 access token
      if (!isWeb) {
        config.headers.Authorization = `Bearer ${res.data.accessToken}`
      }
      applyDeviceHeaders(config.headers)
      return apiClient(config)
    }
    return res
  } catch (error) {
    refreshQueue.forEach(cb => cb(null))
    refreshQueue = []
    await redirectToLogin()
    return Promise.reject(error)
  } finally {
    refreshing = false
  }
}

apiClient.interceptors.request.use(async config => {
  const token = await getToken('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  applyDeviceHeaders(config.headers)
  return config
})

apiClient.interceptors.response.use(
  response => response.data as ApiResult<unknown>,
  async (error: AxiosError<ApiResult<unknown>>) => {
    const config = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    if (isUnauthorized(error) && config && !config._retry) {
      config._retry = true
      return processUnauthorized(config)
    }
    return Promise.reject(error)
  }
)

export { clearTokens, saveTokenPair }
