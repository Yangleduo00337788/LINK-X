package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 处理入群申请 DTO
 */
@Data
public class HandleJoinRequestDTO {

    @NotNull(message = "approve 不能为空")
    private Boolean approve;
}
