<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import logoUrl from '../../src/assets/logo-mark-transparent.png'
import InstallerCheckbox from './InstallerCheckbox.vue'

type StepId = 'main' | 'progress' | 'finish'

const WINDOW_WIDTH = 639
const WINDOW_HEIGHT = 477
const WINDOW_HEIGHT_EXPANDED = 580

const currentStep = ref<StepId>('main')
const version = ref('1.0.0')
const installDir = ref('')
const desktopShortcut = ref(true)
const startMenuShortcut = ref(true)
const autoStartOnBoot = ref(false)
const launchAfter = ref(true)
const acceptedLicense = ref(false)
const showCustomOptions = ref(false)
const showExitConfirm = ref(false)
const installing = ref(false)
const installError = ref('')
const progressPercent = ref(0)
const progressStatus = ref('准备安装...')

const api = window.installer

const canInstall = computed(
  () => acceptedLicense.value && installDir.value.trim().length > 0 && !installing.value
)

const progressLabel = computed(() => {
  const percent = progressPercent.value.toFixed(2)
  return `正在安装 LinkX，请稍候... ${percent}%`
})

let removeProgressListener: (() => void) | undefined

function syncWindowSize() {
  if (!api?.setWindowSize || currentStep.value !== 'main') return
  const height = showCustomOptions.value ? WINDOW_HEIGHT_EXPANDED : WINDOW_HEIGHT
  api.setWindowSize(WINDOW_WIDTH, height)
}

onMounted(async () => {
  api?.setWindowSize?.(WINDOW_WIDTH, WINDOW_HEIGHT)

  if (!api) {
    installError.value = '安装程序接口不可用'
    return
  }

  const defaults = await api.getDefaults()
  version.value = defaults.version
  installDir.value = defaults.defaultDir

  removeProgressListener = api.onProgress(progress => {
    progressPercent.value = progress.percent
    progressStatus.value = progress.status
  })
})

onUnmounted(() => {
  removeProgressListener?.()
})

watch(showCustomOptions, () => {
  syncWindowSize()
})

function toggleCustomOptions() {
  showCustomOptions.value = !showCustomOptions.value
}

function requestClose() {
  if (currentStep.value === 'progress') return
  showExitConfirm.value = true
}

function confirmExit() {
  showExitConfirm.value = false
  api?.close()
}

function minimizeInstaller() {
  api?.minimize()
}

function openLegalPage(kind: 'service' | 'privacy') {
  api?.openLegal?.(kind)
}

async function browseDirectory() {
  if (!api) return
  const selected = await api.browseDirectory(installDir.value)
  if (selected) installDir.value = selected
}

async function handleInstall() {
  if (!api || !canInstall.value) return

  installing.value = true
  installError.value = ''
  currentStep.value = 'progress'
  progressPercent.value = 0
  progressStatus.value = '准备安装...'
  api.setWindowSize?.(WINDOW_WIDTH, WINDOW_HEIGHT)

  const result = await api.startInstall({
    installDir: installDir.value.trim(),
    desktopShortcut: desktopShortcut.value,
    startMenuShortcut: startMenuShortcut.value,
    autoStartOnBoot: autoStartOnBoot.value,
    launchAfter: false
  })

  installing.value = false
  if (!result.ok) {
    installError.value = result.message || '安装失败'
    currentStep.value = 'main'
    syncWindowSize()
    return
  }

  currentStep.value = 'finish'
  if (launchAfter.value) {
    await api.launchApp()
    api.close()
  }
}

async function finishAndLaunch() {
  if (!api) return
  await api.launchApp()
  api.close()
}
</script>

