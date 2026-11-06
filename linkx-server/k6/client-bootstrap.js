/**
 * 客户端冷启动读链路：打开 App 后的一批 REST 请求。
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx client-bootstrap.js
 * k6 run -e K6_PROFILE=soak -e K6_USERNAME=xxx -e K6_PASSWORD=xxx client-bootstrap.js
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, rampingVus, thresholdsForProfile } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: rampingVus('client_bootstrap', [
    { duration: '20s', target: 20 },
    { duration: '2m', target: 20 },
    { duration: '20s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:chat_sessions}': ['p(95)<800'],
    'http_req_duration{name:moments_list}': ['p(95)<1200'],
  },
};

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const homepage = apiGet('/app/homepage', headers, { name: 'app_homepage' });
  check(homepage, {
    'homepage status 200': (r) => r.status === 200,
    'homepage api ok': () => isApiOk(parseJson(homepage)),
  });

  const version = apiGet('/app/version?platform=windows&channel=stable', headers, { name: 'app_version' });
  check(version, {
    'version status 200': (r) => r.status === 200,
    'version api ok': () => isApiOk(parseJson(version)),
  });

  const me = apiGet('/user/me', headers, { name: 'user_me' });
  check(me, {
    'user/me status 200': (r) => r.status === 200,
    'user/me api ok': () => isApiOk(parseJson(me)),
  });

  const sessions = apiGet('/chat/sessions', headers, { name: 'chat_sessions' });
  check(sessions, {
    'sessions status 200': (r) => r.status === 200,
    'sessions api ok': () => isApiOk(parseJson(sessions)),
  });

  const unread = apiGet('/chat/unread-total', headers, { name: 'chat_unread_total' });
  check(unread, {
    'unread-total status 200': (r) => r.status === 200,
    'unread-total api ok': () => isApiOk(parseJson(unread)),
  });

  const friends = apiGet('/friend/list', headers, { name: 'friend_list' });
  check(friends, {
    'friend list status 200': (r) => r.status === 200,
    'friend list api ok': () => isApiOk(parseJson(friends)),
  });

  const notices = apiGet('/notifications/unread', headers, { name: 'notifications_unread' });
  check(notices, {
    'notifications status 200': (r) => r.status === 200,
    'notifications api ok': () => isApiOk(parseJson(notices)),
  });

  const moments = apiGet('/moments?limit=20', headers, { name: 'moments_list' });
  check(moments, {
    'moments status 200': (r) => r.status === 200,
    'moments api ok': () => isApiOk(parseJson(moments)),
  });

  const videos = apiGet('/short-video?limit=10', headers, { name: 'short_video_list' });
  check(videos, {
    'short-video status 200': (r) => r.status === 200,
    'short-video api ok': () => isApiOk(parseJson(videos)),
  });

  sleep(0.4 + Math.random() * 0.8);
}
