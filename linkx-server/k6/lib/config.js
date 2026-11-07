/**
 * LinkX k6 共享配置（通过环境变量覆盖）
 *
 * K6_BASE_URL         默认 http://127.0.0.1:8080/api
 * K6_WS_URL           默认 ws://127.0.0.1:8081/ws
 * K6_WS_ORIGIN        WebSocket Origin，须在 CORS 白名单内（默认 http://127.0.0.1:5173）
 * K6_USERNAME         压测账号（必填，除 smoke 外）
 * K6_PASSWORD         压测密码
 * K6_CONVERSATION_ID  固定会话 ID（可选；不设则从 sessions 列表取第一个）
 * K6_PROFILE          smoke | load | soak | stress | gradient（默认 load）
 * K6_VUS / K6_DURATION 覆盖档位（设置后优先于 PROFILE 的 VU/时长）
 * K6_WS_SEND          1/true 时 chat-ws 会真正发文本消息（会写库，默认关闭）
 * K6_WS_HOLD_MS       单次 WS 连接保持毫秒（默认 25000）
 */

export const BASE_URL = (__ENV.K6_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '');
export const WS_URL = (__ENV.K6_WS_URL || 'ws://127.0.0.1:8081/ws').replace(/\/$/, '');
export const WS_ORIGIN = __ENV.K6_WS_ORIGIN || 'http://127.0.0.1:5173';

export const USERNAME = __ENV.K6_USERNAME || '';
export const PASSWORD = __ENV.K6_PASSWORD || '';
export const CONVERSATION_ID = __ENV.K6_CONVERSATION_ID || '';

export const PROFILE = (__ENV.K6_PROFILE || 'load').toLowerCase();
export const WS_SEND = /^(1|true|yes)$/i.test(__ENV.K6_WS_SEND || '');
export const WS_HOLD_MS = intEnv('K6_WS_HOLD_MS', 25000);

export const SHARED_DEVICE_ID = 'k6-shared-session';

export const THRESHOLDS_DEFAULT = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<800', 'p(99)<1500'],
};

export const THRESHOLDS_SMOKE = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<2000'],
};

export const THRESHOLDS_STRESS = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<2000', 'p(99)<4000'],
};

export function intEnv(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) ? n : fallback;
}

export function thresholdsForProfile() {
  if (PROFILE === 'smoke') {
    return THRESHOLDS_SMOKE;
  }
  if (PROFILE === 'stress' || PROFILE === 'gradient') {
    return THRESHOLDS_STRESS;
  }
  return THRESHOLDS_DEFAULT;
}

/**
 * 默认档位对应「上线前常规压测」；可用 K6_PROFILE / K6_VUS 覆盖。
 */
export function profileStages(loadDefaults) {
  const overrideVus = intEnv('K6_VUS', 0);
  if (overrideVus > 0) {
    const hold = __ENV.K6_DURATION || '2m';
    const ramp = overrideVus >= 500 ? '1m' : '20s';
    const down = overrideVus >= 500 ? '30s' : '15s';
    return [
      { duration: ramp, target: overrideVus },
      { duration: hold, target: overrideVus },
      { duration: down, target: 0 },
    ];
  }

  switch (PROFILE) {
    case 'smoke':
      return [
        { duration: '10s', target: 2 },
        { duration: '20s', target: 2 },
        { duration: '5s', target: 0 },
      ];
    case 'soak':
      return [
        { duration: '30s', target: 20 },
        { duration: __ENV.K6_DURATION || '10m', target: 20 },
        { duration: '30s', target: 0 },
      ];
    case 'stress':
      return [
        { duration: '1m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: __ENV.K6_DURATION || '2m', target: 100 },
        { duration: '1m', target: 0 },
      ];
    case 'gradient':
      return [
        { duration: '20s', target: 50 },
        { duration: '45s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '45s', target: 100 },
        { duration: '20s', target: 150 },
        { duration: '45s', target: 150 },
        { duration: '20s', target: 200 },
        { duration: '45s', target: 200 },
        { duration: '30s', target: 0 },
      ];
    case 'load':
    default:
      return loadDefaults;
  }
}

export function rampingVus(name, loadDefaults) {
  return {
    [name]: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: profileStages(loadDefaults),
      gracefulRampDown: '10s',
    },
  };
}
