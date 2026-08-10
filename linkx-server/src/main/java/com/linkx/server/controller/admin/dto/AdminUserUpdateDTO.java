package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新用户资料")
public class AdminUserUpdateDTO {

    @Schema(description = "昵称")
    @Size(max = 64)
    private String nickname;

    @Schema(description = "头像")
    @Size(max = 512)
    private String avatar;

    @Schema(description = "签名")
    @Size(max = 255)
    private String signature;

    @Schema(description = "邮箱")
    @Size(max = 128)
    private String email;

    @Schema(description = "手机号")
    @Size(max = 32)
    private String phone;

    @Schema(description = "所属部门 ID，传 null 不改；传 0 清空")
    private Long deptId;
}
