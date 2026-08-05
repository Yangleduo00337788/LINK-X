import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { computed, ref } from 'vue'
import { createDiscreteApi, darkTheme } from 'naive-ui'
import type { ApiResult } from '@/types/api'
import type { AppTheme } from '@/i18n'
import { tGlobal } from '@/i18n'
import { promptStepUp } from '@/composables/useStepUpGate'
import type { StepUpChallenge } from '@/api/stepUp'
import { useSecurityStore } from '@/stores/security'
import { buildApiSignHeaders, shouldSignRequest } from '@/utils/apiSign'
import {
  API_ENCRYPT_HEADER,
  API_ENCRYPTED_QUERY_HEADER,
  buildEncryptedQueryHeader,
  canonicalQueryString,
  decryptToBytes,
  decryptUtf8FromBase64,
  encryptUtf8ToBase64,
  isEncryptedResponse,
  shouldEncryptRequest,
  wrapEncryptedBody,
} from '@/utils/apiEncrypt'
import { getDeviceHeaders } from '@/utils/deviceId'
import type { AdminLoginResult } from '@/types/api'

const discreteTheme = ref<AppTheme>('dark')

export function setDiscreteTheme(theme: AppTheme) {
  discreteTheme.value = theme
}

const { message } = createDiscreteApi(['message'], {
  configProviderProps: computed(() => ({
    theme: discreteTheme.value === 'dark' ? darkTheme : null,
  })),
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
  useSecurityStore().clearApiSignKey()
}

/** 页面刷新后若签名密钥丢失，用 refresh 补发 */
export async function ensureApiSignKey(): Promise<void> {
  const security = useSecurityStore()
  if (!security.apiSignEnabled || security.apiSignKey || !getRefreshToken()) {
    return
  }
  await refreshAccessToken()
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
    const { data } = await axios.post<ApiResult<AdminLoginResult>>(
      `${import.meta.env.VITE_API_BASE_URL || '/api'}/admin/auth/refresh`,
      { refreshToken },
      { headers: getDeviceHeaders() }
    )
    if (data.code === 200 && data.data?.accessToken) {
      setTokens(data.data.accessToken, data.data.refreshToken || refreshToken)
      if (data.data.apiSignKey) {
        useSecurityStore().setApiSignKey(data.data.apiSignKey)
      }
      return data.data.accessToken
    }
  } catch {
    /* fall through */
  }
  clearTokens()
  useSecurityStore().clearApiSignKey()
  return null
}

type RetryConfig = AxiosRequestConfig & {
  _retry?: boolean
  _stepUpRetry?: boolean
  /** 加密前的原始 JSON 请求体，供 step-up / 401 重试时重新加签加密 */
  _plainBody?: string
  /** 加密前的原始查询参数 */
  _plainParams?: Record<string, unknown>
}

request.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const meta = config as RetryConfig
  if (meta._plainBody != null) {
    config.data = meta._plainBody
  }
  if (meta._plainParams != null) {
    config.params = meta._plainParams
  }

  Object.assign(config.headers, getDeviceHeaders())

  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  const security = useSecurityStore()
  const url = config.url || ''
  const isFormData = typeof FormData !== 'undefined' && config.data instanceof FormData

  if (
    !isFormData &&
    config.data != null &&
    typeof config.data !== 'string'
  ) {
    config.data = JSON.stringify(config.data)
    config.headers['Content-Type'] = 'application/json;charset=UTF-8'
  }

  let bodyText = ''
  if (config.data != null && typeof config.data === 'string') {
    bodyText = config.data
  } else if (config.data != null && !isFormData) {
    bodyText = JSON.stringify(config.data)
  }

  const useEncrypt =
    security.apiEncryptEnabled &&
    token &&
    security.apiSignKey &&
    shouldEncryptRequest(url) &&
    !isFormData

  let querySignMaterial = ''
  if (useEncrypt) {
    if (meta._plainParams == null) {
      meta._plainParams = (config.params as Record<string, unknown> | undefined) || {}
    }
    const encryptedQuery = await buildEncryptedQueryHeader(
      security.apiSignKey,
      meta._plainParams
    )
    querySignMaterial = encryptedQuery
    config.params = undefined
    config.headers[API_ENCRYPT_HEADER] = '1'
    config.headers[API_ENCRYPTED_QUERY_HEADER] = encryptedQuery
  } else if (config.params && typeof config.params === 'object') {
    querySignMaterial = canonicalQueryString(config.params as Record<string, unknown>)
  }

  const needsEncryptBody = useEncrypt && bodyText.length > 0

  if (needsEncryptBody) {
    if (meta._plainBody == null) {
      meta._plainBody = bodyText
    }
    const encrypted = await encryptUtf8ToBase64(security.apiSignKey, meta._plainBody)
    bodyText = wrapEncryptedBody(encrypted)
    config.data = bodyText
    config.headers['Content-Type'] = 'application/json;charset=UTF-8'
  } else if (useEncrypt) {
    config.headers[API_ENCRYPT_HEADER] = '1'
  }

  const needsSign =
    security.apiSignEnabled &&
    token &&
    security.apiSignKey &&
    shouldSignRequest(url) &&
    !isFormData

  if (needsSign) {
    const signHeaders = await buildApiSignHeaders(
      security.apiSignKey,
      config.method || 'GET',
      url,
      bodyText,
      querySignMaterial
    )
    Object.assign(config.headers, signHeaders)
  }

  return config
})

