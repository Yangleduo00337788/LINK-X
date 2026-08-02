/**
 * LinkX Admin E2E — menus / permission guards / button inventory + light clicks.
 */
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:5174'
const API = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const PASSWORD = process.env.ADMIN_PASSWORD || '030903ZWPp'
const HEADLESS = process.env.HEADLESS !== '0'
const OUT_DIR = path.join(__dirname, 'admin-e2e-report')
const SCREEN_DIR = path.join(OUT_DIR, 'screenshots')

const ACCOUNTS = [
  'admin',
  'ops_admin',
  'audit_admin',
  'security_admin',
  'readonly_observer',
]

const ALL_ROUTES = [
  '/admin/dashboard',
  '/admin/users',
  '/admin/blacklist',
  '/admin/devices',
  '/admin/roles',
  '/admin/permissions',
  '/admin/menus',
  '/admin/depts',
  '/admin/audit-logs',
  '/admin/login-logs',
  '/admin/risk-events',
  '/admin/rate-limits',
  '/admin/feedback',
  '/admin/reviews',
  '/admin/reports',
  '/admin/sensitive-words',
  '/admin/notices',
  '/admin/notice-inbox',
  '/admin/banners',
  '/admin/recommends',
  '/admin/activities',
  '/admin/settings',
  '/admin/statistics',
  '/admin/profile',
]

const DESTRUCTIVE_RE =
  /删除|注销|冻|封禁|解封|重置密码|强制下线|下线|发布|撤回|停用|禁用|永久|保存|提交|确认|delete|ban|freeze|reset|publish|kick|disable/i

// Avoid 导出/编辑/查看 — may trigger downloads, step-up, or navigation that stalls headless
const LIGHT_CLICK_RE = /^(查询|搜索|刷新|重置|新增|新建|创建|添加)$/

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const seenBug = new Set()

const report = {
  startedAt: new Date().toISOString(),
  base: BASE,
  accounts: {},
  summary: { accounts: 0, pagesOk: 0, pagesFail: 0, buttonsClicked: 0, bugs: [] },
  knownIssues: [],
}

function ensureDirs() {
  fs.mkdirSync(SCREEN_DIR, { recursive: true })
}

function addBug(account, severity, where, message) {
  const key = `${account}|${severity}|${where}|${message.slice(0, 120)}`
  if (seenBug.has(key)) return
  seenBug.add(key)
  const bug = { account, severity, where, message }
  report.summary.bugs.push(bug)
  report.accounts[account]?.bugs.push(bug)
  console.log(`  [${severity}] ${account} @ ${where}: ${message}`)
}

function flattenMenus(items, out = []) {
  for (const m of items || []) {
    if (m.path && (m.type === 'menu' || m.component)) out.push(m.path)
    if (m.children?.length) flattenMenus(m.children, out)
  }
  return [...new Set(out)]
}

