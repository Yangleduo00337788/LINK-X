package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员更新个人资料")
public class AdminProfileUpdateDTO {

    @Schema(description = "昵称")
    @Size(max = 64, message = "昵称最多 64 个字符")
    private String nickname;

    @Schema(description = "头像 URL")
    @Size(max = 512, message = "头像地址过长")
    private String avatar;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最多 128 个字符")
    private String email;
}
