<script setup lang="ts">
/**
 * 运营推荐位轮播：按 slotCode 拉取已发布内容。
 * 无数据时不渲染占位，避免干扰主界面。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { NCarousel } from 'naive-ui'
import { listRecommends, type AppRecommend, type RecommendSlot } from '../../api/recommends'
import { resolveRecommendSrc } from '../../utils/mediaUrl'
import { useI18n } from '../../i18n'

useI18n()

const props = withDefaults(
  defineProps<{
    slotCode: RecommendSlot
    height?: number | string
    radius?: number
    showArrow?: boolean
    showCaption?: boolean
  }>(),
  {
    height: 96,
    radius: 10,
    showArrow: false,
    showCaption: true
  }
)

const emit = defineEmits<{
  loaded: [payload: { count: number }]
}>()

const items = ref<AppRecommend[]>([])

const heightCss = computed(() =>
  typeof props.height === 'number' ? `${props.height}px` : props.height
)

const visible = computed(() => items.value.filter(b => !!resolveRecommendSrc(b.imageUrl)))

async function load() {
  try {
    const res = await listRecommends(props.slotCode)
    items.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch {
    items.value = []
  } finally {
    emit('loaded', { count: visible.value.length })
  }
}

function src(b: AppRecommend) {
  return resolveRecommendSrc(b.imageUrl)
}

function onClick(b: AppRecommend) {
  const url = (b.linkUrl || '').trim()
  if (!url || !/^https?:\/\//i.test(url)) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

watch(
  () => props.slotCode,
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
    class="ops-recommend"
    :style="{
      '--ops-h': heightCss,
      '--ops-r': `${radius}px`
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
        class="ops-recommend__slide"
        :class="{ clickable: !!b.linkUrl }"
        @click="onClick(b)"
      >
        <img class="ops-recommend__img" :src="src(b)" :alt="b.title || ''" draggable="false" />
        <div v-if="showCaption && (b.title || b.subtitle)" class="ops-recommend__caption">
          <div v-if="b.title" class="ops-recommend__title">{{ b.title }}</div>
          <div v-if="b.subtitle" class="ops-recommend__sub">{{ b.subtitle }}</div>
        </div>
      </div>
    </NCarousel>
    <div
      v-else
      class="ops-recommend__slide"
      :class="{ clickable: !!visible[0]?.linkUrl }"
      @click="onClick(visible[0]!)"
    >
      <img
        class="ops-recommend__img"
        :src="src(visible[0]!)"
        :alt="visible[0]?.title || ''"
        draggable="false"
      />
      <div
        v-if="showCaption && (visible[0]?.title || visible[0]?.subtitle)"
        class="ops-recommend__caption"
      >
        <div v-if="visible[0]?.title" class="ops-recommend__title">{{ visible[0]?.title }}</div>
        <div v-if="visible[0]?.subtitle" class="ops-recommend__sub">{{ visible[0]?.subtitle }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ops-recommend {
  width: 100%;
  height: var(--ops-h);
  overflow: hidden;
  border-radius: var(--ops-r);
  flex-shrink: 0;
}

.ops-recommend__slide {
  position: relative;
  width: 100%;
  height: var(--ops-h);
  overflow: hidden;
  border-radius: var(--ops-r);
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.04));
}

.ops-recommend__slide.clickable {
  cursor: pointer;
}

.ops-recommend__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  user-select: none;
  -webkit-user-drag: none;
}

.ops-recommend__caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 12px;
  color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  pointer-events: none;
}

.ops-recommend__title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}

.ops-recommend__sub {
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.9;
  line-height: 1.3;
}
</style>
