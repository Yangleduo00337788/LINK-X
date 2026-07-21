<script setup lang="ts">
/**
 * 群聊拼图头像（微信风格）。
 * 按成员数 1–9 排布；无自定义群头像时作为默认展示。
 */
import { computed, ref, watch } from 'vue'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'

export interface GroupAvatarFace {
  text?: string
  color?: string
  imageUrl?: string
}

const props = defineProps<{
  size?: number
  /** 自定义群头像（有则优先整图，不拼图） */
  imageUrl?: string
  text?: string
  color?: string
  faces?: GroupAvatarFace[]
}>()

const size = computed(() => props.size ?? 44)
const gap = computed(() => Math.max(1, Math.round(size.value * 0.045)))
const failed = ref<Record<number, boolean>>({})

const customUrl = computed(() => normalizeMediaUrl(props.imageUrl))
const useCustom = computed(() => isDisplayableMediaUrl(customUrl.value))

const cells = computed(() => {
  const list = (props.faces || []).slice(0, 9)
  return list.map((f, i) => {
    const url = normalizeMediaUrl(f.imageUrl)
    const showImg = isDisplayableMediaUrl(url) && !failed.value[i]
    return {
      text: (f.text || '?').charAt(0),
      color: f.color || pickColor(f.text || String(i)),
      imageUrl: showImg ? url : '',
      showImg
    }
  })
})

const layoutClass = computed(() => {
  const n = Math.min(Math.max(cells.value.length, 1), 9)
  return `n${n}`
})

watch(
  () => props.faces,
  () => {
    failed.value = {}
  },
  { deep: true }
)

function onCellError(index: number) {
  failed.value = { ...failed.value, [index]: true }
}

const COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2', '#f5222d', '#faad14']

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return COLORS[hash % COLORS.length]
}
</script>

<template>
  <div
    v-if="useCustom"
    class="group-avatar single"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <img :src="customUrl" alt="" class="full-img" />
  </div>
  <div
    v-else-if="cells.length > 0"
    class="group-avatar collage"
    :class="layoutClass"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      gap: `${gap}px`,
      padding: `${gap}px`,
      fontSize: `${size}px`
    }"
  >
    <div
      v-for="(cell, i) in cells"
      :key="i"
      class="cell"
      :style="{ backgroundColor: cell.color }"
    >
      <img
        v-if="cell.showImg"
        :src="cell.imageUrl"
        alt=""
        @error="onCellError(i)"
      />
      <span v-else class="cell-text">{{ cell.text }}</span>
    </div>
  </div>
  <div
    v-else
    class="group-avatar fallback"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      backgroundColor: color || '#12b7f5',
      fontSize: `${size * 0.38}px`
    }"
  >
    {{ (text || '群').charAt(0) }}
  </div>
</template>

<style scoped>
.group-avatar {
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
}

.group-avatar.single .full-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.group-avatar.collage {
  display: grid;
  background: #d0d4dc;
}

.group-avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lx-bg-card, #fff);
  font-weight: 500;
  border-radius: 50%;
}

.cell {
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
}

.cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cell-text {
  color: #fff;
  font-weight: 600;
  font-size: 0.28em;
  line-height: 1;
  user-select: none;
}

.n1 {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
}
.n2 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr;
}
.n3 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}
.n3 .cell:nth-child(1) {
  grid-column: 1 / -1;
}
.n4 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}
.n5 {
  grid-template-columns: repeat(6, 1fr);
  grid-template-rows: 1fr 1fr;
}
.n5 .cell:nth-child(1) {
  grid-column: 2 / 4;
  grid-row: 1;
}
.n5 .cell:nth-child(2) {
  grid-column: 4 / 6;
  grid-row: 1;
}
.n5 .cell:nth-child(3) {
  grid-column: 1 / 3;
  grid-row: 2;
}
.n5 .cell:nth-child(4) {
  grid-column: 3 / 5;
  grid-row: 2;
}
.n5 .cell:nth-child(5) {
  grid-column: 5 / 7;
  grid-row: 2;
}
.n6 {
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}
.n7 {
  grid-template-columns: repeat(6, 1fr);
  grid-template-rows: 1fr 1fr 1fr;
}
.n7 .cell:nth-child(1) {
  grid-column: 3 / 5;
  grid-row: 1;
}
.n7 .cell:nth-child(2) {
  grid-column: 1 / 3;
  grid-row: 2;
}
.n7 .cell:nth-child(3) {
  grid-column: 3 / 5;
  grid-row: 2;
}
.n7 .cell:nth-child(4) {
  grid-column: 5 / 7;
  grid-row: 2;
}
.n7 .cell:nth-child(5) {
  grid-column: 1 / 3;
  grid-row: 3;
}
.n7 .cell:nth-child(6) {
  grid-column: 3 / 5;
  grid-row: 3;
}
.n7 .cell:nth-child(7) {
  grid-column: 5 / 7;
  grid-row: 3;
}
.n8 {
  grid-template-columns: repeat(6, 1fr);
  grid-template-rows: 1fr 1fr 1fr;
}
.n8 .cell:nth-child(1) {
  grid-column: 2 / 4;
  grid-row: 1;
}
.n8 .cell:nth-child(2) {
  grid-column: 4 / 6;
  grid-row: 1;
}
.n8 .cell:nth-child(3) {
  grid-column: 1 / 3;
  grid-row: 2;
}
.n8 .cell:nth-child(4) {
  grid-column: 3 / 5;
  grid-row: 2;
}
.n8 .cell:nth-child(5) {
  grid-column: 5 / 7;
  grid-row: 2;
}
.n8 .cell:nth-child(6) {
  grid-column: 1 / 3;
  grid-row: 3;
}
.n8 .cell:nth-child(7) {
  grid-column: 3 / 5;
  grid-row: 3;
}
.n8 .cell:nth-child(8) {
  grid-column: 5 / 7;
  grid-row: 3;
}
.n9 {
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}
</style>
