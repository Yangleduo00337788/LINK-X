package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "退出登录请求")
public class AdminLogoutDTO {

    @Schema(description = "刷新令牌（可选，Cookie 可兜底）")
    private String refreshToken;
}
