/**
 * 朋友圈：列表、详情、点赞/取消、评论。发帖仅 setup 缺数据时补一条。
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
import {
  apiDel,
  apiGet,
  apiPost,
  authHeaders,
  extractSnowflake,
  isApiOk,
  parseJson,
  setupLogin,
} from './lib/client.js';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('moments_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:moments_list}': ['p(95)<1500'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.momentsPostId) {
    const listed = apiGet('/moments?limit=5', headers, { name: 'moments_list_setup' });
    data.momentsPostId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.momentsPostId) {
    const created = apiPost(
      '/moments',
      { content: `k6 moments seed ${Date.now()}`, visibility: 0 },
      headers,
      { name: 'moments_publish_setup' },
    );
    data.momentsPostId = extractSnowflake(created && created.body, 'id');
  }
  if (!data.momentsPostId) {
    throw new Error('no momentsPostId — run python prep-k6-social.py first');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const pid = data.momentsPostId;

  const list = apiGet('/moments?limit=20', headers, { name: 'moments_list' });
  check(list, {
    'moments list 200': (r) => r.status === 200,
    'moments list api ok': () => isApiOk(parseJson(list)),
  });

  const post = apiGet(`/moments/${pid}`, headers, { name: 'moments_get' });
  check(post, {
    'moments get 200': (r) => r.status === 200,
    'moments get api ok': () => isApiOk(parseJson(post)),
  });

  const like = apiPost(`/moments/${pid}/like`, {}, headers, { name: 'moments_like' });
  check(like, {
    'moments like 200': (r) => r.status === 200,
    'moments like api ok': () => isApiOk(parseJson(like)),
  });
  apiDel(`/moments/${pid}/like`, headers, { name: 'moments_unlike' });

  if (__ITER % 5 === 0) {
    const comment = apiPost(
      `/moments/${pid}/comment`,
      { content: `k6 c vu=${__VU} t=${Date.now()}` },
      headers,
      { name: 'moments_comment' },
    );
    check(comment, {
      'moments comment 200': (r) => r.status === 200,
      'moments comment api ok': () => isApiOk(parseJson(comment)),
    });
    const commentId = extractSnowflake(comment && comment.body, 'id');
    if (commentId) {
      apiDel(`/moments/comment/${commentId}`, headers, { name: 'moments_comment_delete' });
    }
  }

  sleep(0.3 + Math.random() * 0.4);
}
