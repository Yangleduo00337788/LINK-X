package com.linkx.server.service;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.MuteAllDTO;
import com.linkx.server.controller.dto.MuteMemberDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupRemarkDTO;
import com.linkx.server.controller.vo.ConversationVO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupMemberVO;

import java.util.List;

/**
 * 群聊服务接口
 */
public interface GroupService {

    /**
     * 创建群聊
     */
    GroupConversationVO createGroup(Long userId, CreateGroupDTO dto);

    /**
     * 获取用户的群聊列表
     */
    List<ConversationVO> listGroups(Long userId);

    /**
     * 获取群详情
     */
    GroupConversationVO getGroupInfo(Long userId, Long conversationId);

    /**
     * 更新群信息：改群名仅群主；发/改公告群主与管理员均可
     */
    GroupConversationVO updateGroup(Long userId, Long conversationId, UpdateGroupDTO dto);

    /**
     * 获取群成员列表
     */
    List<GroupMemberVO> listMembers(Long userId, Long conversationId);

    /**
     * 添加群成员
     */
    List<GroupMemberVO> addMembers(Long userId, Long conversationId, AddGroupMembersDTO dto);

    /**
     * 移除群成员（仅群主/管理员）
     */
    void removeMember(Long userId, Long conversationId, Long memberId);

    /**
     * 退出群聊
     */
    void quitGroup(Long userId, Long conversationId);

    /**
     * 解散群聊（仅群主）
     */
    void dissolveGroup(Long userId, Long conversationId);

    /**
     * 转让群主
     */
    void transferOwner(Long userId, Long conversationId, Long newOwnerId);

    /**
     * 设置或取消管理员（仅群主；role 仅允许 admin / member）
     */
    void updateMemberRole(Long userId, Long conversationId, Long memberId, String role);

    /**
     * 全体禁言或定时全体禁言（群主/管理员）
     */
    GroupConversationVO updateMuteAll(Long userId, Long conversationId, MuteAllDTO dto);

    /**
     * 指定成员禁言（群主/管理员）
     */
    void updateMemberMute(Long userId, Long conversationId, Long memberId, MuteMemberDTO dto);

    /**
     * 定时任务：按计划开关全体禁言、清理到期成员禁言
     */
    void applyMuteSchedules();

    /**
     * 更新当前用户对本群的备注
     */
    String updateMyRemark(Long userId, Long conversationId, String remark);

    // ==================== 群成员批量管理 ====================

    /** 批量移除群成员（仅群主/管理员） */
    void batchRemoveMembers(Long userId, Long conversationId, List<Long> memberIds);

    /** 批量禁言（仅群主/管理员） */
    void batchMuteMembers(Long userId, Long conversationId, List<Long> memberIds, boolean muted);

    // ==================== 入群审核 ====================

    /** 设置入群审核开关（仅群主） */
    void setJoinApproval(Long userId, Long conversationId, boolean required);

    /** 处理入群申请（群主/管理员审批） */
    void handleJoinRequest(Long userId, Long conversationId, Long applicantId, boolean approve);

    /** 申请入群 */
    void requestJoin(Long userId, Long conversationId, String message);

    /** 当前管理员视角的待审批入群申请 */
    java.util.List<com.linkx.server.controller.vo.GroupJoinRequestVO> listJoinRequests(Long userId, Long conversationId);

    // ==================== 群公告已读统计 ====================

    /** 标记群公告已读 */
    void markAnnouncementRead(Long userId, Long conversationId);

    /** 获取群公告已读人数 */
    long getAnnouncementReadCount(Long conversationId);

    // ==================== 群聊邀请策略 ====================

    /** 设置群聊邀请策略：ownerApprove = 需群主审批，anyMember = 任何人可邀请 */
    void setInvitePolicy(Long userId, Long conversationId, String policy);
}