async function apiLogin(username) {
  const res = await fetch(`${API}/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASSWORD }),
  })
  const json = await res.json()
  if (json.code !== 200) throw new Error(`API login ${json.code}: ${json.message}`)
  if (json.data?.requiresTotp) throw new Error('TOTP required')
  const token = json.data.accessToken
  const headers = { Authorization: `Bearer ${token}` }
  const [menusJson, permsJson] = await Promise.all([
    fetch(`${API}/admin/auth/menus`, { headers }).then((r) => r.json()),
    fetch(`${API}/admin/auth/permissions`, { headers }).then((r) => r.json()),
  ])
  return {
    accessToken: token,
    refreshToken: json.data.refreshToken,
    user: json.data.user,
    menus: flattenMenus(menusJson.data || []),
    menuTree: menusJson.data || [],
    permissions: permsJson.data || json.data.user?.permissions || [],
  }
}

async function injectSession(page, session) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 20000 })
  await page.evaluate((s) => {
    localStorage.clear()
    sessionStorage.clear()
    localStorage.setItem('linkx_admin_access_token', s.accessToken)
    localStorage.setItem('linkx_admin_refresh_token', s.refreshToken)
    localStorage.setItem(
      'linkx-admin-auth',
      JSON.stringify({ user: s.user, menus: s.menuTree, permissions: s.permissions }),
    )
  }, session)
  await page.goto(`${BASE}/admin/dashboard`, { waitUntil: 'domcontentloaded', timeout: 20000 })
  await page.waitForSelector('.n-layout-sider', { timeout: 12000 })
  if (page.url().includes('/login')) throw new Error('inject session failed')
}

async function uiLogin(page, username) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 20000 })
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 30000 })
  await page.getByPlaceholder(/管理员账号|账号|username/i).fill(username)
  await page.locator('input[type="password"]').fill(PASSWORD)
  await page.getByRole('button', { name: /登录|Login/i }).click()
  await page.waitForURL((u) => u.pathname.startsWith('/admin'), { timeout: 20000 })
  await page.waitForSelector('.n-layout-sider', { timeout: 12000 })
}

async function expandSidebar(page) {
  const headers = page.locator('.n-layout-sider .n-submenu > .n-menu-item-content')
  const n = Math.min(await headers.count(), 16)
  for (let i = 0; i < n; i++) {
    const el = headers.nth(i)
    if (await el.isVisible().catch(() => false)) {
      await el.click({ timeout: 800 }).catch(() => {})
      await sleep(50)
    }
  }
}

async function sidebarLabels(page) {
  await expandSidebar(page)
  return page.evaluate(() =>
    Array.from(document.querySelectorAll('.n-layout-sider .n-menu-item-content-header'))
      .map((el) => (el.textContent || '').trim())
      .filter(Boolean),
  )
}

async function inventoryButtons(page) {
  return page.evaluate(() => {
    const root = document.querySelector('.n-layout-content') || document.body
    return Array.from(root.querySelectorAll('button'))
      .filter((b) => {
        const st = getComputedStyle(b)
        return st.display !== 'none' && st.visibility !== 'hidden' && b.offsetParent !== null
      })
      .map((b) => ({
        text: (b.innerText || b.getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim().slice(0, 40),
        disabled: !!(b.disabled || b.classList.contains('n-button--disabled') || b.getAttribute('aria-disabled') === 'true'),
      }))
      .filter((b) => b.text)
  })
}

async function lightClicks(page, account, routePath) {
  const clicked = []
  // Exact text match via role to avoid ambiguous/long labels
  const candidates = ['查询', '搜索', '刷新', '重置', '新增', '新建', '创建', '添加']
  for (const text of candidates) {
    if (clicked.length >= 3) break
    const btn = page.locator('.n-layout-content').getByRole('button', { name: text, exact: true }).first()
    if (!(await btn.isVisible().catch(() => false))) continue
    if (!(await btn.isEnabled().catch(() => false))) continue
    try {
      await Promise.race([
        btn.click({ timeout: 700, noWaitAfter: true }),
        sleep(1200).then(() => {
          throw new Error('click soft-timeout')
        }),
      ])
      clicked.push(text)
      report.summary.buttonsClicked++
      await sleep(180)
      await page.keyboard.press('Escape').catch(() => {})
      if (/新增|新建|创建|添加/.test(text)) {
        await sleep(120)
        await page.keyboard.press('Escape').catch(() => {})
        const cancel = page.locator('.n-modal .n-button:has-text("取消"), .n-drawer .n-button:has-text("取消")').first()
        if (await cancel.isVisible().catch(() => false)) {
          await cancel.click({ timeout: 400, noWaitAfter: true }).catch(() => {})
        }
      }
    } catch (e) {
      if (!/soft-timeout/.test(e.message)) {
        addBug(account, 'warn', routePath, `Click "${text}" failed: ${e.message.split('\n')[0].slice(0, 120)}`)
      } else {
        addBug(account, 'warn', routePath, `Click "${text}" soft-timeout (possible hang)`)
      }
      await page.keyboard.press('Escape').catch(() => {})
    }
  }
  return clicked
}

function isLoginPath(urlOrPath) {
  try {
    const p = urlOrPath.startsWith('http') ? new URL(urlOrPath).pathname : urlOrPath
    return p === '/login'
  } catch {
    return false
  }
}

async function ensureAuthed(page, session) {
  // IMPORTANT: do not use includes('/login') — it false-matches /admin/login-logs
  if (isLoginPath(page.url())) {
    await injectSession(page, session)
    return true
  }
  return false
}

async function runAccount(browser, username, { uiLoginFirst }) {
  console.log(`\n======== ${username} ========`)
  const acc = {
    username,
    loginOk: false,
    uiLoginOk: null,
    roles: [],
    apiMenus: [],
    sidebarLabels: [],
    pages: [],
    buttonInventory: {},
    bugs: [],
  }
  report.accounts[username] = acc
  report.summary.accounts++

  let session
  try {
    session = await apiLogin(username)
    acc.roles = session.user?.roles || []
    acc.apiMenus = session.menus
  } catch (e) {
    addBug(username, 'error', '/login', e.message)
    return
  }

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    acceptDownloads: true,
  })
  const page = await context.newPage()
  page.setDefaultTimeout(8000)
  page.setDefaultNavigationTimeout(15000)
  const apiErrors = []
  let i18n = false

  page.on('console', (msg) => {
    if (msg.type() !== 'error') return
    const t = msg.text()
    if (/admin@example\.com|Invalid linked format|Unexpected lexical analysis|Unexpected empty linked key/.test(t)) {
      if (!i18n) {
        i18n = true
        report.knownIssues.push({
          message: "vue-i18n: escape '@' in admin@example.com as {'@'}",
        })
        addBug(username, 'error', 'i18n', "文案含 admin@example.com，vue-i18n 把 @ 解析成 linked message")
      }
    }
  })
  page.on('pageerror', (err) => {
    const m = String(err.message || err)
    if (/admin@example|linked|429|Too Many/i.test(m)) return
    addBug(username, 'warn', 'pageerror', m.slice(0, 180))
  })
  page.on('response', (res) => {
    const url = res.url()
    if (!url.includes('/api/') || url.includes('/events/stream') || url.includes('/media/')) return
    const st = res.status()
    if (st >= 400) apiErrors.push({ st, method: res.request().method(), url, path: new URL(page.url()).pathname })
  })

  try {
    if (uiLoginFirst) {
      await uiLogin(page, username)
      acc.uiLoginOk = true
      console.log('  UI login OK')
    } else {
      await injectSession(page, session)
      console.log('  token session OK')
    }
    acc.loginOk = true
  } catch (e) {
    if (uiLoginFirst) acc.uiLoginOk = false
    addBug(username, 'error', '/login', e.message)
    await page.screenshot({ path: path.join(SCREEN_DIR, `${username}_login_fail.png`) }).catch(() => {})
    await context.close()
    return
  }

  acc.sidebarLabels = await sidebarLabels(page)
  console.log(`  sidebar(${acc.sidebarLabels.length}): ${acc.sidebarLabels.join(' | ')}`)
  if (!acc.sidebarLabels.length && session.menus.length) {
    addBug(username, 'error', 'sidebar', 'API menus present but sidebar empty')
  }

  // Sidebar navigation smoke
  for (const label of acc.sidebarLabels) {
    try {
      await expandSidebar(page)
      const item = page.locator('.n-layout-sider .n-menu-item-content').filter({ hasText: label }).first()
      await item.click({ timeout: 1200, noWaitAfter: true })
      await sleep(300)
      const p = new URL(page.url()).pathname
      if (isLoginPath(p)) {
        addBug(username, 'error', `sidebar:${label}`, 'Navigated to login')
        await injectSession(page, session)
      } else if (p === '/forbidden') {
        addBug(username, 'error', `sidebar:${label}`, 'Navigated to forbidden')
      }
    } catch (e) {
      addBug(username, 'warn', `sidebar:${label}`, e.message.split('\n')[0].slice(0, 120))
    }
  }

  const allowed = new Set([...session.menus, '/admin/profile'])
  if ((session.permissions || []).includes('*')) ALL_ROUTES.forEach((r) => allowed.add(r))

  for (const route of ALL_ROUTES) {
    const mark = apiErrors.length
    console.log(`  probe ${route}`)
    const row = { route, landed: '', ok: true, expected: allowed.has(route), buttons: [], clicked: [] }
    try {
      await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
      await sleep(450)
      if (await ensureAuthed(page, session)) {
        await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
        await sleep(450)
        addBug(username, 'warn', route, 'Had to re-inject session (was on login)')
      }
      const landed = new URL(page.url()).pathname
      row.landed = landed
      const onForbidden = landed === '/forbidden'
      const onLogin = isLoginPath(landed)

      if (onLogin) {
        row.ok = false
        addBug(username, 'error', route, 'Redirected to login')
        await injectSession(page, session)
      } else if (!row.expected) {
        if (!onForbidden && landed === route) {
          row.ok = false
          addBug(username, 'error', route, 'No menu permission but page rendered')
          await page.screenshot({ path: path.join(SCREEN_DIR, `${username}${route.replace(/\//g, '_')}_leak.png`) }).catch(() => {})
        }
      } else if (onForbidden) {
        row.ok = false
        addBug(username, 'error', route, 'Permitted menu hit /forbidden')
      } else if (landed !== route) {
        row.ok = false
        addBug(username, 'error', route, `Redirected to ${landed}`)
      } else {
        row.buttons = await inventoryButtons(page)
        acc.buttonInventory[route] = row.buttons
        // flag disabled-looking primary actions that are unexpectedly disabled for full admin
        row.clicked = await lightClicks(page, username, route)
        // reopen page cleanly after clicks
        await page.keyboard.press('Escape').catch(() => {})
      }

      // API errors on this page
      for (const ae of apiErrors.slice(mark)) {
        const u = ae.url.replace(/^https?:\/\/[^/]+/, '')
        if (ae.st === 429) addBug(username, 'warn', route, `API 429 ${ae.method} ${u}`)
        else if (ae.st >= 500) addBug(username, 'error', route, `API ${ae.st} ${ae.method} ${u}`)
        else if (ae.st === 401) addBug(username, 'error', route, `API 401 ${ae.method} ${u}`)
        else if (ae.st === 403 && row.expected) addBug(username, 'warn', route, `API 403 ${ae.method} ${u}`)
      }
    } catch (e) {
      row.ok = false
      row.landed = new URL(page.url()).pathname
      addBug(username, 'error', route, e.message.split('\n')[0].slice(0, 160))
      await page.keyboard.press('Escape').catch(() => {})
      await ensureAuthed(page, session)
    }

    if (row.ok) report.summary.pagesOk++
    else {
      report.summary.pagesFail++
      await page.screenshot({ path: path.join(SCREEN_DIR, `${username}${route.replace(/\//g, '_')}_fail.png`) }).catch(() => {})
    }
    acc.pages.push(row)
  }

  // Logout check
  try {
    await injectSession(page, session)
    await page.locator('.header .lx-float-btn, .header button').last().click({ timeout: 1500 })
    await sleep(150)
    const lo = page.locator('.n-dropdown-option').filter({ hasText: /退出|Logout/ }).first()
    if (await lo.isVisible().catch(() => false)) {
      await lo.click({ timeout: 1500 })
      await page.waitForURL((u) => u.pathname === '/login', { timeout: 8000 }).catch(() => {})
      if (isLoginPath(page.url())) console.log('  logout OK')
      else addBug(username, 'warn', 'logout', 'Logout did not reach /login')
    }
  } catch {
    /* ignore */
  }

  await context.close()
}

