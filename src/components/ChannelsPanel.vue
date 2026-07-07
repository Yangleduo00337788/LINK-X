<script setup lang="ts">
import { ref, computed } from 'vue'
import { NInput, NIcon, NButton } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import Avatar from './Avatar.vue'
import { channels as channelList } from '../data/mockData'
import { useSecondaryView } from '../composables/useSecondaryView'
import type { ChannelItem } from '../types'

const { activeChannel } = useSecondaryView()
const search = ref('')
const joinedIds = ref(new Set(channelList.filter(c => c.joined).map(c => c.id)))

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return channelList
  return channelList.filter(c => c.name.toLowerCase().includes(q) || c.desc.includes(q))
})

function selectChannel(c: ChannelItem) {
  activeChannel.value = c
}

function toggleJoin(c: ChannelItem) {
  if (joinedIds.value.has(c.id)) {
    joinedIds.value.delete(c.id)
  } else {
    joinedIds.value.add(c.id)
  }
  joinedIds.value = new Set(joinedIds.value)
}
</script>

<template>
  <div class="channels-panel">
    <div class="search-bar">
      <n-input v-model:value="search" placeholder="搜索频道" round size="small" class="search-input">
        <template #prefix>
          <n-icon :component="SearchOutline" :size="16" color="#999" />
        </template>
      </n-input>
    </div>
    <div class="list">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="row"
        :class="{ active: activeChannel?.id === item.id }"
        @click="selectChannel(item)"
      >
        <Avatar :text="item.avatarText" :color="item.avatarColor" :size="44" />
        <div class="info">
          <div class="name">{{ item.name }}</div>
          <div class="desc">{{ item.desc }}</div>
          <div class="members">{{ item.members.toLocaleString() }} 成员</div>
        </div>
        <n-button
          size="tiny"
          :type="joinedIds.has(item.id) ? 'default' : 'primary'"
          @click.stop="toggleJoin(item)"
        >
          {{ joinedIds.has(item.id) ? '已加入' : '加入' }}
        </n-button>
      </div>
    </div>
    <div v-if="activeChannel" class="hint">
      已选：{{ activeChannel.name }} — 在右侧查看详情
    </div>
  </div>
</template>

<style scoped>
.channels-panel {
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebebeb;
  flex-shrink: 0;
}

.search-bar {
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  background: #f5f5f5;
  border-bottom: 1px solid #ebebeb;
}

.search-input {
  flex: 1;
}

.list {
  flex: 1;
  overflow-y: auto;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.row:hover,
.row.active {
  background: #e6f2ff;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.desc,
.members {
  font-size: 12px;
  color: #999;
}

.hint {
  padding: 8px 12px;
  font-size: 11px;
  color: #666;
  background: #fafafa;
  border-top: 1px solid #eee;
}
</style>