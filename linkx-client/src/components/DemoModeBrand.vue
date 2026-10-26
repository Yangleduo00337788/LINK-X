<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 演示站标题栏：管理后台入口 + 引导气泡。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { GridOutline } from '@vicons/ionicons5'
import { useI18n } from '../i18n'
import { DEMO_ADMIN_PATH, openDemoAdmin } from '../utils/demoRepos'
import { demoAdminHintOpen } from '../utils/demoChrome'

const visible = import.meta.env.VITE_LINKX_DEMO === '1'
const { t } = useI18n()
const pillRef = ref<HTMLAnchorElement | null>(null)
const bubblePos = ref({ top: 0, left: 0 })

function placeBubble() {
  const el = pillRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  bubblePos.value = {
    top: Math.round(rect.bottom + 10),
    left: Math.round(rect.left)
  }
}

watch(demoAdminHintOpen, async open => {
  if (!open) return
  await nextTick()
  placeBubble()
})

onMounted(() => {
  window.addEventListener('resize', placeBubble)
  window.addEventListener('scroll', placeBubble, true)
  if (demoAdminHintOpen.value) placeBubble()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', placeBubble)
  window.removeEventListener('scroll', placeBubble, true)
})
</script>

<template>
  <div v-if="visible" class="demo-mode-brand" @mousedown.stop>
    <a
      ref="pillRef"
      class="demo-admin-link"
      :href="DEMO_ADMIN_PATH"
      :aria-describedby="demoAdminHintOpen ? 'demo-admin-hint' : undefined"
      :aria-label="t('demo.adminEntry')"
      @click.prevent="openDemoAdmin"
    >
      <n-icon :component="GridOutline" :size="14" />
      <span>{{ t('demo.adminEntry') }}</span>
    </a>
    <Teleport to="body">
      <div
        v-if="demoAdminHintOpen"
        id="demo-admin-hint"
        class="demo-admin-bubble"
        role="tooltip"
        :style="{ top: `${bubblePos.top}px`, left: `${bubblePos.left}px` }"
      >
        <p class="demo-admin-bubble-title">{{ t('demo.adminEntry') }}</p>
        <p class="demo-admin-bubble-desc">{{ t('demo.adminHint') }}</p>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.demo-mode-brand {
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
  pointer-events: auto;
  user-select: none;
}

.demo-admin-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px 0 8px;
  border: 0;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.12);
  color: #0284c7;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
  animation: demo-admin-pulse 2.2s ease-out infinite;
}

.demo-admin-link:hover {
  background: rgba(14, 165, 233, 0.2);
  color: #0369a1;
}

:global([data-theme='dark']) .demo-admin-link {
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
}

@keyframes demo-admin-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(14, 165, 233, 0.35);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(14, 165, 233, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(14, 165, 233, 0);
  }
}
</style>

<style>
.demo-admin-bubble {
  position: fixed;
  z-index: 10050;
  width: max-content;
  max-width: 220px;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  pointer-events: none;
}

.demo-admin-bubble::after {
  content: '';
  position: absolute;
  top: -4px;
  left: 18px;
  width: 8px;
  height: 8px;
  background: #fff;
  transform: rotate(45deg);
}

.demo-admin-bubble-title {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  color: #0f172a;
}

.demo-admin-bubble-desc {
  margin: 0;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  color: #64748b;
}

[data-theme='dark'] .demo-admin-bubble,
[data-theme='dark'] .demo-admin-bubble::after {
  background: #1e293b;
}

[data-theme='dark'] .demo-admin-bubble-title {
  color: #f8fafc;
}

[data-theme='dark'] .demo-admin-bubble-desc {
  color: #94a3b8;
}
</style>
