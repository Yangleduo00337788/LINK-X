/**
 * 通话信令：invite 后立刻 cancel，不留振铃。需要私聊会话。
 */
import { check, sleep } from 'k6';
import { PASSWORD, USERNAME, rampingVus, thresholdsForProfile } from './lib/config.js';
import { apiPost, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: rampingVus('call_load', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:call_invite}': ['p(95)<2000'],
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
  const invite = apiPost(
    '/call/invite',
    { conversationId: data.conversationId, callType: 'voice' },
    headers,
    { name: 'call_invite' },
  );
  const body = parseJson(invite);
  check(invite, {
    'invite status 200': (r) => r.status === 200,
    'invite api ok': () => isApiOk(body),
  });
  const callId = body && body.data && body.data.callId;
  if (callId) {
    const cancel = apiPost(
      '/call/cancel',
      { callId, reason: 'k6' },
      headers,
      { name: 'call_cancel' },
    );
    check(cancel, {
      'cancel status 200': (r) => r.status === 200,
      'cancel api ok': () => isApiOk(parseJson(cancel)),
    });
  }
  sleep(0.3 + Math.random() * 0.4);
}
