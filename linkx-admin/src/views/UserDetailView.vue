<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const loading = ref(false)
const user = ref<AdminUserDetail | null>(null)
const devices = ref<DeviceItem[]>([])
const deviceLoading = ref(false)

/** 雪花 ID 必须保持字符串，Number() 会丢失精度导致查不到用户 */
const userId = computed(() => String(route.params.id || ''))

const regionText = computed(() => {
  if (!user.value) return '暂无'
  const parts = [user.value.country, user.value.province, user.value.region].filter(Boolean)
  return parts.length ? parts.join(' / ') : '暂无'
})

const rolesText = computed(() => {
  if (!user.value?.roles?.length) return '暂无'
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

const deviceColumns: DataTableColumns<DeviceItem> = [
  { title: '设备 ID', key: 'id', ellipsis: { tooltip: true } },
  { title: '名称', key: 'deviceName' },
  { title: '类型', key: 'deviceType', width: 100 },
  {
    title: 'IP',
    key: 'ip',
    width: 140,
    render: (row) => formatIp(row.ip),
  },
  {
    title: '当前',
    key: 'current',
    width: 80,
    render: (row) => (row.current ? h(NTag, { type: 'success', size: 'small' }, () => '是') : '否'),
  },
  {
    title: '最近活跃',
    key: 'lastActive',
    width: 170,
    render: (row) => formatTime(row.lastActive),
  },
]

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
    title: `确认${label}`,
    content,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      await action()
      message.success(`${label}成功`)
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
    <div class="page-header">
      <h1 class="page-title">用户详情</h1>
      <NSpace>
        <NButton @click="router.back()">返回</NButton>
        <NButton
          v-if="canToggleStatus && user?.status === 1 && canDisable"
          type="error"
          secondary
          @click="confirmAction('禁用', '禁用后该用户将无法登录，已登录会话会被踢下线。', () => freezeUser(userId))"
        >
          禁用
        </NButton>
        <NButton
          v-if="canToggleStatus && user?.status === 0 && canEnable"
          type="primary"
          secondary
          @click="confirmAction('启用', '确定重新启用该用户吗？', () => unfreezeUser(userId))"
        >
          启用
        </NButton>
      </NSpace>
    </div>
    <NSpin :show="loading">
      <div v-if="user" class="page-card">
        <NTabs type="line">
          <NTabPane name="profile" tab="基本信息">
            <NDescriptions label-placement="left" :column="2" bordered>
              <NDescriptionsItem label="ID">{{ user.id }}</NDescriptionsItem>
              <NDescriptionsItem label="用户名">{{ user.username }}</NDescriptionsItem>
              <NDescriptionsItem label="昵称">{{ user.nickname || '-' }}</NDescriptionsItem>
              <NDescriptionsItem label="状态">
                <NTag :type="userStatusType(user.status)" size="small">{{ userStatusLabel(user.status) }}</NTag>
              </NDescriptionsItem>
              <NDescriptionsItem label="邮箱">{{ displayOrNone(user.email) }}</NDescriptionsItem>
              <NDescriptionsItem label="手机">{{ displayOrNone(user.phone) }}</NDescriptionsItem>
              <NDescriptionsItem label="性别">{{ displayOrNone(user.gender) }}</NDescriptionsItem>
              <NDescriptionsItem label="地区">{{ regionText }}</NDescriptionsItem>
              <NDescriptionsItem label="签名" :span="2">{{ displayOrNone(user.signature) }}</NDescriptionsItem>
              <NDescriptionsItem label="角色" :span="2">{{ rolesText }}</NDescriptionsItem>
              <NDescriptionsItem label="创建时间">{{ formatTime(user.createTime) }}</NDescriptionsItem>
              <NDescriptionsItem label="更新时间">{{ formatTime(user.updateTime) }}</NDescriptionsItem>
            </NDescriptions>
          </NTabPane>
          <NTabPane name="devices" tab="设备">
            <NDataTable :columns="deviceColumns" :data="devices" :loading="deviceLoading" />
          </NTabPane>
        </NTabs>
      </div>
    </NSpin>
  </div>
</template>
