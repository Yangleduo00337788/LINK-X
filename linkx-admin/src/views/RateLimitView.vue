<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  addRateLimitWhitelist,
  listRateLimitHits,
  listRateLimitWhitelist,
  removeRateLimitWhitelist,
  unblockRateLimitIp,
  type RateLimitHit,
} from '@/api/rateLimits'
import { useAuthStore } from '@/stores/auth'

const { t, locale } = useI18n()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const hits = ref<RateLimitHit[]>([])
const whitelist = ref<string[]>([])
const query = reactive({ ip: '' })
const whitelistIp = ref('')
const savingWhitelist = ref(false)

const canUnblock = computed(() => auth.hasPermission('admin:rate-limit:unblock'))
const canWhitelist = computed(() => auth.hasPermission('admin:rate-limit:whitelist'))

const hitColumns = computed<DataTableColumns<RateLimitHit>>(() => {
  void locale.value
  return [
    { title: t('rateLimit.scope'), key: 'scope', width: 140, ellipsis: { tooltip: true } },
    { title: 'IP', key: 'ip', width: 140, render: (row) => row.ip || '-' },
    { title: t('rateLimit.identity'), key: 'identity', ellipsis: { tooltip: true } },
    { title: t('rateLimit.count'), key: 'count', width: 90 },
    {
      title: 'TTL',
      key: 'ttlSeconds',
      width: 100,
      render: (row) => (row.ttlSeconds == null ? '-' : `${row.ttlSeconds}s`),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      render: (row) => {
        if (!canUnblock.value || !row.ip) return '-'
        return h(
          NButton,
          {
            size: 'small',
            type: 'warning',
            onClick: () => confirmUnblock(row.ip!),
          },
          () => t('rateLimit.unblock')
        )
      },
    },
  ]
})

async function loadHits() {
  loading.value = true
  try {
    hits.value =
      (await listRateLimitHits({
        ip: query.ip || undefined,
        limit: 200,
      })) || []
  } finally {
    loading.value = false
  }
}

async function loadWhitelist() {
  whitelist.value = (await listRateLimitWhitelist()) || []
}

function confirmUnblock(ip: string) {
  dialog.warning({
    title: t('rateLimit.unblockConfirmTitle'),
    content: t('rateLimit.unblockConfirm', { ip }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      const res = await unblockRateLimitIp(ip)
      message.success(t('rateLimit.unblockSuccess', { n: res?.deleted ?? 0 }))
      await loadHits()
    },
  })
}

async function addWhitelist() {
  const ip = whitelistIp.value.trim()
  if (!ip) {
    message.warning(t('rateLimit.ipRequired'))
    return
  }
  savingWhitelist.value = true
  try {
    await addRateLimitWhitelist(ip)
    whitelistIp.value = ''
    message.success(t('common.success'))
    await loadWhitelist()
  } finally {
    savingWhitelist.value = false
  }
}

function confirmRemoveWhitelist(ip: string) {
  dialog.warning({
    title: t('rateLimit.removeWhitelistTitle'),
    content: t('rateLimit.removeWhitelistConfirm', { ip }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await removeRateLimitWhitelist(ip)
      message.success(t('common.success'))
      await loadWhitelist()
    },
  })
}

function goHistory() {
  void router.push({ path: '/admin/risk-events', query: { eventType: 'RATE_LIMIT' } })
}

onMounted(() => {
  void loadHits()
  void loadWhitelist()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <NSpace>
        <NInput
          v-model:value="query.ip"
          clearable
          :placeholder="t('rateLimit.ipFilter')"
          style="width: 220px"
          @keyup.enter="loadHits"
        />
        <NButton type="primary" :loading="loading" @click="loadHits">
          {{ t('common.search') }}
        </NButton>
        <NButton @click="goHistory">{{ t('rateLimit.historyEvents') }}</NButton>
      </NSpace>
    </div>

    <NDataTable
      :columns="hitColumns"
      :data="hits"
      :loading="loading"
      :bordered="false"
      size="small"
      :scroll-x="900"
    />

    <div class="whitelist">
      <h3>{{ t('rateLimit.whitelist') }}</h3>
      <NForm v-if="canWhitelist" inline>
        <NFormItem :label="t('rateLimit.whitelistIp')">
          <NInput v-model:value="whitelistIp" :placeholder="'1.2.3.4'" style="width: 200px" />
        </NFormItem>
        <NFormItem>
          <NButton type="primary" :loading="savingWhitelist" @click="addWhitelist">
            {{ t('rateLimit.addWhitelist') }}
          </NButton>
        </NFormItem>
      </NForm>
      <NSpace>
        <NTag
          v-for="ip in whitelist"
          :key="ip"
          :closable="canWhitelist"
          type="success"
          @close="confirmRemoveWhitelist(ip)"
        >
          {{ ip }}
        </NTag>
        <span v-if="!whitelist.length" class="muted">{{ t('rateLimit.whitelistEmpty') }}</span>
      </NSpace>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
}
.whitelist {
  padding-top: 8px;
  border-top: 1px solid var(--lx-border, rgba(127, 127, 127, 0.2));
}
.whitelist h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.muted {
  color: var(--lx-text-3, #999);
  font-size: 13px;
}
</style>
