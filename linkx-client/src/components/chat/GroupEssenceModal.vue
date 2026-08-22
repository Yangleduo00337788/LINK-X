<!-- 作者：yangleduo -->
﻿<script setup lang="ts">
// Vue 计算属性
import { computed, ref, watch } from 'vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
import { useMessage } from 'naive-ui'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 群元数据 Store
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'
import { LxButton } from '../ui'
import ModalWinHeadActions from '../ModalWinHeadActions.vue'

const { t } = useI18n()
const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { groupEssenceOpen } = storeToRefs(chatModalsStore)
const { closeGroupEssence } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)

const removingId = ref<string | null>(null)

// 当前群聊的精华消息列表
const items = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.essenceFor(id)
})

/** 群主/管理员可删除精华 */
const canManage = computed(() => {
  const sid = currentSessionId.value
  const me = userProfile.value.userId
  if (!sid || !me) return false
  const members = groupMetaStore.membersFor(sid)
  return members.some(m => m.id === me && (m.role === 'owner' || m.role === 'admin'))
})

watch(
  [groupEssenceOpen, currentSessionId],
  ([open, id]) => {
    if (!open || !id) return
    void groupMetaStore.fetchEssence(id)
    void groupMetaStore.fetchMembers(id)
  },
  { immediate: true }
)

// 关闭群精华弹窗
function close() {
  closeGroupEssence()
}

async function removeItem(id: string) {
  const sid = currentSessionId.value
  if (!sid || !canManage.value) return
  removingId.value = id
  try {
    const ok = await groupMetaStore.removeEssence(sid, id)
    if (ok) message.success(t('extra.essenceDeleted'))
    else message.error(t('extra.essenceDeleteFail'))
  } catch (e: unknown) {
    const ax = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(ax.response?.data?.message || ax.message || t('extra.essenceDeleteFail'))
  } finally {
    removingId.value = null
  }
}
</script>

<template>
  <!-- 群精华弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="groupEssenceOpen" class="modal-root" @click.self="close">
      <div class="essence-window" @click.stop>
        <!-- 窗口标题栏 -->
        <header class="lx-modal-win-head">
          <h2>{{ t('extra.groupEssenceTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <ModalWinHeadActions @close="close" />
        </header>
        <!-- 精华消息列表 -->
        <div class="list">
          <article v-for="item in items" :key="item.id" class="essence-card">
            <div class="card-head">
              <span class="user">{{ item.user }}</span>
              <div class="card-meta">
                <span class="date">{{ item.date }}</span>
                <LxButton
                  v-if="canManage"
                  variant="link-danger"
                  :disabled="removingId === item.id"
                  @click="removeItem(item.id)"
                >
                  {{ t('extra.removeEssence') }}
                </LxButton>
              </div>
            </div>
            <p class="content">{{ item.content }}</p>
          </article>
          <p v-if="!items.length" class="empty">{{ t('extra.noEssence') }}</p>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: var(--lx-z-dialog-top);
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lx-space-3xl);
}

.essence-window {
  width: min(520px, 94vw);
  max-height: min(480px, 85vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-lg) var(--lx-space-2xl) var(--lx-space-2xl);
}

.essence-card {
  padding: var(--lx-space-lg) 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--lx-space-lg);
  margin-bottom: var(--lx-space-sm);
}

.card-meta {
  display: flex;
  align-items: center;
  gap: var(--lx-space-md);
  flex-shrink: 0;
}

.user {
  font-size: var(--lx-font);
  font-weight: 600;
  color: var(--lx-text-body);
}

.date {
  font-size: var(--lx-font-sm);
  color: var(--lx-text-muted);
}

.content {
  margin: 0;
  font-size: var(--lx-font-md);
  color: var(--lx-text-secondary);
  word-break: break-all;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: var(--lx-space-5xl);
}
</style>
