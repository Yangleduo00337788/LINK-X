/**
 * LinkX 热路径压测：login / refresh / chat / friend / group / health
 *
 * k6 run -e BASE_URL=http://127.0.0.1:8080/api -e USER=demo -e PASS=... scenarios/hot-path.js
 * CI smoke: k6 run -e VUS=2 -e DURATION=30s -e BASE_URL=... scenarios/hot-path.js
 */
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const failRate = new Rate('hot_path_failed')
const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const VUS = Number(__ENV.VUS || 10)
const DURATION = __ENV.DURATION || '1m'

// 压测下 429 限流属预期；仅将网络失败/5xx 计入 http_req_failed
http.setResponseCallback(http.expectedStatuses({ min: 100, max: 499 }))

export const options = {
  scenarios: {
    hot: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    hot_path_failed: ['rate<0.01'],
  },
}

function login() {
  const user = __ENV.USER || `load_${__VU}`
  const pass = __ENV.PASS || 'Test1234abcd'
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username: user, password: pass }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } },
  )
  let token = ''
  let refresh = ''
  try {
    const body = res.json()
    token = body?.data?.accessToken || ''
    refresh = body?.data?.refreshToken || ''
  } catch (_) {
    /* ignore */
  }
  const ok = res.status === 200 && !!token
  failRate.add(!ok)
  check(res, { 'login 200+token': () => ok })
  return { token, refresh }
}

export default function () {
  group('public', () => {
    const h = http.get(`${BASE_URL}/health`, { tags: { name: 'health' } })
    failRate.add(h.status !== 200)
    check(h, { 'health 200': (r) => r.status === 200 })
  })

  const session = login()
  if (!session.token) {
    sleep(1)
    return
  }

  const auth = { Authorization: `Bearer ${session.token}`, Accept: 'application/json' }

  group('inbox', () => {
    const s = http.get(`${BASE_URL}/chat/sessions`, { headers: auth, tags: { name: 'chat_sessions' } })
    failRate.add(s.status >= 500 || s.status === 0)
    check(s, { 'sessions <500': (r) => r.status > 0 && r.status < 500 })

    const f = http.get(`${BASE_URL}/friend/list`, { headers: auth, tags: { name: 'friend_list' } })
    failRate.add(f.status >= 500 || f.status === 0)

    const g = http.get(`${BASE_URL}/group/list`, { headers: auth, tags: { name: 'group_list' } })
    failRate.add(g.status >= 500 || g.status === 0)
  })

  if (session.refresh) {
    group('refresh', () => {
      const r = http.post(
        `${BASE_URL}/auth/refresh`,
        JSON.stringify({ refreshToken: session.refresh }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_refresh' } },
      )
      failRate.add(r.status >= 500 || r.status === 0)
    })
  }

  sleep(0.2)
}