function toMarkdown(r) {
  const L = [
    '# LinkX Admin E2E Crawl Report',
    '',
    `- Started: ${r.startedAt}`,
    `- Finished: ${r.finishedAt}`,
    `- Base: ${r.base}`,
    `- Pages OK/Fail: ${r.summary.pagesOk}/${r.summary.pagesFail}`,
    `- Light button clicks: ${r.summary.buttonsClicked}`,
    `- Errors/Warns: ${r.summary.errorCount}/${r.summary.warnCount}`,
    '',
  ]
  if (r.knownIssues.length) {
    L.push('## Known issues', ...r.knownIssues.map((k) => `- ${k.message}`), '')
  }
  for (const [user, acc] of Object.entries(r.accounts)) {
    L.push(`## ${user}`)
    L.push(`- Login: ${acc.loginOk ? 'OK' : 'FAIL'}${acc.uiLoginOk != null ? ` (UI: ${acc.uiLoginOk ? 'OK' : 'FAIL'})` : ' (token inject)'}`)
    L.push(`- Roles: ${acc.roles.join(', ')}`)
    L.push(`- API menus (${acc.apiMenus.length}): ${acc.apiMenus.join(', ')}`)
    L.push(`- Sidebar (${acc.sidebarLabels.length}): ${acc.sidebarLabels.join(' | ') || '(none)'}`)
    L.push(`- Bugs: ${acc.bugs.length}`)
    // sample buttons on a few pages
    const sampleRoutes = Object.keys(acc.buttonInventory).slice(0, 6)
    if (sampleRoutes.length) {
      L.push('- Button samples:')
      for (const rt of sampleRoutes) {
        const btns = acc.buttonInventory[rt] || []
        const summary = btns.slice(0, 10).map((b) => `${b.text}${b.disabled ? '(disabled)' : ''}`).join(', ')
        L.push(`  - ${rt}: ${summary || '(none)'}`)
      }
    }
    if (acc.bugs.length) {
      L.push('', '| Severity | Where | Message |', '| --- | --- | --- |')
      for (const b of acc.bugs) {
        L.push(`| ${b.severity} | ${b.where} | ${String(b.message).replace(/\|/g, '\\|').replace(/\n/g, ' ').slice(0, 220)} |`)
      }
    }
    const fails = acc.pages.filter((p) => !p.ok)
    if (fails.length) L.push('', `Failed pages: ${fails.map((p) => `${p.route}→${p.landed}`).join(', ')}`)
    L.push('')
  }
  L.push('## All bugs')
  if (!r.summary.bugs.length) L.push('_None_')
  else {
    L.push('', '| Account | Severity | Where | Message |', '| --- | --- | --- | --- |')
    for (const b of r.summary.bugs) {
      L.push(`| ${b.account} | ${b.severity} | ${b.where} | ${String(b.message).replace(/\|/g, '\\|').replace(/\n/g, ' ').slice(0, 220)} |`)
    }
  }
  return L.join('\n') + '\n'
}

