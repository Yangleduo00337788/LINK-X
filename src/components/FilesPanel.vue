<script setup lang="ts">
import { ref, computed } from 'vue'
import { NIcon } from 'naive-ui'
import { DocumentTextOutline, FolderOutline, ImageOutline, FilmOutline } from '@vicons/ionicons5'
import PanelSearchBar from './PanelSearchBar.vue'

const search = ref('')
const activeTab = ref('recent') // recent, document, image, media, other

// 模拟文件数据
const mockFiles = [
  { id: 'f1', title: '产品需求文档_v2.docx', size: '1.2 MB', time: '10:30', type: 'document', sender: '张三' },
  { id: 'f2', title: 'Q3季度总结PPT.pptx', size: '4.5 MB', time: '昨天', type: 'document', sender: '李四' },
  { id: 'f3', title: '设计稿_切图.zip', size: '12.8 MB', time: '昨天', type: 'other', sender: '王五' },
  { id: 'f4', title: '会议录屏.mp4', size: '105.2 MB', time: '星期一', type: 'media', sender: '赵六' },
  { id: 'f5', title: 'UI视觉规范.png', size: '3.1 MB', time: '星期一', type: 'image', sender: '张三' },
  { id: 'f6', title: 'API接口联调.json', size: '12 KB', time: '10-05', type: 'other', sender: '系统' }
]

const filtered = computed(() => {
  let list = mockFiles
  if (activeTab.value !== 'recent') {
    list = list.filter(f => f.type === activeTab.value)
  }
  
  const q = search.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f => f.title.toLowerCase().includes(q) || f.sender.toLowerCase().includes(q))
})

function iconFor(type: string) {
  if (type === 'image') return ImageOutline
  if (type === 'media') return FilmOutline
  if (type === 'document') return DocumentTextOutline
  return FolderOutline
}

function setTab(tab: string) {
  activeTab.value = tab
}
</script>

<template>
  <div class="files-panel">
    <PanelSearchBar v-model="search" placeholder="搜索文件 / 发送者" />
    <div class="files-tabs">
      <div class="tab-item" :class="{ active: activeTab === 'recent' }" @click="setTab('recent')">最近</div>
      <div class="tab-item" :class="{ active: activeTab === 'document' }" @click="setTab('document')">文档</div>
      <div class="tab-item" :class="{ active: activeTab === 'image' }" @click="setTab('image')">图片</div>
      <div class="tab-item" :class="{ active: activeTab === 'media' }" @click="setTab('media')">音视频</div>
      <div class="tab-item" :class="{ active: activeTab === 'other' }" @click="setTab('other')">其他</div>
    </div>
    <div class="list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="row"
      >
        <div class="icon-wrap" :class="item.type">
          <n-icon :component="iconFor(item.type)" :size="24" />
        </div>
        <div class="info">
          <div class="title">{{ item.title }}</div>
          <div class="meta">
            <span>{{ item.size }}</span>
            <span class="dot">·</span>
            <span>来自: {{ item.sender }}</span>
          </div>
        </div>
        <span class="time">{{ item.time }}</span>
      </div>
      
      <div v-if="filtered.length === 0" class="empty-state">
        没有找到相关文件
      </div>
    </div>
  </div>
</template>

<style scoped>
.files-panel {
  width: 100%;
  height: 100%;
  background: var(--lx-bg-panel, #f5f5f5);
  display: flex;
  flex-direction: column;
  border-right: none;
  flex-shrink: 0;
}

.files-tabs {
  display: flex;
  padding: 8px 16px 4px;
  gap: 16px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.tab-item {
  font-size: 13px;
  color: #666;
  cursor: pointer;
  padding-bottom: 6px;
  position: relative;
  transition: color 0.2s;
}

.tab-item:hover {
  color: #333;
}

.tab-item.active {
  color: #12b7f5;
  font-weight: 500;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10%;
  width: 80%;
  height: 2px;
  background: #12b7f5;
  border-radius: 2px;
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  border-radius: var(--lx-radius);
  margin-bottom: 4px;
  transition: background 0.2s;
}

.row:hover {
  background: rgba(0, 0, 0, 0.04);
}

.icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-wrap.document { background: #e6f2ff; color: #0099ff; }
.icon-wrap.image { background: #fff0e6; color: #ff8800; }
.icon-wrap.media { background: #f2e6ff; color: #8800ff; }
.icon-wrap.other { background: #e6ffed; color: #00cc44; }

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
}

.dot {
  margin: 0 4px;
}

.time {
  font-size: 11px;
  color: #bbb;
  flex-shrink: 0;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: #999;
  font-size: 13px;
}
</style>