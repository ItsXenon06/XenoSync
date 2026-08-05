package com.xenosync.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String appBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.base-url}") String appBaseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Sent after email/password registration.
     * Raw token is embedded in the link; never stored, never logged here.
     */
    public void sendVerificationEmail(String toEmail, String rawToken) {
        String link = appBaseUrl + "/verify?token=" + rawToken;
        String body = """
                Welcome to XenoSync!

                Click the link below to verify your email address.
                This link expires in 24 hours and can only be used once.

                %s

                If you did not create a XenoSync account, you can safely ignore this email.
                """.formatted(link);

        send(toEmail, "Verify your XenoSync email", body);
    }

    /**
     * Sent when someone registers with an email that belongs to a GitHub-only account.
     * The link, on click, attaches the submitted password to the existing account.
     * Copy is intentionally neutral — does not reveal whether a GitHub account exists.
     */
    public void sendAttachPasswordEmail(String toEmail, String rawToken) {
        String link = appBaseUrl + "/attach-password?token=" + rawToken;
        String body = """
                Hi,

                We received a request to add password login to your XenoSync account.

                Click the link below to confirm and enable password login.
                This link expires in 24 hours and can only be used once.

                %s

                If you did not make this request, you can safely ignore this email.
                Your account has not been changed.
                """.formatted(link);

        send(toEmail, "Add password login to your XenoSync account", body);
    }

    /**
     * Sent on forgot-password request.
     * Same enumeration note as above — caller always sends the same response to the
     * user regardless of whether this method is actually called.
     */
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String link = appBaseUrl + "/reset-password?token=" + rawToken;
        String body = """
                Hi,

                We received a request to reset your XenoSync password.

                Click the link below to choose a new password.
                This link expires in 1 hour and can only be used once.

                %s

                If you did not request a password reset, you can safely ignore this email.
                Your password has not been changed.
                """.formatted(link);

        send(toEmail, "Reset your XenoSync password", body);
    }

    /**
     * Internal send — all mail flows through here.
     * MailException is a runtime exception; we let it propagate so callers
     * (AuthService etc.) can decide whether to surface it or swallow it.
     * Raw token values must never be passed into log statements — callers are
     * responsible for not logging what they pass here.
     */
    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message); // throws MailException on failure
    }
}