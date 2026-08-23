/**
 * 聊天读链路压测（setup 登录一次，VU 复用 token）
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx chat-read.js
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, THRESHOLDS_DEFAULT } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: {
    chat_read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 10 },
        { duration: '45s', target: 10 },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    ...THRESHOLDS_DEFAULT,
    'http_req_duration{name:chat_messages}': ['p(95)<1000'],
    'http_req_duration{name:chat_sessions}': ['p(95)<600'],
  },
};

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const sessions = apiGet('/chat/sessions', headers, { name: 'chat_sessions' });
  const sessionsBody = parseJson(sessions);
  check(sessions, {
    'sessions status 200': (r) => r.status === 200,
    'sessions api ok': () => isApiOk(sessionsBody),
  });

  const conversationId =
    data.conversationId ||
    (Array.isArray(sessionsBody?.data) && sessionsBody.data[0]
      ? String(sessionsBody.data[0].id)
      : '');

  if (conversationId) {
    const messages = apiGet(
      `/chat/sessions/${conversationId}/messages?limit=50`,
      headers,
      { name: 'chat_messages' },
    );
    check(messages, {
      'messages status 200': (r) => r.status === 200,
      'messages api ok': () => isApiOk(parseJson(messages)),
    });

    const unread = apiGet(
      `/chat/sessions/${conversationId}/unread`,
      headers,
      { name: 'chat_session_unread' },
    );
    check(unread, {
      'session unread status 200': (r) => r.status === 200,
      'session unread api ok': () => isApiOk(parseJson(unread)),
    });
  }

  const friends = apiGet('/friend/list', headers, { name: 'friend_list' });
  check(friends, {
    'friend list status 200': (r) => r.status === 200,
    'friend list api ok': () => isApiOk(parseJson(friends)),
  });

  sleep(0.3 + Math.random() * 0.7);
}
