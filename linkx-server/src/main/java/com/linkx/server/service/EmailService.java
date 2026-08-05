package com.linkx.server.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送注册邮箱验证码
     */
    void sendRegisterCode(String to, String username, String code);

    /**
     * 发送找回密码验证码邮件
     */
    void sendPasswordResetCode(String to, String username, String code);

    /**
     * 发送注册成功欢迎邮件
     */
    void sendWelcomeEmail(String to, String username, String nickname);

    /**
     * 发送密码重置成功通知邮件
     */
    void sendPasswordChangedNotification(String to, String username, String ip);

    /**
     * 发送绑定/更换邮箱验证码
     */
    void sendBindEmailCode(String to, String username, String code);

    /**
     * 发送管理端高危操作二次验证码
     */
    void sendAdminStepUpCode(String to, String username, String code);

    /**
     * 审批待办通知邮件（指定用户被指派审批时）。
     */
    void sendApprovalPendingNotification(String to, String displayName, String title, String stepName);
}
