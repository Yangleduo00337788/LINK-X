<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from '../../utils/appVersion'
import * as versionApi from '../../api/version'
import { useI18n } from '../../i18n'
import BrandMarkIcon from '../BrandMarkIcon.vue'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()
const checking = ref(false)
const updating = ref(false)
const progressText = ref('')
const supportEmail = ref('')
const supportPhone = ref('')

const hasSupportContact = computed(
  () => !!(supportEmail.value.trim() || supportPhone.value.trim())
)

async function loadSupportContact() {
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION, APP_CLIENT_CHANNEL)
    if (res.code === 200 && res.data) {
      supportEmail.value = (res.data.supportEmail || '').trim()
      supportPhone.value = (res.data.supportPhone || '').trim()
    }
  } catch (e) {
    console.warn('[AboutSettings] 加载客服联系方式失败:', e)
  }
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
    supportEmail.value = (info.supportEmail || '').trim()
    supportPhone.value = (info.supportPhone || '').trim()
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

onMounted(() => {
  void loadSupportContact()
})
</script>

<template>
  <div class="settings-scroll about-scroll">
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

      <div v-if="hasSupportContact" class="about-support">
        <p class="about-support-title">{{ t('about.supportContact') }}</p>
        <p v-if="supportEmail" class="about-support-line">
          <span class="about-support-label">{{ t('about.supportEmail') }}</span>
          <a class="about-support-link" :href="`mailto:${supportEmail}`">{{ supportEmail }}</a>
        </p>
        <p v-if="supportPhone" class="about-support-line">
          <span class="about-support-label">{{ t('about.supportPhone') }}</span>
          <a class="about-support-link" :href="`tel:${supportPhone}`">{{ supportPhone }}</a>
        </p>
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

.about-support {
  position: relative;
  margin: 22px auto 0;
  max-width: 320px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--lx-bg-soft, rgba(0, 0, 0, 0.03));
  border: 1px solid var(--lx-border-light);
  text-align: left;
}

.about-support-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.about-support-line {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--lx-text-secondary);
}

.about-support-label {
  margin-right: 8px;
  color: var(--lx-text-muted);
}

.about-support-link {
  color: var(--lx-accent, #3b82f6);
  text-decoration: none;
  word-break: break-all;
}

.about-support-link:hover {
  text-decoration: underline;
}

.about-copy {
  position: relative;
  margin: 22px 0 0;
  font-size: 12px;
  color: var(--lx-text-muted);
}
</style>
