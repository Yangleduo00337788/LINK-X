/**
 * 作者：yangleduo
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { demoApiDevPlugin } from './src/mocks/vite-demo-api-plugin.ts'

const demoRoot = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  root: path.join(demoRoot, 'portal'),
  publicDir: path.join(demoRoot, 'portal/public'),
  base: '/',
  plugins: [vue(), demoApiDevPlugin('portal')],
  resolve: {
    alias: {
      '@demo': path.join(demoRoot, 'src'),
    },
  },
  build: {
    outDir: path.join(demoRoot, 'dist'),
    emptyOutDir: true,
  },
  server: {
    port: 5200,
    strictPort: true,
    host: '127.0.0.1',
    proxy: {
      '/admin': { target: 'http://127.0.0.1:5201', changeOrigin: true },
      '/client': { target: 'http://127.0.0.1:5202', changeOrigin: true },
      // iframe 在 5200 同源下请求 /api 时由 portal 的 demoApiDevPlugin 处理，勿再代理走丢
    },
  },
})
