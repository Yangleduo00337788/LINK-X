<script setup lang="ts">
// Naive UI 按钮与消息提示
import { NButton, useMessage } from 'naive-ui'
// 应用版本号（与后端 linkx.app.version 一致；服务端为准）
import { APP_CLIENT_VERSION } from '../../utils/appVersion'
import * as versionApi from '../../api/version'
import { useI18n } from '../../i18n'

// 消息提示实例
const message = useMessage()
const { t } = useI18n()

/**
 * 检查更新
 * 调用后端 /app/version?current=...，根据返回 hasUpdate 决定提示内容。
 */
async function checkUpdate() {
  try {
    const res = await versionApi.checkUpdate(APP_CLIENT_VERSION)
    if (res.code !== 200 || !res.data) {
      message.error(res.message || t('about.checkFail'))
      return
    }
    const info = res.data
    if (info.hasUpdate) {
      const suffix = info.downloadUrl
        ? t('about.download', { url: info.downloadUrl })
        : ''
      message.warning(
        t('about.found', { version: info.version, notes: info.releaseNotes }) + suffix,
        { duration: 8000 }
      )
    } else {
      message.success(t('about.latest', { version: info.version }))
    }
  } catch (e) {
    console.warn('[AboutSettings] 检查更新失败:', e)
    message.error(t('about.checkFailRetry'))
  }
}
</script>

<template>
  <!-- 关于 LinkX 设置页 -->
  <div class="settings-scroll about-scroll">
    <!-- 品牌信息与操作按钮 -->
    <section class="about-card">
      <div class="about-glow" />
      <img src="../../assets/logo-linkx.svg" alt="LinkX" class="about-logo" />
      <h3 class="about-name">LinkX</h3>
      <p class="about-ver">Version {{ APP_CLIENT_VERSION }} · Beta</p>
      <p class="about-desc">{{ t('about.desc') }}</p>
      <div class="about-actions">
        <n-button type="primary" @click="checkUpdate">{{ t('about.checkUpdate') }}</n-button>
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
  width: 200px;
  height: 120px;
  background: radial-gradient(ellipse, var(--lx-accent-soft) 0%, transparent 70%);
  pointer-events: none;
}

.about-logo {
  width: 72px;
  height: 72px;
  position: relative;
  z-index: 1;
}

.about-name {
  margin: 16px 0 4px;
  font-size: 22px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.about-ver {
  margin: 0;
  font-size: 13px;
  color: var(--lx-text-muted);
}

.about-desc {
  margin: 12px 0 24px;
  font-size: 14px;
  color: var(--lx-text-secondary);
  line-height: 1.5;
}

.about-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
}

.about-copy {
  margin: 0;
  font-size: 11px;
  color: var(--lx-text-muted);
}
</style>