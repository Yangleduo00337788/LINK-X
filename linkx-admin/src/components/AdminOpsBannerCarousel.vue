<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NCarousel } from 'naive-ui'
import { listPublishedBanners, type BannerItem, type BannerPosition } from '@/api/banners'
import { resolveBannerSrc } from '@/utils/mediaUrl'

const props = withDefaults(
  defineProps<{
    position: BannerPosition
    /** 数字按 px；字符串为任意 CSS 高度（如 100%） */
    height?: number | string
    radius?: number
    showArrow?: boolean
  }>(),
  {
    height: 168,
    radius: 12,
    showArrow: true,
  }
)

const emit = defineEmits<{
  loaded: [payload: { count: number }]
}>()

useI18n()

const items = ref<BannerItem[]>([])
const loading = ref(false)

const heightCss = computed(() =>
  typeof props.height === 'number' ? `${props.height}px` : props.height
)

const visible = computed(() => items.value.filter((b) => !!resolveBannerSrc(b.imageUrl)))

async function load() {
  loading.value = true
  try {
    const data = await listPublishedBanners(props.position)
    items.value = Array.isArray(data) ? data : []
  } catch {
    items.value = []
  } finally {
    loading.value = false
    emit('loaded', { count: visible.value.length })
  }
}

function src(b: BannerItem) {
  return resolveBannerSrc(b.imageUrl)
}

function onClick(b: BannerItem) {
  const url = (b.linkUrl || '').trim()
  if (!url || !/^https?:\/\//i.test(url)) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

watch(
  () => props.position,
  () => {
    void load()
  }
)

onMounted(() => {
  void load()
})
</script>

<template>
  <div
    v-if="visible.length > 0"
    class="admin-ops-banner"
    :style="{
      '--aob-h': heightCss,
      '--aob-r': `${radius}px`,
    }"
  >
    <NCarousel
      v-if="visible.length > 1"
      autoplay
      :interval="4500"
      :show-arrow="showArrow"
      draggable
      :style="{ height: heightCss }"
    >
      <div
        v-for="b in visible"
        :key="b.id"
        class="admin-ops-banner__slide"
        :class="{ clickable: !!b.linkUrl }"
        @click="onClick(b)"
      >
        <img class="admin-ops-banner__img" :src="src(b)" :alt="b.title || ''" draggable="false" />
        <div v-if="b.title" class="admin-ops-banner__caption">{{ b.title }}</div>
      </div>
    </NCarousel>
    <div
      v-else
      class="admin-ops-banner__slide"
      :class="{ clickable: !!visible[0]?.linkUrl }"
      @click="onClick(visible[0]!)"
    >
      <img
        class="admin-ops-banner__img"
        :src="src(visible[0]!)"
        :alt="visible[0]?.title || ''"
        draggable="false"
      />
      <div v-if="visible[0]?.title" class="admin-ops-banner__caption">{{ visible[0].title }}</div>
    </div>
  </div>
</template>

<style scoped>
.admin-ops-banner {
  width: 100%;
  height: var(--aob-h);
  overflow: hidden;
  border-radius: var(--aob-r);
}

.admin-ops-banner__slide {
  position: relative;
  width: 100%;
  height: var(--aob-h);
  overflow: hidden;
  border-radius: var(--aob-r);
  background: var(--n-color-embedded, rgba(0, 0, 0, 0.04));
}

.admin-ops-banner__slide.clickable {
  cursor: pointer;
}

.admin-ops-banner__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  user-select: none;
  -webkit-user-drag: none;
}

.admin-ops-banner__caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  pointer-events: none;
}
</style>
