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
    <NResult status="404" :title="t('error.notFoundTitle')" :description="t('error.notFoundDesc')">
      <template #footer>
        <NButton type="primary" @click="router.push('/admin/dashboard')">
          {{ t('error.backHome') }}
        </NButton>
      </template>
    </NResult>
  </AuthPageShell>
</template>
