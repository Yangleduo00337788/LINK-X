<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import logoUrl from '../../src/assets/logo-mark-transparent.png'
import InstallerCheckbox from './InstallerCheckbox.vue'
import { useInstallerI18n } from './useInstallerI18n'
import { resolveInstallerLocale } from '../shared/i18n'

type StepId = 'main' | 'progress' | 'finish'

const WINDOW_WIDTH = 639
const WINDOW_HEIGHT = 477

const { t, setLocale } = useInstallerI18n()

const currentStep = ref<StepId>('main')
const version = ref('1.0.0')
const installDir = ref('')
const removeUserData = ref(true)
const showExitConfirm = ref(false)
const uninstalling = ref(false)
const uninstallError = ref('')
const progressPercent = ref(0)
const progressStatus = ref('')

const api = window.uninstaller

const canUninstall = computed(() => installDir.value.trim().length > 0 && !uninstalling.value)

const progressLabel = computed(() =>
  t('uninstallingProgress', { percent: progressPercent.value.toFixed(2) })
)

let removeProgressListener: (() => void) | undefined

onMounted(async () => {
  api?.setWindowSize?.(WINDOW_WIDTH, WINDOW_HEIGHT)
  progressStatus.value = t('preparingUninstall')

  if (!api) {
    uninstallError.value = t('uninstallApiUnavailable')
    return
  }

  const defaults = await api.getDefaults()
  version.value = defaults.version
  installDir.value = defaults.installDir
  if (defaults.locale) {
    setLocale(resolveInstallerLocale(defaults.locale))
    progressStatus.value = t('preparingUninstall')
  }
  document.title = t('pageTitleUninstall')

  removeProgressListener = api.onProgress(progress => {
    progressPercent.value = progress.percent
    progressStatus.value = progress.status
  })
})

onUnmounted(() => {
  removeProgressListener?.()
})

function requestClose() {
  if (currentStep.value === 'progress') return
  showExitConfirm.value = true
}

function confirmExit() {
  showExitConfirm.value = false
  api?.close()
}

function minimizeWindow() {
  api?.minimize()
}

async function handleUninstall() {
  if (!api || !canUninstall.value) return

  uninstalling.value = true
  uninstallError.value = ''
  currentStep.value = 'progress'
  progressPercent.value = 0
  progressStatus.value = t('preparingUninstall')

  const result = await api.startUninstall({
    removeUserData: removeUserData.value
  })

  uninstalling.value = false
  if (!result.ok) {
    uninstallError.value = result.message || t('uninstallFail')
    currentStep.value = 'main'
    return
  }

  currentStep.value = 'finish'
}

function finishAndClose() {
  api?.close()
}
</script>

<template>
  <div class="installer">
    <header class="installer__titlebar">
      <div class="installer__drag" />
      <div class="installer__window-actions" role="toolbar" aria-label="Window">
        <button class="caption-btn" type="button" :aria-label="t('minimize')" @click="minimizeWindow">
          <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
            <path d="M1 5h8" stroke="currentColor" stroke-width="1.1" fill="none" />
          </svg>
        </button>
        <button class="caption-btn caption-btn--close" type="button" :aria-label="t('close')" @click="requestClose">
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
          <p class="installer__uninstall-hint">{{ t('uninstallConfirm') }}</p>
        </div>

        <button
          class="installer__install-btn installer__install-btn--danger"
          type="button"
          :class="{ 'installer__install-btn--ready': canUninstall }"
          :disabled="!canUninstall"
          @click="handleUninstall"
        >
          {{ t('uninstallNow') }}
        </button>

        <p v-if="uninstallError" class="installer__error">{{ uninstallError }}</p>

        <div class="installer__bottom installer__bottom--compact">
          <div class="installer__footer">
            <InstallerCheckbox v-model="removeUserData">{{ t('removeUserData') }}</InstallerCheckbox>
          </div>
          <p class="installer__path-hint">{{ t('installPath', { path: installDir }) }}</p>
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
          <p class="installer__finish-text">{{ t('uninstallComplete') }}</p>
        </div>

        <button
          class="installer__install-btn installer__install-btn--ready"
          type="button"
          @click="finishAndClose"
        >
          {{ t('done') }}
        </button>
      </template>
    </main>

    <div v-if="showExitConfirm" class="exit-modal" @click.self="showExitConfirm = false">
      <div class="exit-modal__card" role="dialog" aria-modal="true" aria-labelledby="exit-title">
        <button class="exit-modal__close" type="button" :aria-label="t('close')" @click="showExitConfirm = false">
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
        <h2 id="exit-title" class="exit-modal__title">{{ t('exitUninstallTitle') }}</h2>
        <p class="exit-modal__message">{{ t('exitUninstallMessage') }}</p>
        <div class="exit-modal__actions">
          <button class="exit-modal__btn exit-modal__btn--ghost" type="button" @click="confirmExit">
            {{ t('exit') }}
          </button>
          <button class="exit-modal__btn exit-modal__btn--primary" type="button" @click="showExitConfirm = false">
            {{ t('cancel') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
