<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NInput,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  batchRiskEvents,
  exportRiskEvents,
  handleRiskEvent,
  listRiskEvents,
  type RiskEventItem,
} from '@/api/riskEvents'
import { formatIp, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const batchSaving = ref(false)
const checkedKeys = ref<Array<string | number>>([])
const items = ref<RiskEventItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: 'pending',
  eventType: '',
  riskLevel: '',
  range: null as [number, number] | null,
})
const knownIds = ref<Set<string>>(new Set())
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
const POLL_MS = 5000

const showHandle = ref(false)
const handleTarget = ref<RiskEventItem | null>(null)
const handleAction = ref<'handled' | 'ignored'>('handled')
const resolution = ref('')
const userAction = ref<'none' | 'freeze' | 'ban'>('none')
const handleSaving = ref(false)

const statusOptions = computed(() => {
  void locale.value
  return [
    { label: t('common.allStatus'), value: '' },
    { label: t('risk.pending'), value: 'pending' },
    { label: t('risk.handled'), value: 'handled' },
    { label: t('risk.ignored'), value: 'ignored' },
  ]
})

const typeOptions = computed(() => {
  void locale.value
  return [
    { label: t('risk.allTypes'), value: '' },
    { label: t('risk.typeSensitive'), value: 'SENSITIVE_WORD_MATCH' },
    { label: t('risk.typeStorm'), value: 'MESSAGE_STORM' },
    { label: t('risk.typeLoginLock'), value: 'LOGIN_LOCK' },
    { label: t('risk.typeRateLimit'), value: 'RATE_LIMIT' },
  ]
})

const levelOptions = computed(() => {
  void locale.value
  return [
    { label: t('risk.allLevels'), value: '' },
    { label: t('risk.levelHigh'), value: 'high' },
    { label: t('risk.levelMedium'), value: 'medium' },
    { label: t('risk.levelLow'), value: 'low' },
  ]
})

function statusTag(status?: string) {
  const map: Record<string, 'warning' | 'success' | 'default'> = {
    pending: 'warning',
    handled: 'success',
    ignored: 'default',
  }
  const label: Record<string, string> = {
    pending: t('risk.pending'),
    handled: t('risk.handled'),
    ignored: t('risk.ignored'),
  }
  return h(
    NTag,
    { type: map[status || ''] || 'default', size: 'small' },
    () => label[status || ''] || status || '-'
  )
}

function levelTag(level?: string) {
  const map: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
    high: 'error',
    medium: 'warning',
    low: 'info',
  }
  const label: Record<string, string> = {
    high: t('risk.levelHigh'),
    medium: t('risk.levelMedium'),
    low: t('risk.levelLow'),
  }
  return h(
    NTag,
    { type: map[level || ''] || 'default', size: 'small' },
    () => label[level || ''] || level || '-'
  )
}

function typeLabel(type?: string) {
  const map: Record<string, string> = {
    SENSITIVE_WORD_MATCH: t('risk.typeSensitive'),
    MESSAGE_STORM: t('risk.typeStorm'),
    LOGIN_LOCK: t('risk.typeLoginLock'),
    RATE_LIMIT: t('risk.typeRateLimit'),
  }
  return map[type || ''] || type || '-'
}

const columns = computed<DataTableColumns<RiskEventItem>>(() => {
  void locale.value
  return [
    { type: 'selection', disabled: (row) => row.status !== 'pending' },
    { title: 'ID', key: 'id', width: 90 },
    {
      title: t('risk.eventType'),
      key: 'eventType',
      width: 120,
      render: (row) => typeLabel(row.eventType),
    },
    { title: t('risk.title'), key: 'title', width: 120 },
    { title: t('risk.detail'), key: 'detail', ellipsis: { tooltip: true } },
    { title: t('risk.user'), key: 'username', width: 110 },
    {
      title: 'IP',
      key: 'ip',
      width: 130,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('risk.region'),
      key: 'region',
      width: 160,
      ellipsis: { tooltip: true },
      render: (row) => row.region || '-',
    },
    {
      title: t('risk.riskLevel'),
      key: 'riskLevel',
      width: 90,
      render: (row) => levelTag(row.riskLevel),
    },
    {
      title: t('common.status'),
      key: 'status',
      width: 100,
      render: (row) => statusTag(row.status),
    },
    {
      title: t('common.time'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (row) =>
        h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'tiny', onClick: () => showDetail(row) }, () => t('common.detail')),
          row.status === 'pending' && auth.hasPermission('admin:risk-event:handle')
            ? h(
                NButton,
                {
                  size: 'tiny',
                  type: 'primary',
                  secondary: true,
                  onClick: () => openHandle(row, 'handled'),
                },
                () => t('risk.handle')
              )
            : null,
          row.status === 'pending' && auth.hasPermission('admin:risk-event:handle')
            ? h(
                NButton,
                { size: 'tiny', secondary: true, onClick: () => openHandle(row, 'ignored') },
                () => t('risk.ignore')
              )
            : null,
        ]),
    },
  ]
})

