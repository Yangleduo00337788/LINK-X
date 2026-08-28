/**
 * 作者：yangleduo
 */
/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import electron from 'vite-plugin-electron'
import fs from 'node:fs'
import path from 'node:path'

function copyPreloadCjs() {
  const src = path.resolve('electron/preload.cjs')
  const dir = path.resolve('dist-electron/preload')
  const dest = path.join(dir, 'preload.cjs')
  if (!fs.existsSync(src)) return
  fs.mkdirSync(dir, { recursive: true })
  fs.copyFileSync(src, dest)
}

export default defineConfig(({ mode, command }) => {
  const isElectron = mode === 'electron'
  const env = loadEnv(mode, process.cwd(), '')
  const envStr = (key: string) => JSON.stringify(env[key] || '')
  const electronEnvDefine = {
    'process.env.VITE_API_BASE_URL': envStr('VITE_API_BASE_URL'),
    'process.env.VITE_WS_BASE_URL': envStr('VITE_WS_BASE_URL'),
    'process.env.VITE_MINIO_PUBLIC_ORIGIN': envStr('VITE_MINIO_PUBLIC_ORIGIN'),
    'process.env.LINKX_MINIO_PUBLIC_ORIGIN': envStr('LINKX_MINIO_PUBLIC_ORIGIN'),
    'process.env.VITE_OSS_PUBLIC_ORIGIN': envStr('VITE_OSS_PUBLIC_ORIGIN'),
    'process.env.LINKX_OSS_PUBLIC_ORIGIN': envStr('LINKX_OSS_PUBLIC_ORIGIN'),
    'process.env.VITE_COS_PUBLIC_ORIGIN': envStr('VITE_COS_PUBLIC_ORIGIN'),
    'process.env.LINKX_COS_PUBLIC_ORIGIN': envStr('LINKX_COS_PUBLIC_ORIGIN'),
    'process.env.VITE_MEDIA_PUBLIC_ORIGIN': envStr('VITE_MEDIA_PUBLIC_ORIGIN'),
    'process.env.LINKX_MEDIA_PUBLIC_ORIGIN': envStr('LINKX_MEDIA_PUBLIC_ORIGIN'),
    'process.env.VITE_COS_REGION': envStr('VITE_COS_REGION'),
    'process.env.VITE_COS_BUCKET_NAME': envStr('VITE_COS_BUCKET_NAME')
  }

  return {
    base: './',
    resolve: {
      alias: {
        '@assets': path.resolve(__dirname, '../assets')
      }
    },
    // [P2-5] 生产构建移除 console / debugger，减少包体积并避免泄露调试信息
    // esbuild drop 仅在 minify 生效时实际剥离，dev serve 不受影响
    esbuild: command === 'build' ? { drop: ['console', 'debugger'] } : undefined,
    build: {
      // Electron 打包不产出 sourcemap，减小 asar 体积
      sourcemap: command === 'build' && isElectron ? false : undefined,
      target: isElectron ? 'chrome130' : 'es2020',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (id.includes('highlight.js') || id.includes('/marked/')) return 'markdown-vendor'
            // vueuc 单独分包，避免与 naive-ui 核心绑死
            if (id.includes('vueuc')) return 'vueuc'
            if (id.includes('naive-ui')) {
              // 重型组件随 AppShell/聊天页懒加载，登录首屏不拉 virtual-list 等
              if (
                /naive-ui[\\/]es[\\/](data-table|virtual-list|tree|cascader|date-picker|time-picker|calendar|transfer|legacy-grid)/.test(
                  id
                )
              ) {
                return 'naive-ui-heavy'
              }
              return 'naive-ui'
            }
            if (id.includes('@vicons/')) return 'vicons'
            if (
              id.includes('/vue/') ||
              id.includes('vue-router') ||
              id.includes('pinia') ||
              id.includes('@vue/')
            ) {
              return 'vue-vendor'
            }
          }
        }
      }
    },
    plugins: [
      vue({
        template: {
          compilerOptions: {
            isCustomElement: tag => tag === 'webview'
          }
        }
      }),
      UnoCSS(),
      isElectron
        ? {
            name: 'copy-electron-preload-cjs',
            buildStart() {
              copyPreloadCjs()
            }
          }
        : null,
      isElectron
        ? electron([
            {
              entry: 'electron/main.ts',
              onstart: options => {
                copyPreloadCjs()
                const electronLauncher =
                  process.platform === 'win32' ? './electron/dev-path.cjs' : 'electron'
                options.startup(['.', '--no-sandbox'], {}, electronLauncher)
              },
              vite: {
                define: electronEnvDefine,
                build: {
                  sourcemap: false,
                  minify: mode === 'electron',
                  outDir: 'dist-electron/main',
                  rollupOptions: {
                    external: ['electron', 'sql.js']
                  }
                }
              }
            }
          ])
        : null
    ].filter(Boolean),
    test: {
      environment: 'happy-dom',
      include: ['src/**/*.test.ts'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'text-summary', 'json-summary'],
        include: ['src/linkmateAgent/**/*.ts', 'src/stores/linkmateAgent.ts'],
        exclude: [
          'src/linkmateAgent/**/*.test.ts',
          'src/linkmateAgent/test/**',
          'src/linkmateAgent/benchmark/**'
        ]
      }
    }
  }
})
