package com.linkx.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件发送配置：暴露委托给 {@link MailSenderHolder} 的 JavaMailSender。
 */
@Configuration
@RequiredArgsConstructor
public class MailConfig {

    private final MailSenderHolder mailSenderHolder;

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return new DelegatingJavaMailSender(mailSenderHolder);
    }
}
