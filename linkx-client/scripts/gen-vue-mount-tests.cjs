const fs = require('fs')
const path = require('path')

const root = path.join(__dirname, '../src')
const outDir = path.join(root, 'components/__tests__')
const outFile = path.join(outDir, 'allComponents.mount.test.ts')

function walk(dir, acc = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) {
      if (ent.name === '__tests__' || ent.name === 'types') continue
      walk(p, acc)
    } else if (ent.name.endsWith('.vue')) {
      acc.push(p)
    }
  }
  return acc
}

const vueFiles = [
  ...walk(path.join(root, 'components')),
  path.join(root, 'App.vue'),
  path.join(root, 'AppRoot.vue'),
  path.join(root, 'views/HomeView.vue')
].filter((p) => fs.existsSync(p))

let body = `import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(async () => ({ code: 200, data: null })),
    post: vi.fn(async () => ({ code: 200, data: null })),
    put: vi.fn(async () => ({ code: 200, data: null })),
    delete: vi.fn(async () => ({ code: 200, data: null })),
    patch: vi.fn(async () => ({ code: 200, data: null }))
  }
}))

vi.mock('@/utils/chatSocket', () => ({
  connectChatSocket: vi.fn(),
  disconnectChatSocket: vi.fn(),
  sendChatSocket: vi.fn(),
  onChatEvent: vi.fn(() => () => {}),
  isChatSocketConnected: vi.fn(() => false)
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }]
})

describe('all vue components mount smoke', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

`

for (const abs of vueFiles) {
  const rel = path.relative(root, abs).split(path.sep).join('/')
  const importVue = '@/' + rel
  body += `  it('mount ${rel}', async () => {
    const mod = await import('${importVue}')
    const Comp = mod.default
    const wrapper = mount(Comp, {
      global: {
        plugins: [createPinia(), router],
        stubs: true
      },
      props: {},
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

`
}

body += `})
`

fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(outFile, body)
console.log('wrote', outFile, 'components', vueFiles.length)
