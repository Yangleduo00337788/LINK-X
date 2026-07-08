<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { GROUP_ANNOUNCEMENT_FULL } from '../../data/groupDemo'
import Avatar from '../Avatar.vue'

const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { groupAnnouncementOpen } = storeToRefs(chatModalsStore)
const { closeGroupAnnouncement } = chatModalsStore
const { currentSession } = storeToRefs(appStore)

function close() {
  closeGroupAnnouncement()
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
        <div class="post">
          <div class="post-head">
            <Avatar text="有" color="#f56c6c" :size="40" />
            <div class="post-meta">
              <span class="author">有BB机的小豆包</span>
              <span class="role">群主</span>
              <span class="time">昨天 20:27</span>
            </div>
            <span class="pin-tag">置顶</span>
          </div>
          <pre class="post-body">{{ GROUP_ANNOUNCEMENT_FULL }}</pre>
          <button type="button" class="expand-btn">展开 ▾</button>
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
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
}

.win-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #eee;
}

.win-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 12px;
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
  background: #e6f7ff;
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

.expand-btn {
  margin-top: 12px;
  border: none;
  background: none;
  color: var(--lx-accent);
  font-size: 13px;
  cursor: pointer;
  float: right;
}
</style>