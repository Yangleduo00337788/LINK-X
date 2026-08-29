/**
 * 作者：yangleduo
 */
import { defineConfig } from 'vite'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dir = path.dirname(fileURLToPath(import.meta.url))
const clientRoot = path.resolve(dir, '..')

export default defineConfig({
  root: path.resolve(dir, 'fixtures'),
  server: {
    port: 5199,
    strictPort: true,
    fs: {
      allow: [clientRoot, path.resolve(clientRoot, '../assets')]
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(clientRoot, 'src'),
      '@assets': path.resolve(clientRoot, '../assets')
    }
  }
})
