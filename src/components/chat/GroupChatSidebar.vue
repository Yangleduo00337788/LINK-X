<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { SearchOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { openGroupAnnouncement } = chatModalsStore
const { currentSessionId } = storeToRefs(appStore)

const announcementText = computed(() => {
  const id = currentSessionId.value
  return id ? groupMetaStore.announcementShort(id) : ''
})

const members = computed(() => {
  const id = currentSessionId.value
  if (!id) return []
  return groupMetaStore.membersFor(id)
})

const memberCount = computed(() => members.value.length)
</script>

<template>
  <aside class="group-side">
    <section class="announce-block">
      <div class="announce-head">
        <h3 class="side-title">群公告</h3>
        <button type="button" class="arrow-btn" title="查看群公告" @click="openGroupAnnouncement">
          <n-icon :component="ChevronForwardOutline" :size="18" />
        </button>
      </div>
      <button type="button" class="announce-text-btn" @click="openGroupAnnouncement">
        {{ announcementText }}
      </button>
    </section>
    <section class="members-block">
      <div class="members-head">
        <span class="side-title">群聊成员 {{ memberCount }}</span>
        <n-icon :component="SearchOutline" :size="18" class="search-ico" title="搜索成员" />
      </div>
      <div class="member-list">
        <div v-for="m in members" :key="m.id" class="member-row">
          <Avatar :text="m.avatarText" :color="m.avatarColor" :size="36" />
          <div class="m-info">
            <span class="m-name">{{ m.name }}</span>
            <span v-if="m.badge" class="m-badge">{{ m.badge }}</span>
          </div>
        </div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.group-side {
  width: 240px;
  flex-shrink: 0;
  height: 100%;
  background: var(--lx-bg-panel);
  border-left: 1px solid var(--lx-border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.announce-block {
  flex-shrink: 0;
  padding: 14px 12px;
  border-bottom: 1px solid var(--lx-bg-panel-deep);
  background: var(--lx-bg-panel);
}

.announce-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.side-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.arrow-btn {
  border: none;
  background: transparent;
  color: var(--lx-text-muted);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.arrow-btn:hover {
  color: var(--lx-accent);
}

.announce-text-btn {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--lx-text-secondary);
  word-break: break-all;
  cursor: pointer;
}

.announce-text-btn:hover {
  color: var(--lx-accent);
}

.members-block {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.members-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}

.search-ico {
  color: var(--lx-text-muted);
  cursor: pointer;
}

.member-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: var(--lx-radius);
}

.member-row:hover {
  background: var(--lx-bg-hover);
}

.m-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.m-name {
  font-size: 13px;
  color: var(--lx-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-badge {
  font-size: 11px;
  color: var(--lx-accent);
}
</style>
