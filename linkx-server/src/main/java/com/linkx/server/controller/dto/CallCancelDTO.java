package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 取消通话请求
 */
@Data
public class CallCancelDTO {

    @NotBlank(message = "通话ID不能为空")
    private String callId;
}
