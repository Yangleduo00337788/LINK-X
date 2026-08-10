<!-- 作者：yangleduo -->
<script setup lang="ts">
/**
 * 创建群聊模态框。
 * <p>
 * 左栏选择好友（最近聊天 + 分组），右栏展示已选成员，确认后调用 createGroup。
 * </p>
 */
import { ref, computed, watch } from 'vue'
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
import { useMessage } from 'naive-ui'
import { useI18n } from '../../i18n'

const { t } = useI18n()
const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { createGroupOpen } = storeToRefs(chatModalsStore)
const { closeCreateGroup } = chatModalsStore
const { sessions } = storeToRefs(appStore)
const { createGroup } = appStore

// 搜索关键词
const search = ref('')
// 已选成员 id 集合
const selected = ref<Set<string>>(new Set())
// 「最近聊天」分组是否展开
const recentExpanded = ref(true)

function defaultFriendGroup() {
  return t('defaults.myFriends')
}

/** 解析联系人所属分组名（与通讯录面板逻辑一致） */
function resolveGroupName(group?: string) {
  const def = defaultFriendGroup()
  return (group || def).trim() || def
}

/** 可选联系人行数据结构（id 必须是真实 userId） */
type PickRow = {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  avatarUrl?: string
}

/** 最近聊天列表：取单聊对方 userId，支持搜索过滤 */
const recentContacts = computed(() => {
  const fromSessions: PickRow[] = sessions.value
    .filter(s => !s.isGroup && s.peerUserId)
    .slice(0, 8)
    .map(s => ({
      id: String(s.peerUserId),
      name: s.name,
      avatarText: s.avatarText,
      avatarColor: s.avatarColor,
      avatarUrl: s.avatarUrl
    }))
  const q = search.value.trim().toLowerCase()
  if (!q) return fromSessions
  return fromSessions.filter(c => c.name.toLowerCase().includes(q))
})

// 当前展开的分组名，null 表示全部折叠
const expandedGroup = ref<string | null>(null)

/** 按分组名取联系人，并应用搜索过滤 */
function groupContacts(group: string) {
  const q = search.value.trim().toLowerCase()
  let list = contactsStore.items
    .filter(c => resolveGroupName(c.group) === group)
    .map(c => ({
      id: String(c.userId || c.id),
      name: c.name,
      avatarText: c.avatarText,
      avatarColor: c.avatarColor,
      avatarUrl: c.avatarUrl
    }))
  if (!q) return list
  return list.filter(c => c.name.toLowerCase().includes(q))
}

/** 通讯录分组（来自好友实际 group 字段，与 ContactsPanel 一致） */
const friendGroups = computed(() => {
  const names = contactsStore.friendGroupNames
  const q = search.value.trim().toLowerCase()
  if (!q) return names
  return names.filter(name => groupContacts(name).length > 0)
})

watch(createGroupOpen, open => {
  if (!open) return
  selected.value = new Set()
  search.value = ''
  recentExpanded.value = true
  expandedGroup.value = null
  void contactsStore.fetchFriends()
})

/** 所有可勾选联系人（最近 + 通讯录去重合并） */
const allPickable = computed(() => {
  const rows = [...recentContacts.value]
  for (const c of contactsStore.items) {
    const uid = String(c.userId || c.id)
    if (!uid || rows.some(r => r.id === uid)) continue
    rows.push({
      id: uid,
      name: c.name,
      avatarText: c.avatarText,
      avatarColor: c.avatarColor,
      avatarUrl: c.avatarUrl
    })
  }
  return rows
})

/** 已选成员详情列表 */
const selectedList = computed(() => allPickable.value.filter(c => selected.value.has(c.id)))

/** 至少选一人才可确认 */
const canConfirm = computed(() => selected.value.size > 0)

/** 切换某联系人选中状态 */
function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

/** 确认创建群聊并关闭模态框 */
async function confirm() {
  if (!canConfirm.value) return
  const members = selectedList.value.map(c => ({
    id: c.id,
    name: c.name,
    avatarText: c.avatarText,
    avatarColor: c.avatarColor
  }))
  try {
    const session = await createGroup(members)
    if (session) {
      message.success(t('modals.createOk', { name: session.name }))
    }
  } catch {
    message.error(t('modals.createFail'))
  }
  selected.value = new Set()
  closeCreateGroup()
}

/** 取消并关闭 */
function cancel() {
  closeCreateGroup()
}
</script>

