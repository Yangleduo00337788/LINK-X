<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { NButton, NIcon } from 'naive-ui'
import { ChevronBackOutline, CloseOutline } from '@vicons/ionicons5'
import { usePreferencesStore } from '@/stores/preferences'

defineProps<{
  subtitle?: string
}>()

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const prefs = usePreferencesStore()
const { breadcrumbEnabled, multiTabEnabled } = storeToRefs(prefs)

const title = computed(() => {
  void locale.value
  const key = route.meta.titleKey as string | undefined
  return key ? t(key) : t('route.admin')
})

const showBack = computed(
  () => route.name === 'UserDetail' || route.name === 'FeedbackDetail' || route.meta.hidden === true
)

const crumbs = computed(() => {
  void locale.value
  const items: { label: string; path?: string }[] = [
    { label: t('route.admin'), path: '/admin/dashboard' },
  ]
  const titleKey = route.meta.titleKey as string | undefined
  if (route.path.startsWith('/admin/users/') && route.name === 'UserDetail') {
    items.push({ label: t('route.users'), path: '/admin/users' })
    items.push({ label: t('route.userDetail') })
  } else if (route.path.startsWith('/admin/feedback/') && route.name === 'FeedbackDetail') {
    items.push({ label: t('route.feedback'), path: '/admin/feedback' })
    items.push({ label: t('route.feedbackDetail') })
  } else if (titleKey && route.path !== '/admin/dashboard') {
    items.push({ label: t(titleKey) })
  } else if (route.path === '/admin/dashboard') {
    items.push({ label: t('route.dashboard') })
  }
  return items
})

function go(path?: string) {
  if (path && path !== route.path) router.push(path)
}

function onBack() {
  router.back()
}

function goHome() {
  if (route.path !== '/admin/dashboard') router.push('/admin/dashboard')
}
</script>

<template>
  <div class="oa-nav-block">
    <!-- 勾股 OA 风格：顶部页签条 -->
    <div v-if="multiTabEnabled" class="oa-tabs-bar">
      <button
        type="button"
        class="oa-tab"
        :class="{ active: route.path === '/admin/dashboard' }"
        @click="goHome"
      >
        {{ t('route.dashboard') }}
      </button>
      <button
        v-if="route.path !== '/admin/dashboard'"
        type="button"
        class="oa-tab active"
      >
        <span class="oa-tab-label">{{ title }}</span>
        <span
          v-if="showBack"
          class="oa-tab-close"
          role="button"
          tabindex="0"
          @click.stop="onBack"
          @keydown.enter.stop="onBack"
        >
          <NIcon :size="12" :component="CloseOutline" />
        </span>
      </button>
    </div>

    <!-- 面包屑 + 标题行（轻量） -->
    <header class="oa-page-header">
      <nav v-if="breadcrumbEnabled" class="oa-breadcrumb" aria-label="Breadcrumb">
        <template v-for="(c, i) in crumbs" :key="`${c.label}-${i}`">
          <span v-if="i > 0" class="oa-breadcrumb-sep">/</span>
          <a
            v-if="c.path && i < crumbs.length - 1"
            class="oa-breadcrumb-link"
            href="#"
            @click.prevent="go(c.path)"
          >
            {{ c.label }}
          </a>
          <span v-else class="oa-breadcrumb-current">{{ c.label }}</span>
        </template>
      </nav>

      <div class="oa-title-row">
        <div class="oa-title-main">
          <NButton
            v-if="showBack"
            quaternary
            circle
            size="small"
            class="oa-back-btn"
            @click="onBack"
          >
            <template #icon>
              <NIcon :component="ChevronBackOutline" />
            </template>
          </NButton>
          <div class="oa-title-block">
            <h1 class="oa-page-title">{{ title }}</h1>
            <p v-if="subtitle" class="oa-page-subtitle">{{ subtitle }}</p>
          </div>
        </div>
        <div v-if="$slots.extra" class="oa-title-extra">
          <slot name="extra" />
        </div>
      </div>

      <div v-if="$slots.footer" class="oa-page-footer">
        <slot name="footer" />
      </div>
      <div v-if="$slots.default" class="oa-page-body">
        <slot />
      </div>
    </header>
  </div>
</template>

<style scoped>
.oa-nav-block {
  margin-bottom: 12px;
}

/* ── Tabs bar (Gougu OA style) ── */
.oa-tabs-bar {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 0 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--lx-border);
  background: transparent;
  overflow-x: auto;
}

.oa-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 4px 4px 0 0;
  background: transparent;
  color: var(--lx-text-2);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s ease, background 0.15s ease;
}

.oa-tab:hover {
  color: var(--lx-oa-blue);
}

.oa-tab.active {
  color: var(--lx-oa-blue);
  font-weight: 600;
  background: var(--lx-card);
}

.oa-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--lx-oa-blue);
}

.oa-tab-label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.oa-tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 2px;
  color: var(--lx-text-3);
  opacity: 0.7;
}

.oa-tab-close:hover {
  color: var(--lx-oa-red);
  background: rgba(207, 19, 34, 0.08);
  opacity: 1;
}

/* ── Page header card ── */
.oa-page-header {
  padding: 14px 20px 16px;
  background: var(--lx-card);
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  box-shadow: var(--lx-card-shadow);
}

.oa-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.oa-breadcrumb-sep {
  margin: 0 8px;
  color: var(--lx-text-3);
  user-select: none;
}

.oa-breadcrumb-link {
  color: var(--lx-text-2);
  text-decoration: none;
  transition: color 0.15s ease;
}

.oa-breadcrumb-link:hover {
  color: var(--lx-oa-blue);
}

.oa-breadcrumb-current {
  color: var(--lx-text);
  font-weight: 500;
}

.oa-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.oa-title-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.oa-back-btn {
  flex-shrink: 0;
  margin-top: 2px;
}

.oa-title-block {
  min-width: 0;
}

.oa-page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--lx-text);
}

.oa-page-title::before {
  content: '';
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: var(--lx-oa-blue);
  flex-shrink: 0;
}

.oa-page-subtitle {
  margin: 6px 0 0 13px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--lx-text-3);
}

.oa-title-extra {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.oa-page-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--lx-border);
}

.oa-page-body {
  margin-top: 12px;
}
</style>
