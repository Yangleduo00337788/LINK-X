/**
 * 群邀请 / 发公告 / 群资产小文件上传（下轮低频写）。
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, PASSWORD, USERNAME, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import {
  apiGet,
  apiPost,
  authHeaders,
  extractSnowflake,
  isApiOk,
  parseJson,
  setupLogin,
} from './lib/client.js';

const PNG = open('./fixtures/k6-tiny.png', 'b');

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('group_extra', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:group_assets}': ['p(95)<4000'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.groupId) {
    const listed = apiGet('/group/list', headers, { name: 'group_list_setup' });
    data.groupId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.groupId) {
    throw new Error('no groupId — run python prep-k6-social.py first');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const gid = data.groupId;

  const assets = apiGet(`/group/${gid}/assets`, headers, { name: 'group_assets' });
  check(assets, {
    'group assets 200': (r) => r.status === 200,
    'group assets api ok': () => isApiOk(parseJson(assets)),
  });
  apiGet('/group/invitations', headers, { name: 'group_invitations' });

  if (__ITER % 20 === 0) {
    const invite = apiPost(
      `/group/invitations/${gid}`,
      { inviteeUserId: data.peerId, message: 'k6' },
      headers,
      { name: 'group_invite' },
    );
    check(invite, { 'group invite http 200': (r) => r.status === 200 });
  }

  if (__ITER % 25 === 0) {
    const announcement = apiPost(
      `/group/${gid}/announcements`,
      { content: `k6 notice vu=${__VU} t=${Date.now()}`, pinned: false },
      headers,
      { name: 'group_announcement_create' },
    );
    check(announcement, {
      'announcement create 200': (r) => r.status === 200,
      'announcement create api ok': () => isApiOk(parseJson(announcement)),
    });
  }

  if (__ITER % 15 === 0) {
    const h = { ...headers };
    delete h['Content-Type'];
    const uploaded = http.post(
      `${BASE_URL}/group/${gid}/assets/upload?type=image`,
      { file: http.file(PNG, `k6-asset-${__VU}-${__ITER}.png`, 'image/png') },
      { headers: h, tags: { name: 'group_asset_upload' } },
    );
    check(uploaded, { 'group asset upload 200': (r) => r.status === 200 });
  }

  sleep(0.35 + Math.random() * 0.4);
}
