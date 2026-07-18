package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅携带 callId 的通话请求（接听 / 拒绝 / 挂断）
 */
@Data
public class CallIdDTO {

    @NotBlank(message = "通话ID不能为空")
    private String callId;
}
