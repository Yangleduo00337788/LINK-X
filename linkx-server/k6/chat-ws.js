/**
 * IM WebSocket 长连接压测：握手 + ping/pong。
 * 默认不发聊天消息（避免把压测数据写进业务库）。需要发消息时加 K6_WS_SEND=1。
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx chat-ws.js
 * k6 run -e K6_WS_SEND=1 -e K6_USERNAME=xxx -e K6_PASSWORD=xxx chat-ws.js
 */
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import ws from 'k6/ws';
import {
  PROFILE,
  SHARED_DEVICE_ID,
  USERNAME,
  PASSWORD,
  WS_HOLD_MS,
  WS_SEND,
  WS_URL,
  rampingVus,
  thresholdsForProfile,
} from './lib/config.js';
import { setupLogin, wsConnectParams, wsUrlWithDevice } from './lib/client.js';

const wsConnectMs = new Trend('ws_connect_ms');
const wsPongOk = new Rate('ws_pong_ok');
const wsAckOk = new Rate('ws_send_ack_ok');
const wsErrors = new Counter('ws_errors');

export const options = {
  scenarios: rampingVus('chat_ws', [
    { duration: '20s', target: 30 },
    { duration: '2m', target: 30 },
    { duration: '20s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    ws_pong_ok: ['rate>0.90'],
    ws_errors: PROFILE === 'stress' || PROFILE === 'gradient' ? ['count<2000'] : ['count<80'],
  },
};

export function setup() {
  return setupLogin(USERNAME, PASSWORD);
}

export default function (data) {
  const url = wsUrlWithDevice(WS_URL, SHARED_DEVICE_ID);
  const params = wsConnectParams(data.token);
  const started = Date.now();
  let gotPong = false;
  let gotAck = false;

  const res = ws.connect(url, params, (socket) => {
    socket.on('open', () => {
      wsConnectMs.add(Date.now() - started);
      socket.send(JSON.stringify({ action: 'ping' }));

      if (WS_SEND && data.conversationId) {
        socket.send(
          JSON.stringify({
            action: 'send',
            conversationId: String(data.conversationId),
            msgType: 'text',
            content: `k6-ws vu=${__VU} iter=${__ITER} t=${Date.now()}`,
            clientMsgId: `k6-${__VU}-${__ITER}-${Date.now()}`,
          }),
        );
      }
    });

    socket.on('message', (raw) => {
      let frame;
      try {
        frame = JSON.parse(raw);
      } catch {
        return;
      }
      const action = frame && frame.action;
      if (action === 'pong') {
        gotPong = true;
        wsPongOk.add(1);
      } else if (action === 'ack') {
        gotAck = true;
        wsAckOk.add(1);
      } else if (action === 'error' || (frame && frame.code && frame.code >= 400)) {
        wsErrors.add(1);
      }
    });

    socket.on('error', () => {
      wsErrors.add(1);
    });

    socket.setInterval(() => {
      socket.send(JSON.stringify({ action: 'ping' }));
    }, 12000);

    socket.setTimeout(() => {
      socket.close();
    }, WS_HOLD_MS);
  });

  if (!gotPong) {
    wsPongOk.add(0);
  }

  check(res, {
    'ws handshake 101': (r) => r && r.status === 101,
  });
  check(gotPong, {
    'received pong': (ok) => ok === true,
  });
  if (WS_SEND && data.conversationId) {
    check(gotAck, {
      'received send ack': (ok) => ok === true,
    });
  }

  sleep(0.2);
}
