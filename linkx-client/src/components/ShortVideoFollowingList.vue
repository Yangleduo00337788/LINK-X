<!-- 作者：yangleduo -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from '../i18n'
import {
  followShortVideoAuthor,
  listShortVideoFollowingUsers,
  unfollowShortVideoAuthor,
  type ShortVideoFollowingUser
} from '../api/shortVideo'
import { resolveApiErrorMessage } from '../api/client'
import { resolveUserAvatarUrl } from '../utils/defaultAvatar'
import Avatar from './Avatar.vue'
import ShortVideoSubPageShell from './ShortVideoSubPageShell.vue'

const props = defineProps<{
  open: boolean
  userId: string
  title?: string
  selfManage?: boolean
}>()

const emit = defineEmits<{
  close: []
  select: [user: ShortVideoFollowingUser]
  unfollow: [userId: string]
  followChange: [userId: string, following: boolean]
}>()

const { t } = useI18n()

const users = ref<ShortVideoFollowingUser[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const hasMore = ref(true)
const togglingId = ref('')
const pageSize = 20

const isEmpty = computed(() => !loading.value && !error.value && users.value.length === 0)
const panelTitle = computed(() => props.title?.trim() || t('shortVideo.myFollowing'))
const manageOwnList = computed(() => props.selfManage === true)

async function loadUsers(reset = false) {
  const userId = props.userId?.trim()
  if (!userId) return
  if (reset) {
    users.value = []
    hasMore.value = true
    error.value = ''
  }
  if (!reset && !hasMore.value) return

  const isFirst = reset || users.value.length === 0
  if (isFirst) loading.value = true
  else loadingMore.value = true

  try {
    const beforeId = reset ? undefined : users.value[users.value.length - 1]?.followId
    const res = await listShortVideoFollowingUsers(userId, { beforeId, limit: pageSize })
    if (res.code !== 200) {
      throw new Error(res.message || 'load following users failed')
    }
    const rows = Array.isArray(res.data) ? res.data : []
    if (reset) {
      users.value = rows
    } else {
      const existing = new Set(users.value.map(item => item.followId))
      users.value.push(...rows.filter(item => !existing.has(item.followId)))
    }
    hasMore.value = rows.length >= pageSize
  } catch (e) {
    error.value = resolveApiErrorMessage(e, t('shortVideo.followingListLoadFail'))
    if (reset) users.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onScroll(event: Event) {
  const el = event.target as HTMLElement | null
  if (!el || loading.value || loadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    void loadUsers(false)
  }
}

function pickUser(user: ShortVideoFollowingUser) {
  emit('select', user)
}

async function unfollow(user: ShortVideoFollowingUser, event: Event) {
  event.stopPropagation()
  if (!user.userId || togglingId.value) return
  togglingId.value = user.userId
  try {
    const res = await unfollowShortVideoAuthor(user.userId)
    if (res.code !== 200) {
      throw new Error(res.message || 'unfollow failed')
    }
    users.value = users.value.filter(item => item.userId !== user.userId)
    emit('unfollow', user.userId)
  } catch (e) {
    error.value = resolveApiErrorMessage(e, t('shortVideo.followFail'))
  } finally {
    togglingId.value = ''
  }
}

async function toggleFollow(user: ShortVideoFollowingUser, event: Event) {
  event.stopPropagation()
  if (!user.userId || togglingId.value) return
  togglingId.value = user.userId
  const nextFollowing = !user.followingAuthor
  try {
    const res = nextFollowing
      ? await followShortVideoAuthor(user.userId)
      : await unfollowShortVideoAuthor(user.userId)
    if (res.code !== 200) {
      throw new Error(res.message || 'follow action failed')
    }
    user.followingAuthor = nextFollowing
    emit('followChange', user.userId, nextFollowing)
  } catch (e) {
    error.value = resolveApiErrorMessage(e, t('shortVideo.followFail'))
  } finally {
    togglingId.value = ''
  }
}

watch(
  () => [props.open, props.userId] as const,
  ([open, userId]) => {
    if (open && userId) {
      void loadUsers(true)
    }
  }
)

defineExpose({
  reload: () => loadUsers(true)
})
</script>

<template>
  <ShortVideoSubPageShell
    v-if="open"
    elevated
    :title="panelTitle"
    body-class="sv-subpage__body--flush"
    @close="emit('close')"
  >
    <div class="sv-following-scroll" @scroll="onScroll">
      <div v-if="loading" class="sv-subpage__empty">{{ t('common.loading') }}</div>
      <div v-else-if="error && users.length === 0" class="sv-subpage__empty sv-subpage__empty--error">
        {{ error }}
      </div>
      <div v-else-if="isEmpty" class="sv-subpage__empty">{{ t('shortVideo.noFollowing') }}</div>
      <ul v-else class="sv-following-list">
        <li v-for="user in users" :key="user.followId" class="sv-following-list__item">
          <button type="button" class="sv-following-list__main" @click="pickUser(user)">
            <Avatar
              class="sv-following-list__avatar"
              :text="(user.nickname || t('shortVideo.author')).slice(0, 1)"
              color="transparent"
              :size="48"
              :image-url="resolveUserAvatarUrl(user.avatar, user.userId)"
            />
            <span class="sv-following-list__info">
              <span class="sv-following-list__name">{{ user.nickname || t('shortVideo.author') }}</span>
              <span class="sv-following-list__meta">
                {{ t('shortVideo.topicPostCount', { n: user.postCount ?? 0 }) }}
              </span>
            </span>
          </button>
          <button
            v-if="manageOwnList"
            type="button"
            class="sv-following-list__unfollow"
            :disabled="Boolean(togglingId && togglingId !== user.userId)"
            @click="unfollow(user, $event)"
          >
            {{ togglingId === user.userId ? '…' : t('shortVideo.followed') }}
          </button>
          <button
            v-else
            type="button"
            class="sv-following-list__unfollow"
            :class="{ 'sv-following-list__unfollow--active': user.followingAuthor }"
            :disabled="Boolean(togglingId && togglingId !== user.userId)"
            @click="toggleFollow(user, $event)"
          >
            {{
              togglingId === user.userId
                ? '…'
                : user.followingAuthor
                  ? t('shortVideo.followed')
                  : t('shortVideo.follow')
            }}
          </button>
        </li>
      </ul>
      <div v-if="loadingMore" class="sv-subpage__footer">{{ t('common.loading') }}</div>
    </div>
  </ShortVideoSubPageShell>
</template>

<style scoped>
.sv-following-scroll {
  height: 100%;
  overflow-y: auto;
  padding: var(--lx-space-xs) var(--lx-space-xl) var(--lx-space-4xl);
}

.sv-following-list__unfollow--active {
  color: var(--lx-text-secondary);
}
</style>
