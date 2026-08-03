package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminFeedbackAssignDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackQueryDTO;
import com.linkx.server.controller.admin.dto.AdminFeedbackReplyDTO;
import com.linkx.server.controller.admin.vo.AdminFeedbackVO;
import com.linkx.server.controller.vo.FeedbackReplyVO;

import java.util.List;

public interface AdminFeedbackService {

    PageResultVO<AdminFeedbackVO> list(AdminFeedbackQueryDTO query);

    List<AdminFeedbackVO> listForExport(AdminFeedbackQueryDTO query);

    AdminFeedbackVO detail(Long id);

    List<FeedbackReplyVO> listReplies(Long id);

    void reply(Long id, AdminFeedbackReplyDTO dto, Long operatorId);

    void close(Long id, Long operatorId);

    void reopen(Long id, Long operatorId);

    void assign(Long id, AdminFeedbackAssignDTO dto, Long operatorId);

    /** 超过 SLA 仍未处理的 pending 反馈数 */
    long countOverdue();
}
