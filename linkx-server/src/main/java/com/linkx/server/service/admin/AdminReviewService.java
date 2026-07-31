package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminReviewQueryDTO;
import com.linkx.server.controller.admin.dto.AdminReviewResolveDTO;
import com.linkx.server.controller.admin.vo.AdminReviewVO;
import com.linkx.server.entity.Feedback;

public interface AdminReviewService {

    PageResultVO<AdminReviewVO> list(AdminReviewQueryDTO query);

    AdminReviewVO detail(Long id);

    void approve(Long id, AdminReviewResolveDTO dto, Long operatorId);

    void reject(Long id, AdminReviewResolveDTO dto, Long operatorId);

    /** 从举报类反馈创建审核任务（幂等） */
    void createFromReportFeedback(Feedback feedback);

    /** 补齐尚未入库的举报反馈审核任务 */
    void ensureReportTasks();

    long countPending();
}
