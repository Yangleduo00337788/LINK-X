package com.linkx.server.service;

import com.linkx.server.controller.dto.CreateGroupAnnouncementDTO;
import com.linkx.server.controller.dto.UpdateGroupAnnouncementDTO;
import com.linkx.server.controller.vo.GroupAnnouncementVO;

import java.util.List;

public interface GroupAnnouncementService {

    List<GroupAnnouncementVO> list(Long userId, Long conversationId);

    /** 侧栏摘要：有置顶取最新置顶，否则取最新一条 */
    GroupAnnouncementVO display(Long userId, Long conversationId);

    GroupAnnouncementVO create(Long userId, Long conversationId, CreateGroupAnnouncementDTO dto);

    GroupAnnouncementVO update(Long userId, Long conversationId, Long announcementId, UpdateGroupAnnouncementDTO dto);

    void delete(Long userId, Long conversationId, Long announcementId);
}
