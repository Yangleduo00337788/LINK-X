/**
 * IM 发文本并写库。每 VU 独立 deviceId，避免同设备互踢。
 * 默认短 hold；需要 K6_CONVERSATION_ID。
 */
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import ws from 'k6/ws';
import {
  PASSWORD,
  PROFILE,
  USERNAME,
  WS_HOLD_MS,
  WS_URL,
  rampingVus,
  thresholdsForProfile,
} from './lib/config.js';
import { setupLogin, wsConnectParams, wsUrlWithDevice } from './lib/client.js';

const wsConnectMs = new Trend('ws_connect_ms');
const wsAckOk = new Rate('ws_send_ack_ok');
const wsErrors = new Counter('ws_errors');

export const options = {
  scenarios: rampingVus('chat_write', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    ws_send_ack_ok: ['rate>0.80'],
    ws_errors: PROFILE === 'stress' || PROFILE === 'gradient' ? ['count<2000'] : ['count<200'],
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
  const hold = WS_HOLD_MS > 0 ? Math.min(WS_HOLD_MS, 8000) : 4000;
  const deviceId = `k6-write-${__VU}`;
  const url = wsUrlWithDevice(WS_URL, deviceId);
  const params = wsConnectParams(data.token);
  const started = Date.now();
  let gotAck = false;

  const res = ws.connect(url, params, (socket) => {
    socket.on('open', () => {
      wsConnectMs.add(Date.now() - started);
      socket.send(
        JSON.stringify({
          action: 'send',
          conversationId: data.conversationId,
          msgType: 'text',
          content: `k6-write vu=${__VU} iter=${__ITER} t=${Date.now()}`,
          clientMsgId: `k6w-${__VU}-${__ITER}-${Date.now()}`,
        }),
      );
    });
    socket.on('message', (raw) => {
      let frame;
      try {
        frame = JSON.parse(raw);
      } catch {
        return;
      }
      if (frame && frame.action === 'ack') {
        gotAck = true;
        wsAckOk.add(1);
        socket.close();
      } else if (frame && (frame.action === 'error' || (frame.code && frame.code >= 400))) {
        wsErrors.add(1);
      }
    });
    socket.on('error', () => {
      wsErrors.add(1);
    });
    socket.setTimeout(() => {
      socket.close();
    }, hold);
  });

  if (!gotAck) {
    wsAckOk.add(0);
  }
  check(res, { 'ws handshake 101': (r) => r && r.status === 101 });
  check(gotAck, { 'received send ack': (ok) => ok === true });
  sleep(0.1);
}
