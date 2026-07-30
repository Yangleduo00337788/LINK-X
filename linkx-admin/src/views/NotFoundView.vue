<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NResult } from 'naive-ui'

const router = useRouter()
</script>

<template>
  <div class="center">
    <NResult status="404" title="页面不存在" description="请检查地址是否正确">
      <template #footer>
        <NButton type="primary" @click="router.push('/admin/dashboard')">返回首页</NButton>
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
