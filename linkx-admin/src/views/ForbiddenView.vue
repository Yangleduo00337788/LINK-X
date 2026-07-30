<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NResult } from 'naive-ui'

const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <div class="center">
    <NResult status="403" :title="t('error.forbiddenTitle')" :description="t('error.forbiddenDesc')">
      <template #footer>
        <NButton type="primary" @click="router.push('/admin/dashboard')">
          {{ t('error.backDashboard') }}
        </NButton>
      </template>
    </NResult>
  </div>
</template>

<style scoped>
.center {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
</style>
