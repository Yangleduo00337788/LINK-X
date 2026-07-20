package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.EmailService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final LinkxProperties linkxProperties;

    @PostConstruct
    public void init() {
        LinkxProperties.Mail mail = linkxProperties.getMail();
        boolean usernameOk = mail.getUsername() != null && !mail.getUsername().isEmpty();
        boolean passwordOk = mail.getPassword() != null && !mail.getPassword().isEmpty();
        if (usernameOk && passwordOk) {
            log.info("邮件服务初始化完成：host={}, port={}, username={}, from={}, tls={}, ssl={}",
                    mail.getHost(), mail.getPort(), mail.getUsername(), mail.getFrom(),
                    mail.isStartTls(), mail.isSsl());
        } else {
            log.warn("邮件服务未完全配置（username={}, password={}），找回密码邮件功能将不可用",
                    usernameOk ? "已配置" : "未配置",
                    passwordOk ? "已配置" : "未配置");
        }
    }

    @Override
    public void sendPasswordResetCode(String to, String username, String code) {
        String subject = "【LinkX】重置密码验证码";
        String htmlContent = buildPasswordResetEmailHtml(username, code);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendPasswordChangedNotification(String to, String username, String ip) {
        String subject = "【LinkX】密码已成功修改";
        String htmlContent = buildPasswordChangedEmailHtml(username, ip);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendBindEmailCode(String to, String username, String code) {
        String subject = "【LinkX】绑定邮箱验证码";
        String htmlContent = buildBindEmailHtml(username, code);
        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * 发送 HTML 邮件
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        LinkxProperties.Mail mailConfig = linkxProperties.getMail();

        // 快速失败：未配置时直接抛错，避免发起无意义的 SMTP 连接
        if (mailConfig.getUsername() == null || mailConfig.getUsername().isEmpty()
                || mailConfig.getPassword() == null || mailConfig.getPassword().isEmpty()) {
            log.error("邮件发送失败：SMTP 账户未配置 username/password，请检查 linkx.mail.* 配置或 MAIL_PASSWORD 环境变量");
            throw new RuntimeException("邮件服务未配置，无法发送邮件");
        }
        if (mailConfig.getHost() == null || mailConfig.getHost().isEmpty()) {
            log.error("邮件发送失败：SMTP 服务器地址未配置");
            throw new RuntimeException("邮件服务未配置，无法发送邮件");
        }

        // 校验收件人邮箱合法性，提前拦截垃圾数据
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("收件人邮箱为空");
        }
        if (!to.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("收件人邮箱格式非法：" + to);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String from = mailConfig.getFrom();
            String fromName = mailConfig.getFromName();

            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", maskEmail(to), subject);
        } catch (MailAuthenticationException e) {
            // 认证失败（绝大多数是授权码错误）
            log.error("SMTP 认证失败: to={}, error={}", maskEmail(to), e.getMessage(), e);
            throw new RuntimeException("邮件认证失败，请联系管理员检查 QQ 邮箱授权码", e);
        } catch (MailParseException e) {
            // 收件人/发件人邮箱格式错误
            log.error("邮件地址解析失败: to={}, error={}", maskEmail(to), e.getMessage(), e);
            throw new RuntimeException("收件人邮箱格式无效", e);
        } catch (MailSendException e) {
            // 发送失败（连接超时、550 收件人被拒等）
            log.error("SMTP 发送失败: to={}, error={}", maskEmail(to), e.getMessage(), e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}, error={}", maskEmail(to), subject, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        } catch (Exception e) {
            log.error("邮件发送异常: to={}, subject={}, error={}", maskEmail(to), subject, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        }
    }

    /**
     * 构建绑定邮箱验证码邮件 HTML
     */
    private String buildBindEmailHtml(String username, String code) {
        int expireMinutes = linkxProperties.getMail().getCodeExpireMinutes();
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><title>绑定邮箱验证码</title></head>
            <body style="margin:0;padding:24px;background:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2329;">
              <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:12px;padding:32px 28px;box-shadow:0 4px 20px rgba(15,23,42,0.06);">
                <div style="font-size:20px;font-weight:700;margin-bottom:8px;">绑定邮箱</div>
                <p style="margin:0 0 16px;color:#646a73;font-size:14px;">您好，%s！请使用以下验证码完成邮箱绑定：</p>
                <div style="font-size:32px;font-weight:700;letter-spacing:6px;color:#12b7f5;text-align:center;padding:16px 0;">%s</div>
                <p style="margin:16px 0 0;color:#8f959e;font-size:12px;">验证码 %d 分钟内有效。如非本人操作，请忽略本邮件。</p>
              </div>
            </body>
            </html>
            """.formatted(username, code, expireMinutes);
    }

    /**
     * 构建重置密码邮件 HTML（字节/腾讯大厂风格）
     * 设计要素：
     *  - 顶部 4px 品牌渐变条
     *  - 圆形 SVG Logo 占位 + 产品名+标语
     *  - Hero 渐变 banner + 大字号验证码
     *  - 信息卡片（步骤指引）
     *  - 安全提示框
     *  - 底部品牌区（链接+版权+小字声明）
     */
    private String buildPasswordResetEmailHtml(String username, String code) {
        int expireMinutes = linkxProperties.getMail().getCodeExpireMinutes();
        int currentYear = java.time.Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="x-apple-disable-message-reformatting">
                <title>重置您的 LinkX 密码</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;color:#1f2329;-webkit-font-smoothing:antialiased;">
                <!-- 顶部品牌渐变条 -->
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(90deg,#12b7f5 0%,#0d8ed9 100%);">
                    <tr><td style="height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr>
                </table>

                <!-- 主容器 -->
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6fa;padding:48px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;width:100%;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                                <!-- Header: Logo + Brand -->
                                <tr>
                                    <td style="padding:32px 40px 24px 40px;border-bottom:1px solid #f0f2f5;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td valign="middle" style="width:48px;">
                                                    <!-- 圆形 Logo 占位：蓝紫渐变 + 白色 L -->
                                                    <div style="width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#12b7f5 0%,#6366f1 100%);text-align:center;line-height:48px;color:#ffffff;font-size:24px;font-weight:700;font-family:-apple-system,BlinkMacSystemFont,sans-serif;">L</div>
                                                </td>
                                                <td valign="middle" style="padding-left:14px;">
                                                    <div style="font-size:18px;font-weight:600;color:#1f2329;line-height:1.3;">LinkX</div>
                                                    <div style="font-size:12px;color:#8f959e;line-height:1.4;margin-top:2px;">企业级即时通讯与协同平台</div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Hero Banner -->
                                <tr>
                                    <td style="padding:40px 40px 8px 40px;">
                                        <div style="font-size:24px;font-weight:600;color:#1f2329;line-height:1.4;margin:0 0 8px 0;">重置密码验证码</div>
                                        <div style="font-size:14px;color:#4e5969;line-height:1.7;margin:0;">
                                            您好 <strong style="color:#1f2329;">${USERNAME}</strong>，<br>
                                            您正在通过邮箱验证码方式重置 LinkX 账号的密码。请在验证码输入框中输入下方 6 位数字：
                                        </div>
                                    </td>
                                </tr>

                                <!-- 验证码卡片 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(135deg,#eef6ff 0%,#f5f3ff 100%);border:1px solid #e5edff;border-radius:12px;">
                                            <tr>
                                                <td align="center" style="padding:32px 24px;">
                                                    <div style="font-size:12px;color:#0d8ed9;font-weight:600;letter-spacing:1px;margin-bottom:12px;text-transform:uppercase;">VERIFICATION CODE</div>
                                                    <div style="font-family:'SF Mono',Monaco,Menlo,Consolas,monospace;font-size:40px;font-weight:700;color:#1f2329;letter-spacing:10px;line-height:1;">${CODE}</div>
                                                    <div style="margin-top:16px;font-size:13px;color:#4e5969;">
                                                        验证码有效期 <strong style="color:#0d8ed9;">${EXPIRE_MINUTES} 分钟</strong>，过期需重新获取
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- 操作步骤 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <div style="font-size:13px;color:#4e5969;line-height:1.6;margin:0 0 12px 0;font-weight:600;color:#1f2329;">接下来的操作</div>
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td valign="top" style="width:28px;padding-top:2px;">
                                                    <div style="width:20px;height:20px;border-radius:50%;background:#12b7f5;color:#ffffff;text-align:center;line-height:20px;font-size:12px;font-weight:600;">1</div>
                                                </td>
                                                <td valign="top" style="padding-bottom:8px;font-size:13px;color:#4e5969;line-height:1.6;">返回 LinkX 客户端的「找回密码」页面</td>
                                            </tr>
                                            <tr>
                                                <td valign="top" style="width:28px;padding-top:2px;">
                                                    <div style="width:20px;height:20px;border-radius:50%;background:#12b7f5;color:#ffffff;text-align:center;line-height:20px;font-size:12px;font-weight:600;">2</div>
                                                </td>
                                                <td valign="top" style="padding-bottom:8px;font-size:13px;color:#4e5969;line-height:1.6;">输入上方 6 位数字验证码</td>
                                            </tr>
                                            <tr>
                                                <td valign="top" style="width:28px;padding-top:2px;">
                                                    <div style="width:20px;height:20px;border-radius:50%;background:#12b7f5;color:#ffffff;text-align:center;line-height:20px;font-size:12px;font-weight:600;">3</div>
                                                </td>
                                                <td valign="top" style="font-size:13px;color:#4e5969;line-height:1.6;">设置并确认您的新密码</td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- 安全提示 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#fff8e6;border-left:3px solid #ffaa00;border-radius:6px;">
                                            <tr>
                                                <td style="padding:14px 16px;">
                                                    <div style="font-size:13px;color:#8a6500;line-height:1.6;margin:0;">
                                                        <strong style="color:#663c00;">安全提示</strong>：请勿将验证码告知任何人，LinkX 工作人员绝不会向您索要验证码。
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- 非本人操作 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <div style="font-size:12px;color:#86909c;line-height:1.7;margin:0;">
                                            如果您并未发起本次密码重置请求，请忽略本邮件，您的账号安全不会受到影响。
                                        </div>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="padding:32px 40px;background:#fafbfc;border-top:1px solid #f0f2f5;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td align="center" style="padding-bottom:12px;">
                                                    <span style="font-size:12px;color:#8f959e;">这是一封自动发送的系统邮件，请勿直接回复</span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center" style="padding-bottom:8px;">
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">帮助中心</a>
                                                    <span style="color:#e5e6eb;">·</span>
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">隐私政策</a>
                                                    <span style="color:#e5e6eb;">·</span>
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">联系我们</a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center">
                                                    <div style="font-size:11px;color:#c9cdd4;line-height:1.6;">
                                                        © ${YEAR} LinkX. All rights reserved.
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """
            .replace("${USERNAME}", escapeHtml(username))
            .replace("${CODE}", escapeHtml(code))
            .replace("${EXPIRE_MINUTES}", String.valueOf(expireMinutes))
            .replace("${YEAR}", String.valueOf(currentYear));
    }

    /**
     * 构建密码修改成功通知邮件 HTML（字节/腾讯大厂风格）
     */
    private String buildPasswordChangedEmailHtml(String username, String ip) {
        String changeTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
                java.time.LocalDateTime.now()
        );
        int currentYear = java.time.Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="x-apple-disable-message-reformatting">
                <title>您的 LinkX 密码已修改</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f4f6fa;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif;color:#1f2329;-webkit-font-smoothing:antialiased;">
                <!-- 顶部品牌渐变条 -->
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:linear-gradient(90deg,#12b7f5 0%,#0d8ed9 100%);">
                    <tr><td style="height:4px;line-height:4px;font-size:0;">&nbsp;</td></tr>
                </table>

                <!-- 主容器 -->
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6fa;padding:48px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;width:100%;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                                <!-- Header: Logo + Brand -->
                                <tr>
                                    <td style="padding:32px 40px 24px 40px;border-bottom:1px solid #f0f2f5;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td valign="middle" style="width:48px;">
                                                    <div style="width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#12b7f5 0%,#6366f1 100%);text-align:center;line-height:48px;color:#ffffff;font-size:24px;font-weight:700;font-family:-apple-system,BlinkMacSystemFont,sans-serif;">L</div>
                                                </td>
                                                <td valign="middle" style="padding-left:14px;">
                                                    <div style="font-size:18px;font-weight:600;color:#1f2329;line-height:1.3;">LinkX</div>
                                                    <div style="font-size:12px;color:#8f959e;line-height:1.4;margin-top:2px;">企业级即时通讯与协同平台</div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- Hero: 成功图标 + 标题 -->
                                <tr>
                                    <td style="padding:40px 40px 8px 40px;" align="center">
                                        <div style="width:64px;height:64px;border-radius:50%;background:linear-gradient(135deg,#d4edda 0%,#c3e6cb 100%);margin:0 auto 20px auto;text-align:center;line-height:64px;">
                                            <span style="display:inline-block;color:#28a745;font-size:32px;font-weight:700;line-height:64px;font-family:-apple-system,BlinkMacSystemFont,sans-serif;">&#10003;</span>
                                        </div>
                                        <div style="font-size:24px;font-weight:600;color:#1f2329;line-height:1.4;margin:0 0 8px 0;">密码修改成功</div>
                                        <div style="font-size:14px;color:#4e5969;line-height:1.7;margin:0;text-align:center;">
                                            您好 <strong style="color:#1f2329;">${USERNAME}</strong>，您的 LinkX 账号密码已成功修改。
                                        </div>
                                    </td>
                                </tr>

                                <!-- 详情卡片 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f7f8fa;border:1px solid #e5e6eb;border-radius:12px;">
                                            <tr>
                                                <td style="padding:8px 24px;">
                                                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                                        <tr>
                                                            <td style="padding:14px 0;border-bottom:1px solid #e5e6eb;font-size:13px;color:#8f959e;width:96px;">修改时间</td>
                                                            <td style="padding:14px 0;border-bottom:1px solid #e5e6eb;font-size:13px;color:#1f2329;font-family:'SF Mono',Monaco,Menlo,Consolas,monospace;">${CHANGE_TIME}</td>
                                                        </tr>
                                                        <tr>
                                                            <td style="padding:14px 0;font-size:13px;color:#8f959e;width:96px;">操作 IP</td>
                                                            <td style="padding:14px 0;font-size:13px;color:#1f2329;font-family:'SF Mono',Monaco,Menlo,Consolas,monospace;">${IP}</td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- 安全提醒 -->
                                <tr>
                                    <td style="padding:24px 40px 8px 40px;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#fff8e6;border-left:3px solid #ffaa00;border-radius:6px;">
                                            <tr>
                                                <td style="padding:14px 16px;">
                                                    <div style="font-size:13px;color:#8a6500;line-height:1.7;margin:0;">
                                                        <strong style="color:#663c00;">账号安全提醒</strong><br>
                                                        如果您并未执行此操作，您的账号可能存在安全风险。请立即通过「找回密码」重置密码，并检查账号异常活动。
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>

                                <!-- 紧急按钮 -->
                                <tr>
                                    <td align="center" style="padding:24px 40px 8px 40px;">
                                        <a href="#" style="display:inline-block;padding:12px 28px;background-color:#0d8ed9;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;border-radius:8px;">查看账号活动</a>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="padding:32px 40px;background:#fafbfc;border-top:1px solid #f0f2f5;">
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                            <tr>
                                                <td align="center" style="padding-bottom:12px;">
                                                    <span style="font-size:12px;color:#8f959e;">这是一封自动发送的系统邮件，请勿直接回复</span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center" style="padding-bottom:8px;">
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">帮助中心</a>
                                                    <span style="color:#e5e6eb;">·</span>
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">隐私政策</a>
                                                    <span style="color:#e5e6eb;">·</span>
                                                    <a href="#" style="display:inline-block;font-size:12px;color:#0d8ed9;text-decoration:none;margin:0 8px;">联系我们</a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td align="center">
                                                    <div style="font-size:11px;color:#c9cdd4;line-height:1.6;">
                                                        © ${YEAR} LinkX. All rights reserved.
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """
            .replace("${USERNAME}", escapeHtml(username))
            .replace("${CHANGE_TIME}", escapeHtml(changeTime))
            .replace("${IP}", escapeHtml(ip))
            .replace("${YEAR}", String.valueOf(currentYear));
    }

    /**
     * 简单的 HTML 转义，防止用户名/验证码/时间字符串中含特殊字符破坏 HTML 结构
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 脱敏邮箱地址
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        int len = localPart.length();
        if (len <= 2) {
            return localPart.charAt(0) + "***@" + parts[1];
        }
        return localPart.substring(0, 2) + "***@" + parts[1];
    }
}
