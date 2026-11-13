package com.tirocinioaltrama.slmorchestrator.impl.email;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.tirocinioaltrama.slmorchestrator.core.email.EmailMessage;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailSender;
import com.tirocinioaltrama.slmorchestrator.core.email.EmailSendingException;

/**
 * Adapter SMTP. Il server concreto è fornito dalla configurazione Spring Mail.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(message.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            throw new EmailSendingException("Invio SMTP fallito", e);
        }
    }
}
