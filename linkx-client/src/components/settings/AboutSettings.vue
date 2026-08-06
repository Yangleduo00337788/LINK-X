<script setup lang="ts">
import { ref } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from '../../utils/appVersion'
import * as versionApi from '../../api/version'
import { useI18n } from '../../i18n'
import { openLegalPageInBrowser } from '../../utils/legalPage'
import BrandMarkIcon from '../BrandMarkIcon.vue'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const checking = ref(false)
const updating = ref(false)
const progressText = ref('')

function openServiceAgreement() {
  void openLegalPageInBrowser('service')
}

function openPrivacyPolicy() {
  void openLegalPageInBrowser('privacy')
}

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

function showUpdateDialog(info: versionApi.AppVersion) {
  const notes = (info.releaseNotes || '').trim()
  const force = info.forceUpdate === true
  dialog.warning({
    title: force ? t('about.forceUpdateTitle') : t('about.updateTitle'),
    content:
      t('about.found', { version: info.version, notes: notes || t('about.noNotes') }) +
      '\n\n' +
      (force ? t('about.forceUpdateHint') : t('about.autoInstallHint')),
    positiveText: t('about.downloadInstall'),
    negativeText: force ? undefined : t('common.cancel'),
    closable: !force,
    maskClosable: !force,
    closeOnEsc: !force,
    onPositiveClick: () => {
      void startDownloadAndInstall(info)
    }
  })
}

async function checkUpdate() {
  if (checking.value || updating.value) return
  checking.value = true
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION, APP_CLIENT_CHANNEL)
    if (res.code !== 200 || !res.data) {
      message.error(res.message || t('about.checkFail'))
      return
    }
    const info = res.data
    if (!info.hasUpdate) {
      message.success(t('about.latest', { version: info.version }))
      return
    }
    showUpdateDialog(info)
  } catch (e) {
    console.warn('[AboutSettings] 检查更新失败:', e)
    message.error(t('about.checkFailRetry'))
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <div class="about-page">
    <section class="about-card">
      <div class="about-glow" />
      <div class="about-logo">
        <BrandMarkIcon :size="72" />
      </div>
      <h3 class="about-name">LinkX</h3>
      <p class="about-ver">Version {{ APP_CLIENT_VERSION }} · {{ APP_CLIENT_CHANNEL }}</p>
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

      <footer class="about-legal">
        <div class="about-legal-links">
          <button type="button" class="about-legal-link" @click="openServiceAgreement">
            {{ t('about.serviceAgreement') }}
          </button>
          <span class="about-legal-sep">·</span>
          <button type="button" class="about-legal-link" @click="openPrivacyPolicy">
            {{ t('about.privacyPolicy') }}
          </button>
        </div>
        <p class="about-legal-brand">{{ t('about.companyRights') }}</p>
        <p class="about-legal-copy">{{ t('about.copyright') }}</p>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.about-page {
  width: 100%;
  max-width: 720px;
  padding: 0 28px 24px;
  box-sizing: border-box;
}

.about-card {
  position: relative;
  text-align: center;
  padding: 36px 24px 28px;
  border-radius: 12px;
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
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
  display: flex;
  align-items: center;
  justify-content: center;
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

.about-legal {
  position: relative;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid var(--lx-border-light);
}

.about-legal-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
}

.about-legal-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--lx-accent);
  cursor: pointer;
}

.about-legal-link:hover {
  text-decoration: underline;
}

.about-legal-sep {
  color: var(--lx-text-muted);
  font-size: 12px;
  line-height: 1;
}

.about-legal-brand {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--lx-text-secondary);
}

.about-legal-copy {
  margin: 0;
  font-size: 11px;
  color: var(--lx-text-muted);
  line-height: 1.5;
}
</style>
