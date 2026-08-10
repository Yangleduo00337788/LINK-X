package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import java.util.List;

/**
 * 管理端事件受众解析：通知仅推送给具备对应职责权限的管理员。
 */
public interface AdminAudienceService {

    /** 具备内容审核/举报处理权限的管理员（admin:review:list）。 */
    List<Long> reviewOperatorUserIds();

    /** 具备用户反馈处理权限的管理员（admin:feedback:list）。 */
    List<Long> feedbackOperatorUserIds();

    /** 具备风险事件查看权限的管理员（admin:risk-event:list）。 */
    List<Long> riskOperatorUserIds();

    /**
     * 具备指定权限的管理员用户 ID。
     */
    List<Long> userIdsWithPermission(String permissionCode);
}
