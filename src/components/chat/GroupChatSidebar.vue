<script setup lang="ts">
import { NIcon } from 'naive-ui'
import { SearchOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import Avatar from '../Avatar.vue'
import { useChatModals } from '../../composables/useChatModals'
import { GROUP_ANNOUNCEMENT_SHORT } from '../../data/groupDemo'

const { openGroupAnnouncement } = useChatModals()

const members = [
  { name: '有BB机的小豆包', text: '有', color: '#f56c6c', badge: '群主' },
  { name: '颜笙', text: '颜', color: '#12b7f5', badge: '管理员' },
  { name: '重生之我用Pc', text: '重', color: '#722ed1', badge: '管理员' },
  { name: '嫉妒群主的坏蛋', text: '嫉', color: '#fa541c', badge: '管理员' },
  { name: 'jeffy', text: 'J', color: '#52c41a', badge: '管理员' },
  { name: '李子', text: '李', color: '#fa8c16', badge: '管理员' },
  { name: 'MCP拼车老队', text: 'M', color: '#1890ff', badge: '管理员' },
  { name: 'Q群管家', text: 'Q', color: '#0099ff', badge: '机器人' },
  { name: '我不是车神', text: '我', color: '#eb2f96', badge: '管理员' },
  { name: '小程序APP开发', text: '小', color: '#13c2c2', badge: '管理员' },
  { name: '雪飞扬', text: '雪', color: '#2f54eb', badge: '管理员' },
  { name: '执法大队', text: '执', color: '#595959', badge: '管理员' },
  { name: '33', text: '3', color: '#8c8c8c', badge: '管理员' },
  { name: 'a', text: 'a', color: '#bfbfbf', badge: '管理员' }
]
</script>

<template>
  <aside class="group-side">
    <section class="announce-block">
      <div class="announce-head">
        <h3 class="side-title">群公告</h3>
        <button type="button" class="arrow-btn" title="查看群公告" @click="openGroupAnnouncement">
          <n-icon :component="ChevronForwardOutline" :size="18" />
        </button>
      </div>
      <button type="button" class="announce-text-btn" @click="openGroupAnnouncement">
        {{ GROUP_ANNOUNCEMENT_SHORT }}
      </button>
    </section>
    <section class="members-block">
      <div class="members-head">
        <span class="side-title">群聊成员 1136</span>
        <n-icon :component="SearchOutline" :size="18" class="search-ico" title="搜索成员" />
      </div>
      <div class="member-list">
        <div v-for="m in members" :key="m.name" class="member-row">
          <Avatar :text="m.text" :color="m.color" :size="36" />
          <div class="m-info">
            <span class="m-name">{{ m.name }}</span>
            <span v-if="m.badge" class="m-badge">{{ m.badge }}</span>
          </div>
        </div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.group-side {
  width: 240px;
  flex-shrink: 0;
  height: 100%;
  background: #f5f5f5;
  border-left: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.announce-block {
  flex-shrink: 0;
  padding: 14px 12px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
}

.announce-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.side-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.arrow-btn {
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}

.arrow-btn:hover {
  color: #12b7f5;
}

.announce-text-btn {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: #666;
  word-break: break-all;
  cursor: pointer;
}

.announce-text-btn:hover {
  color: #12b7f5;
}

.members-block {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.members-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  flex-shrink: 0;
}

.search-ico {
  color: #999;
  cursor: pointer;
}

.member-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: var(--lx-radius);
}

.member-row:hover {
  background: rgba(0, 0, 0, 0.04);
}

.m-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.m-name {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-badge {
  font-size: 11px;
  color: #12b7f5;
}
</style>