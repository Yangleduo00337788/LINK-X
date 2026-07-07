<script setup lang="ts">
import { computed } from 'vue'
import { useAppState } from '../composables/useAppState'
import FriendNotifications from './contacts/FriendNotifications.vue'
import GroupNotifications from './contacts/GroupNotifications.vue'
import PlaceholderMainView from './PlaceholderMainView.vue'

const { contactsActiveView, navKey } = useAppState()
</script>

<template>
  <FriendNotifications v-if="contactsActiveView === 'friend-notifs'" />
  <GroupNotifications v-else-if="contactsActiveView === 'group-notifs'" />
  <PlaceholderMainView v-else :nav="navKey" />
</template>
