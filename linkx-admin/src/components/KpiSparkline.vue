<script setup lang="ts">
import { computed, ref } from 'vue'
import { buildSparkOption, useChart } from '@/utils/charts'

const props = withDefaults(
  defineProps<{
    values?: number[]
    color?: string
  }>(),
  { values: () => [] }
)

const el = ref<HTMLElement | null>(null)
const option = computed(() => buildSparkOption(props.values || [], props.color))
useChart(el, option)
</script>

<template>
  <div ref="el" class="kpi-sparkline" />
</template>

<style scoped>
.kpi-sparkline {
  width: 100%;
  height: 44px;
  min-width: 0;
}
</style>
