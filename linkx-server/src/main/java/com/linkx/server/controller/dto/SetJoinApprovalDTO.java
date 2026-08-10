package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置入群审核 DTO
 */
@Data
public class SetJoinApprovalDTO {

    @NotNull(message = "required 不能为空")
    private Boolean required;
}
