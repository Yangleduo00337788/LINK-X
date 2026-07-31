package com.linkx.server.service.admin;

import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminRiskEventBatchDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventHandleDTO;
import com.linkx.server.controller.admin.dto.AdminRiskEventQueryDTO;
import com.linkx.server.controller.admin.vo.AdminReviewBatchResultVO;
import com.linkx.server.controller.admin.vo.AdminRiskEventVO;

import java.util.List;

public interface AdminRiskEventService {

    PageResultVO<AdminRiskEventVO> list(AdminRiskEventQueryDTO query);

    AdminRiskEventVO detail(Long id);

    void handle(Long id, AdminRiskEventHandleDTO dto, Long operatorId);

    /** 批量处置（不连带处罚用户；返回成功/失败明细） */
    AdminReviewBatchResultVO batch(AdminRiskEventBatchDTO dto, Long operatorId);

    long countPending();

    long countSince(java.util.Date since);

    List<AdminRiskEventVO> listForExport(AdminRiskEventQueryDTO query);

    /** 记录敏感词命中风险事件 */
    void recordSensitiveMatch(Long userId, String matchedWords, String failReason, Long conversationId);

    /** 记录消息风暴风险事件 */
    void recordMessageStorm(Long userId, String eventType, int messageCount, Long conversationId);

    /** 记录登录暴力破解锁定 */
    void recordLoginLock(Long userId, String username, String ip, String side, int lockMinutes);

    /**
     * 记录接口/登录限流触发（仅首次超限时调用，避免刷爆）。
     *
     * @param userId   可空
     * @param identity 限流标识（用户ID 或 ip:...）
     * @param scope    限流范围
     * @param ip       客户端 IP
     */
    void recordRateLimit(Long userId, String identity, String scope, String ip);
}
