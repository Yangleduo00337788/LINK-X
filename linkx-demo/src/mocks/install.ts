/**
 * 作者：yangleduo
 */
import axios, {
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiEnvelope, DemoApp } from './types'
import { ctxFromConfig } from './utils'
import { resolveAdminMock } from './admin/handlers'
import { resolveClientMock } from './client/handlers'
import { installClientFetchMock } from './client/fetch-mock'
import { installClientMediaUrlGuard } from './client/media-guard'

const patchedApps = new Set<DemoApp>()

async function resolveMock(config: InternalAxiosRequestConfig, app: DemoApp): Promise<ApiEnvelope> {
  const ctx = ctxFromConfig(config, app)
  const payload =
    app === 'admin' ? await resolveAdminMock(ctx) : await resolveClientMock(ctx)
  return payload ?? { code: 200, message: '', data: null }
}

function buildMockAdapter(app: DemoApp): AxiosAdapter {
  return async config => {
    const url = String(config.url || '')
    if (config.responseType === 'blob' && /\/media\/|\/file(?:\?|$)|\/content(?:\?|$)/.test(url)) {
      const origin = typeof window !== 'undefined' ? window.location.origin : ''
      const href = /^https?:\/\//i.test(url)
        ? url
        : `${origin}${url.startsWith('/') ? url : `/${url}`}`
      const local = href
        .replace(/^https?:\/\/127\.0\.0\.1:8080/i, origin)
        .replace(/^https?:\/\/localhost:8080/i, origin)
      const res = await fetch(local)
      const blob = await res.blob()
      return {
        data: blob,
        status: 200,
        statusText: 'OK',
        headers: { 'content-type': res.headers.get('content-type') || 'image/jpeg' },
        config,
        request: {},
      }
    }
    if (config.responseType === 'blob' || /\/download(?:\?|$)/.test(url)) {
      const csv = '\uFEFFid,name,status\n1,demo,ok\n'
      return {
        data: new Blob([csv], { type: 'text/csv;charset=utf-8' }),
        status: 200,
        statusText: 'OK',
        headers: {
          'content-type': 'text/csv;charset=utf-8',
          'content-disposition': 'attachment; filename="export.csv"',
        },
        config,
        request: {},
      }
    }
    const data = await resolveMock(config, app)
    const response: AxiosResponse<ApiEnvelope> = {
      data,
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      config,
      request: {},
    }
    return response
  }
}

function attachMockAdapter(instance: AxiosInstance, app: DemoApp) {
  instance.defaults.adapter = buildMockAdapter(app)
}

export function installHttpMocks(app: DemoApp) {
  if (patchedApps.has(app)) return
  patchedApps.add(app)

  attachMockAdapter(axios, app)

  const originalCreate = axios.create.bind(axios)
  axios.create = function createWithDemo(...args) {
    const instance = originalCreate(...args)
    attachMockAdapter(instance, app)
    return instance
  }

  if (app === 'client') {
    installClientFetchMock()
    installClientMediaUrlGuard()
  }
}
