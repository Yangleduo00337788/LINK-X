package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端重置用户密码；newPassword 为空时由服务端生成临时密码并仅返回一次")
public class AdminUserResetPasswordDTO {

    @Schema(description = "新密码；留空则自动生成符合策略的临时密码")
    @Size(max = 64)
    private String newPassword;
}
