<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAppStore } from '../stores/app'
import FriendNotifications from './contacts/FriendNotifications.vue'
import GroupNotifications from './contacts/GroupNotifications.vue'
import PlaceholderMainView from './PlaceholderMainView.vue'

const appStore = useAppStore()
const { contactsActiveView, navKey } = storeToRefs(appStore)
</script>

<template>
  <FriendNotifications v-if="contactsActiveView === 'friend-notifs'" />
  <GroupNotifications v-else-if="contactsActiveView === 'group-notifs'" />
  <PlaceholderMainView v-else :nav="navKey" />
</template>
