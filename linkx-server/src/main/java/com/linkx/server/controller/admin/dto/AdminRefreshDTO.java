package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "刷新令牌请求")
public class AdminRefreshDTO {

    @Schema(description = "刷新令牌（Web 可省略，从 HttpOnly Cookie 读取）")
    private String refreshToken;
}
