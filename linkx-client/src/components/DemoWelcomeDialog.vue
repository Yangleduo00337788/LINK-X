<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 演示站开屏说明：当前为 Mock，并给出源码仓库入口。
 */
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { LogoGithub } from '@vicons/ionicons5'
import { useI18n } from '../i18n'
import BrandMarkIcon from './BrandMarkIcon.vue'
import LxButton from './ui/LxButton.vue'
import { GITEE_REPO_URL, GITHUB_REPO_URL, openDemoRepo } from '../utils/demoRepos'
import { demoWelcomeOpen, dismissDemoWelcome } from '../utils/demoChrome'

const { t } = useI18n()
const show = demoWelcomeOpen

function dismiss() {
  dismissDemoWelcome()
}

function onRepoClick(event: MouseEvent, url: string) {
  event.preventDefault()
  openDemoRepo(url)
}

function onKeydown(event: KeyboardEvent) {
  if (!show.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    dismiss()
  }
}

function lockPageScroll(lock: boolean) {
  document.body.style.overflow = lock ? 'hidden' : ''
}

watch(show, visible => {
  lockPageScroll(visible)
})

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  if (show.value) lockPageScroll(true)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  lockPageScroll(false)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="demo-welcome"
      role="dialog"
      aria-modal="true"
      aria-labelledby="demo-welcome-title"
      aria-describedby="demo-welcome-desc"
    >
      <div class="demo-welcome-card" @mousedown.stop>
        <div class="demo-welcome-hero">
          <BrandMarkIcon :size="52" />
          <p id="demo-welcome-title" class="demo-welcome-mode">
            <span class="demo-welcome-label">{{ t('demo.currentMode') }}</span>
            <span class="demo-welcome-value">{{ t('demo.demoMode') }}</span>
          </p>
        </div>
        <div id="demo-welcome-desc" class="demo-welcome-copy-block">
          <p class="demo-welcome-body">{{ t('demo.splashMock') }}</p>
          <p class="demo-welcome-body">{{ t('demo.splashSource') }}</p>
        </div>
        <div class="demo-welcome-links">
          <a
            class="demo-welcome-link"
            :href="GITEE_REPO_URL"
            target="_blank"
            rel="noopener noreferrer"
            @click="onRepoClick($event, GITEE_REPO_URL)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M11.556 4.444h6.518c.328 0 .593.266.593.593v1.482a.594.594 0 0 1-.593.592H9.777c-.982 0-1.778.796-1.778 1.778v5.63c0 .327.266.592.593.592h5.63c.982 0 1.778-.796 1.778-1.778v-.296a.593.593 0 0 0-.592-.593h-4.15a.592.592 0 0 1-.592-.592v-1.482a.593.593 0 0 1 .593-.592h6.815c.327 0 .593.265.593.592v3.408a4 4 0 0 1-4 4H5.926a.593.593 0 0 1-.593-.593V9.778a5.334 5.334 0 0 1 6.223-5.334z"
              />
            </svg>
            <span>{{ t('demo.gitee') }}</span>
          </a>
          <a
            class="demo-welcome-link"
            :href="GITHUB_REPO_URL"
            target="_blank"
            rel="noopener noreferrer"
            @click="onRepoClick($event, GITHUB_REPO_URL)"
          >
            <n-icon :component="LogoGithub" :size="16" />
            <span>{{ t('demo.github') }}</span>
          </a>
        </div>
        <LxButton variant="login" class="demo-welcome-ok" @click="dismiss">
          {{ t('common.know') }}
        </LxButton>
        <p class="demo-welcome-legal">
          <span>{{ t('about.companyRights') }}</span>
          <span>{{ t('about.copyright') }}</span>
        </p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.demo-welcome {
  position: fixed;
  inset: 0;
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(10px);
  -webkit-app-region: no-drag;
}

.demo-welcome-card {
  width: min(420px, 100%);
  padding: 28px 28px 20px;
  border-radius: 16px;
  background: var(--lx-bg-card, #fff);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
}

.demo-welcome-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  text-align: center;
}

.demo-welcome-mode {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
}

.demo-welcome-label {
  font-weight: 700;
  color: #64748b;
}

.demo-welcome-value {
  font-weight: 700;
  color: #ef4444;
}

.demo-welcome-copy-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-welcome-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--lx-text-body, #334155);
}

.demo-welcome-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 18px 0 16px;
}

.demo-welcome-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 12px;
  border: 0;
  outline: none;
  box-shadow: none;
  border-radius: 10px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  background: transparent;
}

.demo-welcome-link svg {
  width: 16px;
  height: 16px;
  display: block;
  flex-shrink: 0;
}

.demo-welcome-link:hover {
  color: var(--lx-accent, #0284c7);
  background: rgba(15, 23, 42, 0.04);
}

.demo-welcome-ok {
  width: 100%;
  outline: none;
}

.demo-welcome-ok:focus,
.demo-welcome-ok:focus-visible {
  outline: none;
  box-shadow:
    0 8px 20px rgba(18, 183, 245, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.demo-welcome-legal {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: var(--lx-text-muted, #94a3b8);
}

:global([data-theme='dark']) .demo-welcome-label {
  color: #94a3b8;
}

:global([data-theme='dark']) .demo-welcome-value {
  color: #f87171;
}

:global([data-theme='dark']) .demo-welcome-link:hover {
  background: rgba(255, 255, 255, 0.06);
}
</style>
