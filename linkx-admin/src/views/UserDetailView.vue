<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NSpace,
  NSpin,
  NTabPane,
  NTabs,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  freezeUser,
  getUser,
  listUserDevices,
  unfreezeUser,
  type AdminUserDetail,
  type DeviceItem,
} from '@/api/users'
import { formatTime, displayOrNone, formatIp, userStatusLabel, userStatusType } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const { t, locale } = useI18n()

const loading = ref(false)
const user = ref<AdminUserDetail | null>(null)
const devices = ref<DeviceItem[]>([])
const deviceLoading = ref(false)

const userId = computed(() => String(route.params.id || ''))

const regionText = computed(() => {
  void locale.value
  if (!user.value) return t('common.none')
  const parts = [user.value.country, user.value.province, user.value.region].filter(Boolean)
  return parts.length ? parts.join(' / ') : t('common.none')
})

const rolesText = computed(() => {
  void locale.value
  if (!user.value?.roles?.length) return t('common.none')
  return user.value.roles.join(', ')
})

const ADMIN_ROLES = new Set(['admin', 'super_admin'])

const canToggleStatus = computed(() => {
  if (!user.value) return false
  if (String(user.value.id) === String(auth.user?.id)) return false
  if (user.value.roles?.some((r) => ADMIN_ROLES.has(r))) return false
  return true
})

const canDisable = computed(() => auth.hasPermission(['admin:user:freeze', 'admin:user:ban']))
const canEnable = computed(() => auth.hasPermission(['admin:user:unfreeze', 'admin:user:unban']))

const deviceColumns = computed<DataTableColumns<DeviceItem>>(() => {
  void locale.value
  return [
    { title: t('user.deviceId'), key: 'id', ellipsis: { tooltip: true } },
    { title: t('user.deviceName'), key: 'deviceName' },
    { title: t('user.deviceType'), key: 'deviceType', width: 100 },
    {
      title: 'IP',
      key: 'ip',
      width: 140,
      render: (row) => formatIp(row.ip),
    },
    {
      title: t('user.current'),
      key: 'current',
      width: 80,
      render: (row) =>
        row.current
          ? h(NTag, { type: 'success', size: 'small' }, () => t('common.yes'))
          : t('common.no'),
    },
    {
      title: t('user.lastActive'),
      key: 'lastActive',
      width: 170,
      render: (row) => formatTime(row.lastActive),
    },
  ]
})

async function load() {
  loading.value = true
  try {
    user.value = await getUser(userId.value)
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  deviceLoading.value = true
  try {
    devices.value = (await listUserDevices(userId.value)) || []
  } finally {
    deviceLoading.value = false
  }
}

function confirmAction(label: string, content: string, action: () => Promise<unknown>) {
  dialog.warning({
    title: t('common.confirmAction', { action: label }),
    content,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      await action()
      message.success(t('common.actionSuccess', { action: label }))
      await load()
    },
  })
}

onMounted(async () => {
  await load()
  await loadDevices()
})
</script>

<template>
  <div class="page">
    <NSpin :show="loading">
      <div v-if="user" class="page-shell">
        <NSpace class="page-toolbar" justify="end">
          <NButton @click="router.back()">{{ t('common.back') }}</NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 1 && canDisable"
            type="error"
            secondary
            @click="confirmAction(t('user.freeze'), t('user.freezeConfirm'), () => freezeUser(userId))"
          >
            {{ t('user.freeze') }}
          </NButton>
          <NButton
            v-if="canToggleStatus && user?.status === 0 && canEnable"
            type="primary"
            secondary
            @click="confirmAction(t('user.unfreeze'), t('user.unfreezeConfirm'), () => unfreezeUser(userId))"
          >
            {{ t('user.unfreeze') }}
          </NButton>
        </NSpace>
        <NTabs type="line">
          <NTabPane name="profile" :tab="t('user.tabProfile')">
            <NDescriptions label-placement="left" :column="2" bordered>
              <NDescriptionsItem label="ID">{{ user.id }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.username')">{{ user.username }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.nickname')">{{ user.nickname || '-' }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('common.status')">
                <NTag :type="userStatusType(user.status)" size="small">{{ userStatusLabel(user.status) }}</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem :label="t('user.email')">{{ displayOrNone(user.email) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.phone')">{{ displayOrNone(user.phone) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.gender')">{{ displayOrNone(user.gender) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.region')">{{ regionText }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.signature')" :span="2">{{ displayOrNone(user.signature) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('user.roles')" :span="2">{{ rolesText }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('common.createTime')">{{ formatTime(user.createTime) }}</NDescriptionsItem>
              <NDescriptionsItem :label="t('common.updateTime')">{{ formatTime(user.updateTime) }}</NDescriptionsItem>
            </NDescriptions>
          </NTabPane>
          <NTabPane name="devices" :tab="t('user.tabDevices')">
            <NDataTable :columns="deviceColumns" :data="devices" :loading="deviceLoading" />
          </NTabPane>
        </NTabs>
      </div>
    </NSpin>
  </div>
</template>
