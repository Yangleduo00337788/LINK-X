<script setup lang="ts">
import { computed, ref } from 'vue'
import { NInput, NButton, useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useGroupMetaStore } from '../../stores/groupMeta'
import Avatar from '../Avatar.vue'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const groupMetaStore = useGroupMetaStore()
const { groupAnnouncementOpen } = storeToRefs(chatModalsStore)
const { closeGroupAnnouncement } = chatModalsStore
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)

const editing = ref(false)
const draft = ref('')

const announcement = computed(() => {
  const id = currentSessionId.value
  if (!id) return null
  return groupMetaStore.announcementFor(id)
})

function close() {
  editing.value = false
  closeGroupAnnouncement()
}

function startEdit() {
  draft.value = announcement.value?.content ?? ''
  editing.value = true
}

function save() {
  const id = currentSessionId.value
  if (!id || !draft.value.trim()) return
  groupMetaStore.updateAnnouncement(id, draft.value.trim())
  message.success('群公告已更新')
  editing.value = false
}
</script>

<template>
  <Teleport to="body">
    <div v-if="groupAnnouncementOpen" class="modal-root" @click.self="close">
      <div class="announce-window" @click.stop>
        <header class="win-head">
          <h2>{{ currentSession?.name || '群聊' }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <div v-if="announcement" class="post">
          <div class="post-head">
            <Avatar :text="userProfile.nickname.charAt(0)" color="var(--lx-accent)" :size="40" />
            <div class="post-meta">
              <span class="author">{{ announcement.author }}</span>
              <span class="role">{{ announcement.role }}</span>
              <span class="time">{{ announcement.time }}</span>
            </div>
            <span class="pin-tag">置顶</span>
          </div>
          <pre v-if="!editing" class="post-body">{{ announcement.content }}</pre>
          <n-input v-else v-model:value="draft" type="textarea" :rows="6" />
          <div class="actions">
            <n-button v-if="!editing" size="small" @click="startEdit">编辑公告</n-button>
            <template v-else>
              <n-button size="small" @click="editing = false">取消</n-button>
              <n-button size="small" type="primary" @click="save">保存</n-button>
            </template>
          </div>
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

.announce-window {
  width: min(480px, 94vw);
  max-height: min(420px, 80vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  display: flex;
  flex-direction: column;
  box-shadow: var(--lx-shadow-modal);
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 12px;
  color: var(--lx-text-body);
}

.close-x {
  border: none;
  background: none;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
  flex-shrink: 0;
}

.post {
  padding: 16px 18px 20px;
  overflow-y: auto;
}

.post-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
  position: relative;
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.author {
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-body);
}

.role {
  font-size: 11px;
  color: #fa8c16;
  background: #fff7e6;
  padding: 1px 6px;
  border-radius: var(--lx-radius);
}

.time {
  font-size: 12px;
  color: var(--lx-text-muted);
  width: 100%;
}

.pin-tag {
  position: absolute;
  top: 0;
  right: 0;
  font-size: 11px;
  color: var(--lx-accent);
  background: var(--lx-accent-bg-soft);
  padding: 2px 8px;
  border-radius: var(--lx-radius);
}

.post-body {
  margin: 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--lx-text-body);
  white-space: pre-wrap;
  word-break: break-word;
}

.actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
