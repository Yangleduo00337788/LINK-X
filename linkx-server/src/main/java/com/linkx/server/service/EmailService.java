package com.linkx.server.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送找回密码验证码邮件
     *
     * @param to      收件人邮箱
     * @param username 用户名
     * @param code    验证码
     */
    void sendPasswordResetCode(String to, String username, String code);

    /**
     * 发送密码重置成功通知邮件
     *
     * @param to      收件人邮箱
     * @param username 用户名
     * @param ip      操作 IP
     */
    void sendPasswordChangedNotification(String to, String username, String ip);
}
