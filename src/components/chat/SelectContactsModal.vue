<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronForwardOutline, CheckmarkCircle, EllipseOutline } from '@vicons/ionicons5'
import PanelSearchBar from '../PanelSearchBar.vue'
import Avatar from '../Avatar.vue'
import { contacts } from '../../data/mockData'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const { selectContactsOpen } = storeToRefs(chatModalsStore)
const { closeSelectContacts } = chatModalsStore

const search = ref('')
const selected = ref<Set<string>>(new Set(['c1']))

const recent = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = contacts.slice(0, 8)
  if (!q) return list
  return list.filter(c => c.name.toLowerCase().includes(q))
})

const groups = ['特别关心', '我的好友', '朋友', '家人', '同学']

function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function confirm() {
  message.success(`已选择 ${selected.value.size} 人（演示）`)
  closeSelectContacts()
}

function cancel() {
  closeSelectContacts()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="selectContactsOpen" class="modal-root" @click.self="cancel">
      <div class="modal-card" @click.stop>
        <h2 class="modal-title">选择联系人</h2>
        <div class="modal-body">
          <div class="left-pane">
            <PanelSearchBar v-model="search" placeholder="搜索" />
            <div class="scroll-list">
              <div class="section-label">最近聊天</div>
              <button
                v-for="c in recent"
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
              <button
                v-for="g in groups"
                :key="g"
                type="button"
                class="group-row"
                @click="message.info('展开分组（演示）')"
              >
                <n-icon :component="ChevronForwardOutline" :size="16" color="var(--lx-text-muted)" />
                <span>{{ g }}</span>
              </button>
            </div>
          </div>
          <div class="right-pane" />
        </div>
        <div class="modal-footer">
          <button type="button" class="btn primary" @click="confirm">确定</button>
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
  width: min(720px, 92vw);
  height: min(520px, 85vh);
  background: var(--lx-bg-panel);
  border-radius: var(--lx-radius);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-title {
  margin: 0;
  padding: 20px 24px 12px;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  color: #222;
}

.modal-body {
  flex: 1;
  display: flex;
  min-height: 0;
  margin: 0 16px;
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  overflow: hidden;
}

.left-pane {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
}

.left-pane :deep(.panel-search-bar) {
  border-bottom: none;
  height: 48px;
}

.scroll-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.section-label {
  font-size: 12px;
  color: var(--lx-text-muted);
  padding: 8px 8px 4px;
}

.contact-row,
.group-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border: none;
  background: transparent;
  border-radius: var(--lx-radius);
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  color: var(--lx-text-body);
}

.contact-row:hover,
.group-row:hover {
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
  background: #fafafa;
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

.btn:hover {
  opacity: 0.92;
}
</style>