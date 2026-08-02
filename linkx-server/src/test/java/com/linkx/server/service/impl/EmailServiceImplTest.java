package com.linkx.server.service.impl;

import com.linkx.server.config.LinkxProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl 单元测试")
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private LinkxProperties props;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        LinkxProperties.Mail mail = props.getMail();
        mail.setHost("smtp.test.local");
        mail.setPort(587);
        mail.setUsername("bot@test.local");
        mail.setPassword("secret");
        mail.setFrom("bot@test.local");
        mail.setFromName("LinkX");
        mail.setCodeExpireMinutes(10);
        emailService = new EmailServiceImpl(mailSender, props);
    }

    private void stubSender() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("init 已配置应不抛异常")
    void init_configured_ok() {
        assertDoesNotThrow(() -> emailService.init());
    }

    @Test
    @DisplayName("init 未配置密码应仅告警")
    void init_missingPassword_warns() {
        props.getMail().setPassword("");
        assertDoesNotThrow(() -> emailService.init());
    }

    @Test
    @DisplayName("sendRegisterCode 应调用 mailSender.send")
    void sendRegisterCode_sends() {
        stubSender();
        emailService.sendRegisterCode("u@test.com", "alice", "123456");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetCode 应发送")
    void sendPasswordResetCode_sends() {
        stubSender();
        emailService.sendPasswordResetCode("u@test.com", "alice", "654321");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendWelcomeEmail 应发送")
    void sendWelcomeEmail_sends() {
        stubSender();
        emailService.sendWelcomeEmail("u@test.com", "alice", "昵称");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendWelcomeEmail 空昵称回退用户名")
    void sendWelcomeEmail_blankNickname() {
        stubSender();
        emailService.sendWelcomeEmail("u@test.com", "alice", "  ");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPasswordChangedNotification 应发送")
    void sendPasswordChanged_sends() {
        stubSender();
        emailService.sendPasswordChangedNotification("u@test.com", "alice", "127.0.0.1");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendBindEmailCode 应发送")
    void sendBindEmailCode_sends() {
        stubSender();
        emailService.sendBindEmailCode("u@test.com", "alice", "111222");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendAdminStepUpCode 应发送")
    void sendAdminStepUpCode_sends() {
        stubSender();
        emailService.sendAdminStepUpCode("u@test.com", "admin", "999888");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("自定义 HTML 模板应渲染占位符")
    void customHtmlTemplate_renders() {
        stubSender();
        props.getMailTemplates().setRegisterSubject("Reg ${USERNAME}");
        props.getMailTemplates().setRegisterHtml("<html><body><p>Hi ${USERNAME} code=${CODE}</p></body></html>");
        emailService.sendRegisterCode("u@test.com", "alice", "123456");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("未配置 SMTP 应快速失败")
    void missingSmtp_throws() {
        props.getMail().setUsername("");
        assertThrows(RuntimeException.class,
                () -> emailService.sendRegisterCode("u@test.com", "a", "1"));
    }

    @Test
    @DisplayName("空 host 应失败")
    void missingHost_throws() {
        props.getMail().setHost("");
        assertThrows(RuntimeException.class,
                () -> emailService.sendRegisterCode("u@test.com", "a", "1"));
    }

    @Test
    @DisplayName("非法收件人应失败")
    void invalidRecipient_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> emailService.sendRegisterCode("not-an-email", "a", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> emailService.sendRegisterCode(" ", "a", "1"));
    }

    @Test
    @DisplayName("SMTP 认证失败应包装异常")
    void authFailure_wraps() {
        stubSender();
        doThrow(new MailAuthenticationException("bad")).when(mailSender).send(any(MimeMessage.class));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendRegisterCode("u@test.com", "a", "1"));
        assertTrue(ex.getMessage().contains("认证"));
    }

    @Test
    @DisplayName("SMTP 发送失败应包装异常")
    void sendFailure_wraps() {
        stubSender();
        doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendRegisterCode("u@test.com", "a", "1"));
        assertTrue(ex.getMessage().contains("发送失败"));
    }
}
