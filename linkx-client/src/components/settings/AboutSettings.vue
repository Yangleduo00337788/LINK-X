<script setup lang="ts">
import { ref } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import { APP_CLIENT_VERSION } from '../../utils/appVersion'
import * as versionApi from '../../api/version'
import { useI18n } from '../../i18n'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const checking = ref(false)
const updating = ref(false)
const progressText = ref('')

/**
 * 发现新版本后自动下载并拉起安装。
 * Electron：主进程下载到临时目录后 openPath；Web：打开下载链接。
 */
async function startDownloadAndInstall(info: {
  version: string
  downloadUrl: string
  releaseNotes?: string
}) {
  const url = (info.downloadUrl || '').trim()
  if (!url) {
    message.warning(t('about.noDownloadUrl'))
    return
  }

  updating.value = true
  progressText.value = t('about.downloading')

  const unsub = window.electronAPI?.onUpdateProgress?.(data => {
    if (data.phase === 'installing') {
      progressText.value = t('about.installing')
    } else {
      progressText.value = t('about.downloading')
    }
  })

  try {
    if (window.electronAPI?.downloadAndInstallUpdate) {
      const result = await window.electronAPI.downloadAndInstallUpdate({
        url,
        version: info.version
      })
      if (!result.ok) {
        message.error(result.message || t('about.installFail'))
        return
      }
      if (result.launched) {
        message.success(t('about.installStarted'))
      } else {
        message.success(result.message || t('about.downloadReady'))
      }
      return
    }

    // Web：触发浏览器下载 / 打开安装包地址
    const a = document.createElement('a')
    a.href = url
    a.target = '_blank'
    a.rel = 'noopener'
    a.download = ''
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    message.success(t('about.webDownloadStarted'))
  } catch (e) {
    console.warn('[AboutSettings] 下载安装失败:', e)
    message.error(t('about.installFail'))
  } finally {
    unsub?.()
    updating.value = false
    progressText.value = ''
  }
}

async function checkUpdate() {
  if (checking.value || updating.value) return
  checking.value = true
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION)
    if (res.code !== 200 || !res.data) {
      message.error(res.message || t('about.checkFail'))
      return
    }
    const info = res.data
    if (!info.hasUpdate) {
      message.success(t('about.latest', { version: info.version }))
      return
    }

    const notes = (info.releaseNotes || '').trim()
    dialog.warning({
      title: t('about.updateTitle'),
      content:
        t('about.found', { version: info.version, notes: notes || t('about.noNotes') }) +
        '\n\n' +
        t('about.autoInstallHint'),
      positiveText: t('about.downloadInstall'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => {
        void startDownloadAndInstall(info)
      }
    })
  } catch (e) {
    console.warn('[AboutSettings] 检查更新失败:', e)
    message.error(t('about.checkFailRetry'))
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <div class="settings-scroll about-scroll">
    <section class="about-card">
      <div class="about-glow" />
      <img src="../../assets/logo-linkx.svg" alt="LinkX" class="about-logo" />
      <h3 class="about-name">LinkX</h3>
      <p class="about-ver">Version {{ APP_CLIENT_VERSION }} · Beta</p>
      <p class="about-desc">{{ t('about.desc') }}</p>
      <div class="about-actions">
        <n-button
          type="primary"
          :loading="checking || updating"
          :disabled="checking || updating"
          @click="checkUpdate"
        >
          {{ updating ? progressText || t('about.downloading') : t('about.checkUpdate') }}
        </n-button>
      </div>
      <p class="about-copy">© 2026 LinkX Team</p>
    </section>
  </div>
</template>

<style scoped>
@import './settings-common.css';

.about-scroll {
  justify-content: center;
}

.about-card {
  position: relative;
  text-align: center;
  padding: 36px 24px 28px;
  border-radius: 12px;
  background: var(--lx-bg-panel);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
}

.about-glow {
  position: absolute;
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--lx-accent-soft), transparent 70%);
  pointer-events: none;
}

.about-logo {
  position: relative;
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
}

.about-name {
  position: relative;
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--lx-text-body);
}

.about-ver {
  position: relative;
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.about-desc {
  position: relative;
  margin: 14px auto 0;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--lx-text-secondary);
}

.about-actions {
  position: relative;
  margin-top: 20px;
}

.about-copy {
  position: relative;
  margin: 22px 0 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}
</style>
