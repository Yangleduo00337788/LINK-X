<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NResult } from 'naive-ui'

const router = useRouter()
</script>

<template>
  <div class="center">
    <NResult status="403" title="无访问权限" description="你没有权限访问该页面">
      <template #footer>
        <NButton type="primary" @click="router.push('/admin/dashboard')">返回仪表盘</NButton>
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
