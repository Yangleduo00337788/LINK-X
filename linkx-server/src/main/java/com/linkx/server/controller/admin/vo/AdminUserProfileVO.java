package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
@Schema(description = "管理员资料")
public class AdminUserProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private List<String> roles;
    private Set<String> permissions;

    @Schema(description = "是否已启用管理端 TOTP")
    private Boolean totpEnabled;
}
