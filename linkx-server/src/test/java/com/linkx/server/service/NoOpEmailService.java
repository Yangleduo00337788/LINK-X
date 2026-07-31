package com.linkx.server.service;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 测试环境不真正发信，避免依赖本地 SMTP。
 */
@Service
@Primary
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public void sendRegisterCode(String to, String username, String code) {
        // no-op
    }

    @Override
    public void sendPasswordResetCode(String to, String username, String code) {
        // no-op
    }

    @Override
    public void sendWelcomeEmail(String to, String username, String nickname) {
        // no-op
    }

    @Override
    public void sendPasswordChangedNotification(String to, String username, String ip) {
        // no-op
    }

    @Override
    public void sendBindEmailCode(String to, String username, String code) {
        // no-op
    }
}
