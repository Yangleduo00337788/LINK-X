/**
 * 公开接口冒烟测试（无需登录）
 *
 * k6 run smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THRESHOLDS_SMOKE } from './lib/config.js';
import { isApiOk, parseJson } from './lib/client.js';

export const options = {
  vus: 1,
  duration: '10s',
  thresholds: THRESHOLDS_SMOKE,
};

export default function () {
  const health = http.get(`${BASE_URL}/health`, { tags: { name: 'health' } });
  const healthBody = parseJson(health);
  check(health, {
    'health status 200': (r) => r.status === 200,
    'health api ok': () => isApiOk(healthBody),
  });

  const config = http.get(`${BASE_URL}/auth/config`, { tags: { name: 'auth_config' } });
  const configBody = parseJson(config);
  check(config, {
    'auth config status 200': (r) => r.status === 200,
    'auth config api ok': () => isApiOk(configBody),
  });

  sleep(1);
}
