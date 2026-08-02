#!/usr/bin/env node
/**
 * 从 openapi.json 生成 Postman Collection（Apifox 可直接导入）
 * 含后置断言脚本，与集成测试 IT 冒烟路径对齐。
 *
 * 用法：
 *   node generate-postman-collection.mjs --all
 *   node generate-postman-collection.mjs --filter admin
 *   node generate-postman-collection.mjs --filter client
 *   node generate-postman-collection.mjs --filter all --out ./custom.json
 *   node generate-postman-collection.mjs --smoke
 */
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '../../../..')
const openApiPath = join(repoRoot, 'linkx-server/perf/k6/openapi.json')
const smokePathsPath = join(__dirname, 'smoke-paths.json')

const args = process.argv.slice(2)
const filter = args.includes('--filter') ? args[args.indexOf('--filter') + 1] : null
const outArg = args.includes('--out') ? args[args.indexOf('--out') + 1] : null
const smokeOnly = args.includes('--smoke')
const generateAll = args.includes('--all')

const spec = JSON.parse(readFileSync(openApiPath, 'utf8'))
const smokePaths = JSON.parse(readFileSync(smokePathsPath, 'utf8'))

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete']

// ---- 冒烟路径索引 ----
function buildSmokeIndex() {
  const index = new Map()
  const add = (tier, paths) => {
    for (const p of paths || []) {
      index.set(`GET ${p}`, tier)
    }
  }
  add('smoke_ok', smokePaths.clientPublicGet)
  add('smoke_ok', smokePaths.clientAuthGet)
  add('smoke_ok', smokePaths.clientHotPathGet)
  add('catalog', smokePaths.clientCatalogGet)
  add('catalog', smokePaths.adminCatalogGet)
  add('smoke_ok', smokePaths.adminDashboardGet)
  add('smoke_ok', smokePaths.adminSmokeAfterLogin)
  return index
}

const smokeIndex = buildSmokeIndex()

// ---- OpenAPI schema 示例 ----
function resolveRef(schema) {
  if (!schema?.$ref) return schema
  const name = schema.$ref.replace('#/components/schemas/', '')
  return spec.components?.schemas?.[name] ?? schema
}

function exampleFromSchema(schema, depth = 0) {
  if (!schema || depth > 6) return null
  const resolved = resolveRef(schema)
  if (resolved.example != null) return resolved.example
  if (resolved.default != null) return resolved.default
  const type = resolved.type
  if (type === 'object') {
    const obj = {}
    const props = resolved.properties || {}
    const required = resolved.required || []
    for (const key of required.length ? required : Object.keys(props)) {
      const val = exampleFromSchema(props[key], depth + 1)
      if (val != null) obj[key] = val
    }
    return Object.keys(obj).length ? obj : {}
  }
  if (type === 'array') {
    const item = exampleFromSchema(resolved.items, depth + 1)
    return item != null ? [item] : []
  }
  if (type === 'string') {
    if (resolved.format === 'date-time') return '2026-01-01T00:00:00Z'
    if (resolved.format === 'email') return 'user@linkx.test'
    if (resolved.enum?.length) return resolved.enum[0]
    return resolved.example ?? 'string'
  }
  if (type === 'integer' || type === 'number') return resolved.example ?? 1
  if (type === 'boolean') return resolved.example ?? true
  return null
}

function requestBodyJson(op) {
  const content = op.requestBody?.content?.['application/json']
  if (!content?.schema) return null
  const example = exampleFromSchema(content.schema)
  if (example == null) return null
  return JSON.stringify(example, null, 2)
}

// ---- 鉴权与测试脚本 ----
function needsAuth(op) {
  if (op.security === undefined) return true
  return op.security.length > 0
}

function isLoginPath(path) {
  return path === '/auth/login' || path === '/admin/auth/login'
}

const TEST_SMOKE_OK = [
  'pm.test("HTTP 200", function () {',
  '    pm.response.to.have.status(200);',
  '});',
  'pm.test("业务 code=200", function () {',
  '    var json = pm.response.json();',
  '    pm.expect(json.code).to.eql(200);',
  '});',
].join('\n')

const TEST_CATALOG = [
  'pm.test("无服务端错误 (<500)", function () {',
  '    pm.expect(pm.response.code).to.be.below(500);',
  '});',
].join('\n')

