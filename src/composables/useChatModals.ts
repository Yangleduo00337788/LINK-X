import { ref } from 'vue'

const moreDrawerOpen = ref(false)
const groupInfoDrawerOpen = ref(false)
const createGroupOpen = ref(false)
const comprehensiveSearchOpen = ref(false)
const momentsModalOpen = ref(false)
const voiceCallOpen = ref(false)
const videoCallOpen = ref(false)

const addMembersOpen = ref(false)
const groupFilesOpen = ref(false)
const groupAlbumOpen = ref(false)
const groupEssenceOpen = ref(false)
const groupAnnouncementOpen = ref(false)

export function useChatModals() {
  function openMore() {
    moreDrawerOpen.value = true
  }
  function closeMore() {
    moreDrawerOpen.value = false
  }
  function openGroupInfo() {
    groupInfoDrawerOpen.value = true
  }
  function closeGroupInfo() {
    groupInfoDrawerOpen.value = false
  }
  function openCreateGroup() {
    createGroupOpen.value = true
  }
  function closeCreateGroup() {
    createGroupOpen.value = false
  }
  function openComprehensiveSearch() {
    comprehensiveSearchOpen.value = true
  }
  function closeComprehensiveSearch() {
    comprehensiveSearchOpen.value = false
  }
  function openMomentsModal() {
    momentsModalOpen.value = true
  }
  function closeMomentsModal() {
    momentsModalOpen.value = false
  }
  function openVoiceCall() {
    voiceCallOpen.value = true
  }
  function closeVoiceCall() {
    voiceCallOpen.value = false
  }
  function openVideoCall() {
    videoCallOpen.value = true
  }
  function closeVideoCall() {
    videoCallOpen.value = false
  }

  function openAddMembers() {
    addMembersOpen.value = true
  }
  function closeAddMembers() {
    addMembersOpen.value = false
  }
  function openGroupFiles() {
    groupFilesOpen.value = true
  }
  function closeGroupFiles() {
    groupFilesOpen.value = false
  }
  function openGroupAlbum() {
    groupAlbumOpen.value = true
  }
  function closeGroupAlbum() {
    groupAlbumOpen.value = false
  }
  function openGroupEssence() {
    groupEssenceOpen.value = true
  }
  function closeGroupEssence() {
    groupEssenceOpen.value = false
  }
  function openGroupAnnouncement() {
    groupAnnouncementOpen.value = true
  }
  function closeGroupAnnouncement() {
    groupAnnouncementOpen.value = false
  }

  /** @deprecated 使用 openCreateGroup */
  function openSelectContacts() {
    openCreateGroup()
  }
  function closeSelectContacts() {
    closeCreateGroup()
  }

  return {
    moreDrawerOpen,
    groupInfoDrawerOpen,
    createGroupOpen,
    comprehensiveSearchOpen,
    momentsModalOpen,
    selectContactsOpen: createGroupOpen,
    voiceCallOpen,
    videoCallOpen,
    addMembersOpen,
    groupFilesOpen,
    groupAlbumOpen,
    groupEssenceOpen,
    groupAnnouncementOpen,
    openMore,
    closeMore,
    openGroupInfo,
    closeGroupInfo,
    openCreateGroup,
    closeCreateGroup,
    openComprehensiveSearch,
    closeComprehensiveSearch,
    openMomentsModal,
    closeMomentsModal,
    openSelectContacts,
    closeSelectContacts,
    openVoiceCall,
    closeVoiceCall,
    openVideoCall,
    closeVideoCall,
    openAddMembers,
    closeAddMembers,
    openGroupFiles,
    closeGroupFiles,
    openGroupAlbum,
    closeGroupAlbum,
    openGroupEssence,
    closeGroupEssence,
    openGroupAnnouncement,
    closeGroupAnnouncement
  }
}