const userActionOptions = computed(() => {
  void locale.value
  const opts: { label: string; value: 'none' | 'freeze' | 'ban' }[] = [
    { label: t('risk.userActionNone'), value: 'none' },
  ]
  if (auth.hasPermission('admin:user:freeze')) {
    opts.push({ label: t('risk.userActionFreeze'), value: 'freeze' })
  }
  if (auth.hasPermission('admin:user:ban')) {
    opts.push({ label: t('risk.userActionBan'), value: 'ban' })
  }
  return opts
})

const canPunishUser = computed(() => {
  return (
    handleAction.value === 'handled' &&
    !!handleTarget.value?.userId &&
    userActionOptions.value.length > 1
  )
})

function openHandle(row: RiskEventItem, action: 'handled' | 'ignored') {
  handleTarget.value = row
  handleAction.value = action
  resolution.value = ''
  userAction.value = 'none'
  showHandle.value = true
}

function showDetail(row: RiskEventItem) {
  dialog.info({
    title: row.title || t('risk.detailTitle'),
    content: () =>
      h(
        'div',
        { style: 'line-height: 1.7; max-height: 420px; overflow: auto; white-space: pre-wrap;' },
        [
          h('div', `${t('risk.eventType')}: ${typeLabel(row.eventType)}`),
          h('div', `${t('risk.riskLevel')}: ${row.riskLevel || '-'}`),
          h('div', `${t('risk.user')}: ${row.username || row.userId || '-'}`),
          h('div', `${t('risk.target')}: ${row.targetResourceId || '-'}`),
          h('div', `IP: ${formatIp(row.ip)}`),
          h('div', `${t('risk.region')}: ${row.region || '-'}`),
          h('div', { style: 'margin-top: 10px;' }, row.detail || '-'),
          row.resolution
            ? h('div', { style: 'margin-top: 12px;' }, `${t('risk.resolution')}: ${row.resolution}`)
            : null,
        ]
      ),
    positiveText: t('common.confirm'),
  })
}

async function submitHandle() {
  if (!handleTarget.value) return
  handleSaving.value = true
  try {
    const punish = handleAction.value === 'handled' ? userAction.value : 'none'
    await handleRiskEvent(
      handleTarget.value.id,
      handleAction.value,
      resolution.value.trim() || undefined,
      punish
    )
    if (punish === 'freeze') {
      message.success(t('risk.handleWithFreezeSuccess'))
    } else if (punish === 'ban') {
      message.success(t('risk.handleWithBanSuccess'))
    } else {
      message.success(
        handleAction.value === 'handled' ? t('risk.handleSuccess') : t('risk.ignoreSuccess')
      )
    }
    showHandle.value = false
    await load()
  } finally {
    handleSaving.value = false
  }
}

