import { defineStore } from 'pinia'
import type { ContactItem } from '../types'

export const useChatModalsStore = defineStore('chatModals', {
  state: () => ({
    moreDrawerOpen: false,
    groupInfoDrawerOpen: false,
    createGroupOpen: false,
    comprehensiveSearchOpen: false,
    momentsModalOpen: false,
    voiceCallOpen: false,
    videoCallOpen: false,
    addMembersOpen: false,
    groupFilesOpen: false,
    groupAlbumOpen: false,
    groupEssenceOpen: false,
    groupAnnouncementOpen: false,
    redPacketOpen: false,
    contactProfileOpen: false,
    currentContactProfile: null as ContactItem | null
  }),

  getters: {
    selectContactsOpen: state => state.createGroupOpen
  },

  actions: {
    openMore() {
      this.moreDrawerOpen = true
    },
    closeMore() {
      this.moreDrawerOpen = false
    },
    openGroupInfo() {
      this.groupInfoDrawerOpen = true
    },
    closeGroupInfo() {
      this.groupInfoDrawerOpen = false
    },
    openCreateGroup() {
      this.createGroupOpen = true
    },
    closeCreateGroup() {
      this.createGroupOpen = false
    },
    openComprehensiveSearch() {
      this.comprehensiveSearchOpen = true
    },
    closeComprehensiveSearch() {
      this.comprehensiveSearchOpen = false
    },
    openMomentsModal() {
      this.momentsModalOpen = true
    },
    closeMomentsModal() {
      this.momentsModalOpen = false
    },
    openVoiceCall() {
      this.voiceCallOpen = true
    },
    closeVoiceCall() {
      this.voiceCallOpen = false
    },
    openVideoCall() {
      this.videoCallOpen = true
    },
    closeVideoCall() {
      this.videoCallOpen = false
    },
    openAddMembers() {
      this.addMembersOpen = true
    },
    closeAddMembers() {
      this.addMembersOpen = false
    },
    openGroupFiles() {
      this.groupFilesOpen = true
    },
    closeGroupFiles() {
      this.groupFilesOpen = false
    },
    openGroupAlbum() {
      this.groupAlbumOpen = true
    },
    closeGroupAlbum() {
      this.groupAlbumOpen = false
    },
    openGroupEssence() {
      this.groupEssenceOpen = true
    },
    closeGroupEssence() {
      this.groupEssenceOpen = false
    },
    openGroupAnnouncement() {
      this.groupAnnouncementOpen = true
    },
    closeGroupAnnouncement() {
      this.groupAnnouncementOpen = false
    },
    openRedPacket() {
      this.redPacketOpen = true
    },
    closeRedPacket() {
      this.redPacketOpen = false
    },
    openContactProfile(contact: ContactItem) {
      this.currentContactProfile = contact
      this.contactProfileOpen = true
    },
    closeContactProfile() {
      this.contactProfileOpen = false
    },
    /** @deprecated 使用 openCreateGroup */
    openSelectContacts() {
      this.openCreateGroup()
    },
    /** @deprecated 使用 closeCreateGroup */
    closeSelectContacts() {
      this.closeCreateGroup()
    }
  }
})
