/**
 * LinkX 全 REST API 压测（OpenAPI 驱动）
 *
 * 前置:
 *   1. 启动 linkx-server
 *   2. ./scripts/export-openapi.sh 生成 endpoints.json（或复制 endpoints.sample.json）
 *   3. k6 run -e BASE_URL=http://127.0.0.1:8080/api -e USER=... -e PASS=... full-api.js
 *
 * 环境变量:
 *   BASE_URL          默认 http://127.0.0.1:8080/api
 *   USER / PASS       登录账号（setup 取 JWT）
 *   INCLUDE_MUTATING  1=包含写操作（默认 0，仅 GET/HEAD）
 *   MAX_ENDPOINTS     限制扫描端点数（默认全部）
 *   VUS / DURATION    覆盖场景（CI smoke: VUS=1 DURATION=30s）
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { SharedArray } from 'k6/data'
import { Rate, Trend } from 'k6/metrics'
import { fillPath, filterCatalog } from './lib/guards.js'

const failRate = new Rate('http_req_custom_failed')
const latency = new Trend('linkx_api_latency', true)

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const INCLUDE_MUTATING = __ENV.INCLUDE_MUTATING === '1'
const MAX_ENDPOINTS = Number(__ENV.MAX_ENDPOINTS || 0)
const VUS = Number(__ENV.VUS || 5)
const DURATION = __ENV.DURATION || '1m'

// 4xx（含无权限/缺参）在全扫中常见；仅 5xx/网络失败计入 http_req_failed
http.setResponseCallback(http.expectedStatuses({ min: 100, max: 499 }))

const catalog = new SharedArray('endpoints', () => {
  let raw
  try {
    raw = open('./endpoints.json')
  } catch (_) {
    raw = open('./endpoints.sample.json')
  }
  const parsed = JSON.parse(raw)
  let list = filterCatalog(parsed.endpoints || [], { includeMutating: INCLUDE_MUTATING })
  if (MAX_ENDPOINTS > 0) {
    list = list.slice(0, MAX_ENDPOINTS)
  }
  return list
})

export const options = {
  scenarios: {
    full_api: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
    http_req_custom_failed: ['rate<0.05'],
  },
}

export function setup() {
  const user = __ENV.USER || ''
  const pass = __ENV.PASS || ''
  if (!user || !pass) {
    console.warn('USER/PASS not set — authenticated endpoints will likely 401')
    return { token: '' }
  }
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username: user, password: pass }),
    { headers: { 'Content-Type': 'application/json' } },
  )
  let token = ''
  try {
    const body = res.json()
    token = (body && body.data && body.data.accessToken) || ''
  } catch (_) {
    /* ignore */
  }
  check(res, { 'login ok': (r) => r.status === 200 && !!token })
  return { token }
}

export default function (data) {
  if (!catalog.length) {
    console.error('No endpoints loaded')
    return
  }
  const ep = catalog[Math.floor(Math.random() * catalog.length)]
  const url = `${BASE_URL}${fillPath(ep.path)}`
  const headers = { Accept: 'application/json' }
  if (data.token && ep.security !== false) {
    headers.Authorization = `Bearer ${data.token}`
  }

  let res
  const start = Date.now()
  if (ep.method === 'GET' || ep.method === 'HEAD') {
    res = http.request(ep.method, url, null, { headers, tags: { name: ep.operationId } })
  } else {
    // 写操作：发最小 JSON，接受 4xx 业务错误，只统计 5xx / 网络失败
    res = http.request(ep.method, url, '{}', {
      headers: { ...headers, 'Content-Type': 'application/json' },
      tags: { name: ep.operationId },
    })
  }
  latency.add(Date.now() - start)

  const ok = res.status > 0 && res.status < 500
  failRate.add(!ok)
  check(res, {
    'no server error': (r) => r.status > 0 && r.status < 500,
  })
  sleep(0.05)
}
