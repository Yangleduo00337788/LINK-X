/**
 * 群聊发文本写库。每 VU 独立 deviceId。需要 K6_GROUP_ID。
 */
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import ws from 'k6/ws';
import {
  PASSWORD,
  USERNAME,
  WS_HOLD_MS,
  WS_URL,
  SOCIAL_LOAD_STAGES,
  rampingVusFixed,
  thresholdsForProfile,
  withSetupTimeout,
} from './lib/config.js';
import {
  apiGet,
  authHeaders,
  extractSnowflake,
  setupLogin,
  wsConnectParams,
  wsUrlWithDevice,
} from './lib/client.js';

const wsConnectMs = new Trend('ws_connect_ms');
const wsAckOk = new Rate('ws_send_ack_ok');
const wsErrors = new Counter('ws_errors');

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('group_write', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    ws_send_ack_ok: ['rate>0.50'],
    ws_errors: ['count<50000'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  if (!data.groupId) {
    const listed = apiGet(
      '/group/list',
      authHeaders(data.token, 0, { fixedDevice: true }),
      { name: 'group_list_setup' },
    );
    data.groupId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.groupId) {
    throw new Error('no groupId — run python prep-k6-social.py first');
  }
  return data;
}

export default function (data) {
  const hold = WS_HOLD_MS > 0 ? Math.min(WS_HOLD_MS, 8000) : 4000;
  const deviceId = `k6-group-${__VU}`;
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
          conversationId: data.groupId,
          msgType: 'text',
          content: `k6-group vu=${__VU} iter=${__ITER} t=${Date.now()}`,
          clientMsgId: `k6g-${__VU}-${__ITER}-${Date.now()}`,
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
