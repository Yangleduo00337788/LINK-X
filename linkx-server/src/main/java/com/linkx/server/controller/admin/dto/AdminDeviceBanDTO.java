package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "设备封禁请求")
public class AdminDeviceBanDTO {

    @Size(max = 255)
    @Schema(description = "封禁原因")
    private String reason;
}
