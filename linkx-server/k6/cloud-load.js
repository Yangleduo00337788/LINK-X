/**
 * 网盘：空间、列表、面包屑、动态、读文件；每 10 次迭代上传一张小图再删掉。
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, PASSWORD, USERNAME, SOCIAL_LOAD_STAGES, rampingVusFixed, thresholdsForProfile, withSetupTimeout } from './lib/config.js';
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

const PNG = open('./fixtures/k6-tiny.png', 'b');

export const options = withSetupTimeout({
  scenarios: rampingVusFixed('cloud_load', SOCIAL_LOAD_STAGES),
  thresholds: {
    ...thresholdsForProfile(),
    'http_req_duration{name:cloud_upload}': ['p(95)<4000'],
    'http_req_duration{name:cloud_items}': ['p(95)<1500'],
  },
});

function uploadOne(headers, vu = 0, iter = 0) {
  const h = { ...headers };
  delete h['Content-Type'];
  return http.post(
    `${BASE_URL}/cloud/files/upload`,
    { file: http.file(PNG, `k6-cloud-${vu}-${iter}-${Date.now()}.png`, 'image/png') },
    { headers: h, tags: { name: 'cloud_upload' } },
  );
}

export function setup() {
  const data = setupLogin(USERNAME, PASSWORD);
  const headers = authHeaders(data.token, 0, { fixedDevice: true });
  if (!data.cloudFolderId) {
    const folder = apiPost(
      '/cloud/folders',
      { name: `k6-folder-${Date.now()}` },
      headers,
      { name: 'cloud_folder_setup' },
    );
    data.cloudFolderId = extractSnowflake(folder && folder.body, 'id');
  }
  if (!data.cloudFileId) {
    const listed = apiGet('/cloud/items', headers, { name: 'cloud_items_setup' });
    data.cloudFileId = extractSnowflake(listed && listed.body, 'id');
  }
  if (!data.cloudFileId) {
    const uploaded = uploadOne(headers);
    data.cloudFileId = extractSnowflake(uploaded && uploaded.body, 'id');
  }
  if (!data.cloudFileId) {
    throw new Error('no cloud file — start MinIO then rerun, or python prep-k6-social.py');
  }
  return data;
}

export default function (data) {
  const headers = authHeaders(data.token, __VU, { fixedDevice: true });

  const storage = apiGet('/cloud/storage', headers, { name: 'cloud_storage' });
  check(storage, {
    'cloud storage 200': (r) => r.status === 200,
    'cloud storage api ok': () => isApiOk(parseJson(storage)),
  });

  const items = apiGet('/cloud/items', headers, { name: 'cloud_items' });
  check(items, {
    'cloud items 200': (r) => r.status === 200,
    'cloud items api ok': () => isApiOk(parseJson(items)),
  });

  apiGet('/cloud/breadcrumb', headers, { name: 'cloud_breadcrumb' });
  apiGet('/cloud/activities?limit=20', headers, { name: 'cloud_activities' });
  apiGet('/files?limit=20', headers, { name: 'cloud_files_list' });

  if (data.cloudFileId) {
    const file = apiGet(`/cloud/files/${data.cloudFileId}`, headers, { name: 'cloud_file_get' });
    check(file, {
      'cloud file 200': (r) => r.status === 200,
      'cloud file api ok': () => isApiOk(parseJson(file)),
    });
  }

  if (__ITER % 10 === 0) {
    const uploaded = uploadOne(headers, __VU, __ITER);
    check(uploaded, {
      'cloud upload 200': (r) => r.status === 200,
      'cloud upload api ok': () => isApiOk(parseJson(uploaded)),
    });
    const newId = extractSnowflake(uploaded && uploaded.body, 'id');
    if (newId) {
      apiDel(`/cloud/files/${newId}`, headers, { name: 'cloud_file_delete' });
    }
  }

  sleep(0.35 + Math.random() * 0.4);
}
