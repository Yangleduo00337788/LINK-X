import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SHARED_DEVICE_ID, WS_ORIGIN } from './config.js';

const JSON_HEADERS = {
  'Content-Type': 'application/json',
  Accept: 'application/json',
};

export function deviceHeaders(vuId = 1) {
  const deviceId = typeof vuId === 'string' && vuId.startsWith('k6-') ? vuId : `k6-vu-${vuId}`;
  return {
    'X-Device-Id': deviceId,
    'X-Device-Name': 'k6-load-test',
    'X-Device-Type': 'desktop',
  };
}

export function authHeaders(token, vuId = 1, { fixedDevice = false } = {}) {
  const deviceKey = fixedDevice ? SHARED_DEVICE_ID : vuId;
  return {
    ...JSON_HEADERS,
    ...deviceHeaders(deviceKey),
    Authorization: `Bearer ${token}`,
  };
}

export function wsConnectParams(token) {
  return {
    headers: {
      Origin: WS_ORIGIN,
      'Sec-WebSocket-Protocol': `linkx-access-token, ${token}`,
    },
    tags: { name: 'im_ws' },
  };
}

export function wsUrlWithDevice(wsBase, deviceId = SHARED_DEVICE_ID) {
  const encoded = encodeURIComponent(deviceId);
  return `${wsBase}?deviceId=${encoded}&deviceName=k6-load-test&deviceType=desktop`;
}

export function parseJson(res) {
  try {
    return res.json();
  } catch {
    return null;
  }
}

export function isApiOk(body) {
  return body && body.code === 200;
}

/**
 * 雪花 ID 必须保持十进制字符串。JS Number 会丢掉超过 2^53-1 的低位。
 */
export function snowflakeId(raw) {
  if (raw == null || raw === '') {
    return '';
  }
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (/^\d+$/.test(trimmed)) {
      return trimmed;
    }
  }
  return '';
}

export function extractSnowflake(bodyText, key = 'id') {
  if (!bodyText) {
    return '';
  }
  const re = new RegExp(`"${key}"\\s*:\\s*"?(\\d{15,})"?`);
  const m = String(bodyText).match(re);
  return m ? m[1] : '';
}

export function apiPost(path, payload, headers = JSON_HEADERS, tags = {}) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(payload), {
    headers,
    tags,
  });
}

export function apiGet(path, headers = JSON_HEADERS, tags = {}) {
  return http.get(`${BASE_URL}${path}`, { headers, tags });
}

/**
 * 登录并返回 accessToken；失败时返回 null。
 */
export function login(username, password, vuId = 1, { fixedDevice = false } = {}) {
  const deviceKey = fixedDevice ? SHARED_DEVICE_ID : vuId;
  const res = apiPost(
    '/auth/login',
    { username, password },
    { ...JSON_HEADERS, ...deviceHeaders(deviceKey) },
    { name: 'auth_login' },
  );
  const body = parseJson(res);
  const ok = check(res, {
    'login status 200': (r) => r.status === 200,
    'login api code 200': () => isApiOk(body),
    'login has accessToken': () => Boolean(body?.data?.accessToken),
  });
  if (!ok) {
    return null;
  }
  return {
    accessToken: body.data.accessToken,
    refreshToken: body.data.refreshToken,
  };
}

/**
 * setup() 阶段登录一次，供 default 函数复用 token。
 */
export function setupLogin(username, password) {
  if (!username || !password) {
    throw new Error('K6_USERNAME / K6_PASSWORD 未设置');
  }
  const tokens = login(username, password, 0, { fixedDevice: true });
  if (!tokens) {
    throw new Error(`登录失败: ${username}`);
  }
  const token = tokens.accessToken;

  let conversationId = snowflakeId(__ENV.K6_CONVERSATION_ID);
  if (!conversationId) {
    const sessionsRes = apiGet('/chat/sessions', authHeaders(token, 0, { fixedDevice: true }), { name: 'chat_sessions_setup' });
    conversationId = extractSnowflake(sessionsRes && sessionsRes.body, 'id');
  }

  return { token, refreshToken: tokens.refreshToken, conversationId };
}
