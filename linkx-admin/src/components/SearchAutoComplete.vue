<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAutoComplete } from 'naive-ui'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    suggestions?: string[]
    style?: string | Record<string, string>
    width?: string
  }>(),
  {
    placeholder: '',
    suggestions: () => [],
    width: '240px',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  search: []
  enter: []
}>()

const historyKey = 'linkx-admin-search-history'
const localHistory = ref<string[]>(loadHistory())

function loadHistory(): string[] {
  try {
    const raw = localStorage.getItem(historyKey)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string').slice(0, 12) : []
  } catch {
    return []
  }
}

function persistHistory(value: string) {
  const v = value.trim()
  if (!v) return
  const next = [v, ...localHistory.value.filter((x) => x !== v)].slice(0, 12)
  localHistory.value = next
  localStorage.setItem(historyKey, JSON.stringify(next))
}

const options = computed(() => {
  const q = props.modelValue.trim().toLowerCase()
  const pool = [...new Set([...props.suggestions, ...localHistory.value])]
  const filtered = q ? pool.filter((x) => x.toLowerCase().includes(q)) : pool
  return filtered.slice(0, 10).map((label) => ({ label, value: label }))
})

const mergedStyle = computed(() => {
  if (typeof props.style === 'string') return props.style
  return { width: props.width, ...props.style }
})

function onUpdate(v: string) {
  emit('update:modelValue', v ?? '')
}

function onSelect(v: string) {
  emit('update:modelValue', v)
  persistHistory(v)
  emit('search')
}

function onKeyup(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    persistHistory(props.modelValue)
    emit('enter')
    emit('search')
  }
}
</script>

<template>
  <NAutoComplete
    :value="modelValue"
    :options="options"
    clearable
    :placeholder="placeholder"
    :style="mergedStyle"
    @update:value="onUpdate"
    @select="onSelect"
    @keyup="onKeyup"
  />
</template>
