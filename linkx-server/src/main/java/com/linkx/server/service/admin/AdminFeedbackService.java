package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackReplyDTO;
import com.linkx.server.controller.admin.dto.AdminPageQueryDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;

public interface AdminFeedbackService {

    PageResultVO<AdminFeedbackVO> list(AdminPageQueryDTO query);

    AdminFeedbackVO detail(Long id);

    void reply(Long id, AdminFeedbackReplyDTO dto, Long operatorId);

    void close(Long id, Long operatorId);

    void reopen(Long id, Long operatorId);
}
