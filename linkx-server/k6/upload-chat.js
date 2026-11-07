/**
 * 聊天附件上传（走对象存储）。小 PNG，需要会话 + MinIO。
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, PASSWORD, USERNAME, rampingVus, thresholdsForProfile } from './lib/config.js';
import { authHeaders, isApiOk, parseJson, setupLogin } from './lib/client.js';

const PNG = open('./fixtures/k6-tiny.png', 'b');

export const options = {
  scenarios: rampingVus('upload_chat', [
    { duration: '20s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '15s', target: 0 },
  ]),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:chat_upload}': ['p(95)<4000'],
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
  delete headers['Content-Type'];
  const res = http.post(
    `${BASE_URL}/chat/sessions/${data.conversationId}/upload`,
    { file: http.file(PNG, `k6-${__VU}-${__ITER}.png`, 'image/png') },
    { headers, tags: { name: 'chat_upload' } },
  );
  check(res, {
    'upload status 200': (r) => r.status === 200,
    'upload api ok': () => isApiOk(parseJson(res)),
  });
  sleep(0.4 + Math.random() * 0.4);
}
