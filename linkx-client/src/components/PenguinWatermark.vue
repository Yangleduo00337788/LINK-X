<script setup lang="ts">
// LinkX Logo 占位组件：空状态占位，可自定义提示文案
withDefaults(
  defineProps<{
    hint?: string // 底部提示文字，默认引导选择会话
  }>(),
  {
    hint: '在左侧选择会话开始聊天'
  }
)
</script>

<template>
  <!-- LinkX Logo 占位区域 -->
  <div class="logo-wrap">
    <svg
      class="logo-icon"
      viewBox="0 0 200 80"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <!-- Logo background circle -->
      <circle cx="40" cy="40" r="32" fill="var(--lx-accent)"/>
      <!-- Link icon: two connected nodes -->
      <circle cx="28" cy="40" r="8" fill="white"/>
      <circle cx="52" cy="40" r="8" fill="white"/>
      <path d="M36 40h8" stroke="white" stroke-width="3" stroke-linecap="round"/>
      <!-- X accent -->
      <path d="M28 32l8 16M36 32l8 16" stroke="white" stroke-width="2" stroke-linecap="round"/>
      <!-- LinkX text -->
      <text x="85" y="52" font-family="Segoe UI, PingFang SC, Microsoft YaHei, sans-serif" font-size="36" font-weight="700" fill="var(--lx-text-body)">LinkX</text>
    </svg>
    <p v-if="hint" class="hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.logo-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 24px;
  background: radial-gradient(
    ellipse 55% 45% at 50% 42%,
    rgba(255, 255, 255, 0.65) 0%,
    transparent 70%
  );
}

.logo-icon {
  width: 240px;
  height: 96px;
  opacity: 0.4;
  pointer-events: none;
  user-select: none;
  filter: drop-shadow(0 8px 24px var(--lx-bg-hover));
}

.hint {
  margin: 16px 0 0;
  font-size: 14px;
  color: #b8b8b8;
  letter-spacing: 0.04em;
  font-weight: 400;
}
</style>