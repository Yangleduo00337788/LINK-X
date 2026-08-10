package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 可热更新的 JavaMailSender 持有者：管理端保存 SMTP 配置后调用 {@link #reload()}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailSenderHolder {

    private final LinkxProperties linkxProperties;
    private volatile JavaMailSenderImpl sender;

    @PostConstruct
    public void init() {
        reload();
    }

    public JavaMailSender get() {
        JavaMailSenderImpl current = sender;
        if (current == null) {
            synchronized (this) {
                if (sender == null) {
                    reload();
                }
                current = sender;
            }
        }
        return current;
    }

    public synchronized void reload() {
        LinkxProperties.Mail mail = linkxProperties.getMail();
        JavaMailSenderImpl next = new JavaMailSenderImpl();
        next.setHost(mail.getHost());
        next.setPort(mail.getPort());
        next.setUsername(mail.getUsername());
        next.setPassword(mail.getPassword());
        next.setDefaultEncoding("UTF-8");

        Properties props = next.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", 10000);
        props.put("mail.smtp.connectiontimeout", 10000);
        props.put("mail.smtp.writetimeout", 10000);

        // 587 = STARTTLS（先明文再升级）；465 = SSL 直连。混用会报
        // "Unsupported or unrecognized SSL message"。
        boolean useSsl = mail.isSsl();
        boolean useStartTls = mail.isStartTls();
        int port = mail.getPort();
        if (port == 587 && useSsl) {
            log.warn("Port 587 requires STARTTLS; ignoring SSL=true to avoid handshake failure");
            useSsl = false;
            useStartTls = true;
        } else if (port == 465 && useStartTls && !useSsl) {
            log.warn("Port 465 requires SSL; enabling SSL and disabling STARTTLS");
            useSsl = true;
            useStartTls = false;
        }

        if (useSsl) {
            // 465：连接即 TLS
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
            if (mail.getHost() != null && !mail.getHost().isBlank()) {
                props.put("mail.smtp.ssl.trust", mail.getHost());
            }
        } else if (useStartTls) {
            // 587：必须显式关闭 ssl.enable，否则会立刻做 SSL 握手
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
        }

        this.sender = next;
        log.info("JavaMailSender reloaded: host={}, port={}, startTls={}, ssl={}",
                mail.getHost(), port, useStartTls, useSsl);
    }
}