async function load(opts?: { silent?: boolean; announceNew?: boolean }) {
  const silent = !!opts?.silent
  if (!silent) loading.value = true
  try {
    const data = await listRiskEvents({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      eventStatus: query.status || undefined,
      eventType: query.eventType || undefined,
      riskLevel: query.riskLevel || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    const next = data.items || []
    if (opts?.announceNew && knownIds.value.size > 0) {
      const fresh = next.filter((row) => !knownIds.value.has(String(row.id)))
      if (fresh.length > 0 && query.page === 1 && query.status === 'pending') {
        message.info(t('risk.newArrived', { n: fresh.length }))
      }
    }
    items.value = next
    total.value = data.total || 0
    knownIds.value = new Set(next.map((row) => String(row.id)))
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  query.page = 1
  checkedKeys.value = []
  load()
}

async function doExport() {
  exporting.value = true
  try {
    await exportRiskEvents({
      keyword: query.keyword || undefined,
      eventStatus: query.status || undefined,
      eventType: query.eventType || undefined,
      riskLevel: query.riskLevel || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

function doBatch(action: 'handled' | 'ignored') {
  if (checkedKeys.value.length === 0) {
    message.warning(t('risk.batchEmpty'))
    return
  }
  dialog.warning({
    title: action === 'handled' ? t('risk.batchHandleTitle') : t('risk.batchIgnoreTitle'),
    content: t('risk.batchConfirm', { n: checkedKeys.value.length }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      batchSaving.value = true
      try {
        const result = await batchRiskEvents(checkedKeys.value, action)
        checkedKeys.value = []
        if (result.failCount > 0) {
          message.warning(
            t('risk.batchPartial', { ok: result.successCount, fail: result.failCount })
          )
        } else {
          message.success(t('risk.batchSuccess', { n: result.successCount }))
        }
        await load()
      } finally {
        batchSaving.value = false
      }
    },
  })
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void load({ silent: true, announceNew: true })
  }
}

onMounted(() => {
  void load()
  pollTimer.value = setInterval(() => {
    if (document.visibilityState !== 'visible') return
    if (showHandle.value) return
    void load({ silent: true, announceNew: true })
  }, POLL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('risk.searchPlaceholder')"
            width="220px"
            @search="search"
          />
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 140px" />
          <NSelect v-model:value="query.eventType" :options="typeOptions" style="width: 150px" />
          <NSelect v-model:value="query.riskLevel" :options="levelOptions" style="width: 130px" />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
        </NSpace>
        <NSpace>
          <NButton
            v-if="auth.hasPermission('admin:risk-event:batch')"
            type="primary"
            secondary
            :loading="batchSaving"
            :disabled="checkedKeys.length === 0"
            @click="doBatch('handled')"
          >
            {{ t('risk.batchHandle') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:risk-event:batch')"
            secondary
            :loading="batchSaving"
            :disabled="checkedKeys.length === 0"
            @click="doBatch('ignored')"
          >
            {{ t('risk.batchIgnore') }}
          </NButton>
          <NButton
            v-if="auth.hasPermission('admin:risk-event:export')"
            :loading="exporting"
            @click="doExport"
          >
            {{ t('common.export') }}
          </NButton>
        </NSpace>
      </NSpace>
      <NDataTable
        v-model:checked-row-keys="checkedKeys"
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1200"
        :row-key="(row: RiskEventItem) => row.id"
        :pagination="{
          page: query.page,
          pageSize: query.size,
          itemCount: total,
          showSizePicker: true,
          pageSizes: [10, 20, 50],
          onUpdatePage: (p: number) => {
            query.page = p
            load()
          },
          onUpdatePageSize: (s: number) => {
            query.size = s
            query.page = 1
            load()
          },
        }"
        remote
      />
    </div>

    <NModal
      v-model:show="showHandle"
      preset="card"
      :title="handleAction === 'handled' ? t('risk.handleTitle') : t('risk.ignoreTitle')"
      style="width: 520px"
    >
      <p class="quote">{{ handleTarget?.detail || '-' }}</p>
      <p v-if="handleTarget?.username || handleTarget?.userId" class="user-line">
        {{ t('risk.user') }}: {{ handleTarget?.username || handleTarget?.userId }}
      </p>
      <NInput
        v-model:value="resolution"
        type="textarea"
        :rows="3"
        :placeholder="t('risk.resolutionPlaceholder')"
      />
      <div v-if="canPunishUser" class="user-action">
        <div class="user-action-label">{{ t('risk.userAction') }}</div>
        <NRadioGroup v-model:value="userAction">
          <NSpace>
            <NRadio v-for="opt in userActionOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </NRadio>
          </NSpace>
        </NRadioGroup>
        <p v-if="userAction !== 'none'" class="user-action-hint">
          {{ userAction === 'ban' ? t('risk.userActionBanHint') : t('risk.userActionFreezeHint') }}
        </p>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showHandle = false">{{ t('common.cancel') }}</NButton>
          <NButton
            :type="
              handleAction === 'handled' ? (userAction === 'ban' ? 'error' : 'primary') : 'default'
            "
            :loading="handleSaving"
            @click="submitHandle"
          >
            {{ handleAction === 'handled' ? t('risk.handle') : t('risk.ignore') }}
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.quote {
  color: var(--lx-text-2);
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 180px;
  overflow: auto;
}
.user-line {
  margin: 0 0 12px;
  color: var(--lx-text-2);
}
.user-action {
  margin-top: 14px;
}
.user-action-label {
  font-weight: 600;
  margin-bottom: 8px;
}
.user-action-hint {
  margin: 8px 0 0;
  color: var(--lx-text-3, #999);
  font-size: 12px;
}
</style>
