<!-- 作者：yangleduo -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NResult } from 'naive-ui'
import AuthPageShell from '@/components/AuthPageShell.vue'

const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <AuthPageShell mode="centered">
    <NResult
      status="403"
      :title="t('error.forbiddenTitle')"
      :description="t('error.forbiddenDesc')"
    >
      <template #footer>
        <NButton type="primary" @click="router.push('/admin/dashboard')">
          {{ t('error.backDashboard') }}
        </NButton>
      </template>
    </NResult>
  </AuthPageShell>
</template>
