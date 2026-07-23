package com.linkx.server.support;

import com.linkx.server.config.LinkxProperties;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Properties;

/**
 * 邮件诊断测试：直接使用 JavaMail 原生 API 发送，绕过 Spring 包装。
 * 用法：mvn test -Dtest=MailDiagnosticsTest -Dsurefire.failIfNoSpecifiedTests=false
 * <p>
 * 默认禁用：会连接真实 SMTP，不得进入 CI / 覆盖率套件。
 */
@Slf4j
@Disabled("真实 SMTP 诊断，仅人工排查时手动启用")
@SpringBootTest
@ActiveProfiles("local")
class MailDiagnosticsTest {

    @Autowired
    private LinkxProperties linkxProperties;

    @Test
    void diagnoseSmtpConnection() {
        LinkxProperties.Mail m = linkxProperties.getMail();
        log.info("============ 邮件配置 ============");
        log.info("host       = {}", m.getHost());
        log.info("port       = {}", m.getPort());
        log.info("username   = {}", m.getUsername());
        log.info("password   = {}", m.getPassword() == null ? "<空>" : "长度=" + m.getPassword().length() + ", 前4位=" + m.getPassword().substring(0, Math.min(4, m.getPassword().length())));
        log.info("from       = {}", m.getFrom());
        log.info("fromName   = {}", m.getFromName());
        log.info("startTls   = {}", m.isStartTls());
        log.info("ssl        = {}", m.isSsl());
        log.info("============ 结束 ============");

        // 启用 JavaMail 调试日志
        Properties props = new Properties();
        props.put("mail.smtp.host", m.getHost());
        props.put("mail.smtp.port", m.getPort());
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", m.isStartTls());
        props.put("mail.smtp.starttls.required", m.isStartTls());
        if (m.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.debug", "true");
        props.put("mail.debug.auth", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(m.getUsername(), m.getPassword());
            }
        });
        session.setDebug(true);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(m.getFrom(), m.getFromName()));
            msg.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(m.getUsername()));
            msg.setSubject("LinkX 诊断测试");
            msg.setText("如果收到这封邮件，说明 SMTP 配置正确。", "UTF-8");
            Transport.send(msg);
            log.info("============ 发送成功 ============");
        } catch (Exception e) {
            log.error("============ 发送失败 ============", e);
            System.out.println("===== 完整错误 =====");
            e.printStackTrace(System.out);
        }
    }
}