async function main() {
  ensureDirs()
  console.log(`Admin E2E → ${BASE} headless=${HEADLESS}`)
  if (!(await fetch(`${BASE}/login`).then((r) => r.ok).catch(() => false))) {
    console.error('Admin UI not reachable')
    process.exit(2)
  }

  // Prefer clearing biz rate counters + ensuring local whitelist via docker redis (best-effort)
  try {
    const { execSync } = await import('node:child_process')
    execSync(
      'docker exec redis redis-cli -a redis123 --no-auth-warning SADD linkx:rate:whitelist 127.0.0.1 ::1',
      { stdio: 'ignore' },
    )
  } catch {
    /* optional */
  }

  const browser = await chromium.launch({ headless: HEADLESS })
  try {
    for (let i = 0; i < ACCOUNTS.length; i++) {
      // Full UI login for every admin account
      await runAccount(browser, ACCOUNTS[i], { uiLoginFirst: true })
      if (i < ACCOUNTS.length - 1) {
        console.log('  ... wait 2s ...')
        await sleep(2000)
      }
    }
  } finally {
    await browser.close()
  }

  report.finishedAt = new Date().toISOString()
  report.summary.errorCount = report.summary.bugs.filter((b) => b.severity === 'error').length
  report.summary.warnCount = report.summary.bugs.filter((b) => b.severity === 'warn').length
  fs.writeFileSync(path.join(OUT_DIR, 'report.json'), JSON.stringify(report, null, 2))
  fs.writeFileSync(path.join(OUT_DIR, 'report.md'), toMarkdown(report))
  // also copy to scripts folder for convenience
  try {
    const scriptsDir = path.join(__dirname, '..', 'scripts', 'admin-e2e-report')
    fs.mkdirSync(scriptsDir, { recursive: true })
    fs.copyFileSync(path.join(OUT_DIR, 'report.md'), path.join(scriptsDir, 'report.md'))
    fs.copyFileSync(path.join(OUT_DIR, 'report.json'), path.join(scriptsDir, 'report.json'))
  } catch {
    /* ignore */
  }

  console.log('\n======== SUMMARY ========')
  console.log(`Pages OK/Fail: ${report.summary.pagesOk}/${report.summary.pagesFail}`)
  console.log(`Buttons clicked: ${report.summary.buttonsClicked}`)
  console.log(`Bugs: ${report.summary.errorCount} errors / ${report.summary.warnCount} warns`)
  console.log(`Report: ${path.join(OUT_DIR, 'report.md')}`)
  if (report.summary.errorCount > 0) process.exitCode = 1
}

main().catch((e) => {
  console.error(e)
  process.exit(2)
})
