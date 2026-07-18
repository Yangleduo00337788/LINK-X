<script setup lang="ts">
/**
 * 添加群成员弹窗。
 * <p>
 * 真实调用 {@code POST /group/{conversationId}/members} 添加成员；
 * 服务端会在事务内写入会话成员表，并返回最新的成员列表。
 * </p>
 */
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import {
  ChevronDownOutline,
  EllipseOutline,
  CheckmarkCircle
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import { useGroupMetaStore } from '../../stores/groupMeta'
import * as groupApi from '../../api/group'
import * as groupInvitationApi from '../../api/groupInvitation'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const groupMetaStore = useGroupMetaStore()
const { addMembersOpen } = storeToRefs(chatModalsStore)
const { closeAddMembers } = chatModalsStore
const { currentSessionId, userProfile } = storeToRefs(appStore)

const search = ref('')
const selected = ref<Set<string>>(new Set())
const recentExpanded = ref(true)
const attachHistory = ref(true)
const inviteMode = ref(false)
const submitting = ref(false)

// 当前用户的真实 userId，用于排除自己（不能邀请自己入群）
const myUserId = computed(() => userProfile.value?.userId || '')

const recentContacts = computed(() => {
  const list = contactsStore.friends
    .filter(c => c.userId && c.userId !== myUserId.value)
    .map(c => ({
      id: c.userId || c.id,
      name: c.name,
      text: c.avatarText,
      color: c.avatarColor
    }))
  const q = search.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(c => c.name.toLowerCase().includes(q))
})

const selectedList = computed(() => recentContacts.value.filter(c => selected.value.has(c.id)))

function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

async function confirm() {
  if (!currentSessionId.value || selected.value.size === 0) {
    message.warning('请选择要邀请的成员')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const memberIds = Array.from(selected.value).filter(id => id !== myUserId.value)
    if (inviteMode.value) {
      for (const memberId of memberIds) {
        const res = await groupInvitationApi.inviteToGroup(currentSessionId.value, {
          inviteeUserId: memberId,
          message: '邀请你加入群聊'
        })
        if (res.code !== 200) {
          throw new Error(res.message || '发送群邀请失败')
        }
      }
      message.success(`已向 ${memberIds.length} 人发送群邀请`)
    } else {
      const res = await groupApi.addGroupMembers(currentSessionId.value, { memberIds })
      if (res.code === 200) {
        void groupMetaStore.fetchMembers(currentSessionId.value)
        message.success(`已邀请 ${memberIds.length} 人加入群聊`)
      } else {
        message.error(res.message || '邀请失败')
        return
      }
    }
    selected.value = new Set()
    closeAddMembers()
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    message.error(err.response?.data?.message || err.message || '邀请失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  closeAddMembers()
}
</script>

<template>
  <!-- 添加群成员弹窗：Teleport 挂载到 body -->
  <Teleport to="body">
    <div v-if="addMembersOpen" class="modal-root" @click.self="cancel">
      <div class="modal-card" @click.stop>
        <!-- 弹窗标题 -->
        <h2 class="modal-title">添加成员</h2>
        <!-- 左右分栏主体 -->
        <div class="modal-body">
          <!-- 左侧：搜索与联系人列表 -->
          <div class="left-pane">
            <div class="search-wrap">
              <input v-model="search" type="text" class="search-field" placeholder="搜索好友" />
            </div>
            <div class="scroll-list">
              <button type="button" class="group-head" @click="recentExpanded = !recentExpanded">
                <n-icon
                  :component="ChevronDownOutline"
                  :size="16"
                  class="chev"
                  :class="{ collapsed: !recentExpanded }"
                />
                <span>我的好友（{{ recentContacts.length }}）</span>
              </button>
              <template v-if="recentExpanded">
                <button
                  v-for="c in recentContacts"
                  :key="c.id"
                  type="button"
                  class="contact-row"
                  @click="toggle(c.id)"
                >
                  <n-icon
                    :component="selected.has(c.id) ? CheckmarkCircle : EllipseOutline"
                    :size="20"
                    :color="selected.has(c.id) ? 'var(--lx-accent)' : 'var(--lx-border-strong)'"
                  />
                  <Avatar :text="c.text" :color="c.color" :size="36" />
                  <span class="c-name">{{ c.name }}</span>
                </button>
                <div v-if="!recentContacts.length" class="empty-tip">暂无可邀请的好友</div>
              </template>
            </div>
          </div>
          <!-- 右侧：已选成员预览 -->
          <div class="right-pane">
            <h3 class="right-title">已选择（{{ selectedList.length }}）</h3>
            <div v-if="selectedList.length" class="selected-list">
              <div v-for="c in selectedList" :key="c.id" class="chip">
                <Avatar :text="c.text" :color="c.color" :size="44" />
                <span>{{ c.name }}</span>
              </div>
            </div>
            <div v-else class="empty-tip">未选择任何好友</div>
          </div>
        </div>
        <!-- 底部：附带聊天记录选项与操作按钮 -->
        <div class="modal-footer">
          <label class="history-opt">
            <input v-model="inviteMode" type="checkbox" :disabled="submitting" />
            <span>发送邀请待对方确认</span>
          </label>
          <label class="history-opt">
            <input v-model="attachHistory" type="checkbox" :disabled="submitting" />
            <span>附带聊天记录</span>
            <span class="history-link">最近30条 ›</span>
          </label>
          <div class="footer-btns">
            <button type="button" class="btn" :disabled="submitting" @click="cancel">取消</button>
            <button
              type="button"
              class="btn primary"
              :disabled="submitting || selectedList.length === 0"
              @click="confirm"
            >
              确定
            </button>
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
  z-index: 2200;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-card {
  width: min(760px, 94vw);
  height: min(560px, 88vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
}

.modal-title {
  margin: 0;
  padding: 18px 24px;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.modal-body {
  flex: 1;
  display: flex;
  min-height: 0;
  margin: 0 20px;
  border: 1px solid #eee;
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.left-pane {
  width: 50%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
}

.search-wrap {
  padding: 12px;
}

.search-field {
  width: 100%;
  height: 32px;
  border: 1px solid var(--lx-bg-panel-deep);
  border-radius: var(--lx-radius);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.scroll-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.group-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 6px;
  border: none;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
}

.chev.collapsed {
  transform: rotate(-90deg);
}

.contact-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
}

.contact-row:hover {
  background: var(--lx-bg-panel);
}

.right-pane {
  flex: 1;
  padding: 16px;
}

.right-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lx-text-secondary);
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 72px;
  font-size: 11px;
  text-align: center;
  color: var(--lx-text-secondary);
}

.empty-tip {
  padding: 16px;
  text-align: center;
  color: var(--lx-text-muted);
  font-size: 12px;
}

.modal-footer {
  padding: 12px 24px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.history-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--lx-text-secondary);
  cursor: pointer;
}

.history-link {
  color: var(--lx-accent);
}

.footer-btns {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.btn {
  min-width: 88px;
  height: 36px;
  border-radius: var(--lx-radius);
  border: 1px solid #ddd;
  background: var(--lx-bg-card);
  font-size: 14px;
  cursor: pointer;
}

.btn.primary {
  background: var(--lx-accent);
  border-color: var(--lx-accent);
  color: var(--lx-bg-card);
}
</style>