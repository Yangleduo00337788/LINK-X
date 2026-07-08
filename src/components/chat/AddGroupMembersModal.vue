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
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useMessage } from 'naive-ui'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const { addMembersOpen } = storeToRefs(chatModalsStore)
const { closeAddMembers } = chatModalsStore

const search = ref('')
const selected = ref<Set<string>>(new Set(['r-dou']))
const recentExpanded = ref(true)
const attachHistory = ref(true)

const recentContacts = computed(() => {
  const extra = [
    { id: 'r-yld', name: '养乐多', text: '养', color: '#f39c12' },
    { id: 'f-zwz', name: '吱唔猪', text: '吱', color: '#7cb342' },
    { id: 'r-bei', name: '北挽', text: '北', color: '#8e8e93' },
    { id: 'r-ling', name: '____Z铃ღ', text: '铃', color: '#9b59b6' },
    { id: 'r-qing', name: '清风', text: '清', color: '#52c41a' },
    { id: 'r-dou', name: '有BB机的小豆包', text: '有', color: '#f56c6c' }
  ]
  const q = search.value.trim().toLowerCase()
  if (!q) return extra
  return extra.filter(c => c.name.toLowerCase().includes(q))
})

const selectedList = computed(() => recentContacts.value.filter(c => selected.value.has(c.id)))

function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function confirm() {
  message.success(`已邀请 ${selected.value.size} 人（演示）`)
  closeAddMembers()
}

function cancel() {
  closeAddMembers()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="addMembersOpen" class="modal-root" @click.self="cancel">
      <div class="modal-card" @click.stop>
        <h2 class="modal-title">添加成员</h2>
        <div class="modal-body">
          <div class="left-pane">
            <div class="search-wrap">
              <input v-model="search" type="text" class="search-field" placeholder="搜索" />
            </div>
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
                    :color="selected.has(c.id) ? '#12b7f5' : '#d0d0d0'"
                  />
                  <Avatar :text="c.text" :color="c.color" :size="36" />
                  <span class="c-name">{{ c.name }}</span>
                </button>
              </template>
              <button
                v-for="g in ['特别关心', '我的好友', '朋友', '家人']"
                :key="g"
                type="button"
                class="group-head"
              >
                <n-icon :component="ChevronForwardOutline" :size="16" class="chev collapsed" />
                <span>{{ g }}</span>
              </button>
            </div>
          </div>
          <div class="right-pane">
            <h3 class="right-title">添加成员</h3>
            <div v-if="selectedList.length" class="selected-list">
              <div v-for="c in selectedList" :key="c.id" class="chip">
                <Avatar :text="c.text" :color="c.color" :size="44" />
                <span>{{ c.name }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <label class="history-opt">
            <input v-model="attachHistory" type="checkbox" />
            <span>附带聊天记录</span>
            <span class="history-link">最近30条 ›</span>
          </label>
          <div class="footer-btns">
            <button type="button" class="btn primary" @click="confirm">确定</button>
            <button type="button" class="btn" @click="cancel">取消</button>
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
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-card {
  width: min(760px, 94vw);
  height: min(560px, 88vh);
  background: #fff;
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
  border: 1px solid #e8e8e8;
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
  background: #f5f5f5;
}

.right-pane {
  flex: 1;
  padding: 16px;
}

.right-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #666;
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
  color: #666;
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
  color: #666;
  cursor: pointer;
}

.history-link {
  color: #12b7f5;
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
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

.btn.primary {
  background: #12b7f5;
  border-color: #12b7f5;
  color: #fff;
}
</style>