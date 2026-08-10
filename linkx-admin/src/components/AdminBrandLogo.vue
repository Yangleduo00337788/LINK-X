<!-- 作者：yangleduo -->
<script setup lang="ts">
import logoLinkx from '@/assets/logo-linkx.png'

withDefaults(
  defineProps<{
    /** sider: 侧栏；login: 登录表单；hero: 登录左侧大号 */
    size?: 'sider' | 'login' | 'hero'
    /** 收起时只显示图形标 */
    collapsed?: boolean
  }>(),
  {
    size: 'sider',
    collapsed: false,
  }
)
</script>

<template>
  <div class="lx-logo" :class="[`lx-logo--${size}`, { 'lx-logo--collapsed': collapsed }]">
    <div class="lx-logo-img-wrap" :class="{ 'lx-logo-img-wrap--collapsed': collapsed }">
      <img class="lx-logo-img" :src="logoLinkx" alt="LinkX" draggable="false" />
    </div>
  </div>
</template>

<style scoped>
.lx-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  user-select: none;
}

.lx-logo-img-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.lx-logo-img {
  display: block;
  object-fit: contain;
  object-position: center center;
}

.lx-logo-img-wrap--collapsed {
  width: 40px;
  height: 40px;
}

.lx-logo-img-wrap--collapsed .lx-logo-img {
  width: 140px;
  max-width: none;
  height: 40px;
  object-fit: cover;
  object-position: left center;
}

/* ── sider ── */
.lx-logo--sider {
  width: 100%;
}

.lx-logo--sider .lx-logo-img-wrap {
  width: 100%;
  height: 56px;
}

.lx-logo--sider .lx-logo-img {
  width: 100%;
  height: auto;
  max-height: 56px;
  max-width: 100%;
  margin: 0 auto;
}

.lx-logo--sider.lx-logo--collapsed .lx-logo-img-wrap--collapsed {
  width: 40px;
  height: 40px;
}

.lx-logo--sider.lx-logo--collapsed .lx-logo-img {
  width: 140px;
  height: 40px;
}

/* ── login ── */
.lx-logo--login .lx-logo-img {
  height: 64px;
  max-width: 280px;
}

/* ── hero ── */
.lx-logo--hero {
  flex-direction: column;
  align-items: flex-start;
}

.lx-logo--hero .lx-logo-img {
  height: 80px;
  max-width: 360px;
}
</style>
