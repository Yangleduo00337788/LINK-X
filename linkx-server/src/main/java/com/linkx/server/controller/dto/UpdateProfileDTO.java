package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Pattern;
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

    /**
     * 性别
     */
    @Pattern(regexp = "^(男|女)?$", message = "性别只能是男或女")
    private String gender;

    /**
     * 生日（毫秒时间戳）
     */
    private Long birthday;

    /**
     * 国家
     */
    @Size(max = 64, message = "国家长度不能超过64字符")
    private String country;

    /**
     * 省份
     */
    @Size(max = 64, message = "省份长度不能超过64字符")
    private String province;

    /**
     * 地区
     */
    @Size(max = 64, message = "地区长度不能超过64字符")
    private String region;
}
