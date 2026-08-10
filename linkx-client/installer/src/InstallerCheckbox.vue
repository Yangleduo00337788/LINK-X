<!-- 作者：yangleduo -->
<script setup lang="ts">
const model = defineModel<boolean>({ default: false })
</script>

<template>
  <label class="lx-check" :class="{ 'lx-check--checked': model }">
    <input v-model="model" type="checkbox" class="lx-check__input" />
    <span class="lx-check__box" aria-hidden="true">
      <svg v-if="model" width="11" height="11" viewBox="0 0 11 11" fill="none">
        <path
          d="M2 5.5 4.5 8 9 3"
          stroke="#fff"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </span>
    <span class="lx-check__label"><slot /></span>
  </label>
</template>

<style scoped>
.lx-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.lx-check__input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.lx-check__box {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: 5px;
  border: 1.5px solid #c5d0dc;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.lx-check--checked .lx-check__box {
  border-color: transparent;
  background: var(--lx-login-accent);
  box-shadow: 0 2px 8px rgba(18, 183, 245, 0.28);
}

.lx-check__label {
  font-size: inherit;
  line-height: 1.5;
  color: #475569;
}
</style>
