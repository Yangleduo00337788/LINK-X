<script setup lang="ts">
/**
 * 面板通用搜索栏组件。
 * <p>
 * 提供搜索输入框与可选的「添加」下拉菜单，
 * 用于聊天列表、联系人列表等左侧面板顶部。
 * </p>
 */
// Naive UI 输入框、图标、下拉菜单组件
import { NInput, NIcon, NDropdown } from 'naive-ui'
// Ionicons5 搜索与添加图标
import { SearchOutline, AddOutline } from '@vicons/ionicons5'
// 主题 CSS 变量工具
import { lxVar } from '../theme/vars'

// 定义组件属性，设置默认值
withDefaults(
  defineProps<{
    modelValue: string // 搜索关键词（v-model 绑定值）
    placeholder?: string // 输入框占位文字
    /** 有选项时显示右侧 + 下拉 */
    addOptions?: { label: string; key: string }[] // 添加按钮的下拉选项
  }>(),
  {
    placeholder: '搜索', // 默认占位文字
    addOptions: undefined // 默认不显示添加按钮
  }
)

// 定义组件事件：更新搜索值、选择添加选项
const emit = defineEmits<{
  'update:modelValue': [value: string] // v-model 双向绑定
  addSelect: [key: string] // 选中添加菜单项
}>()

// 处理添加下拉菜单选项选中
function onAdd(key: string) {
  emit('addSelect', key) // 向父组件派发选中项的 key
}
</script>

<template>
  <!-- 搜索栏容器 -->
  <div class="panel-search-bar">
    <!-- 搜索输入框 -->
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
    <!-- 可选的添加下拉按钮 -->
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