function loginTestScript(isAdmin) {
  const tokenVar = isAdmin ? 'accessToken' : 'clientAccessToken'
  return [
    'pm.test("HTTP 200", function () {',
    '    pm.response.to.have.status(200);',
    '});',
    'pm.test("业务 code=200", function () {',
    '    var json = pm.response.json();',
    '    pm.expect(json.code).to.eql(200);',
    '});',
    'var data = pm.response.json().data;',
    'if (data && data.accessToken) {',
    `    pm.collectionVariables.set("${tokenVar}", data.accessToken);`,
    '}',
    'if (data && data.refreshToken) {',
    `    pm.collectionVariables.set("${isAdmin ? 'adminRefreshToken' : 'clientRefreshToken'}", data.refreshToken);`,
    '}',
  ].join('\n')
}

function buildTestScript(path, method, op) {
  if (isLoginPath(path)) {
    return loginTestScript(path.startsWith('/admin'))
  }
  const key = `${method.toUpperCase()} ${path}`
  const tier = smokeIndex.get(key)
  if (tier === 'smoke_ok') return TEST_SMOKE_OK
  if (tier === 'catalog') return TEST_CATALOG
  if (method === 'get') return TEST_CATALOG
  return TEST_CATALOG
}

// ---- URL / 请求构建 ----
function concretePath(path) {
  return path.replace(/\{[^}]+}/g, '1')
}

function pathVariables(path) {
  const vars = []
  const re = /\{([^}]+)}/g
  let m
  while ((m = re.exec(path)) !== null) {
    vars.push({ key: m[1], value: '1' })
  }
  return vars
}

function groupName(path, op) {
  const tags = op.tags || []
  if (tags.length) {
    const t = tags[0].replace(/\$\{[^}]+}/g, '').trim()
    if (t) return t
  }
  const parts = path.split('/').filter(Boolean)
  if (parts[0] === 'admin') return parts[1] || 'root'
  return parts[0] || 'root'
}

function matchesFilter(path, f) {
  if (!f || f === 'all') return true
  if (f === 'admin') return path.startsWith('/admin')
  if (f === 'client') return !path.startsWith('/admin')
  return true
}

function bearerAuth(tokenVar) {
  return {
    type: 'bearer',
    bearer: [{ key: 'token', value: `{{${tokenVar}}}`, type: 'string' }],
  }
}

function toPostmanItem(path, method, op, tokenVar) {
  const concrete = concretePath(path)
  const urlVars = pathVariables(path)
  const query = []
  if (op.parameters) {
    for (const p of op.parameters) {
      if (p.in === 'query') {
        query.push({
          key: p.name,
          value: p.example != null ? String(p.example) : '',
          description: p.description || '',
          disabled: !p.required,
        })
      }
    }
  }
  const bodyRaw = requestBodyJson(op)
  const body =
    bodyRaw != null
      ? {
          mode: 'raw',
          raw: bodyRaw,
          options: { raw: { language: 'json' } },
        }
      : op.requestBody?.content?.['application/json']
        ? {
            mode: 'raw',
            raw: '{}',
            options: { raw: { language: 'json' } },
          }
        : undefined

  const item = {
    name: op.summary || `${method.toUpperCase()} ${path}`,
    request: {
      method: method.toUpperCase(),
      header: [
        {
          key: 'Content-Type',
          value: 'application/json',
          disabled: method === 'get' || method === 'delete',
        },
      ],
      body,
      url: {
        raw: `{{baseUrl}}${concrete}`,
        host: ['{{baseUrl}}'],
        path: concrete.replace(/^\//, '').split('/'),
        query: query.length ? query : undefined,
        variable: urlVars.length ? urlVars : undefined,
      },
      description: [op.description, op.operationId ? `operationId: ${op.operationId}` : '']
        .filter(Boolean)
        .join('\n\n'),
    },
    response: [],
    event: [
      {
        listen: 'test',
        script: {
          type: 'text/javascript',
          exec: buildTestScript(path, method, op).split('\n'),
        },
      },
    ],
  }

  if (!needsAuth(op) || isLoginPath(path)) {
    item.request.auth = { type: 'noauth' }
  } else {
    item.request.auth = bearerAuth(tokenVar)
  }

  return item
}

function buildGroupedCollection(name, description, filterName, tokenVar, extraVars = []) {
  const groups = new Map()
  for (const [path, pathItem] of Object.entries(spec.paths || {})) {
    if (!matchesFilter(path, filterName)) continue
    for (const [method, op] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.includes(method)) continue
      const group = groupName(path, op)
      if (!groups.has(group)) groups.set(group, [])
      groups.get(group).push(toPostmanItem(path, method, op, tokenVar))
    }
  }

  const total = [...groups.values()].reduce((n, arr) => n + arr.length, 0)

  return {
    info: {
      name,
      description,
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    auth: bearerAuth(tokenVar),
    variable: [
      { key: 'baseUrl', value: 'http://localhost:8080/api' },
      { key: tokenVar, value: '' },
      ...extraVars,
    ],
    item: [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, items]) => ({
        name,
        item: items.sort((a, b) =>
          a.request.method.localeCompare(b.request.method) ||
          a.name.localeCompare(b.name),
        ),
      })),
    _meta: { total, folders: groups.size },
  }
}

