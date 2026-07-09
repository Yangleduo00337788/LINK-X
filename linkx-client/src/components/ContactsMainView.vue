<script setup lang="ts">
/**
 * 联系人模块右侧主视图。
 * <p>
 * 根据 contactsActiveView 状态切换展示好友通知、群通知或占位视图。
 * </p>
 */
// Pinia 响应式解构工具
import { storeToRefs } from 'pinia'
// 应用全局状态 Store
import { useAppStore } from '../stores/app'
// 好友通知子页面
import FriendNotifications from './contacts/FriendNotifications.vue'
// 群通知子页面
import GroupNotifications from './contacts/GroupNotifications.vue'
// 通用占位主视图
import PlaceholderMainView from './PlaceholderMainView.vue'

// 获取应用 Store 实例
const appStore = useAppStore()
// 解构联系人当前视图与导航键的响应式引用
const { contactsActiveView, navKey } = storeToRefs(appStore)
</script>

<template>
  <!-- 好友通知视图 -->
  <FriendNotifications v-if="contactsActiveView === 'friend-notifs'" />
  <!-- 群通知视图 -->
  <GroupNotifications v-else-if="contactsActiveView === 'group-notifs'" />
  <!-- 默认占位视图 -->
  <PlaceholderMainView v-else :nav="navKey" />
</template>
