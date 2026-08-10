package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "解除 IP 限流")
public class AdminRateLimitUnblockDTO {

    @NotBlank
    @Schema(description = "客户端 IP")
    private String ip;
}
