package com.linkx.server.service;

import com.linkx.server.controller.dto.AddGroupMembersDTO;
import com.linkx.server.controller.dto.CreateGroupDTO;
import com.linkx.server.controller.dto.UpdateGroupDTO;
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
     * 更新群信息（名称/公告）
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
     * 更新当前用户对本群的备注
     */
    String updateMyRemark(Long userId, Long conversationId, String remark);
}
