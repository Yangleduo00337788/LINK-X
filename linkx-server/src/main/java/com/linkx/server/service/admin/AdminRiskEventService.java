package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;

public interface AdminRiskEventService {

    PageResultVO<AdminRiskEventVO> list(AdminRiskEventQueryDTO query);

    AdminRiskEventVO detail(Long id);

    void handle(Long id, AdminRiskEventHandleDTO dto, Long operatorId);

    long countPending();

    long countSince(java.util.Date since);

    /** 记录敏感词命中风险事件 */
    void recordSensitiveMatch(Long userId, String matchedWords, String failReason, Long conversationId);

    /** 记录消息风暴风险事件 */
    void recordMessageStorm(Long userId, String eventType, int messageCount, Long conversationId);
}
