package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

/**
 * 始终转发到 {@link MailSenderHolder#get()}，以便 SMTP 热更新后立即生效。
 */
public class DelegatingJavaMailSender implements JavaMailSender {

    private final MailSenderHolder holder;

    public DelegatingJavaMailSender(MailSenderHolder holder) {
        this.holder = holder;
    }

    private JavaMailSender delegate() {
        return holder.get();
    }

    @Override
    public MimeMessage createMimeMessage() {
        return delegate().createMimeMessage();
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate().createMimeMessage(contentStream);
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        delegate().send(mimeMessage);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        delegate().send(mimeMessages);
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        delegate().send(mimeMessagePreparator);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        delegate().send(mimeMessagePreparators);
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        delegate().send(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        delegate().send(simpleMessages);
    }
}
