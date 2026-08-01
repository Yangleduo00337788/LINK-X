package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "二次验证通过后签发的短效令牌")
public class AdminStepUpTokenVO {

    @Schema(description = "放入请求头 X-Step-Up-Token")
    private String stepUpToken;

    @Schema(description = "绑定的动作 scope")
    private String action;

    @Schema(description = "使用的验证方式")
    private String method;

    @Schema(description = "有效秒数")
    private Long expiresIn;
}