<template>
  <!-- 全屏模态：创建群聊 -->
  <Teleport to="body">
    <div v-if="createGroupOpen" class="modal-root" @click.self="cancel">
      <div class="modal-card" @click.stop>
        <h2 class="modal-title">{{ t('modals.createGroupTitle') }}</h2>
        <div class="modal-body">
          <!-- 左侧：搜索与好友选择列表 -->
          <div class="left-pane">
            <div class="search-wrap">
              <input
                v-model="search"
                type="text"
                class="search-field"
                :placeholder="t('modals.search')"
              />
            </div>
            <div class="section-hint">{{ t('modals.selectFriends') }}</div>
            <div class="scroll-list">
              <!-- 最近聊天分组 -->
              <button type="button" class="group-head" @click="recentExpanded = !recentExpanded">
                <n-icon
                  :component="ChevronDownOutline"
                  :size="16"
                  class="chev"
                  :class="{ collapsed: !recentExpanded }"
                />
                <span>{{ t('modals.recentChats') }}</span>
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
                  <Avatar
                    :text="c.avatarText"
                    :color="c.avatarColor"
                    :image-url="c.avatarUrl"
                    :size="36"
                  />
                  <span class="c-name">{{ c.name }}</span>
                </button>
              </template>
              <!-- 通讯录分组：标题与成员紧挨展示，避免成员落到所有分组标题下方 -->
              <template v-for="g in friendGroups" :key="g">
                <button
                  type="button"
                  class="group-head"
                  @click="expandedGroup = expandedGroup === g ? null : g"
                >
                  <n-icon
                    :component="ChevronDownOutline"
                    :size="16"
                    class="chev"
                    :class="{ collapsed: expandedGroup !== g }"
                  />
                  <span>{{ g }}</span>
                </button>
                <template v-if="expandedGroup === g">
                  <button
                    v-for="c in groupContacts(g)"
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
                    <Avatar
                      :text="c.avatarText"
                      :color="c.avatarColor"
                      :image-url="c.avatarUrl"
                      :size="36"
                    />
                    <span class="c-name">{{ c.name }}</span>
                  </button>
                </template>
              </template>
            </div>
          </div>
          <!-- 右侧：已选成员预览 -->
          <div class="right-pane">
            <div v-if="!selectedList.length" class="right-empty" />
            <div v-else class="selected-list">
              <div v-for="c in selectedList" :key="c.id" class="selected-chip">
                <Avatar
                  :text="c.avatarText"
                  :color="c.avatarColor"
                  :image-url="c.avatarUrl"
                  :size="40"
                />
                <span>{{ c.name }}</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 底部确认/取消 -->
        <div class="modal-footer">
          <button
            type="button"
            class="btn primary"
            :class="{ disabled: !canConfirm }"
            :disabled="!canConfirm"
            @click="confirm"
          >
            {{ t('common.confirm') }}
          </button>
          <button type="button" class="btn" @click="cancel">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2100;
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
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-title {
  margin: 0;
  padding: 18px 24px 14px;
  font-size: 17px;
  font-weight: 600;
  text-align: left;
  color: var(--lx-text-body);
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
  min-width: 280px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
  background: var(--lx-bg-card);
}

.search-wrap {
  padding: 12px 12px 8px;
}

.search-field {
  width: 100%;
  height: 32px;
  border: 1px solid var(--lx-bg-panel-deep);
  border-radius: var(--lx-radius);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  background: var(--lx-bg-panel);
  box-sizing: border-box;
}

.search-field:focus {
  border-color: var(--lx-accent);
  background: var(--lx-bg-card);
}

.section-hint {
  padding: 10px 14px 4px;
  font-size: 13px;
  color: var(--lx-text-secondary);
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
  color: var(--lx-text-body);
  cursor: pointer;
  text-align: left;
}

.chev {
  transition: transform 0.15s ease;
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
  border-radius: var(--lx-radius);
  cursor: pointer;
  text-align: left;
  font-size: 14px;
}

.contact-row:hover {
  background: var(--lx-bg-panel);
}

.c-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right-pane {
  flex: 1;
  background: var(--lx-bg-panel);
  min-width: 0;
}

.right-empty {
  width: 100%;
  height: 100%;
}

.selected-list {
  padding: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-content: flex-start;
}

.selected-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 64px;
  font-size: 11px;
  color: var(--lx-text-secondary);
  text-align: center;
  word-break: break-all;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px 20px;
}

.btn {
  min-width: 88px;
  height: 36px;
  border-radius: var(--lx-radius);
  border: 1px solid #ddd;
  background: var(--lx-bg-card);
  font-size: 14px;
  cursor: pointer;
  color: var(--lx-text-body);
}

.btn.primary {
  background: var(--lx-accent);
  border-color: var(--lx-accent);
  color: var(--lx-bg-card);
}

.btn.primary.disabled {
  background: #b8e8fa;
  border-color: #b8e8fa;
  cursor: not-allowed;
  color: var(--lx-bg-card);
}

.btn:hover:not(:disabled) {
  opacity: 0.92;
}
</style>
