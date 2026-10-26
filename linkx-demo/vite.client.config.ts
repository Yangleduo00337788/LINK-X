/**
 * 作者：yangleduo
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { demoApiDevPlugin } from './src/mocks/vite-demo-api-plugin.ts'
import { demoSpaEntryPlugin } from './src/mocks/vite-demo-entry-plugin.ts'

const demoRoot = fileURLToPath(new URL('.', import.meta.url))
const repoRoot = path.resolve(demoRoot, '..')
const clientRoot = path.join(repoRoot, 'linkx-client')

export default defineConfig({
  root: clientRoot,
  base: '/client/',
  envDir: demoRoot,
  envPrefix: 'VITE_',
  publicDir: path.join(clientRoot, 'public'),
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: tag => tag === 'webview',
        },
      },
    }),
    UnoCSS(),
    demoApiDevPlugin('client'),
    demoSpaEntryPlugin({ base: '/client' }),
  ],
  resolve: {
    dedupe: ['axios', 'vue', 'vue-router', 'pinia'],
    alias: {
      '@assets': path.join(repoRoot, 'assets'),
      '@demo': path.join(demoRoot, 'src'),
      axios: path.join(demoRoot, 'node_modules/axios/index.js'),
    },
  },
  server: {
    port: 5202,
    strictPort: true,
    host: '127.0.0.1',
    fs: { allow: [repoRoot] },
  },
  build: {
    outDir: path.join(demoRoot, 'dist/client'),
    emptyOutDir: true,
    target: 'es2020',
    rollupOptions: {
      input: { index: path.join(clientRoot, 'demo.html') },
    },
  },
})
