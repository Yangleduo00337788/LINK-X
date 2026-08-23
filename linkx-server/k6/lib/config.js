/**
 * LinkX k6 共享配置（通过环境变量覆盖）
 *
 * K6_BASE_URL      默认 http://127.0.0.1:8080/api
 * K6_USERNAME      压测账号（必填，除 smoke-public 外）
 * K6_PASSWORD      压测密码（必填，除 smoke-public 外）
 * K6_CONVERSATION_ID  固定会话 ID（可选；不设则从 sessions 列表取第一个）
 */

export const BASE_URL = (__ENV.K6_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '');

export const USERNAME = __ENV.K6_USERNAME || '';
export const PASSWORD = __ENV.K6_PASSWORD || '';
export const CONVERSATION_ID = __ENV.K6_CONVERSATION_ID || '';

export const THRESHOLDS_DEFAULT = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<800', 'p(99)<1500'],
};

export const THRESHOLDS_SMOKE = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<2000'],
};
