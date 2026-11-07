/**
 * 笔记 / 日历 / 收藏读路径；少量创建后删除，避免把库灌满。
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

const DATE = '2026-11-07';

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('workspace_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:notes_list}': ['p(95)<1500'],
    'http_req_duration{name:favorites_list}': ['p(95)<1500'],
  },
});

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.noteId) {
    const listed = apiGet('/notes', headers, { name: 'notes_list_setup' });
    data.noteId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.calendarEventId) {
    const listed = apiGet(`/calendar/date/${DATE}`, headers, { name: 'calendar_list_setup' });
    data.calendarEventId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.favoriteId) {
    const listed = apiGet('/favorites?limit=5', headers, { name: 'favorites_list_setup' });
    data.favoriteId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.noteId || !data.calendarEventId || !data.favoriteId) {
    throw new Error('missing note/calendar/favorite — run python prep-k6-social.py first');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const notes = apiGet('/notes', headers, { name: 'notes_list' });
  check(notes, {
    'notes list 200': (r) => r.status === 200,
    'notes list api ok': () => isApiOk(parseJson(notes)),
  });
  const note = apiGet(`/notes/${data.noteId}`, headers, { name: 'notes_get' });
  check(note, {
    'note get 200': (r) => r.status === 200,
    'note get api ok': () => isApiOk(parseJson(note)),
  });

  const calendar = apiGet('/calendar', headers, { name: 'calendar_list' });
  check(calendar, {
    'calendar list 200': (r) => r.status === 200,
    'calendar list api ok': () => isApiOk(parseJson(calendar)),
  });
  apiGet(`/calendar/date/${DATE}`, headers, { name: 'calendar_by_date' });
  apiGet(`/calendar/${data.calendarEventId}`, headers, { name: 'calendar_get' });

  const favorites = apiGet('/favorites?limit=20', headers, { name: 'favorites_list' });
  check(favorites, {
    'favorites list 200': (r) => r.status === 200,
    'favorites list api ok': () => isApiOk(parseJson(favorites)),
  });
  apiGet('/favorites/storage', headers, { name: 'favorites_storage' });
  apiGet('/favorites/tags', headers, { name: 'favorites_tags' });
  apiGet(`/favorites/${data.favoriteId}`, headers, { name: 'favorites_get' });

  if (__ITER % 8 === 0) {
    const createdNote = apiPost(
      '/notes',
      { title: `k6 n ${__VU}`, content: `k6 note ${Date.now()}`, type: 'note' },
      headers,
      { name: 'notes_create' },
    );
    const nid = extractSnowflake(createdNote && createdNote.body, 'id');
    if (nid) {
      apiDel(`/notes/${nid}`, headers, { name: 'notes_delete' });
    }

    const createdFav = apiPost(
      '/favorites',
      { title: `k6 f ${__VU}`, content: `k6-fav-${Date.now()}`, type: 'note' },
      headers,
      { name: 'favorites_create' },
    );
    const fid = extractSnowflake(createdFav && createdFav.body, 'id');
    if (fid) {
      apiDel(`/favorites/${fid}`, headers, { name: 'favorites_delete' });
    }
  }

  sleep(0.3 + Math.random() * 0.4);
}
