package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 用户资料响应 VO
 */
@Data
@Builder
public class UserProfileVO {

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 账号创建时间
     */
    private Date createTime;
}
