package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 邮件发送配置：
 * 由于使用 LinkxProperties.Mail（而非 Spring 默认的 spring.mail.*），
 * 需要显式定义 JavaMailSender Bean 让 Spring 自动装配找到依赖。
 * <p>
 * 注意：QQ 邮箱常用两种配置：
 * <ul>
 *   <li>587 端口 + STARTTLS（推荐）</li>
 *   <li>465 端口 + SSL</li>
 * </ul>
 * 两者互斥，不要同时开启。
 */
@Configuration
@RequiredArgsConstructor
public class MailConfig {

    private final LinkxProperties linkxProperties;

    /**
     * 暴露 JavaMailSender Bean，Spring 会优先使用这里返回的实现。
     * 使用 @Primary 避免与自动配置的派生 Bean 冲突。
     */
    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        LinkxProperties.Mail mailConfig = linkxProperties.getMail();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailConfig.getHost());
        sender.setPort(mailConfig.getPort());
        sender.setUsername(mailConfig.getUsername());
        sender.setPassword(mailConfig.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", true);
        // 超时设置，避免 SMTP 服务器无响应时阻塞业务
        props.put("mail.smtp.timeout", 10000);
        props.put("mail.smtp.connectiontimeout", 10000);
        props.put("mail.smtp.writetimeout", 10000);

        if (mailConfig.isSsl()) {
            // 465 端口走 SSL
            props.put("mail.smtp.ssl.enable", true);
            // QQ 邮箱 SSL 也建议开启 STARTTLS
            props.put("mail.smtp.starttls.enable", true);
            props.put("mail.smtp.starttls.required", true);
            // 信任 QQ 邮箱的证书
            props.put("mail.smtp.ssl.trust", mailConfig.getHost());
        } else if (mailConfig.isStartTls()) {
            // 587 端口走 STARTTLS（QQ 邮箱默认推荐）
            props.put("mail.smtp.starttls.enable", true);
            props.put("mail.smtp.starttls.required", true);
        } else {
            // 明文连接（生产环境不推荐）
            props.put("mail.smtp.starttls.enable", false);
        }

        return sender;
    }
}
