<script setup lang="ts">
import PrefSwitcher from '@/components/PrefSwitcher.vue'
import AuthParticleBackground from '@/components/AuthParticleBackground.vue'
import AdminBrandLogo from '@/components/AdminBrandLogo.vue'

withDefaults(
  defineProps<{
    /** split: 登录左右分栏；centered: 403/404 等居中卡片 */
    mode?: 'split' | 'centered'
    /** 全屏沉浸式分栏（登录页参考阿里云/整页铺满） */
    immersive?: boolean
    /** 左侧主标题（无 banner 时展示） */
    visualTitle?: string
    /** 左侧副标题 */
    visualDesc?: string
    /** 登录页专用样式（右上角切换器用蓝色高亮） */
    loginPage?: boolean
  }>(),
  {
    mode: 'split',
    immersive: false,
    visualTitle: '',
    visualDesc: '',
    loginPage: false,
  }
)
</script>

<template>
  <div
    class="auth-page"
    :class="[
      `auth-page--${mode}`,
      { 'auth-page--immersive': immersive, 'auth-page--login': loginPage },
    ]"
  >
    <div class="auth-bg" aria-hidden="true">
      <div class="auth-bg-base" />
      <span class="auth-aurora auth-aurora-a" />
      <span class="auth-aurora auth-aurora-b" />
      <span class="auth-aurora auth-aurora-c" />
      <span class="auth-orb auth-orb-a" />
      <span class="auth-orb auth-orb-b" />
      <AuthParticleBackground />
      <span class="auth-mesh" />
      <span class="auth-vignette" />
    </div>

    <div class="auth-prefs">
      <PrefSwitcher />
    </div>

    <div class="auth-shell lx-appear-in">
      <template v-if="mode === 'split'">
        <aside class="auth-visual">
          <slot name="visual" />
          <div v-if="$slots['visual-fallback']" class="auth-visual-fallback">
            <slot name="visual-fallback" />
          </div>
          <div v-else-if="visualTitle || visualDesc" class="auth-visual-fallback">
            <AdminBrandLogo size="hero" />
            <p v-if="visualDesc" class="auth-visual-desc">{{ visualDesc }}</p>
          </div>
        </aside>

        <section class="auth-panel">
          <div class="auth-panel-inner">
            <slot />
          </div>
        </section>
      </template>

      <section v-else class="auth-centered">
        <div class="auth-centered-card">
          <slot />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
  box-sizing: border-box;
}

.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.auth-bg-base {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 90% 70% at 12% 8%, var(--lx-login-grad-1), transparent 58%),
    radial-gradient(ellipse 75% 55% at 88% 18%, var(--lx-login-grad-2), transparent 52%),
    radial-gradient(ellipse 60% 50% at 50% 100%, var(--lx-login-grad-3), transparent 55%),
    var(--lx-login-base);
}

.auth-aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(48px);
  opacity: 0.75;
  animation: auth-aurora-shift 18s ease-in-out infinite;
}

.auth-aurora-a {
  top: -8%;
  left: 10%;
  width: 42%;
  height: 38%;
  background: radial-gradient(circle, rgba(24, 144, 255, 0.35) 0%, transparent 70%);
}

.auth-aurora-b {
  top: 28%;
  right: -6%;
  width: 36%;
  height: 34%;
  background: radial-gradient(circle, rgba(19, 194, 194, 0.28) 0%, transparent 72%);
  animation-delay: -6s;
}

.auth-aurora-c {
  bottom: -12%;
  left: 24%;
  width: 48%;
  height: 40%;
  background: radial-gradient(circle, rgba(114, 46, 209, 0.2) 0%, transparent 70%);
  animation-delay: -11s;
}

.auth-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  animation: lx-orb-drift 20s ease-in-out infinite;
}

.auth-orb-a {
  top: 18%;
  left: 6%;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(24, 144, 255, 0.16) 0%, transparent 68%);
}

.auth-orb-b {
  bottom: 14%;
  right: 8%;
  width: 160px;
  height: 160px;
  background: radial-gradient(circle, rgba(19, 194, 194, 0.14) 0%, transparent 70%);
  animation-delay: -7s;
}

.auth-mesh {
  position: absolute;
  inset: 0;
  opacity: 0.22;
  background-image: radial-gradient(rgba(255, 255, 255, 0.55) 0.55px, transparent 0.55px);
  background-size: 22px 22px;
  mask-image: radial-gradient(ellipse 85% 75% at 50% 45%, #000 20%, transparent 100%);
}

.auth-vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 45%, rgba(0, 0, 0, 0.22) 100%);
  opacity: 0.35;
}

