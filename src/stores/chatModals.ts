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
    redPacketReceiveOpen: false,
    redPacketReceiveMsgId: null as string | null,
    contactProfileOpen: false,
    currentContactProfile: null as ContactItem | null,
    profileCardIsSelf: false,
    profileCardPos: { x: 0, y: 0 }
  }),

  getters: {},

  actions: {
    toggleMore() {
      if (this.moreDrawerOpen) {
        this.moreDrawerOpen = false
        return
      }
      this.groupInfoDrawerOpen = false
      this.moreDrawerOpen = true
    },
    openMore() {
      this.groupInfoDrawerOpen = false
      this.moreDrawerOpen = true
    },
    closeMore() {
      this.moreDrawerOpen = false
    },
    toggleGroupInfo() {
      if (this.groupInfoDrawerOpen) {
        this.groupInfoDrawerOpen = false
        return
      }
      this.moreDrawerOpen = false
      this.groupInfoDrawerOpen = true
    },
    openGroupInfo() {
      this.moreDrawerOpen = false
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
    openRedPacketReceive(messageId: string) {
      this.redPacketReceiveMsgId = messageId
      this.redPacketReceiveOpen = true
    },
    closeRedPacketReceive() {
      this.redPacketReceiveOpen = false
      this.redPacketReceiveMsgId = null
    },
    openContactProfile(contact: ContactItem, event?: MouseEvent) {
      this.profileCardIsSelf = false
      this.currentContactProfile = contact
      this.contactProfileOpen = true
      this.setProfileCardPosition(event)
    },
    openSelfProfile(
      profile: { nickname: string; username?: string; avatarText?: string },
      event?: MouseEvent
    ) {
      this.profileCardIsSelf = true
      this.currentContactProfile = {
        id: profile.username || 'self',
        name: profile.nickname,
        avatarText: profile.avatarText || profile.nickname.charAt(0) || '我',
        avatarColor: 'var(--lx-success)',
        group: '我的好友',
        online: true
      }
      this.contactProfileOpen = true
      this.setProfileCardPosition(event)
    },
    setProfileCardPosition(event?: MouseEvent) {
      const cardW = 320
      const cardH = 300
      if (event) {
        let x = event.clientX + 14
        let y = event.clientY - 12
        x = Math.min(Math.max(12, x), window.innerWidth - cardW - 12)
        y = Math.min(Math.max(12, y), window.innerHeight - cardH - 12)
        this.profileCardPos = { x, y }
      } else {
        this.profileCardPos = {
          x: Math.max(12, (window.innerWidth - cardW) / 2),
          y: 72
        }
      }
    },
    closeContactProfile() {
      this.contactProfileOpen = false
      this.currentContactProfile = null
      this.profileCardIsSelf = false
    },

    closeAllModals() {
      this.moreDrawerOpen = false
      this.groupInfoDrawerOpen = false
      this.createGroupOpen = false
      this.comprehensiveSearchOpen = false
      this.momentsModalOpen = false
      this.voiceCallOpen = false
      this.videoCallOpen = false
      this.addMembersOpen = false
      this.groupFilesOpen = false
      this.groupAlbumOpen = false
      this.groupEssenceOpen = false
      this.groupAnnouncementOpen = false
      this.redPacketOpen = false
      this.redPacketReceiveOpen = false
      this.redPacketReceiveMsgId = null
      this.contactProfileOpen = false
      this.currentContactProfile = null
      this.profileCardIsSelf = false
    }
  }
})
