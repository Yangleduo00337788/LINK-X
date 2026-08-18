<!-- 作者：yangleduo -->
<script setup lang="ts">
import { ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { APP_CLIENT_CHANNEL, APP_CLIENT_VERSION } from '../../utils/appVersion'
import { useI18n } from '../../i18n'
import { openLegalPageInBrowser } from '../../utils/legalPage'
import { openHelpPageInBrowser } from '../../utils/helpPage'
import { checkAppUpdate } from '../../utils/appUpdate'
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

async function checkUpdate() {
  if (checking.value || updating.value) return
  checking.value = true
  try {
    await checkAppUpdate({
      message,
      dialog,
      t,
      onProgress: (active, text) => {
        updating.value = active
        progressText.value = text || ''
      }
    })
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
  min-width: 0;
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
