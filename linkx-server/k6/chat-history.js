/**
 * 聊天历史消息读。需要 K6_CONVERSATION_ID 或账号已有会话。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, rampingVus, thresholdsForProfile } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: rampingVus('chat_history', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:chat_messages}': ['p(95)<1500'],
  },
};

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  if (!data.conversationId) {
    throw new Error('no conversationId — run prep-k6-write.py first');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const messages = apiGet(
    `/chat/sessions/${data.conversationId}/messages?limit=50`,
    headers,
    { name: 'chat_messages' },
  );
  check(messages, {
    'messages status 200': (r) => r.status === 200,
    'messages api ok': () => isApiOk(parseJson(messages)),
  });
  sleep(0.2 + Math.random() * 0.3);
}
