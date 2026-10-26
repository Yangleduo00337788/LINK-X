/**
 * 作者：yangleduo
 */
import { build } from 'vite'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = fileURLToPath(new URL('..', import.meta.url))

async function run() {
  console.log('[linkx-demo] build portal …')
  await build({ configFile: path.join(demoRoot, 'vite.portal.config.ts') })

  console.log('[linkx-demo] build admin …')
  await build({ configFile: path.join(demoRoot, 'vite.admin.config.ts') })

  console.log('[linkx-demo] build client …')
  await build({ configFile: path.join(demoRoot, 'vite.client.config.ts') })

  console.log('[linkx-demo] done → dist/')
}

run().catch(err => {
  console.error(err)
  process.exit(1)
})
