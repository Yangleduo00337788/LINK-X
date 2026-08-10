package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "角色下的用户")
public class AdminRoleUserVO {

    private Long id;
    private String username;
    private String nickname;
    private Integer status;
}
