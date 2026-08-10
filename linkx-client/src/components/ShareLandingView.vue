<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 云盘公开分享落地页（免登录）。
 * 路由：#/share/:token
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NSpin, useMessage } from 'naive-ui'
import { getPublicShare, type DriveShareVO } from '../api/drive'
import { downloadShareContent } from '../utils/authDownload'
import { formatFileSize } from '../utils/file'
import { useI18n } from '../i18n'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()

const token = computed(() => String(route.params.token || '').trim())
const share = ref<DriveShareVO | null>(null)
const loading = ref(false)
const needPassword = ref(false)
const password = ref('')
const unlockedPassword = ref<string | undefined>()
const downloading = ref(false)
const errorText = ref('')

const IMAGE_EXT = /\.(png|jpe?g|gif|webp|bmp|svg)$/i

const isImage = computed(() => {
  const name = share.value?.targetName || ''
  return !!share.value?.fileUrl && IMAGE_EXT.test(name)
})

const metaLine = computed(() => {
  const s = share.value
  if (!s) return ''
  const parts: string[] = []
  parts.push(s.shareType === 'folder' ? t('shareLanding.folder') : t('shareLanding.file'))
  if (s.fileSize != null && s.fileSize > 0) parts.push(formatFileSize(s.fileSize))
  if (s.expireAt) {
    parts.push(t('shareLanding.expiresAt', { time: new Date(s.expireAt).toLocaleString() }))
  }
  return parts.join(' · ')
})

async function loadShare(pwd?: string) {
  if (!token.value) {
    errorText.value = t('shareLanding.invalidLink')
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const res = await getPublicShare(token.value, pwd)
    if (res.code === 200 && res.data) {
      share.value = res.data
      needPassword.value = false
      unlockedPassword.value = pwd
      return
    }
    if (res.code === 403) {
      share.value = null
      needPassword.value = true
      if (pwd) {
        errorText.value = res.message || t('shareLanding.passwordWrong')
      }
      return
    }
    if (res.code === 429) {
      errorText.value = res.message || t('shareLanding.tooManyAttempts')
      needPassword.value = true
      return
    }
    errorText.value = res.message || t('shareLanding.loadFail')
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : t('shareLanding.loadFail')
  } finally {
    loading.value = false
  }
}

async function submitPassword() {
  const pwd = password.value.trim()
  if (!pwd) {
    message.warning(t('files.sharePasswordRequired'))
    return
  }
  await loadShare(pwd)
}

async function handleDownload() {
  const s = share.value
  if (!s || !token.value) return
  if (s.shareType === 'folder') {
    message.warning(t('shareLanding.folderNoDownload'))
    return
  }
  downloading.value = true
  try {
    const result = await downloadShareContent(
      token.value,
      s.targetName || 'download',
      unlockedPassword.value
    )
    if (!result.ok) {
      message.error(result.message || t('shareLanding.downloadFail'))
    }
  } finally {
    downloading.value = false
  }
}

function goHome() {
  void router.replace('/')
}

watch(
  token,
  value => {
    share.value = null
    needPassword.value = false
    password.value = ''
    unlockedPassword.value = undefined
    errorText.value = ''
    if (value) void loadShare()
  },
  { immediate: true }
)
</script>

<template>
  <div class="share-page">
    <div class="share-card">
      <h1 class="brand">LinkX</h1>
      <p class="subtitle">{{ t('shareLanding.title') }}</p>

      <div v-if="loading" class="state">
        <n-spin size="medium" />
      </div>

      <template v-else-if="needPassword && !share">
        <p class="hint">{{ t('shareLanding.passwordHint') }}</p>
        <n-input
          v-model:value="password"
          type="password"
          show-password-on="click"
          :placeholder="t('files.sharePasswordPrompt')"
          maxlength="32"
          @keydown.enter="submitPassword"
        />
        <p v-if="errorText" class="error">{{ errorText }}</p>
        <n-button type="primary" block class="action" :loading="loading" @click="submitPassword">
          {{ t('shareLanding.unlock') }}
        </n-button>
      </template>

      <template v-else-if="share">
        <div class="file-name">{{ share.targetName || t('shareLanding.unnamed') }}</div>
        <div class="meta">{{ metaLine }}</div>

        <div v-if="isImage" class="preview">
          <img :src="share.fileUrl" :alt="share.targetName || ''" referrerpolicy="no-referrer" />
        </div>

        <n-button
          type="primary"
          block
          class="action"
          :loading="downloading"
          :disabled="share.shareType === 'folder'"
          @click="handleDownload"
        >
          {{ t('files.downloadFile') }}
        </n-button>
        <p v-if="share.shareType === 'folder'" class="hint">{{ t('shareLanding.folderNoDownload') }}</p>
      </template>

      <template v-else>
        <p class="error">{{ errorText || t('shareLanding.loadFail') }}</p>
        <n-button quaternary block class="action" @click="goHome">
          {{ t('shareLanding.backHome') }}
        </n-button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(ellipse at 20% 0%, rgba(18, 183, 245, 0.12), transparent 50%),
    radial-gradient(ellipse at 80% 100%, rgba(16, 185, 129, 0.1), transparent 45%),
    var(--lx-bg-app, #f5f7fa);
}

.share-card {
  width: min(420px, 100%);
  padding: 28px 24px;
  border-radius: 16px;
  background: var(--lx-bg-card, #fff);
  border: 1px solid var(--lx-border-light, rgba(0, 0, 0, 0.06));
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}

.brand {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--lx-accent, #12b7f5);
}

.subtitle {
  margin: 6px 0 20px;
  color: var(--lx-text-secondary, #64748b);
  font-size: 14px;
}

.state {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.file-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--lx-text-primary, #0f172a);
  word-break: break-all;
}

.meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--lx-text-secondary, #64748b);
}

.preview {
  margin: 16px 0;
  border-radius: 10px;
  overflow: hidden;
  background: var(--lx-bg-panel, #f1f5f9);
  text-align: center;
}

.preview img {
  max-width: 100%;
  max-height: 280px;
  object-fit: contain;
  vertical-align: middle;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--lx-text-secondary, #64748b);
}

.error {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--lx-danger, #ef4444);
}

.action {
  margin-top: 16px;
}
</style>