<template>
  <div class="installer">
    <header class="installer__titlebar">
      <div class="installer__drag" />
      <div class="installer__window-actions" role="toolbar" aria-label="Window">
        <button class="caption-btn" type="button" aria-label="最小化" @click="minimizeInstaller">
          <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
            <path d="M1 5h8" stroke="currentColor" stroke-width="1.1" fill="none" />
          </svg>
        </button>
        <button class="caption-btn caption-btn--close" type="button" aria-label="关闭" @click="requestClose">
          <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
            <path
              d="M2 2l6 6M8 2L2 8"
              stroke="currentColor"
              stroke-width="1.2"
              fill="none"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>
    </header>

    <main class="installer__main">
      <template v-if="currentStep === 'main'">
        <div class="installer__brand-block">
          <div class="installer__logo-wrap">
            <img class="installer__logo" :src="logoUrl" alt="LinkX" />
          </div>
          <h1 class="installer__brand">LinkX</h1>
          <p class="installer__version">v{{ version }}</p>
        </div>

        <button
          class="installer__install-btn"
          type="button"
          :class="{ 'installer__install-btn--ready': canInstall }"
          :disabled="!canInstall"
          @click="handleInstall"
        >
          立即安装
        </button>

        <p v-if="installError" class="installer__error">{{ installError }}</p>

        <div class="installer__bottom">
          <div class="installer__footer">
            <InstallerCheckbox v-model="acceptedLicense" class="agreement-row">
              <span class="agreement-row__text">
                阅读并同意
                <span class="agreement-gap" aria-hidden="true"> </span>
                <button
                  type="button"
                  class="agreement-link"
                  @click.stop="openLegalPage('service')"
                >
                  用户协议
                </button>
                <span class="agreement-gap" aria-hidden="true"> </span>
                <button
                  type="button"
                  class="agreement-link"
                  @click.stop="openLegalPage('privacy')"
                >
                  隐私条款
                </button>
              </span>
            </InstallerCheckbox>

            <button class="custom-toggle" type="button" @click="toggleCustomOptions">
              <span>自定义安装</span>
              <svg
                class="custom-toggle__icon"
                :class="{ 'custom-toggle__icon--open': showCustomOptions }"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
              >
                <path
                  d="M6 9l6 6 6-6"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </div>

          <div v-if="showCustomOptions" class="custom-panel">
              <div class="path-field">
                <input v-model="installDir" class="path-field__input" type="text" spellcheck="false" />
                <button class="path-field__browse" type="button" aria-label="浏览目录" @click="browseDirectory">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path
                      d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5V18a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18V7.5Z"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>
              </div>

            <div class="option-row">
              <InstallerCheckbox v-model="startMenuShortcut">添加到开始菜单</InstallerCheckbox>
              <InstallerCheckbox v-model="desktopShortcut">添加到桌面快捷方式</InstallerCheckbox>
              <InstallerCheckbox v-model="autoStartOnBoot">开机自启动</InstallerCheckbox>
              <InstallerCheckbox v-model="launchAfter">安装完成后启动</InstallerCheckbox>
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="currentStep === 'progress'">
        <div class="installer__brand-block installer__brand-block--compact">
          <div class="installer__logo-wrap">
            <img class="installer__logo" :src="logoUrl" alt="LinkX" />
          </div>
          <h1 class="installer__brand">LinkX</h1>
        </div>

        <div class="installer__progress">
          <p class="installer__progress-text">{{ progressLabel }}</p>
          <div class="installer__progress-bar">
            <div class="installer__progress-fill" :style="{ width: `${progressPercent}%` }" />
          </div>
          <p class="installer__progress-status">{{ progressStatus }}</p>
        </div>
      </template>

      <template v-else>
        <div class="installer__brand-block">
          <div class="installer__logo-wrap">
            <img class="installer__logo" :src="logoUrl" alt="LinkX" />
          </div>
          <h1 class="installer__brand">LinkX</h1>
          <p class="installer__finish-text">安装完成</p>
        </div>

        <button class="installer__install-btn installer__install-btn--ready" type="button" @click="finishAndLaunch">
          立即体验
        </button>
      </template>
    </main>

    <div v-if="showExitConfirm" class="exit-modal" @click.self="showExitConfirm = false">
      <div class="exit-modal__card" role="dialog" aria-modal="true" aria-labelledby="exit-title">
        <button class="exit-modal__close" type="button" aria-label="关闭" @click="showExitConfirm = false">
          <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
            <path
              d="M2 2l6 6M8 2L2 8"
              stroke="currentColor"
              stroke-width="1.2"
              fill="none"
              stroke-linecap="round"
            />
          </svg>
        </button>
        <h2 id="exit-title" class="exit-modal__title">退出安装</h2>
        <p class="exit-modal__message">确定要退出安装 LinkX 程序吗？</p>
        <div class="exit-modal__actions">
          <button class="exit-modal__btn exit-modal__btn--ghost" type="button" @click="confirmExit">
            退出
          </button>
          <button class="exit-modal__btn exit-modal__btn--primary" type="button" @click="showExitConfirm = false">
            取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
