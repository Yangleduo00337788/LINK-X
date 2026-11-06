/**
 * 混合场景：公开探针 + 认证读（接近客户端打开后的请求模式）
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx mixed.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, USERNAME, PASSWORD, thresholdsForProfile, profileStages } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: {
    public_probe: {
      executor: 'constant-vus',
      vus: 2,
      duration: '90s',
      exec: 'publicProbe',
      startTime: '0s',
    },
    authenticated: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: profileStages([
        { duration: '15s', target: 15 },
        { duration: '60s', target: 15 },
        { duration: '15s', target: 0 },
      ]),
      exec: 'authenticatedFlow',
      startTime: '5s',
      gracefulRampDown: '5s',
    },
  },
  thresholds: thresholdsForProfile(),
};

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export function publicProbe() {
  const health = http.get(`${BASE_URL}/health`, { tags: { name: 'health' } });
  check(health, { 'health ok': (r) => r.status === 200 });

  const live = http.get(`${BASE_URL}/health/live`, { tags: { name: 'health_live' } });
  check(live, { 'live ok': (r) => r.status === 200 });

  sleep(2);
}

export function authenticatedFlow(data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const me = apiGet('/user/me', headers, { name: 'user_me' });
  check(me, { 'user/me ok': (r) => r.status === 200 && isApiOk(parseJson(r)) });

  const sessions = apiGet('/chat/sessions', headers, { name: 'chat_sessions' });
  check(sessions, { 'sessions ok': (r) => r.status === 200 && isApiOk(parseJson(r)) });

  apiGet('/chat/unread-total', headers, { name: 'chat_unread_total' });
  apiGet('/friend/list', headers, { name: 'friend_list' });
  apiGet('/moments?limit=10', headers, { name: 'moments_list' });

  if (data.conversationId) {
    apiGet(`/chat/sessions/${data.conversationId}/messages?limit=30`, headers, {
      name: 'chat_messages',
    });
  }

  sleep(0.5 + Math.random());
}
