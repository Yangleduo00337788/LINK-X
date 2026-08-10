package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
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

    private String apiSignKey;

    @Schema(description = "是否需要 TOTP 二次验证")
    private Boolean requiresTotp;

    @Schema(description = "是否需要先绑定 TOTP（强制策略）")
    private Boolean requiresTotpSetup;

    @Schema(description = "登录挑战令牌（二次验证 / 强制绑定时返回）")
    private String challengeToken;

    @Schema(description = "挑战令牌有效期（秒）")
    private Long challengeExpiresIn;

    @Schema(description = "本次登录 IP（规范化后）")
    private String loginIp;

    @Schema(description = "是否为相对近期成功登录的新 IP（有历史记录且当前 IP 未见过）")
    private Boolean newLoginIp;
}
