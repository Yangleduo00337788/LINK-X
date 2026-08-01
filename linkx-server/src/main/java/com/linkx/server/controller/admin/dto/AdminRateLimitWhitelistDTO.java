package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "限流白名单 IP")
public class AdminRateLimitWhitelistDTO {

    @NotBlank
    @Schema(description = "客户端 IP")
    private String ip;
}
