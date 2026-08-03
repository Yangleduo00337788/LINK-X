<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref, type VNode } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDatePicker,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
  type SelectOption,
} from 'naive-ui'
import {
  banDevice,
  exportDevices,
  kickDevice,
  listDevices,
  unbanDevice,
  type AdminDeviceItem,
} from '@/api/devices'
import { onAdminRealtimeEvent } from '@/api/realtime'
import { formatIp, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import SearchAutoComplete from '@/components/SearchAutoComplete.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const items = ref<AdminDeviceItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  deviceType: null as string | null,
  range: null as [number, number] | null,
})

const deviceTypeOptions = computed<SelectOption[]>(() => {
  void locale.value
  return [
    { label: 'Web', value: 'Web' },
    { label: 'Desktop', value: 'Desktop' },
    { label: 'Android', value: 'Android' },
    { label: 'iOS', value: 'iOS' },
  ]
})

let offRealtime: (() => void) | null = null
let reloadTimer: ReturnType<typeof setTimeout> | null = null

const columns = computed<DataTableColumns<AdminDeviceItem>>(() => {
  void locale.value
  return [
    {
      title: t('user.username'),
      key: 'username',
      width: 130,
      ellipsis: { tooltip: true },
      render: (row) =>
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            disabled: !row.userId || !auth.hasPermission('admin:user:view'),
            onClick: () => row.userId && router.push(`/admin/users/${row.userId}`),
          },
          () => row.username || row.userId || '-'
        ),
    },
    {
      title: t('user.nickname'),
      key: 'nickname',
      width: 120,
      ellipsis: { tooltip: true },
      render: (row) => row.nickname || '-',
    },
    {
      title: t('user.deviceId'),
      key: 'deviceId',
      ellipsis: { tooltip: true },
    },
    {
      title: t('user.deviceName'),
      key: 'deviceName',
      width: 140,
      ellipsis: { tooltip: true },
      render: (row) => row.deviceName || '-',
    },
    {
      title: t('user.deviceType'),
      key: 'deviceType',
      width: 100,
      render: (row) => row.deviceType || '-',
    },
    {
      title: t('device.onlineStatus'),
      key: 'online',
      width: 90,
      render: (row) =>
        h(NTag, { type: row.online ? 'success' : 'default', size: 'small' }, () =>
          row.online ? t('device.online') : t('device.offline')
        ),
    },
    {
      title: t('device.banned'),
      key: 'banned',
      width: 90,
      render: (row) =>
        row.banned
          ? h(NTag, { type: 'error', size: 'small' }, () => t('device.banned'))
          : h(NTag, { type: 'success', size: 'small' }, () => t('device.notBanned')),
    },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('user.lastActive'),
      key: 'lastActive',
      width: 170,
      render: (row) => formatTime(row.lastActive),
    },
    {
      title: t('common.createTime'),
      key: 'createTime',
      width: 170,
      render: (row) => formatTime(row.createTime),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (row) => {
        const buttons: VNode[] = []
        if (auth.hasPermission('admin:device:kick')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'warning',
                secondary: true,
                onClick: () => confirmKick(row),
              },
              () => t('device.kick')
            )
          )
        }
        if (row.banned && auth.hasPermission('admin:device:unban')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                secondary: true,
                onClick: () => confirmUnban(row),
              },
              () => t('device.unban')
            )
          )
        } else if (!row.banned && auth.hasPermission('admin:device:ban')) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                secondary: true,
                onClick: () => confirmBan(row),
              },
              () => t('device.ban')
            )
          )
        }
        return buttons.length ? h(NSpace, { size: 4 }, () => buttons) : null
      },
    },
  ]
})

function applyPresenceEvent(evt: {
  relatedId?: string
  userId?: string
  deviceId?: unknown
  online?: unknown
}) {
  const userId = String(evt.relatedId || evt.userId || '')
  const deviceId = typeof evt.deviceId === 'string' ? evt.deviceId : ''
  if (!userId || !deviceId) return
  const online = Boolean(evt.online)
  let matched = false
  items.value = items.value.map((row) => {
    if (String(row.userId) === userId && row.deviceId === deviceId) {
      matched = true
      return {
        ...row,
        online,
        lastActive: online ? new Date().toISOString() : row.lastActive,
      }
    }
    return row
  })
  // 新上线设备不在当前页时，防抖刷新列表
  if (online && !matched) {
    if (reloadTimer) clearTimeout(reloadTimer)
    reloadTimer = setTimeout(() => {
      void load()
    }, 800)
  }
}

function confirmKick(row: AdminDeviceItem) {
  if (!row.userId || !row.deviceId) return
  dialog.warning({
    title: t('device.kickTitle'),
    content: t('device.kickConfirm', {
      user: row.username || row.userId,
      device: row.deviceName || row.deviceId,
    }),
    positiveText: t('device.kick'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await kickDevice(row.userId!, row.deviceId)
      message.success(t('device.kickSuccess'))
      await load()
    },
  })
}

function confirmBan(row: AdminDeviceItem) {
  if (!row.userId || !row.deviceId) return
  dialog.error({
    title: t('device.banTitle'),
    content: t('device.banConfirm', {
      user: row.username || row.userId,
      device: row.deviceName || row.deviceId,
    }),
    positiveText: t('device.ban'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await banDevice(row.userId!, row.deviceId)
      message.success(t('device.banSuccess'))
      await load()
    },
  })
}

function confirmUnban(row: AdminDeviceItem) {
  if (!row.userId || !row.deviceId) return
  dialog.warning({
    title: t('device.unbanTitle'),
    content: t('device.unbanConfirm', {
      user: row.username || row.userId,
      device: row.deviceName || row.deviceId,
    }),
    positiveText: t('device.unban'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await unbanDevice(row.userId!, row.deviceId)
      message.success(t('device.unbanSuccess'))
      await load()
    },
  })
}

async function load() {
  loading.value = true
  try {
    const data = await listDevices({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      deviceType: query.deviceType || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function doExport() {
  exporting.value = true
  try {
    await exportDevices({
      keyword: query.keyword || undefined,
      deviceType: query.deviceType || undefined,
      startTime: query.range?.[0],
      endTime: query.range?.[1],
    })
    message.success(t('common.exportSuccess'))
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  void load()
  offRealtime = onAdminRealtimeEvent((evt) => {
    if (evt?.type === 'device_presence') {
      applyPresenceEvent(evt)
    }
  })
})

onUnmounted(() => {
  offRealtime?.()
  offRealtime = null
  if (reloadTimer) {
    clearTimeout(reloadTimer)
    reloadTimer = null
  }
})
</script>

<template>
  <div class="page">
    <div class="page-shell">
      <NSpace class="page-toolbar" justify="space-between">
        <NSpace>
          <SearchAutoComplete
            v-model="query.keyword"
            :placeholder="t('device.searchPlaceholder')"
            width="260px"
            @search="search"
          />
          <NSelect
            v-model:value="query.deviceType"
            :options="deviceTypeOptions"
            :placeholder="t('device.deviceType')"
            clearable
            style="width: 140px"
            @update:value="search"
          />
          <NDatePicker
            v-model:value="query.range"
            type="datetimerange"
            clearable
            style="width: 360px"
          />
          <NButton type="primary" @click="search">{{ t('common.search') }}</NButton>
          <NButton @click="load">{{ t('common.refresh') }}</NButton>
        </NSpace>
        <NButton
          v-if="auth.hasPermission('admin:device:export')"
          :loading="exporting"
          @click="doExport"
        >
          {{ t('common.export') }}
        </NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="items"
        :loading="loading"
        :scroll-x="1200"
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
  </div>
</template>
