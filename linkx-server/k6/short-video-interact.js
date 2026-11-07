/**
 * 短视频点赞 / 评论 / 播放；需要已有视频，prep 会写入 K6_SHORT_VIDEO_POST_ID。
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
  scenarios: rampingVusFixed('short_video_interact', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:short_video_get}': ['p(95)<2000'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.shortVideoPostId) {
    const listed = apiGet('/short-video?limit=5', headers, { name: 'short_video_list_setup' });
    data.shortVideoPostId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.shortVideoPostId) {
    throw new Error('no short-video post — seed a video or skip this scenario');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });
  const pid = data.shortVideoPostId;

  const get = apiGet(`/short-video/${pid}`, headers, { name: 'short_video_get' });
  check(get, {
    'short-video get 200': (r) => r.status === 200,
    'short-video get api ok': () => isApiOk(parseJson(get)),
  });

  apiGet(`/short-video/${pid}/comments?limit=20`, headers, { name: 'short_video_comments' });
  apiPost(`/short-video/${pid}/play`, {}, headers, { name: 'short_video_play' });

  const like = apiPost(`/short-video/${pid}/like`, {}, headers, { name: 'short_video_like' });
  check(like, { 'short-video like http 200': (r) => r.status === 200 });
  apiDel(`/short-video/${pid}/like`, headers, { name: 'short_video_unlike' });

  if (__ITER % 6 === 0) {
    const comment = apiPost(
      `/short-video/${pid}/comment`,
      { content: `k6 sv vu=${__VU} t=${Date.now()}` },
      headers,
      { name: 'short_video_comment' },
    );
    check(comment, {
      'short-video comment 200': (r) => r.status === 200,
      'short-video comment api ok': () => isApiOk(parseJson(comment)),
    });
  }

  sleep(0.3 + Math.random() * 0.4);
}