[data-theme='light'] .auth-vignette {
  background: radial-gradient(ellipse at center, transparent 50%, rgba(9, 109, 217, 0.08) 100%);
  opacity: 0.65;
}

.auth-prefs {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 2;
  pointer-events: auto;
}

.auth-page--login .auth-prefs :deep(.n-button--primary-type) {
  --n-color: #1677ff !important;
  --n-color-hover: #4096ff !important;
  --n-color-pressed: #0958d9 !important;
  --n-text-color: #fff !important;
}

.auth-page--immersive {
  padding: 0;
  place-items: stretch;
  background: linear-gradient(135deg, #eef4fb 0%, #f3f7fc 42%, #f7faff 100%);
}

.auth-page--immersive::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  opacity: 0.45;
  background-image: radial-gradient(rgba(148, 163, 184, 0.28) 1px, transparent 1px);
  background-size: 18px 18px;
  mask-image: linear-gradient(115deg, transparent 0%, transparent 38%, #000 62%, #000 100%);
}

.auth-page--immersive .auth-bg {
  display: none;
}

.auth-page--immersive .auth-prefs {
  top: 16px;
  right: 24px;
  z-index: 3;
}

.auth-page--immersive .auth-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  min-height: 100vh;
  border: none;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
  background: transparent;
  grid-template-columns: minmax(0, 1.28fr) minmax(380px, 0.72fr);
}

.auth-page--immersive .auth-visual {
  min-height: 100vh;
  background: transparent;
  overflow: hidden;
}

.auth-page--immersive .auth-visual-fallback {
  position: relative;
  inset: auto;
  height: 100%;
  justify-content: flex-start;
  overflow: visible;
}

.auth-page--immersive .auth-panel {
  min-height: 100vh;
  padding: 40px 72px 40px 24px;
  background: transparent;
  align-items: center;
  position: relative;
  z-index: 2;
}

.auth-page--immersive .auth-panel-inner {
  max-width: 420px;
  width: 100%;
  margin-left: auto;
  margin-right: auto;
}

.auth-shell {
  position: relative;
  z-index: 1;
  width: min(960px, calc(100vw - 48px));
}

.auth-page--split .auth-shell {
  min-height: min(560px, calc(100vh - 48px));
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  border-radius: calc(var(--lx-radius) + 2px);
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--lx-border) 80%, transparent);
  background: color-mix(in srgb, var(--lx-login-card) 92%, transparent);
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.16),
    0 0 0 1px color-mix(in srgb, #ffffff 6%, transparent) inset;
  backdrop-filter: blur(16px) saturate(1.2);
}

.auth-visual {
  position: relative;
  min-height: 280px;
  overflow: hidden;
  background: #e8f0fa;
}

.auth-visual-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: var(--lx-text);
  pointer-events: none;
  overflow: hidden;
}

.auth-visual-desc {
  margin: 14px 0 0;
  max-width: 280px;
  color: var(--lx-text-3);
  font-size: 14px;
  line-height: 1.6;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px 28px;
  background: color-mix(in srgb, var(--lx-login-card) 94%, transparent);
}

.auth-panel-inner {
  width: 100%;
  max-width: 360px;
}

.auth-page--centered .auth-shell {
  width: min(480px, calc(100vw - 48px));
}

.auth-centered {
  display: grid;
  place-items: center;
}

.auth-centered-card {
  width: 100%;
  padding: 36px 28px;
  border-radius: calc(var(--lx-radius) + 2px);
  border: 1px solid color-mix(in srgb, var(--lx-border) 80%, transparent);
  background: color-mix(in srgb, var(--lx-login-card) 94%, transparent);
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.14),
    0 0 0 1px color-mix(in srgb, #ffffff 6%, transparent) inset;
  backdrop-filter: blur(16px) saturate(1.2);
}

@keyframes auth-aurora-shift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  33% {
    transform: translate3d(2%, -3%, 0) scale(1.04);
  }
  66% {
    transform: translate3d(-2%, 2%, 0) scale(0.98);
  }
}

@media (max-width: 960px) {
  .auth-page--immersive .auth-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .auth-page--immersive .auth-visual {
    min-height: auto;
    aspect-ratio: auto;
  }

  .auth-page--immersive .auth-panel {
    min-height: auto;
    padding: 24px 20px 32px;
  }
}

@media (max-width: 820px) {
  .auth-page {
    padding: 16px;
    place-items: stretch;
  }

  .auth-page--split .auth-shell {
    width: 100%;
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .auth-visual {
    min-height: 200px;
    aspect-ratio: 16 / 9;
  }

  .auth-panel {
    padding: 24px 20px 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-aurora,
  .auth-orb {
    animation: none !important;
  }
}
</style>
