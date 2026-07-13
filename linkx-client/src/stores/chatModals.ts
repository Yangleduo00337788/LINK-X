/**
 * 聊天相关模态框与抽屉 Store
 * 统一管理聊天界面中各类弹层、抽屉的开关状态及联系人资料卡位置
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入联系人类型（资料卡展示用）
import type { ContactItem } from '../types'

// 定义并导出 chatModals Store
export const useChatModalsStore = defineStore('chatModals', {
  // 各弹层/抽屉的布尔开关及资料卡相关状态
  state: () => ({
    moreDrawerOpen: false,              // 聊天「更多」抽屉
    groupInfoDrawerOpen: false,         // 群资料抽屉
    createGroupOpen: false,             // 创建群聊模态框
    comprehensiveSearchOpen: false,     // 综合搜索模态框
    momentsModalOpen: false,            // 朋友圈模态框
    voiceCallOpen: false,               // 语音通话模态框
    videoCallOpen: false,               // 视频通话模态框
    addMembersOpen: false,              // 添加群成员模态框
    groupFilesOpen: false,              // 群文件模态框
    groupAlbumOpen: false,              // 群相册模态框
    groupEssenceOpen: false,            // 群精华模态框
    groupAnnouncementOpen: false,       // 群公告模态框
    redPacketOpen: false,               // 发红包模态框
    redPacketReceiveOpen: false,        // 领红包模态框
    redPacketReceiveMsgId: null as string | null, // 当前要领红包的消息 id
    contactProfileOpen: false,          // 联系人资料卡是否显示
    editProfileOpen: false,             // 编辑资料弹窗是否显示
    currentContactProfile: null as ContactItem | null, // 资料卡展示的联系人
    profileCardIsSelf: false,           // 资料卡是否为「自己的资料」
    profileCardPos: { x: 0, y: 0 }      // 资料卡屏幕坐标（像素）
  }),

  getters: {},

  actions: {
    /** 切换「更多」抽屉；打开时关闭群资料抽屉 */
    toggleMore() {
      if (this.moreDrawerOpen) {
        this.moreDrawerOpen = false
        return
      }
      this.groupInfoDrawerOpen = false // 互斥：先关群资料
      this.moreDrawerOpen = true
    },
    /** 打开「更多」抽屉 */
    openMore() {
      this.groupInfoDrawerOpen = false
      this.moreDrawerOpen = true
    },
    /** 关闭「更多」抽屉 */
    closeMore() {
      this.moreDrawerOpen = false
    },
    /** 切换群资料抽屉；打开时关闭「更多」抽屉 */
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
    /**
     * 打开领红包弹窗
     * @param messageId 红包消息 id
     */
    openRedPacketReceive(messageId: string) {
      this.redPacketReceiveMsgId = messageId
      this.redPacketReceiveOpen = true
    },
    closeRedPacketReceive() {
      this.redPacketReceiveOpen = false
      this.redPacketReceiveMsgId = null
    },
    /**
     * 打开他人联系人资料卡
     * @param contact 联系人数据
     * @param event 可选鼠标事件，用于定位卡片
     */
    openContactProfile(contact: ContactItem, event?: MouseEvent) {
      this.profileCardIsSelf = false
      this.currentContactProfile = contact
      this.contactProfileOpen = true
      this.setProfileCardPosition(event)
    },
    /**
     * 打开当前用户自己的资料卡
     * @param profile 昵称等基本信息
     * @param event 可选鼠标事件
     */
    openSelfProfile(
      profile: { nickname: string; username?: string; avatarText?: string; avatarUrl?: string; userId?: number },
      event?: MouseEvent
    ) {
      this.profileCardIsSelf = true
      // 将用户资料映射为 ContactItem 结构以便复用同一 UI
      this.currentContactProfile = {
        id: profile.username || 'self',
        name: profile.nickname,
        avatarText: profile.avatarText || profile.nickname.charAt(0) || '我',
        avatarColor: profile.avatarUrl ? 'transparent' : 'var(--lx-success)',
        avatarUrl: profile.avatarUrl,
        group: '我的好友',
        online: true,
        userId: profile.userId
      }
      this.contactProfileOpen = true
      this.setProfileCardPosition(event)
    },
    /** 打开编辑资料弹窗 */
    openEditProfile() {
      this.editProfileOpen = true
    },
    /** 关闭编辑资料弹窗 */
    closeEditProfile() {
      this.editProfileOpen = false
    },
    /**
     * 计算资料卡位置：优先跟随鼠标，否则居中偏上
     * @param event 鼠标事件
     */
    setProfileCardPosition(event?: MouseEvent) {
      const cardW = 320  // 卡片预估宽度
      const cardH = 300  // 卡片预估高度
      if (event) {
        // 默认在点击位置右下方偏移
        let x = event.clientX + 14
        let y = event.clientY - 12
        // 限制在视口内，留 12px 边距
        x = Math.min(Math.max(12, x), window.innerWidth - cardW - 12)
        y = Math.min(Math.max(12, y), window.innerHeight - cardH - 12)
        this.profileCardPos = { x, y }
      } else {
        // 无事件时水平居中、距顶 72px
        this.profileCardPos = {
          x: Math.max(12, (window.innerWidth - cardW) / 2),
          y: 72
        }
      }
    },
    /** 关闭资料卡并重置相关状态 */
    closeContactProfile() {
      this.contactProfileOpen = false
      this.editProfileOpen = false
      this.currentContactProfile = null
      this.profileCardIsSelf = false
    },

    /** 一键关闭所有聊天相关弹层（路由切换或 ESC 时可用） */
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
      this.editProfileOpen = false
      this.currentContactProfile = null
      this.profileCardIsSelf = false
    }
  }
})
