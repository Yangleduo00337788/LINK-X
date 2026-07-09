<script setup lang="ts">
// Vue 响应式 API 与计算属性
import { ref, computed } from 'vue'
// Naive UI 图标组件与消息提示
import { NIcon } from 'naive-ui'
// Ionicons5 图标：展开/折叠、选中/未选中
import {
  ChevronDownOutline,
  ChevronForwardOutline,
  EllipseOutline,
  CheckmarkCircle
} from '@vicons/ionicons5'
// 通用头像组件
import Avatar from '../Avatar.vue'
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 聊天弹窗状态 Store
import { useChatModalsStore } from '../../stores/chatModals'
// 应用全局状态 Store
import { useAppStore } from '../../stores/app'
// 联系人 Store
import { useContactsStore } from '../../stores/contacts'
// Naive UI 全局消息提示
import { useMessage } from 'naive-ui'

// 消息提示实例
const message = useMessage()
// 聊天弹窗 Store 实例
const chatModalsStore = useChatModalsStore()
// 应用 Store 实例
const appStore = useAppStore()
// 联系人 Store 实例
const contactsStore = useContactsStore()
// 添加成员弹窗是否打开
const { addMembersOpen } = storeToRefs(chatModalsStore)
// 关闭添加成员弹窗的方法
const { closeAddMembers } = chatModalsStore
// 当前会话 ID
const { currentSessionId } = storeToRefs(appStore)
// 邀请成员加入群聊的方法
const { inviteGroupMembers } = appStore

// 搜索关键词
const search = ref('')
// 已选中的联系人 ID 集合
const selected = ref<Set<string>>(new Set())
// 「最近聊天」分组是否展开
const recentExpanded = ref(true)
// 是否附带聊天记录
const attachHistory = ref(true)

// 根据搜索词过滤后的可邀请联系人列表
const recentContacts = computed(() => {
  // 将好友列表映射为弹窗所需格式
  const list = contactsStore.friends.map(c => ({
    id: c.id,
    name: c.name,
    text: c.avatarText,
    color: c.avatarColor
  }))
  // 获取小写搜索词
  const q = search.value.trim().toLowerCase()
  // 无搜索词时返回全部
  if (!q) return list
  // 按名称模糊匹配
  return list.filter(c => c.name.toLowerCase().includes(q))
})

// 当前已选中联系人的完整信息列表
const selectedList = computed(() => recentContacts.value.filter(c => selected.value.has(c.id)))

// 切换联系人选中状态
function toggle(id: string) {
  // 复制 Set 以触发响应式更新
  const s = new Set(selected.value)
  // 已选中则取消，否则加入
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

// 确认邀请所选成员
function confirm() {
  // 校验会话与选中人数
  if (!currentSessionId.value || selected.value.size === 0) {
    message.warning('请选择要邀请的成员')
    return
  }
  // 提取选中成员名称
  const names = selectedList.value.map(c => c.name)
  // 调用 Store 执行邀请
  inviteGroupMembers(currentSessionId.value, names)
  message.success(`已邀请 ${names.length} 人加入群聊`)
  // 清空选中并关闭弹窗
  selected.value = new Set()
  closeAddMembers()
}

// 取消并关闭弹窗
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
                    :color="selected.has(c.id) ? 'var(--lx-accent)' : 'var(--lx-border-strong)'"
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
          <!-- 右侧：已选成员预览 -->
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
        <!-- 底部：附带聊天记录选项与操作按钮 -->
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