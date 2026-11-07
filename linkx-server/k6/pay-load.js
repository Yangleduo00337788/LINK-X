/**
 * 演示充值 + 发红包（无真实支付网关）。需要会话与余额。
 */
import { check, sleep } from 'k6';
import { PASSWORD, USERNAME, rampingVus, thresholdsForProfile } from './lib/config.js';
import { apiGet, apiPost, authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

export const options = {
  scenarios: rampingVus('pay_load', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:balance_recharge}': ['p(95)<2000'],
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

  const bal = apiGet('/balance', headers, { name: 'balance_get' });
  check(bal, {
    'balance status 200': (r) => r.status === 200,
    'balance api ok': () => isApiOk(parseJson(bal)),
  });

  const recharge = apiPost(
    '/balance/recharge',
    { amount: 0.01 },
    headers,
    { name: 'balance_recharge' },
  );
  check(recharge, {
    'recharge status 200': (r) => r.status === 200,
    'recharge api ok': () => isApiOk(parseJson(recharge)),
  });

  const packet = apiPost(
    '/red-packet',
    {
      conversationId: data.conversationId,
      type: 'normal',
      totalAmount: 0.01,
      totalCount: 1,
      greeting: 'k6',
      clientMsgId: `k6p-${__VU}-${__ITER}-${Date.now()}`,
    },
    headers,
    { name: 'red_packet_send' },
  );
  check(packet, {
    'red-packet status 200': (r) => r.status === 200,
    'red-packet api ok': () => isApiOk(parseJson(packet)),
  });

  sleep(0.4 + Math.random() * 0.4);
}
