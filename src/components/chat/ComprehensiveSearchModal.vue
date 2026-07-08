<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useChatModalsStore } from '../../stores/chatModals'
import { useAppStore } from '../../stores/app'

const message = useMessage()
const chatModalsStore = useChatModalsStore()
const appStore = useAppStore()
const { comprehensiveSearchOpen } = storeToRefs(chatModalsStore)
const { closeComprehensiveSearch } = chatModalsStore
const { joinGroup } = appStore

const keyword = ref('')
const mainTab = ref<'all' | 'user' | 'group'>('all')

const mainTabs = [
  { key: 'all', label: '全部' },
  { key: 'user', label: '用户' },
  { key: 'group', label: '群聊' }
] as const

const groups = [
  {
    id: '1',
    name: 'cursor, Kiro, Windsurf, GPT, 交流群',
    count: '717/2000',
    age: '4个月',
    desc: '禁止广告',
    tags: [] as string[]
  },
  {
    id: '2',
    name: '三角洲行动撞车沟通群',
    count: '1984/2000',
    age: '6年',
    desc: '',
    tags: ['00后多', '三角洲行动']
  },
  {
    id: '3',
    name: '洛克王国世界官方Q群96群',
    count: '1984/2000',
    age: '1年',
    desc: '',
    tags: ['00后多', '洛克王国：世界']
  },
  {
    id: '4',
    name: 'KIRO倒车',
    count: '807/1000',
    age: '5个月',
    desc: '',
    tags: [] as string[]
  },
  {
    id: '5',
    name: 'Cursor&Windsurf Shifter用户交流群',
    count: '450/500',
    age: '8个月',
    desc: '',
    tags: ['cursor']
  },
  {
    id: '6',
    name: '重装机兵 · 域轮安静群',
    count: '551/1000',
    age: '4个月',
    desc: '',
    tags: [] as string[]
  }
]

function close() {
  closeComprehensiveSearch()
}

function doSearch() {
  if (!keyword.value.trim()) {
    message.warning('请输入关键词')
    return
  }
  message.success(`已搜索「${keyword.value}」`)
}

function joinGroupAction(name: string) {
  const session = joinGroup(name)
  message.success(`已加入群聊「${session.name}」`)
  close()
}

</script>

<template>
  <Teleport to="body">
    <div v-if="comprehensiveSearchOpen" class="modal-root" @click.self="close">
      <div class="search-window" @click.stop>
        <header class="win-title">综合搜索</header>
        <div class="search-row">
          <input
            v-model="keyword"
            type="text"
            class="search-input"
            placeholder="输入搜索关键词"
            @keydown.enter="doSearch"
          />
          <button type="button" class="search-btn" @click="doSearch">搜索</button>
        </div>
        <div class="main-tabs">
          <button
            v-for="t in mainTabs"
            :key="t.key"
            type="button"
            class="main-tab"
            :class="{ active: mainTab === t.key }"
            @click="mainTab = t.key"
          >
            {{ t.label }}
          </button>
        </div>
        <div class="result-list">
          <article v-for="g in groups" :key="g.id" class="group-card">
            <div class="g-avatar">群</div>
            <div class="g-body">
              <h3 class="g-name">{{ g.name }}</h3>
              <p class="g-meta">
                <span>{{ g.count }}</span>
                <span v-for="tag in g.tags" :key="tag" class="tag">{{ tag }}</span>
                <span class="age">群年龄 {{ g.age }}</span>
              </p>
              <p v-if="g.desc" class="g-desc">{{ g.desc }}</p>
            </div>
            <button type="button" class="join-btn" @click="joinGroupAction(g.name)">加入</button>
          </article>
        </div>
        <button type="button" class="close-fab" title="关闭" @click="close">×</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-root {
  position: fixed;
  inset: 0;
  z-index: 2150;
  background: var(--lx-bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.search-window {
  position: relative;
  width: min(920px, 96vw);
  height: min(640px, 90vh);
  background: var(--lx-bg-card);
  border-radius: var(--lx-radius);
  box-shadow: 0 16px 56px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.win-title {
  margin: 0;
  padding: 16px 20px 8px;
  font-size: 16px;
  font-weight: 600;
  color: #222;
}

.search-row {
  display: flex;
  gap: 10px;
  padding: 8px 20px 12px;
}

.search-input {
  flex: 1;
  height: 36px;
  border: 1px solid #e0e0e0;
  border-radius: var(--lx-radius);
  padding: 0 14px;
  font-size: 14px;
  outline: none;
}

.search-input:focus {
  border-color: var(--lx-accent);
}

.search-btn {
  min-width: 72px;
  height: 36px;
  border: none;
  border-radius: var(--lx-radius);
  background: var(--lx-accent);
  color: var(--lx-bg-card);
  font-size: 14px;
  cursor: pointer;
}

.main-tabs {
  display: flex;
  gap: 24px;
  padding: 0 20px;
  border-bottom: 1px solid #eee;
}

.main-tab {
  border: none;
  background: none;
  padding: 10px 0;
  font-size: 14px;
  color: var(--lx-text-secondary);
  cursor: pointer;
  position: relative;
}

.main-tab.active {
  color: var(--lx-accent);
  font-weight: 600;
}

.main-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--lx-accent);
  border-radius: 1px;
}

.result-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 20px;
}

.group-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}

.g-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--lx-radius);
  background: linear-gradient(135deg, var(--lx-accent-light), var(--lx-accent));
  color: var(--lx-bg-card);
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.g-body {
  flex: 1;
  min-width: 0;
}

.g-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.g-meta {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--lx-text-muted);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.tag {
  background: var(--lx-bg-panel);
  padding: 2px 6px;
  border-radius: var(--lx-radius);
  color: var(--lx-text-secondary);
}

.g-desc {
  margin: 0;
  font-size: 12px;
  color: #888;
}

.join-btn {
  flex-shrink: 0;
  min-width: 64px;
  height: 32px;
  border: 1px solid var(--lx-accent);
  border-radius: var(--lx-radius);
  background: var(--lx-bg-card);
  color: var(--lx-accent);
  font-size: 13px;
  cursor: pointer;
}

.join-btn:hover {
  background: #e6f7ff;
}

.close-fab {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 22px;
  color: var(--lx-text-muted);
  cursor: pointer;
  line-height: 1;
}

.close-fab:hover {
  color: var(--lx-text-body);
}
</style>