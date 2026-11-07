/**
 * 会议信令读/控制：详情、进行中、历史、入会、静音、视频、举手。
 * 不结束会议。需要 K6_CONFERENCE_ID 与 K6_GROUP_ID。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import {
  apiGet,
  apiPost,
  authHeaders,
  extractSnowflake,
  isApiOk,
  parseJson,
  setupLogin,
} from './lib/client.js';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('conference_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:conference_info}': ['p(95)<1500'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.conferenceId) {
    const active = apiGet('/conference/active', headers, { name: 'conference_active_setup' });
    data.conferenceId = extractSnowflake(active && active.body, 'id');
  }
  if (!data.groupId) {
    const listed = apiGet('/group/list', headers, { name: 'group_list_setup' });
    data.groupId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.conferenceId) {
    throw new Error('no conferenceId — run python prep-k6-social.py first');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const cid = data.conferenceId;
  const gid = data.groupId;

  const info = apiGet(`/conference/info/${cid}`, headers, { name: 'conference_info' });
  check(info, {
    'conference info 200': (r) => r.status === 200,
    'conference info api ok': () => isApiOk(parseJson(info)),
  });

  const active = apiGet('/conference/active', headers, { name: 'conference_active' });
  check(active, {
    'conference active 200': (r) => r.status === 200,
    'conference active api ok': () => isApiOk(parseJson(active)),
  });

  if (gid) {
    apiGet(
      `/conference/active-in-conversation?conversationId=${gid}`,
      headers,
      { name: 'conference_active_in_conv' },
    );
    apiGet(`/conference/history?conversationId=${gid}`, headers, { name: 'conference_history' });
  }

  const join = apiPost(
    '/conference/join',
    { conferenceId: cid },
    headers,
    { name: 'conference_join' },
  );
  check(join, {
    'conference join 200': (r) => r.status === 200,
    'conference join api ok': () => isApiOk(parseJson(join)),
  });

  const muted = __ITER % 2 === 0;
  apiPost('/conference/mute', { conferenceId: cid, muted }, headers, { name: 'conference_mute' });
  apiPost(
    '/conference/video',
    { conferenceId: cid, videoOff: !muted },
    headers,
    { name: 'conference_video' },
  );
  apiPost(
    '/conference/raise',
    { conferenceId: cid, raised: muted },
    headers,
    { name: 'conference_raise' },
  );
  apiPost(
    '/conference/signal',
    {
      conferenceId: cid,
      signalType: 'offer',
      sdp: 'v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=k6\r\nt=0 0\r\n',
      targetUserId: data.peerId,
    },
    headers,
    { name: 'conference_signal' },
  );
  apiPost(
    '/conference/admit',
    { conferenceId: cid, targetUserId: data.peerId },
    headers,
    { name: 'conference_admit' },
  );
  apiPost(
    '/conference/transfer-host',
    { conferenceId: cid, newHostId: data.peerId },
    headers,
    { name: 'conference_transfer' },
  );

  sleep(0.3 + Math.random() * 0.4);
}
