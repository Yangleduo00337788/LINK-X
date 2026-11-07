/**
 * 好友搜索 / 列表 / 申请箱；申请打给预种陌生人（并发会撞唯一约束）。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import { apiGet, apiPost, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('friend_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:friend_search}': ['p(95)<1500'],
  },
});

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const list = apiGet('/friend/list', headers, { name: 'friend_list' });
  check(list, {
    'friend list 200': (r) => r.status === 200,
    'friend list api ok': () => isApiOk(parseJson(list)),
  });

  const search = apiGet('/friend/search?keyword=k6', headers, { name: 'friend_search' });
  check(search, {
    'friend search 200': (r) => r.status === 200,
    'friend search api ok': () => isApiOk(parseJson(search)),
  });

  apiGet('/friend/requests/incoming', headers, { name: 'friend_incoming' });
  apiGet('/friend/requests/outgoing', headers, { name: 'friend_outgoing' });

  if (__ITER % 8 === 0) {
    const req = apiPost(
      '/friend/request',
      { username: data.strangerUsername, message: 'k6' },
      headers,
      { name: 'friend_request' },
    );
    check(req, { 'friend request http 200': (r) => r.status === 200 });
  }

  sleep(0.3 + Math.random() * 0.4);
}
