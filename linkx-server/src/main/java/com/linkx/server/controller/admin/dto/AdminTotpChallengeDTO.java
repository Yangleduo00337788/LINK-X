package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录挑战下发起 TOTP 绑定")
public class AdminTotpChallengeDTO {

    @NotBlank
    @Size(max = 128)
    private String challengeToken;
}
