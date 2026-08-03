<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NBreadcrumb, NBreadcrumbItem, NPageHeader } from 'naive-ui'

defineProps<{
  subtitle?: string
}>()

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

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
</script>

<template>
  <NPageHeader
    class="page-header-bar"
    :title="title"
    :subtitle="subtitle"
    v-bind="showBack ? { onBack } : {}"
  >
    <template #header>
      <NBreadcrumb>
        <NBreadcrumbItem
          v-for="(c, i) in crumbs"
          :key="`${c.label}-${i}`"
          :clickable="!!c.path && i < crumbs.length - 1"
          @click="go(c.path)"
        >
          {{ c.label }}
        </NBreadcrumbItem>
      </NBreadcrumb>
    </template>
    <template v-if="$slots.extra" #extra>
      <slot name="extra" />
    </template>
    <template v-if="$slots.default" #default>
      <slot />
    </template>
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </NPageHeader>
</template>

<style scoped>
.page-header-bar {
  margin-bottom: 4px;
  padding: 4px 2px 12px;
}
.page-header-bar :deep(.n-page-header-header) {
  align-items: center;
}
</style>
