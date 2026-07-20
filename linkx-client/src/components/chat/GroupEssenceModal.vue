<script setup lang="ts">
// Vue 计算属性
import { computed } from 'vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 群元数据 Store
import { useGroupMetaStore } from '../../stores/groupMeta'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { groupEssenceOpen } = storeToRefs(chatModalsStore)
const { closeGroupEssence } = chatModalsStore
const { currentSession, currentSessionId } = storeToRefs(appStore)

// 当前群聊的精华消息列表
const items = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.essenceFor(id)
})

// 关闭群精华弹窗
function close() {
  closeGroupEssence()
}
</script>

<template>
  <!-- 群精华弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="groupEssenceOpen" class="modal-root" @click.self="close">
      <div class="essence-window" @click.stop>
        <!-- 窗口标题栏 -->
        <header class="win-head">
          <h2>{{ t('extra.groupEssenceTitle', { name: currentSession?.name || t('extra.groupChat') }) }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <!-- 精华消息列表 -->
        <div class="list">
          <article v-for="item in items" :key="item.id" class="essence-card">
            <div class="card-head">
              <span class="user">{{ item.user }}</span>
              <span class="date">{{ item.date }}</span>
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
  z-index: 2250;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
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

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--lx-border-light);
}

.win-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 18px 18px;
}

.essence-card {
  padding: 12px 0;
  border-bottom: 1px solid var(--lx-border-light);
}

.card-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.user {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.date {
  font-size: 12px;
  color: var(--lx-text-muted);
}

.content {
  margin: 0;
  font-size: 13px;
  color: var(--lx-text-secondary);
  word-break: break-all;
}

.empty {
  text-align: center;
  color: var(--lx-text-muted);
  padding: 32px;
}
</style>
