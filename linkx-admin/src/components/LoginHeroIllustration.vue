<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AdminBrandLogo from '@/components/AdminBrandLogo.vue'
import loginHeroImg from '@/assets/login-hero.png'

const { t } = useI18n()
</script>

<template>
  <div class="login-hero">
    <div class="login-hero__content">
      <header class="login-hero__head">
        <AdminBrandLogo size="login" :show-admin="false" />
      </header>

      <div class="login-hero__copy">
        <h1 class="login-hero__slogan">{{ t('login.heroSlogan') }}</h1>
        <p class="login-hero__tagline">{{ t('login.heroTagline') }}</p>
        <span class="login-hero__accent" aria-hidden="true" />
      </div>
    </div>

    <div class="login-hero__art" aria-hidden="true">
      <img
        class="login-hero__img"
        :src="loginHeroImg"
        width="648"
        height="518"
        alt=""
        draggable="false"
      />
    </div>
  </div>
</template>

<style scoped>
.login-hero {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: transparent;
}

.login-hero__content {
  position: relative;
  z-index: 2;
  padding: 48px 32px 0 56px;
  max-width: 560px;
}

.login-hero__head {
  flex-shrink: 0;
}

.login-hero__copy {
  margin-top: 32px;
}

.login-hero__slogan {
  margin: 0;
  font-size: clamp(28px, 2.8vw, 36px);
  font-weight: 700;
  line-height: 1.35;
  color: #1a1a1a;
  letter-spacing: 0.01em;
}

.login-hero__tagline {
  margin: 14px 0 0;
  font-size: 15px;
  line-height: 1.65;
  color: #8c8c8c;
}

.login-hero__accent {
  display: block;
  width: 52px;
  height: 4px;
  margin-top: 18px;
  border-radius: 999px;
  background: linear-gradient(90deg, #2b7fff 0%, #6eb5ff 100%);
}

.login-hero__art {
  position: absolute;
  left: -6%;
  right: -18%;
  bottom: -6%;
  top: 22%;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  pointer-events: none;
}

.login-hero__img {
  display: block;
  width: auto;
  height: 100%;
  min-height: 420px;
  max-height: min(88vh, 820px);
  object-fit: contain;
  object-position: left bottom;
  user-select: none;
  -webkit-user-drag: none;
  filter: drop-shadow(0 20px 40px rgba(43, 127, 255, 0.1));
}

[data-theme='dark'] .login-hero__slogan {
  color: #e5eaf3;
}

[data-theme='dark'] .login-hero__tagline {
  color: #9aa4b2;
}

@media (max-width: 960px) {
  .login-hero {
    min-height: auto;
    padding-bottom: 24px;
  }

  .login-hero__content {
    padding: 32px 24px 0;
  }

  .login-hero__copy {
    margin-top: 24px;
  }

  .login-hero__art {
    position: relative;
    left: auto;
    right: auto;
    bottom: auto;
    top: auto;
    margin-top: 20px;
    height: auto;
    padding: 0 16px;
    justify-content: center;
  }

  .login-hero__img {
    width: 100%;
    height: auto;
    min-height: 0;
    max-height: min(360px, 56vw);
    object-position: center bottom;
  }
}
</style>
