<script setup lang="ts">
import { NInput, NIcon, NDropdown } from 'naive-ui'
import { SearchOutline, AddOutline } from '@vicons/ionicons5'
import { lxVar } from '../theme/vars'

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
      class="search-input lx-search-input"
      :placeholder="placeholder"
      @update:value="emit('update:modelValue', $event)"
    >
      <template #prefix>
        <n-icon :component="SearchOutline" :size="16" :color="lxVar.textMuted" />
      </template>
    </n-input>
    <n-dropdown
      v-if="addOptions?.length"
      trigger="click"
      :options="addOptions"
      class="add-dropdown"
      @select="onAdd"
    >
      <div class="add-btn lx-icon-btn" title="添加">
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
  background: var(--lx-bg-panel);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
}
</style>
