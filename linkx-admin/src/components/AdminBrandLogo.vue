<!-- 作者：yangleduo -->
<script setup lang="ts">
withDefaults(
  defineProps<{
    /** sider: 侧栏；login: 登录表单；hero: 登录左侧大号 */
    size?: 'sider' | 'login' | 'hero'
    /** 收起时只显示图形标 */
    collapsed?: boolean
    /** 是否显示 Admin 副标 */
    showAdmin?: boolean
  }>(),
  {
    size: 'sider',
    collapsed: false,
    showAdmin: true,
  }
)
</script>

<template>
  <div class="lx-logo" :class="[`lx-logo--${size}`, { 'lx-logo--collapsed': collapsed }]">
    <!--
      几何标（对齐勾股式构图）：
      左下实心蓝 L + 右上描边黄 X，拼成方形 footprint
    -->
    <svg
      class="lx-logo-mark"
      viewBox="0 0 40 40"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <!-- 实心红 L（居中构图） -->
      <path class="lx-mark-l" d="M6 6 H13 V27 H34 V34 H6 Z" fill="#E54D42" />
      <!-- 描边橙 X -->
      <path
        class="lx-mark-x"
        d="M21 7 L35 21 M35 7 L21 21"
        fill="none"
        stroke="#FA8C16"
        stroke-width="4.5"
        stroke-linecap="square"
      />
    </svg>

    <div v-show="!collapsed" class="lx-logo-text">
      <span v-if="showAdmin" class="lx-logo-eyebrow">ADMIN</span>
      <div class="lx-logo-title-row">
        <span class="lx-logo-title">
          <span class="lx-l">L</span><span class="lx-ink">ink</span><span class="lx-x">X</span>
        </span>
        <span v-if="showAdmin && size !== 'hero'" class="lx-logo-badge">OA</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lx-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  user-select: none;
}

.lx-logo-mark {
  flex-shrink: 0;
  display: block;
}

.lx-logo-text {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  line-height: 1;
}

.lx-logo-eyebrow {
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #2d7bff;
}

.lx-logo-title-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.lx-logo-title {
  font-weight: 800;
  letter-spacing: 0.01em;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  line-height: 1;
}

.lx-l,
.lx-ink {
  color: #2d7bff;
}

.lx-x {
  color: #f3af2b;
}

.lx-logo-badge {
  font-weight: 700;
  color: #2d7bff;
  opacity: 0.85;
  letter-spacing: 0.04em;
}

/* ── sider ── */
.lx-logo--sider {
  justify-content: center;
  width: 100%;
}

.lx-logo--sider .lx-logo-mark {
  width: 36px;
  height: 36px;
}

.lx-logo--sider .lx-logo-text {
  align-items: flex-start;
}

.lx-logo--sider .lx-logo-eyebrow {
  font-size: 11px;
  margin-bottom: 5px;
}

.lx-logo--sider .lx-logo-title {
  font-size: 22px;
}

.lx-logo--sider .lx-logo-badge {
  font-size: 13px;
}

.lx-logo--sider.lx-logo--collapsed .lx-logo-mark {
  width: 32px;
  height: 32px;
}

/* ── login ── */
.lx-logo--login {
  gap: 14px;
}

.lx-logo--login .lx-logo-mark {
  width: 48px;
  height: 48px;
}

.lx-logo--login .lx-logo-eyebrow {
  font-size: 12px;
  margin-bottom: 6px;
}

.lx-logo--login .lx-logo-title {
  font-size: 34px;
}

.lx-logo--login .lx-logo-badge {
  font-size: 15px;
}

/* ── hero ── */
.lx-logo--hero {
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}

.lx-logo--hero .lx-logo-mark {
  width: 64px;
  height: 64px;
}

.lx-logo--hero .lx-logo-eyebrow {
  font-size: 13px;
  margin-bottom: 8px;
}

.lx-logo--hero .lx-logo-title {
  font-size: 42px;
}

[data-theme='dark'] .lx-logo-eyebrow,
[data-theme='dark'] .lx-logo-badge,
[data-theme='dark'] .lx-l,
[data-theme='dark'] .lx-ink {
  color: #40a9ff;
}

[data-theme='dark'] .lx-x {
  color: #ffc53d;
}

[data-theme='dark'] .lx-mark-l {
  fill: #ff7875;
}

[data-theme='dark'] .lx-mark-x {
  stroke: #ffa940;
}
</style>
