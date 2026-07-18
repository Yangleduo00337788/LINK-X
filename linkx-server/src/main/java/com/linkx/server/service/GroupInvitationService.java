package com.linkx.server.service;

import com.linkx.server.controller.dto.InviteGroupDTO;
import com.linkx.server.controller.vo.GroupConversationVO;
import com.linkx.server.controller.vo.GroupInvitationVO;

import java.util.List;

/**
 * 群邀请服务。
 * <p>
 * 群成员主动邀请外部用户入群：写一条 {@code group_invitation} 记录，
 * 被邀请人可在"群通知"中查看、接受或拒绝；接受后写入会话成员表。
 * </p>
 */
public interface GroupInvitationService {

    /**
     * 创建邀请（仅群主/管理员）。
     */
    GroupInvitationVO invite(Long userId, Long conversationId, InviteGroupDTO dto);

    /**
     * 列出当前用户收到的群邀请（按状态可筛选，默认全部）。
     */
    List<GroupInvitationVO> listMyInvitations(Long userId);

    /**
     * 接受邀请：写入会话成员表 + 标记状态。
     * @return 成功返回新会话信息，便于前端跳转到该群
     */
    GroupConversationVO accept(Long userId, Long invitationId);

    /**
     * 拒绝邀请
     */
    void reject(Long userId, Long invitationId);
}
