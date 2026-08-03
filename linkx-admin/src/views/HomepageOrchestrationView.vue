<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NInputNumber,
  NSpace,
  NSwitch,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  listHomepageSections,
  reorderHomepageSections,
  type HomepageSectionItem,
} from '@/api/homepageSections'
import { useAuthStore } from '@/stores/auth'

const message = useMessage()
const auth = useAuthStore()
const router = useRouter()
const { t, locale } = useI18n()

const loading = ref(false)
const saving = ref(false)
const items = ref<HomepageSectionItem[]>([])

const canEdit = computed(() => auth.hasPermission('admin:homepage:edit'))

function typeLabel(type?: string) {
  const map: Record<string, string> = {
    banner: t('homepage.typeBanner'),
    recommend: t('homepage.typeRecommend'),
    activity: t('homepage.typeActivity'),
    notice: t('homepage.typeNotice'),
  }
  return map[type || ''] || type || '-'
}

const columns = computed<DataTableColumns<HomepageSectionItem>>(() => {
  void locale.value
  return [
    { title: t('homepage.sectionTitle'), key: 'title', width: 180 },
    {
      title: t('homepage.sectionType'),
      key: 'sectionType',
      width: 110,
      render: (row) => h(NTag, { size: 'small' }, () => typeLabel(row.sectionType)),
    },
    { title: t('homepage.sectionKey'), key: 'sectionKey', width: 120 },
    {
      title: t('homepage.publishedCount'),
      key: 'publishedCount',
      width: 100,
      render: (row) => row.publishedCount ?? 0,
    },
    {
      title: t('homepage.sortOrder'),
      key: 'sortOrder',
      width: 120,
      render: (row) =>
        canEdit.value
          ? h(NInputNumber, {
              value: row.sortOrder ?? 0,
              min: 0,
              max: 9999,
              size: 'small',
              onUpdateValue: (v: number | null) => {
                row.sortOrder = v ?? 0
              },
            })
          : String(row.sortOrder ?? 0),
    },
    {
      title: t('common.enabled'),
      key: 'enabled',
      width: 90,
      render: (row) =>
        canEdit.value
          ? h(NSwitch, {
              value: row.enabled !== false,
              onUpdateValue: (v: boolean) => {
                row.enabled = v
              },
            })
          : row.enabled !== false
            ? t('common.enabled')
            : t('common.disabled'),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      render: (row) =>
        row.managePath
          ? h(NButton, { size: 'tiny', onClick: () => router.push(row.managePath!) }, () =>
              t('homepage.manageContent')
            )
          : '-',
    },
  ]
})

async function load() {
  loading.value = true
  try {
    items.value = (await listHomepageSections()) || []
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await reorderHomepageSections({
      items: items.value.map((row) => ({
        id: row.id,
        enabled: row.enabled,
        sortOrder: row.sortOrder ?? 0,
      })),
    })
    message.success(t('common.saveSuccess'))
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <p class="hint">{{ t('homepage.hint') }}</p>
        <NButton v-if="canEdit" type="primary" :loading="saving" @click="save">
          {{ t('common.save') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="900"
        :pagination="false"
      />
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 0;
  color: var(--lx-text-2);
  font-size: 13px;
}
</style>
