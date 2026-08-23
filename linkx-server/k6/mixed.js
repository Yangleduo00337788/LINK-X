/**
 * 混合场景：公开探针 + 认证读 + 聊天读（接近客户端打开后的请求模式）
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx mixed.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, USERNAME, PASSWORD, THRESHOLDS_DEFAULT } from './lib/config.js';
import {
  apiGet,
  authHeaders,
  isApiOk,
  login,
  parseJson,
  setupLogin,
} from './lib/client.js';

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
      stages: [
        { duration: '15s', target: 15 },
        { duration: '60s', target: 15 },
        { duration: '15s', target: 0 },
      ],
      exec: 'authenticatedFlow',
      startTime: '5s',
      gracefulRampDown: '5s',
    },
  },
  thresholds: THRESHOLDS_DEFAULT,
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
  const tokens = login(USERNAME, PASSWORD, __VU);
  if (!tokens) {
    sleep(1);
    return;
  }
  const headers = authHeaders(tokens.accessToken, __VU);

  apiGet('/user/me', headers, { name: 'user_me' });
  apiGet('/chat/sessions', headers, { name: 'chat_sessions' });
  apiGet('/chat/unread-total', headers, { name: 'chat_unread_total' });
  apiGet('/friend/list', headers, { name: 'friend_list' });

  if (data.conversationId) {
    apiGet(`/chat/sessions/${data.conversationId}/messages?limit=30`, authHeaders(tokens.accessToken, __VU, { fixedDevice: true }), {
      name: 'chat_messages',
    });
  }

  sleep(0.5 + Math.random());
}
