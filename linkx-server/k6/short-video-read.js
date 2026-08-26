/**
 * 短视频读接口压测：发现流 / 热榜 / 话题
 *
 * k6 run -e K6_USERNAME=xxx -e K6_PASSWORD=xxx short-video-read.js
 */
import { check, sleep } from 'k6';
import { USERNAME, PASSWORD, THRESHOLDS_DEFAULT } from './lib/config.js';
import { apiGet, authHeaders, isApiOk, login, parseJson } from './lib/client.js';

export const options = {
  scenarios: {
    short_video_read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 8 },
        { duration: '40s', target: 8 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    ...THRESHOLDS_DEFAULT,
    'http_req_duration{name:short_video_list}': ['p(95)<1500'],
  },
};

export default function () {
  const tokens = login(USERNAME, PASSWORD, __VU);
  if (!tokens) {
    sleep(1);
    return;
  }
  const headers = authHeaders(tokens.accessToken, __VU);

  const list = apiGet('/short-video?limit=10', headers, { name: 'short_video_list' });
  check(list, {
    'short-video list status 200': (r) => r.status === 200,
    'short-video list api ok': () => isApiOk(parseJson(list)),
  });

  const hot = apiGet('/short-video/hot?limit=5', headers, { name: 'short_video_hot' });
  check(hot, {
    'short-video hot status 200': (r) => r.status === 200,
    'short-video hot api ok': () => isApiOk(parseJson(hot)),
  });

  const topics = apiGet('/short-video/topics/hot?limit=5', headers, { name: 'short_video_topics_hot' });
  check(topics, {
    'short-video topics status 200': (r) => r.status === 200,
    'short-video topics api ok': () => isApiOk(parseJson(topics)),
  });

  sleep(0.5 + Math.random() * 0.5);
}
