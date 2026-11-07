/**
 * 朋友圈发纯文本。500 VU 会写库，仅 k6 档使用。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import { apiPost, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('moments_publish', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:moments_publish}': ['p(95)<4000'],
  },
});

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const res = apiPost(
    '/moments',
    { content: `k6 pub vu=${__VU} iter=${__ITER} t=${Date.now()}`, visibility: 0 },
    headers,
    { name: 'moments_publish' },
  );
  check(res, {
    'moments publish 200': (r) => r.status === 200,
    'moments publish api ok': () => isApiOk(parseJson(res)),
  });
  sleep(0.4 + Math.random() * 0.4);
}
