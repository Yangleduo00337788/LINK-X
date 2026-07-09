<script setup lang="ts">
// Vue 响应式 API 与计算属性
import { computed, ref } from 'vue'
// Naive UI 输入框、按钮与消息提示
import { NInput, NButton, useMessage } from 'naive-ui'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 群元数据 Store
import { useGroupMetaStore } from '../../stores/groupMeta'
// 通用头像组件
import Avatar from '../Avatar.vue'
// 置顶图标组件
import PinIcon from '../icons/PinIcon.vue'

// 消息提示实例
const message = useMessage()
// 聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 应用 Store 实例
const appStore = useAppStore()
// 群元数据 Store 实例
const groupMetaStore = useGroupMetaStore()
// 群公告弹窗是否打开
const { groupAnnouncementOpen } = storeToRefs(chatModalsStore)
// 关闭群公告弹窗的方法
const { closeGroupAnnouncement } = chatModalsStore
// 当前会话、会话 ID、用户资料
const { currentSession, currentSessionId, userProfile } = storeToRefs(appStore)

// 是否处于编辑模式
const editing = ref(false)
// 编辑中的公告草稿内容
const draft = ref('')

// 当前群聊的公告信息
const announcement = computed(() => {
  const id = currentSessionId.value
  if (!id) return null
  return groupMetaStore.announcementFor(id)
})

// 关闭弹窗并重置编辑状态
function close() {
  editing.value = false
  closeGroupAnnouncement()
}

// 进入编辑模式并填充当前公告内容
function startEdit() {
  draft.value = announcement.value?.content ?? ''
  editing.value = true
}

// 保存编辑后的群公告
function save() {
  const id = currentSessionId.value
  if (!id || !draft.value.trim()) return
  groupMetaStore.updateAnnouncement(id, draft.value.trim())
  message.success('群公告已更新')
  editing.value = false
}
</script>

<template>
  <!-- 群公告弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="groupAnnouncementOpen" class="modal-root" @click.self="close">
      <div class="announce-window" @click.stop>
        <!-- 窗口标题栏 -->
        <header class="win-head">
          <h2>{{ currentSession?.name || '群聊' }}</h2>
          <button type="button" class="close-x" @click="close">×</button>
        </header>
        <!-- 公告正文：查看或编辑模式 -->
        <div v-if="announcement" class="post">
          <div class="post-head">
            <Avatar :text="userProfile.nickname.charAt(0)" color="var(--lx-accent)" :size="40" />
            <div class="post-meta">
              <span class="author">{{ announcement.author }}</span>
              <span class="role">{{ announcement.role }}</span>
              <span class="time">{{ announcement.time }}</span>
            </div>
            <span class="pin-tag">
              <PinIcon :size="11" />
              置顶
            </span>
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
  background: var(--lx-warning-bg);
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
  display: inline-flex;
  align-items: center;
  gap: 3px;
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
