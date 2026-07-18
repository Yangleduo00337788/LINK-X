package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建群邀请 DTO（owner/admin 主动邀请用户入群）。
 */
@Data
public class InviteGroupDTO {

    @NotNull(message = "被邀请人 ID 不能为空")
    private Long inviteeUserId;

    /**
     * 可选邀请留言
     */
    private String message;
}
