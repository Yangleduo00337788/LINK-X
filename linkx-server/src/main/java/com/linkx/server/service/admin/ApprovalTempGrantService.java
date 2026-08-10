package com.linkx.server.service.admin;


/**
 * 作者：yangleduo
 */
import java.util.Collection;
import java.util.List;

/**
 * 审批流程临时权限：为无审核角色的指定审批人临时放开审批/查看权限，处理完成后撤销。
 */
public interface ApprovalTempGrantService {

    List<String> APPROVAL_TEMP_PERMISSIONS = List.of(
            "admin:approval:inbox",
            "admin:approval:action",
            "admin:review:list"
    );

    /**
     * 为审批记录的处理人授予临时权限（若已具备则跳过）。
     */
    void grantForRecord(Long recordId, Long userId);

    /**
     * 撤销单条审批记录关联的临时权限。
     */
    void revokeForRecord(Long recordId);

    /**
     * 撤销审批实例下全部临时权限。
     */
    void revokeForInstance(Long instanceId);

    /**
     * 查询用户当前有效的临时权限码。
     */
    List<String> activePermissionCodes(Long userId);

    /**
     * 判断用户是否因某条记录获得过临时授权。
     */
    boolean wasGrantedForRecord(Long recordId, Long userId);

    /**
     * 为当前用户全部待处理审批记录补齐临时权限（幂等）。
     */
    void syncGrantsForUser(Long userId);
}
