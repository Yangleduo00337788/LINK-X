<script setup lang="ts">
import { NInput, NIcon, NDropdown } from 'naive-ui'
import { SearchOutline, AddOutline } from '@vicons/ionicons5'

withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    /** 有选项时显示右侧 + 下拉 */
    addOptions?: { label: string; key: string }[]
  }>(),
  {
    placeholder: '搜索',
    addOptions: undefined
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  addSelect: [key: string]
}>()

function onAdd(key: string) {
  emit('addSelect', key)
}
</script>

<template>
  <div class="panel-search-bar">
    <n-input
      :value="modelValue"
      size="small"
      class="search-input"
      :placeholder="placeholder"
      @update:value="emit('update:modelValue', $event)"
    >
      <template #prefix>
        <n-icon :component="SearchOutline" :size="16" color="#999" />
      </template>
    </n-input>
    <n-dropdown
      v-if="addOptions?.length"
      trigger="click"
      :options="addOptions"
      class="add-dropdown"
      @select="onAdd"
    >
      <div class="add-btn" title="添加">
        <n-icon :component="AddOutline" :size="18" />
      </div>
    </n-dropdown>
  </div>
</template>

<style scoped>
.panel-search-bar {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: var(--lx-bg-panel, #f3f3f3);
  /* border-bottom: 1px solid var(--lx-border, rgba(0, 0, 0, 0.06)); */
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  --search-radius: var(--lx-radius);
  --n-color: #ebebeb !important;
  --n-color-focus: #ebebeb !important;
  --n-border-radius: var(--lx-radius) !important;
  --n-border: 1px solid transparent !important;
  --n-border-hover: 1px solid transparent !important;
  --n-border-focus: 1px solid transparent !important;
  --n-box-shadow-focus: none !important;
}

.search-input :deep(.n-input-wrapper) {
  border-radius: var(--search-radius);
  background-color: #ebebeb !important;
}

.search-input :deep(.n-input__border),
.search-input :deep(.n-input__state-border) {
  border-radius: var(--search-radius);
  border: none !important;
}

.search-input :deep(.n-input__input-el) {
  height: 32px;
}

.add-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--lx-radius);
  background: #ebebeb;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.12s ease;
  flex-shrink: 0;
}

.add-btn:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #333;
}

.add-btn:active {
  transform: scale(0.96);
}
</style>