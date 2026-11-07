/**
 * 注册写库。验证码由 prep 写入 Redis，不打真实 SMTP。
 * 用户名 k6w{vu}x{iter}，验证码由 prep-k6-write.py 按同一规则写入 Redis。
 */
import { check, sleep } from 'k6';
import { apiPost, isApiOk, parseJson } from './lib/client.js';
import { rampingVus, thresholdsForProfile } from './lib/config.js';

export const options = {
  scenarios: rampingVus('register_load', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:auth_register}': ['p(95)<4000'],
  },
};

export default function () {
  const username = `k6w${__VU}x${__ITER}`;
  const email = `${username}@k6.invalid`;
  const res = apiPost(
    '/auth/register',
    {
      username,
      password: 'K6test123',
      nickname: `k6w${__VU}`,
      email,
      emailCode: '123456',
    },
    undefined,
    { name: 'auth_register' },
  );
  const body = parseJson(res);
  check(res, {
    'register status 200': (r) => r.status === 200,
    'register api ok': () => isApiOk(body),
  });
  sleep(0.3 + Math.random() * 0.5);
}
