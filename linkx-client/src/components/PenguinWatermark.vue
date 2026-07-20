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
  <div class="logo-wrap">
    <div class="logo-lockup" aria-hidden="true">
      <svg class="logo-mark" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="32" cy="32" r="30" fill="var(--lx-accent)"/>
        <!-- 双环相扣：流畅曲线，比节点 X 更轻盈 -->
        <g transform="translate(32 32)" fill="none" stroke="#fff" stroke-width="4.2" stroke-linecap="round">
          <ellipse rx="14.5" ry="6.9" transform="rotate(-42)"/>
          <ellipse rx="14.5" ry="6.9" transform="rotate(42)"/>
        </g>
      </svg>
      <span class="logo-wordmark">LinkX</span>
    </div>
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
}

.logo-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
  opacity: 0.42;
  pointer-events: none;
  user-select: none;
}

.logo-mark {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
}

.logo-wordmark {
  font-size: 34px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--lx-text-body);
  line-height: 1;
  font-family: 'Segoe UI Semibold', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.hint {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--lx-text-muted, #b8b8b8);
  letter-spacing: 0.06em;
  font-weight: 400;
}
</style>
