package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求 DTO
 */
@Data
public class UpdateProfileDTO {

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称长度不能超过50字符")
    private String nickname;

    /**
     * 个性签名
     */
    @Size(max = 200, message = "个性签名长度不能超过200字符")
    private String signature;
}
