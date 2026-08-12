<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from '../../utils/appVersion'
import * as versionApi from '../../api/version'
import { useI18n } from '../../i18n'
import { openLegalPageInBrowser } from '../../utils/legalPage'
import { openHelpPageInBrowser } from '../../utils/helpPage'
import BrandMarkIcon from '../BrandMarkIcon.vue'
import { LxButton } from '../ui'

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

function openHelpCenter() {
  void openHelpPageInBrowser()
}

/**
 * 发现新版本后静默下载并安装（Windows 下 /S），安装完成后由 NSIS 自动启动 LinkX。
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
    progressText.value =
      data.phase === 'installing' ? t('about.installing') : t('about.downloading')
  })

  try {
    if (window.electronAPI?.downloadAndInstallUpdate) {
      const result = await window.electronAPI.downloadAndInstallUpdate({
        url,
        version: info.version,
        silent: true
      })
      if (!result.ok) {
        message.error(result.message || t('about.installFail'))
        return
      }
      if (result.launched && result.silent) {
        progressText.value = t('about.silentInstallHint')
        return
      }
      if (result.launched) {
        progressText.value = t('about.installStarted')
        return
      }
      message.info(result.message || t('about.downloadReady'))
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
    if (!window.electronAPI?.downloadAndInstallUpdate) {
      updating.value = false
      progressText.value = ''
    }
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
        <LxButton
          variant="primary-comfortable"
          :disabled="checking || updating"
          @click="checkUpdate"
        >
          {{ updating ? progressText || t('about.downloading') : t('about.checkUpdate') }}
        </LxButton>
      </div>

      <footer class="about-legal">
        <div class="about-legal-links">
          <LxButton variant="link-md" class="about-legal-link" @click="openServiceAgreement">
            {{ t('about.serviceAgreement') }}
          </LxButton>
          <span class="about-legal-sep">·</span>
          <LxButton variant="link-md" class="about-legal-link" @click="openPrivacyPolicy">
            {{ t('about.privacyPolicy') }}
          </LxButton>
          <span class="about-legal-sep">·</span>
          <LxButton variant="link-md" class="about-legal-link" @click="openHelpCenter">
            {{ t('about.helpCenter') }}
          </LxButton>
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
  padding: 0 var(--lx-space-5xl-minus) var(--lx-space-4xl);
  box-sizing: border-box;
}

.about-card {
  position: relative;
  text-align: center;
  padding: var(--lx-space-6xl-minus) var(--lx-space-4xl) var(--lx-space-5xl-minus);
  border-radius: var(--lx-radius-lg);
  background: var(--lx-bg-card);
  border: 1px solid var(--lx-border-light);
  overflow: hidden;
  box-shadow: var(--lx-shadow-xs);
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
  margin: 0 auto var(--lx-space-lg);
}

.about-name {
  position: relative;
  margin: 0;
  font-size: var(--lx-font-5xl);
  font-weight: 700;
  color: var(--lx-text-body);
}

.about-ver {
  position: relative;
  margin: var(--lx-space-sm) 0 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-muted);
}

.about-desc {
  position: relative;
  margin: var(--lx-space-xl) auto 0;
  max-width: 320px;
  font-size: var(--lx-font-md);
  line-height: var(--lx-leading-normal);
  color: var(--lx-text-secondary);
}

.about-actions {
  position: relative;
  margin-top: var(--lx-space-3xl);
}

.about-legal {
  position: relative;
  margin-top: var(--lx-space-5xl-minus);
  padding-top: var(--lx-space-2xl);
  border-top: 1px solid var(--lx-border-light);
}

.about-legal-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lx-space-md);
  margin-bottom: var(--lx-space-lg);
}

.about-legal-link {
  font-size: var(--lx-font-md);
}

.about-legal-link:hover {
  text-decoration: underline;
}

.about-legal-sep {
  color: var(--lx-text-muted);
  font-size: var(--lx-font-sm);
  line-height: var(--lx-leading-none);
}

.about-legal-brand {
  margin: 0 0 var(--lx-space-sm);
  font-size: var(--lx-font-sm);
  color: var(--lx-text-secondary);
}

.about-legal-copy {
  margin: 0;
  font-size: var(--lx-font-xs);
  color: var(--lx-text-muted);
  line-height: var(--lx-leading-normal);
}
</style>
