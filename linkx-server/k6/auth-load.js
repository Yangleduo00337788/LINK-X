/**
 * 认证链路压测：登录 → 用户信息 → 未读数
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx auth-load.js
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, THRESHOLDS_DEFAULT } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, login, parseJson } from './lib/client.js';

export const options = {
  scenarios: {
    auth_burst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 10 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    ...THRESHOLDS_DEFAULT,
    'http_req_duration{name:auth_login}': ['p(95)<1200'],
  },
};

export default function () {
  const tokens = login(USERNAME, PASSWORD, __VU);
  if (!tokens) {
    sleep(1);
    return;
  }
  const headers = authHeaders(tokens.accessToken, __VU);

  const me = apiGet('/user/me', headers, { name: 'user_me' });
  check(me, {
    'user/me status 200': (r) => r.status === 200,
    'user/me api ok': () => isApiOk(parseJson(me)),
  });

  const unread = apiGet('/chat/unread-total', headers, { name: 'chat_unread_total' });
  check(unread, {
    'unread-total status 200': (r) => r.status === 200,
    'unread-total api ok': () => isApiOk(parseJson(unread)),
  });

  sleep(0.5 + Math.random() * 0.5);
}
