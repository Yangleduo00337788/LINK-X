import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResult, TokenData } from '../types/auth'
import { clearTokens, getRefreshToken, getToken, saveTokenPair } from '../utils/tokenStorage'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const apiClient = axios.create({
  baseURL,
  timeout: 10000
})

let refreshing = false
let refreshQueue: Array<(token: string | null) => void> = []

function isUnauthorized(error: AxiosError<ApiResult<unknown>>): boolean {
  return error.response?.status === 401 || error.response?.data?.code === 401
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
        config.headers.Authorization = `Bearer ${token}`
        resolve(apiClient(config))
      })
    })
  }

  refreshing = true
  try {
    const refresh = await getRefreshToken()
    if (!refresh) {
      await redirectToLogin()
      return Promise.reject(new Error('登录已过期'))
    }

    const { data: res } = await axios.post<ApiResult<TokenData>>(
      `${baseURL}/auth/refresh`,
      { refreshToken: refresh },
      { timeout: 10000 }
    )
    if (res.code !== 200 || !res.data) {
      await redirectToLogin()
      return Promise.reject(new Error(res.message || '登录已过期'))
    }

    await saveTokenPair(res.data.accessToken, res.data.refreshToken)
    refreshQueue.forEach(cb => cb(res.data.accessToken))
    refreshQueue = []

    if (config) {
      config.headers.Authorization = `Bearer ${res.data.accessToken}`
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
  return config
})

apiClient.interceptors.response.use(
  // 拦截器解包 response.data，调用方直接获得 ApiResult
  response => response.data as ApiResult<unknown> as never,
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
