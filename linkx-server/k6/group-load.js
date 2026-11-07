/**
 * 群聊读：列表、详情、成员、公告、群消息历史。
 * 需要 K6_GROUP_ID，或先跑 python prep-k6-social.py。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import {
  apiGet,
  authHeaders,
  extractSnowflake,
  isApiOk,
  parseJson,
  setupLogin,
} from './lib/client.js';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('group_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:group_info}': ['p(95)<1500'],
    'http_req_duration{name:group_messages}': ['p(95)<1500'],
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

  const list = apiGet('/group/list', headers, { name: 'group_list' });
  check(list, {
    'group list 200': (r) => r.status === 200,
    'group list api ok': () => isApiOk(parseJson(list)),
  });

  const info = apiGet(`/group/${gid}/info`, headers, { name: 'group_info' });
  check(info, {
    'group info 200': (r) => r.status === 200,
    'group info api ok': () => isApiOk(parseJson(info)),
  });

  const members = apiGet(`/group/${gid}/members`, headers, { name: 'group_members' });
  check(members, {
    'group members 200': (r) => r.status === 200,
    'group members api ok': () => isApiOk(parseJson(members)),
  });

  const announcements = apiGet(`/group/${gid}/announcements`, headers, { name: 'group_announcements' });
  check(announcements, {
    'announcements 200': (r) => r.status === 200,
    'announcements api ok': () => isApiOk(parseJson(announcements)),
  });

  apiGet(`/group/${gid}/announcements/display`, headers, { name: 'group_announcement_display' });
  apiGet(`/group/${gid}/announcement/read-count`, headers, { name: 'group_announcement_read_count' });
  apiGet(`/group/${gid}/join-requests`, headers, { name: 'group_join_requests' });
  apiGet(`/group/${gid}/assets`, headers, { name: 'group_assets' });
  apiGet('/group/invitations', headers, { name: 'group_invitations' });

  const messages = apiGet(`/chat/sessions/${gid}/messages?limit=50`, headers, { name: 'group_messages' });
  check(messages, {
    'group messages 200': (r) => r.status === 200,
    'group messages api ok': () => isApiOk(parseJson(messages)),
  });

  sleep(0.25 + Math.random() * 0.35);
}
