package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户偏好设置 VO（GET /user/preference 响应体）
 * 直接使用 Boolean 类型以便"未设置/三态"判定，前端只关心 true/false。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String favoritesViewMode;
    private String favoritesSort;

    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
    private Boolean notifyChat;
    private Boolean notifySocial;
    private Boolean notifyMoments;
    private Boolean notifySystem;
}