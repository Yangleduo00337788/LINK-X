<script setup lang="ts">
// 企鹅水印组件：空状态占位，可自定义提示文案
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
  <!-- 企鹅水印占位区域 -->
  <div class="penguin-wrap">
    <svg
      class="penguin"
      viewBox="0 0 200 220"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <!-- 身体 -->
      <ellipse cx="100" cy="118" rx="52" ry="72" fill="var(--lx-divider)" />
      <!-- 肚皮 -->
      <ellipse cx="100" cy="128" rx="32" ry="48" fill="var(--lx-bg-panel)" />
      <!-- 头 -->
      <circle cx="100" cy="72" r="44" fill="var(--lx-divider)" />
      <!-- 眼睛 -->
      <ellipse cx="82" cy="68" rx="7" ry="9" fill="#2c2c2c" />
      <ellipse cx="118" cy="68" rx="7" ry="9" fill="#2c2c2c" />
      <circle cx="84" cy="65" r="2.5" fill="var(--lx-bg-card)" />
      <circle cx="120" cy="65" r="2.5" fill="var(--lx-bg-card)" />
      <!-- 嘴 -->
      <path
        d="M88 82 Q100 92 112 82 Q100 88 88 82"
        fill="#f5a623"
      />
      <!-- 腮红 -->
      <ellipse cx="68" cy="78" rx="8" ry="5" fill="var(--lx-bg-panel-deep)" opacity="0.9" />
      <ellipse cx="132" cy="78" rx="8" ry="5" fill="var(--lx-bg-panel-deep)" opacity="0.9" />
      <!-- 翅膀 -->
      <ellipse cx="48" cy="118" rx="14" ry="28" fill="#e0e0e0" transform="rotate(-12 48 118)" />
      <ellipse cx="152" cy="118" rx="14" ry="28" fill="#e0e0e0" transform="rotate(12 152 118)" />
      <!-- 脚 -->
      <ellipse cx="82" cy="188" rx="16" ry="8" fill="#f5a623" />
      <ellipse cx="118" cy="188" rx="16" ry="8" fill="#f5a623" />
    </svg>
    <p v-if="hint" class="hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.penguin-wrap {
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

.penguin {
  width: 180px;
  height: 198px;
  opacity: 0.22;
  pointer-events: none;
  user-select: none;
  filter: drop-shadow(0 8px 24px var(--lx-bg-hover));
}

.hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: #b8b8b8;
  letter-spacing: 0.04em;
  font-weight: 400;
}
</style>