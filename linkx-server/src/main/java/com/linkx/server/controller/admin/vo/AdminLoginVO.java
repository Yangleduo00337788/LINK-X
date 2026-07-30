package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
@Schema(description = "管理员登录响应")
public class AdminLoginVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private AdminUserProfileVO user;
}
