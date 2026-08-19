package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    private Boolean privacySendReadReceipt;

    @Size(max = 16, message = "语言代码最长16字符")
    private String language;

    @Pattern(regexp = "^(auto|zh|en|ja|ko)$", message = "翻译目标语言仅支持 auto/zh/en/ja/ko")
    @Size(max = 16, message = "翻译目标语言最长16字符")
    private String translateTargetLang;

    @Size(max = 512, message = "聊天背景最长512字符")
    private String chatBackground;
    @Size(max = 128, message = "通知铃声最长128字符")
    private String notifyTone;
    @Size(max = 512, message = "朋友圈背景最长512字符")
    private String momentsBackground;
    @Size(max = 32, message = "收藏视图模式最长32字符")
    private String favoritesViewMode;
    @Size(max = 32, message = "收藏排序方式最长32字符")
    private String favoritesSort;

    private Boolean quietHoursEnabled;
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "免打扰开始时间格式必须为 HH:mm")
    private String quietHoursStart;
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "免打扰结束时间格式必须为 HH:mm")
    private String quietHoursEnd;
    private Boolean notifyChat;
    private Boolean notifySocial;
    private Boolean notifyMoments;
    private Boolean notifySystem;
    private Boolean notifyFriendOnline;
}
