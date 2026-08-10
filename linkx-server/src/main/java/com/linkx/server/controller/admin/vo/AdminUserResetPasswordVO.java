package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重置密码结果")
public class AdminUserResetPasswordVO {

    @Schema(description = "是否由服务端生成临时密码")
    private boolean generated;

    @Schema(description = "临时明文密码；仅 generated=true 时返回，请立即告知用户后丢弃")
    private String temporaryPassword;
}
