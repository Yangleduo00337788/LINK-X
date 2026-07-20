package com.linkx.server.controller.dto;

import lombok.Data;

/**
 * 用户偏好设置 DTO（PUT /user/preference 请求体）
 * 所有字段均为可选；为 null 表示"不修改"。
 */
@Data
public class UserPreferenceDTO {

    private Boolean autoStart;
    private Boolean soundNotify;
    private Boolean messageDetail;
    private Boolean notifyAtMe;
    private Boolean notifySound;

    private Boolean privacyVerifyFriend;
    private Boolean privacyAllowStranger;
    private Boolean privacyShowOnline;

    private String language;
    private String chatBackground;
    private String notifyTone;
    private String momentsBackground;
}