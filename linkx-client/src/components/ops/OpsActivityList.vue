<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 运营活动列表：展示已发布活动封面与标题。
 */
import { computed, onMounted, ref } from 'vue'
import { listActivities, type AppActivity } from '../../api/activities'
import { resolveActivitySrc } from '../../utils/mediaUrl'
import { useI18n } from '../../i18n'

const props = withDefaults(
  defineProps<{
    /** 最多展示条数 */
    limit?: number
    showEmpty?: boolean
  }>(),
  {
    limit: 8,
    showEmpty: false
  }
)

const emit = defineEmits<{
  loaded: [payload: { count: number }]
}>()

const { t } = useI18n()
const items = ref<AppActivity[]>([])

const visible = computed(() =>
  items.value
    .filter(a => !!resolveActivitySrc(a.coverUrl))
    .slice(0, props.limit)
)

async function load() {
  try {
    const res = await listActivities()
    items.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch {
    items.value = []
  } finally {
    emit('loaded', { count: visible.value.length })
  }
}

function src(a: AppActivity) {
  return resolveActivitySrc(a.coverUrl)
}

function onClick(a: AppActivity) {
  const url = (a.linkUrl || '').trim()
  if (!url || !/^https?:\/\//i.test(url)) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div v-if="visible.length > 0 || showEmpty" class="ops-activity">
    <div class="ops-activity__head">{{ t('ops.activitiesTitle') }}</div>
    <div v-if="visible.length === 0" class="ops-activity__empty">{{ t('ops.noActivities') }}</div>
    <div v-else class="ops-activity__grid">
      <button
        v-for="a in visible"
        :key="a.id"
        type="button"
        class="ops-activity__card"
        :class="{ clickable: !!a.linkUrl }"
        @click="onClick(a)"
      >
        <img class="ops-activity__cover" :src="src(a)" :alt="a.title || ''" draggable="false" />
        <div class="ops-activity__meta">
          <div class="ops-activity__title">{{ a.title || t('ops.untitledActivity') }}</div>
          <div v-if="a.description" class="ops-activity__desc">{{ a.description }}</div>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.ops-activity {
  width: 100%;
  max-width: 560px;
}

.ops-activity__head {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-primary, inherit);
}

.ops-activity__empty {
  font-size: 13px;
  color: var(--lx-text-secondary, #888);
}

.ops-activity__grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ops-activity__card {
  display: flex;
  gap: 12px;
  align-items: stretch;
  width: 100%;
  padding: 0;
  border: none;
  border-radius: 12px;
  overflow: hidden;
  text-align: left;
  background: var(--lx-bg-card, rgba(0, 0, 0, 0.03));
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
  cursor: default;
  color: inherit;
}

.ops-activity__card.clickable {
  cursor: pointer;
}

.ops-activity__card.clickable:hover {
  background: var(--lx-bg-hover, rgba(0, 0, 0, 0.06));
}

.ops-activity__cover {
  width: 112px;
  height: 72px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--lx-bg-hover, #eee);
}

.ops-activity__meta {
  flex: 1;
  min-width: 0;
  padding: 10px 12px 10px 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.ops-activity__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ops-activity__desc {
  font-size: 12px;
  color: var(--lx-text-secondary, #888);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
