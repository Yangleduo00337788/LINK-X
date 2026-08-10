package com.linkx.server.service;


/**
 * 作者：yangleduo
 */
import com.linkx.server.controller.dto.ConferenceCreateDTO;
import com.linkx.server.controller.dto.ConferenceSignalDTO;
import com.linkx.server.controller.vo.ConferenceInfoVO;

import java.util.List;

/**
 * 多人会议（DB 持久化 + CallService Redis 实时信令）。
 */
public interface ConferenceService {

    ConferenceInfoVO create(Long userId, ConferenceCreateDTO dto);

    ConferenceInfoVO join(Long userId, Long conferenceId, String password);

    void leave(Long userId, Long conferenceId);

    void end(Long userId, Long conferenceId);

    ConferenceInfoVO info(Long userId, Long conferenceId);

    List<ConferenceInfoVO> listActive(Long userId);

    /**
     * 查询会话当前 ACTIVE 会议（会话成员即可，无需已入会），用于聊天顶栏展示。
     */
    ConferenceInfoVO findActiveInConversation(Long userId, Long conversationId);

    void mute(Long userId, Long conferenceId, Long targetUserId, boolean muted);

    void setVideo(Long userId, Long conferenceId, boolean videoOff);

    void removeMember(Long hostId, Long conferenceId, Long targetUserId);

    void transferHost(Long hostId, Long conferenceId, Long newHostId);

    void admitMember(Long hostId, Long conferenceId, Long targetUserId);

    void setMemberRole(Long hostId, Long conferenceId, Long targetUserId, String role);

    void raiseHand(Long userId, Long conferenceId, boolean raised);

    List<ConferenceInfoVO> listHistory(Long userId, Long conversationId);

    void signal(Long userId, ConferenceSignalDTO dto);
}
