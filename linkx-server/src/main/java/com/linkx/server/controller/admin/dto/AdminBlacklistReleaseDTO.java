package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "移出黑名单")
public class AdminBlacklistReleaseDTO {

    @Schema(description = "解封原因")
    @Size(max = 255)
    private String reason;
}