async function handleStepUp(original: RetryConfig, challenge: StepUpChallenge) {
  if (original._stepUpRetry) {
    return Promise.reject(new Error(tGlobal('stepUp.failed')))
  }
  const stepUpToken = await promptStepUp(challenge)
  if (!stepUpToken) {
    return Promise.reject(new Error(tGlobal('stepUp.cancelled')))
  }
  original._stepUpRetry = true
  original.headers = {
    ...original.headers,
    'X-Step-Up-Token': stepUpToken,
  }
  return request(original)
}

request.interceptors.response.use(
  async (response) => {
    const security = useSecurityStore()
    const headers = response.headers as Record<string, unknown>
    if (
      security.apiEncryptEnabled &&
      security.apiSignKey &&
      isEncryptedResponse(headers) &&
      response.config.responseType !== 'blob'
    ) {
      const payload = response.data as ApiResult
      if (payload && typeof payload.data === 'string') {
        try {
          const plain = await decryptUtf8FromBase64(security.apiSignKey, payload.data)
          payload.data = JSON.parse(plain) as unknown
          response.data = payload
        } catch {
          message.error(tGlobal('common.requestFailed'))
          return Promise.reject(new Error(tGlobal('common.requestFailed')))
        }
      }
    }

    const payload = response.data as ApiResult
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      if (payload.code === 428 && payload.data) {
        return handleStepUp(response.config as RetryConfig, payload.data as StepUpChallenge)
      }
      const fallback = tGlobal('common.requestFailed')
      message.error(payload.message || fallback)
      return Promise.reject(new Error(payload.message || fallback))
    }
    return response
  },
  async (error: AxiosError<ApiResult | string>) => {
    const original = error.config as RetryConfig | undefined
    const status = error.response?.status
    const payload = error.response?.data as ApiResult | undefined

    if (status === 428 && original && payload?.data) {
      return handleStepUp(original, payload.data as StepUpChallenge)
    }

    if (
      status === 401 &&
      original &&
      !original._retry &&
      !original.url?.includes('/admin/auth/login')
    ) {
      original._retry = true
      refreshing =
        refreshing ||
        refreshAccessToken().finally(() => {
          refreshing = null
        })
      const token = await refreshing
      if (token) {
        original.headers = { ...original.headers, Authorization: `Bearer ${token}` }
        return request(original)
      }
      if (!window.location.pathname.includes('/login')) {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    }
    const msg = payload?.message || error.message || tGlobal('common.networkError')
    if (status !== 401 && status !== 428) message.error(msg)
    return Promise.reject(error)
  }
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

/** 下载 CSV / 二进制附件（加密开启时响应为 JSON 密文包装） */
export async function downloadFile(
  url: string,
  params?: Record<string, unknown>,
  fallbackName = 'export.csv'
) {
  const security = useSecurityStore()
  const response = await request.get(url, {
    params,
    responseType: 'blob',
    transformResponse: [(data) => data],
  })
  const headers = response.headers as Record<string, unknown>
  let blob = response.data as Blob

  if (
    security.apiEncryptEnabled &&
    security.apiSignKey &&
    isEncryptedResponse(headers)
  ) {
    const text = await blob.text()
    const payload = JSON.parse(text) as ApiResult<string>
    const bytes = await decryptToBytes(security.apiSignKey, payload.data || '')
    blob = new Blob([bytes], { type: 'text/csv;charset=utf-8' })
  } else if (blob.type && blob.type.includes('application/json')) {
    const text = await blob.text()
    try {
      const payload = JSON.parse(text) as ApiResult
      const msg = payload.message || tGlobal('common.requestFailed')
      message.error(msg)
      throw new Error(msg)
    } catch (e) {
      if (e instanceof Error && e.message !== tGlobal('common.requestFailed')) throw e
      message.error(tGlobal('common.requestFailed'))
      throw e
    }
  }
  const disposition = response.headers['content-disposition'] as string | undefined
  let filename = fallbackName
  if (disposition) {
    const m = /filename="?([^";]+)"?/i.exec(disposition)
    if (m?.[1]) filename = decodeURIComponent(m[1])
  }
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(href)
}

export default request
