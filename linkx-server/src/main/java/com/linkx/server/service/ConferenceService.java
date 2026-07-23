package com.linkx.server.service;

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

    ConferenceInfoVO info(Long conferenceId);

    List<ConferenceInfoVO> listActive(Long userId);

    void mute(Long userId, Long conferenceId, Long targetUserId, boolean muted);

    void setVideo(Long userId, Long conferenceId, boolean videoOff);

    void removeMember(Long hostId, Long conferenceId, Long targetUserId);

    void transferHost(Long hostId, Long conferenceId, Long newHostId);

    void signal(Long userId, ConferenceSignalDTO dto);
}
