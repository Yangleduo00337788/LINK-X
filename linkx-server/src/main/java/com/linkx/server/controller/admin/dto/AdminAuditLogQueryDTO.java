package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志列表查询")
public class AdminAuditLogQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "操作类型，如 ROLE_GRANT / USER_BAN")
    private String operationType;

    @Schema(description = "操作状态：SUCCESS / FAIL")
    private String resultStatus;
}
