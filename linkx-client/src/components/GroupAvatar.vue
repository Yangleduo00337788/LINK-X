<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 群聊拼图头像（多成员头像拼接）。
 * 按成员数 1–9 排布；无自定义群头像时作为默认展示。
 */
import { computed, ref, watch } from 'vue'
import { DEFAULT_AVATAR_URL } from '../utils/defaultAvatar'
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
    const raw = normalizeMediaUrl(f.imageUrl)
    const isDefaultLogo = !raw || raw === DEFAULT_AVATAR_URL
    const hasCustom = isDisplayableMediaUrl(raw) && !failed.value[i] && !isDefaultLogo
    return {
      imageUrl: hasCustom ? raw : DEFAULT_AVATAR_URL,
      isLogo: !hasCustom,
      color: f.color || '#f0f4f8'
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
  if (cells.value[index]?.isLogo) return
  failed.value = { ...failed.value, [index]: true }
}
</script>

<template>
  <div
    v-if="useCustom"
    class="group-avatar single"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <img :src="customUrl" alt="" class="full-img" referrerpolicy="no-referrer" />
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
      :style="{ backgroundColor: cell.isLogo ? '#f0f4f8' : cell.color }"
    >
      <img
        :src="cell.imageUrl"
        alt=""
        class="cell-img"
        :class="{ 'cell-img--logo': cell.isLogo }"
        referrerpolicy="no-referrer"
        @error="onCellError(i)"
      />
    </div>
  </div>
  <div
    v-else
    class="group-avatar fallback"
    :style="{
      width: `${size}px`,
      height: `${size}px`
    }"
  >
    <img :src="DEFAULT_AVATAR_URL" alt="" class="full-img full-img--logo" referrerpolicy="no-referrer" />
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
  background: #f0f4f8;
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

.cell-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cell-img--logo {
  object-fit: contain;
  padding: 12%;
  box-sizing: border-box;
}

.full-img--logo {
  object-fit: contain;
  padding: 10%;
  box-sizing: border-box;
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
