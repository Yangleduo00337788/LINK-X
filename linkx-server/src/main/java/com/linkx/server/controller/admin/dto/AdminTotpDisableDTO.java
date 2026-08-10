package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端关闭 TOTP")
public class AdminTotpDisableDTO {

    @NotBlank
    @Size(max = 128)
    private String password;

    @NotBlank
    @Size(min = 6, max = 8)
    private String code;
}
