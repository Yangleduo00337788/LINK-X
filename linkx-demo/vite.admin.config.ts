/**
 * 作者：yangleduo
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { demoApiDevPlugin } from './src/mocks/vite-demo-api-plugin.ts'
import { demoSpaEntryPlugin } from './src/mocks/vite-demo-entry-plugin.ts'

const demoRoot = fileURLToPath(new URL('.', import.meta.url))
const repoRoot = path.resolve(demoRoot, '..')
const adminRoot = path.join(repoRoot, 'linkx-admin')

export default defineConfig({
  root: adminRoot,
  base: '/admin/',
  envDir: demoRoot,
  envPrefix: 'VITE_',
  publicDir: path.join(adminRoot, 'public'),
  plugins: [vue(), demoApiDevPlugin('admin'), demoSpaEntryPlugin({ base: '/admin' })],
  resolve: {
    dedupe: ['axios', 'vue', 'vue-router', 'pinia'],
    alias: {
      '@': path.join(adminRoot, 'src'),
      '@demo': path.join(demoRoot, 'src'),
      '@linkx-client': path.join(repoRoot, 'linkx-client/src'),
      axios: path.join(demoRoot, 'node_modules/axios/index.js'),
    },
  },
  server: {
    port: 5201,
    strictPort: true,
    host: '127.0.0.1',
    fs: { allow: [repoRoot] },
  },
  build: {
    outDir: path.join(demoRoot, 'dist/admin'),
    emptyOutDir: true,
    rollupOptions: {
      input: { index: path.join(adminRoot, 'demo.html') },
    },
  },
})
