package com.linkx.server.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户偏好设置（per-user，一行一用户）
 * userId 既是主键也是外键关联到 sys_user.id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_preference")
public class UserPreference implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long userId;

    /** 开机自动启动 */
    private Boolean autoStart;

    /** 新消息声音提示 */
    private Boolean soundNotify;

    /** 通知显示消息详情 */
    private Boolean messageDetail;

    /** 群聊 @ 我特别提醒 */
    private Boolean notifyAtMe;

    /** 通知提示音 */
    private Boolean notifySound;

    /** 加好友需验证 */
    private Boolean privacyVerifyFriend;

    /** 允许陌生人会话 */
    private Boolean privacyAllowStranger;

    /** 在线状态可见 */
    private Boolean privacyShowOnline;

    /** 界面语言 */
    private String language;

    /** 聊天背景主题 */
    private String chatBackground;

    /** 提示音（音色 ID：default/chime/bell/pop 等） */
    private String notifyTone;

    /** 友链背景图（对象存储 key） */
    private String momentsBackground;

    /** 收藏视图：grid / list */
    private String favoritesViewMode;

    /** 收藏排序：newest / oldest / title */
    private String favoritesSort;

    @Column(onInsertValue = "NOW()")
    private Date createTime;

    @Column(onInsertValue = "NOW()", onUpdateValue = "NOW()")
    private Date updateTime;
}
