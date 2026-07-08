<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import {
  ChevronDownOutline,
  ChevronForwardOutline,
  EllipseOutline,
  CheckmarkCircle
} from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { initialSessions } from '../../data/mockData'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'
import { useContactsStore } from '../../stores/contacts'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const contactsStore = useContactsStore()
const { createGroupOpen } = storeToRefs(chatModalsStore)
const { closeCreateGroup, openSelectContacts } = chatModalsStore
const { createGroup } = appStore

const search = ref('')
const selected = ref<Set<string>>(new Set())
const recentExpanded = ref(true)

type PickRow = {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  avatarUrl?: string
}

const extraRecent: PickRow[] = [
  { id: 'r-bei', name: '北挽', avatarText: '北', avatarColor: 'var(--lx-text-muted)' },
  { id: 'r-dou', name: '有BB机的小豆包', avatarText: '豆', avatarColor: '#f56c6c' },
  { id: 'r-ling', name: '____Z铃ღ', avatarText: '铃', avatarColor: '#9b59b6' },
  { id: 'r-qing', name: '清风', avatarText: '清', avatarColor: 'var(--lx-success)' }
]

const recentContacts = computed(() => {
  const fromSessions: PickRow[] = initialSessions
    .filter(s => !s.isGroup && s.name !== '我的手机' && s.name !== 'QQ游戏中心')
    .slice(0, 6)
    .map(s => ({
      id: s.id,
      name: s.name,
      avatarText: s.avatarText,
      avatarColor: s.avatarColor,
      avatarUrl: s.avatarUrl
    }))
  const merged = [...fromSessions]
  for (const e of extraRecent) {
    if (!merged.some(m => m.name === e.name)) merged.push(e)
  }
  const q = search.value.trim().toLowerCase()
  if (!q) return merged.slice(0, 8)
  return merged.filter(c => c.name.toLowerCase().includes(q))
})

const collapsedGroups = ['特别关心', '我的好友', '朋友']
const expandedGroup = ref<string | null>(null)

const groupContacts = (group: string) => {
  const q = search.value.trim().toLowerCase()
  let list = contactsStore.items.filter(c => c.group === group)
  if (!q) return list
  return list.filter(c => c.name.toLowerCase().includes(q))
}

const allPickable = computed(() => {
  const rows = [...recentContacts.value]
  for (const c of contactsStore.items) {
    if (!rows.some(r => r.id === c.id)) {
      rows.push({
        id: c.id,
        name: c.name,
        avatarText: c.avatarText,
        avatarColor: c.avatarColor
      })
    }
  }
  return rows
})

const selectedList = computed(() => allPickable.value.filter(c => selected.value.has(c.id)))

const canConfirm = computed(() => selected.value.size > 0)

function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function confirm() {
  if (!canConfirm.value) return
  const members = selectedList.value.map(c => ({
    id: c.id,
    name: c.name,
    avatarText: c.avatarText,
    avatarColor: c.avatarColor
  }))
  const session = createGroup(members)
  if (session) {
    message.success(`已创建群聊「${session.name}」`)
  }
  selected.value = new Set()
  closeCreateGroup()
}

function cancel() {
  closeCreateGroup()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="createGroupOpen" class="modal-root" @click.self="cancel">
      <div class="modal-card" @click.stop>
        <h2 class="modal-title">创建群聊</h2>
        <div class="modal-body">
          <div class="left-pane">
            <div class="search-wrap">
              <input
                v-model="search"
                type="text"
                class="search-field"
                placeholder="搜索"
              />
            </div>
            <button type="button" class="category-row" @click="openSelectContacts">
              <span>按分类创建</span>
              <span class="more-link">更多 <n-icon :component="ChevronForwardOutline" :size="14" /></span>
            </button>
            <div class="section-hint">选择好友创建</div>
            <div class="scroll-list">
              <button type="button" class="group-head" @click="recentExpanded = !recentExpanded">
                <n-icon
                  :component="ChevronDownOutline"
                  :size="16"
                  class="chev"
                  :class="{ collapsed: !recentExpanded }"
                />
                <span>最近聊天</span>
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
              <button
                v-for="g in collapsedGroups"
                :key="g"
                type="button"
                class="group-head"
                @click="expandedGroup = expandedGroup === g ? null : g"
              >
                <n-icon
                  :component="ChevronForwardOutline"
                  :size="16"
                  class="chev"
                  :class="{ collapsed: expandedGroup !== g }"
                />
                <span>{{ g }}</span>
              </button>
              <template v-for="g in collapsedGroups" :key="'items-' + g">
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
                    <Avatar :text="c.avatarText" :color="c.avatarColor" :size="36" />
                    <span class="c-name">{{ c.name }}</span>
                  </button>
                </template>
              </template>
            </div>
          </div>
          <div class="right-pane">
            <div v-if="!selectedList.length" class="right-empty" />
            <div v-else class="selected-list">
              <div v-for="c in selectedList" :key="c.id" class="selected-chip">
                <Avatar :text="c.avatarText" :color="c.avatarColor" :size="40" />
                <span>{{ c.name }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button
            type="button"
            class="btn primary"
            :class="{ disabled: !canConfirm }"
            :disabled="!canConfirm"
            @click="confirm"
          >
            确定
          </button>
          <button type="button" class="btn" @click="cancel">取消</button>
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

.category-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--lx-text-body);
  cursor: pointer;
  border-bottom: 1px solid var(--lx-bg-panel);
}

.more-link {
  display: flex;
  align-items: center;
  gap: 2px;
  color: var(--lx-text-muted);
  font-size: 13px;
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