function buildFullCollection() {
  const client = buildGroupedCollection(
    'LinkX Client API',
    '客户端接口（非 /admin/**）。含 IT 对齐断言脚本。',
    'client',
    'clientAccessToken',
    [{ key: 'clientRefreshToken', value: '' }],
  )
  const admin = buildGroupedCollection(
    'LinkX Admin API',
    '管理端 /admin/** 接口。含 IT 对齐断言脚本。',
    'admin',
    'accessToken',
    [{ key: 'adminRefreshToken', value: '' }],
  )
  delete client._meta
  delete admin._meta

  const total =
    client.item.reduce((n, g) => n + g.item.length, 0) +
    admin.item.reduce((n, g) => n + g.item.length, 0)

  return {
    info: {
      name: 'LinkX Full API',
      description:
        'LinkX 全量 REST API（客户端 + 管理端）。由 openapi.json 自动生成，Apifox 可直接导入。\n\n变量：\n- `baseUrl`\n- `clientAccessToken` / `accessToken`',
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    variable: [
      { key: 'baseUrl', value: 'http://localhost:8080/api' },
      { key: 'clientAccessToken', value: '' },
      { key: 'clientRefreshToken', value: '' },
      { key: 'accessToken', value: '' },
      { key: 'adminRefreshToken', value: '' },
    ],
    item: [
      { name: 'client', auth: bearerAuth('clientAccessToken'), item: client.item },
      { name: 'admin', auth: bearerAuth('accessToken'), item: admin.item },
    ],
    _meta: { total },
  }
}

// ---- 冒烟场景（多步流程） ----
function makeRequest(name, method, path, options = {}) {
  const {
    body,
    tokenVar,
    noAuth = false,
    testScript,
    description = '',
  } = options
  const concrete = concretePath(path)
  const urlVars = pathVariables(path)
  const req = {
    name,
    request: {
      method: method.toUpperCase(),
      header: [
        {
          key: 'Content-Type',
          value: 'application/json',
          disabled: method === 'get',
        },
      ],
      body: body
        ? { mode: 'raw', raw: body, options: { raw: { language: 'json' } } }
        : undefined,
      url: {
        raw: `{{baseUrl}}${concrete}`,
        host: ['{{baseUrl}}'],
        path: concrete.replace(/^\//, '').split('/'),
        variable: urlVars.length ? urlVars : undefined,
      },
      description,
      auth: noAuth ? { type: 'noauth' } : bearerAuth(tokenVar),
    },
    response: [],
    event: testScript
      ? [
          {
            listen: 'test',
            script: { type: 'text/javascript', exec: testScript.split('\n') },
          },
        ]
      : [],
  }
  return req
}

function buildSmokeScenarios() {
  const clientLoginScript = loginTestScript(false)
  const adminLoginScript = loginTestScript(true)

  const clientPublicFolder = {
    name: '01-客户端-公开读接口',
    item: smokePaths.clientPublicGet.map((p) =>
      makeRequest(`GET ${p}`, 'get', p, {
        noAuth: true,
        tokenVar: 'clientAccessToken',
        testScript: TEST_SMOKE_OK,
        description: 'ClientReadApiSuccessIT · publicReadApis',
      }),
    ),
  }

  const clientAuthFolder = {
    name: '02-客户端-登录后只读',
    item: [
      makeRequest('POST /auth/login', 'post', '/auth/login', {
        noAuth: true,
        body: JSON.stringify({
          username: '{{clientUsername}}',
          password: '{{clientPassword}}',
        }),
        testScript: clientLoginScript,
        description: '先执行本步获取 clientAccessToken',
      }),
      ...smokePaths.clientAuthGet.map((p) =>
        makeRequest(`GET ${p}`, 'get', p, {
          tokenVar: 'clientAccessToken',
          testScript: TEST_SMOKE_OK,
          description: 'ClientReadApiSuccessIT · batchGet',
        }),
      ),
    ],
  }

  const clientHotFolder = {
    name: '03-客户端-热路径',
    item: [
      makeRequest('POST /auth/login', 'post', '/auth/login', {
        noAuth: true,
        body: JSON.stringify({
          username: '{{clientUsername}}',
          password: '{{clientPassword}}',
        }),
        testScript: clientLoginScript,
      }),
      ...smokePaths.clientHotPathGet.map((p) =>
        makeRequest(`GET ${p}`, 'get', p, {
          tokenVar: 'clientAccessToken',
          testScript: TEST_SMOKE_OK,
          description: 'ClientHotPathSuccessIT',
        }),
      ),
    ],
  }

  const adminSmokeFolder = {
    name: '04-管理端-登录后核心',
    item: [
      makeRequest('POST /admin/auth/login', 'post', '/admin/auth/login', {
        noAuth: true,
        body: JSON.stringify({
          username: '{{adminUsername}}',
          password: '{{adminPassword}}',
        }),
        testScript: adminLoginScript,
      }),
      ...smokePaths.adminSmokeAfterLogin.map((p) =>
        makeRequest(`GET ${p}`, 'get', p, {
          tokenVar: 'accessToken',
          testScript: TEST_SMOKE_OK,
          description: 'AdminRoleSmokeIT / dashboard',
        }),
      ),
    ],
  }

  const adminCatalogFolder = {
    name: '05-管理端-目录扫（未鉴权 <500）',
    description: 'AdminEndpointPathCatalogIT · 允许 4xx，禁止 5xx',
    item: smokePaths.adminCatalogGet.map((p) =>
      makeRequest(`GET ${p}`, 'get', p, {
        noAuth: true,
        tokenVar: 'accessToken',
        testScript: TEST_CATALOG,
      }),
    ),
  }

  return {
    info: {
      name: 'LinkX Smoke Scenarios',
      description:
        '与 linkx-server 集成测试对齐的冒烟场景。Apifox 导入后可在「自动化测试」中直接运行。\n\n环境变量：baseUrl、clientUsername、clientPassword、adminUsername、adminPassword',
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    variable: [
      { key: 'baseUrl', value: 'http://localhost:8080/api' },
      { key: 'clientUsername', value: '' },
      { key: 'clientPassword', value: '' },
      { key: 'adminUsername', value: 'admin' },
      { key: 'adminPassword', value: '' },
      { key: 'clientAccessToken', value: '' },
      { key: 'accessToken', value: '' },
    ],
    item: [
      clientPublicFolder,
      clientAuthFolder,
      clientHotFolder,
      adminSmokeFolder,
      adminCatalogFolder,
    ],
  }
}

function buildEnvironment() {
  return {
    name: 'LinkX Local',
    values: [
      { key: 'baseUrl', value: 'http://localhost:8080/api', type: 'default', enabled: true },
      { key: 'clientUsername', value: '', type: 'default', enabled: true },
      { key: 'clientPassword', value: '', type: 'secret', enabled: true },
      { key: 'adminUsername', value: 'admin', type: 'default', enabled: true },
      { key: 'adminPassword', value: '', type: 'secret', enabled: true },
      { key: 'clientAccessToken', value: '', type: 'secret', enabled: true },
      { key: 'clientRefreshToken', value: '', type: 'secret', enabled: true },
      { key: 'accessToken', value: '', type: 'secret', enabled: true },
      { key: 'adminRefreshToken', value: '', type: 'secret', enabled: true },
    ],
    _postman_variable_scope: 'environment',
    _postman_exported_using: 'generate-postman-collection.mjs',
  }
}

function writeCollection(path, collection) {
  const { _meta, ...payload } = collection
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, JSON.stringify(payload, null, 2), 'utf8')
  return _meta
}

function generateAllArtifacts() {
  const apiDir = join(repoRoot, 'docs/api')
  const adminDir = join(repoRoot, 'docs/admin')
  mkdirSync(apiDir, { recursive: true })

  const resolveScript = join(__dirname, 'resolve-openapi-i18n.mjs')
  spawnSync(process.execPath, [resolveScript, openApiPath, join(apiDir, 'linkx-openapi.json')], {
    stdio: 'inherit',
  })

  const clientCol = buildGroupedCollection(
    'LinkX Client API',
    '客户端接口。Apifox 可直接导入。',
    'client',
    'clientAccessToken',
    [{ key: 'clientRefreshToken', value: '' }],
  )
  const adminCol = buildGroupedCollection(
    'LinkX Admin API',
    '管理端接口。Apifox 可直接导入。',
    'admin',
    'accessToken',
    [{ key: 'adminRefreshToken', value: '' }],
  )
  const fullCol = buildFullCollection()
  const smokeCol = buildSmokeScenarios()
  const env = buildEnvironment()

  const clientMeta = writeCollection(join(apiDir, 'linkx-client.postman_collection.json'), clientCol)
  const adminMeta = writeCollection(join(adminDir, 'linkx-admin.postman_collection.json'), adminCol)
  const fullMeta = writeCollection(join(apiDir, 'linkx-full.postman_collection.json'), fullCol)
  writeCollection(join(apiDir, 'linkx-smoke-scenarios.postman_collection.json'), smokeCol)
  writeFileSync(join(apiDir, 'linkx-apifox-environment.json'), JSON.stringify(env, null, 2), 'utf8')

  console.log('Generated Apifox/Postman artifacts:')
  console.log(`  OpenAPI:     docs/api/linkx-openapi.json`)
  console.log(`  Client:      ${clientMeta.total} requests (${clientMeta.folders} folders)`)
  console.log(`  Admin:       ${adminMeta.total} requests (${adminMeta.folders} folders)`)
  console.log(`  Full:        ${fullMeta.total} requests`)
  console.log(`  Smoke:       docs/api/linkx-smoke-scenarios.postman_collection.json`)
  console.log(`  Environment: docs/api/linkx-apifox-environment.json`)
}

// ---- CLI ----
if (generateAll) {
  generateAllArtifacts()
} else if (smokeOnly) {
  const out = outArg || join(repoRoot, 'docs/api/linkx-smoke-scenarios.postman_collection.json')
  writeCollection(out, buildSmokeScenarios())
  console.log(`Wrote ${out}`)
} else if (filter) {
  const configs = {
    admin: {
      name: 'LinkX Admin API',
      desc: '管理端 /admin/**',
      token: 'accessToken',
      extra: [{ key: 'adminRefreshToken', value: '' }],
      defaultOut: join(repoRoot, 'docs/admin/linkx-admin.postman_collection.json'),
    },
    client: {
      name: 'LinkX Client API',
      desc: '客户端接口',
      token: 'clientAccessToken',
      extra: [{ key: 'clientRefreshToken', value: '' }],
      defaultOut: join(repoRoot, 'docs/api/linkx-client.postman_collection.json'),
    },
    all: null,
  }
  if (filter === 'all') {
    const out = outArg || join(repoRoot, 'docs/api/linkx-full.postman_collection.json')
    const meta = writeCollection(out, buildFullCollection())
    console.log(`Wrote ${out} (${meta.total} requests)`)
  } else {
    const cfg = configs[filter]
    if (!cfg) {
      console.error(`Unknown filter: ${filter}. Use admin|client|all`)
      process.exit(1)
    }
    const out = outArg || cfg.defaultOut
    const col = buildGroupedCollection(cfg.name, cfg.desc, filter, cfg.token, cfg.extra)
    const meta = writeCollection(out, col)
    console.log(`Wrote ${out} (${meta.total} requests, ${meta.folders} folders)`)
  }
} else {
  generateAllArtifacts()
}
