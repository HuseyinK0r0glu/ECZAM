package com.eczam.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional emails asynchronously.
 * If SMTP is not configured the mailer bean is absent from the context and
 * all methods silently skip (graceful degradation for dev environments).
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailer;
    private final String fromAddress;
    private final String appName;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailer,
                        @Value("${spring.mail.username:noreply@eczam.app}") String fromAddress,
                        @Value("${eczam.app.name:ECZAM}") String appName,
                        @Value("${eczam.cors.allowed-origin:http://localhost:5173}") String frontendUrl) {
        this.mailer = mailer;
        this.fromAddress = fromAddress;
        this.appName = appName;
        this.frontendUrl = frontendUrl;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Async("taskExecutor")
    public void sendVerificationEmail(String toEmail, String displayName, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(toEmail,
             appName + " – Verify your email",
             buildVerificationHtml(displayName, link));
    }

    @Async("taskExecutor")
    public void sendPasswordResetEmail(String toEmail, String displayName, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(toEmail,
             appName + " – Password reset",
             buildPasswordResetHtml(displayName, link));
    }

    @Async("taskExecutor")
    public void sendWelcomeEmail(String toEmail, String displayName) {
        send(toEmail,
             "Welcome to " + appName + "!",
             buildWelcomeHtml(displayName));
    }

    @Async("taskExecutor")
    public void sendAccountDeletionConfirmation(String toEmail, String displayName) {
        send(toEmail,
             appName + " – Account deleted",
             buildDeletionHtml(displayName));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void send(String to, String subject, String html) {
        if (mailer == null) {
            log.debug("Mail not configured — skipping: {}", subject);
            return;
        }
        try {
            var msg = mailer.createMimeMessage();
            var helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailer.send(msg);
            log.debug("Email sent to={} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("Failed to send email to={} subject={}: {}", to, subject, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // HTML templates (inline — no template engine dependency at MVP)
    // -------------------------------------------------------------------------

    private String buildVerificationHtml(String name, String link) {
        return wrap("Verify your email",
            "<p>Hi " + esc(name) + ",</p>" +
            "<p>Thanks for registering with " + appName + ". " +
            "Please verify your email address by clicking the button below.</p>" +
            button("Verify Email", link) +
            "<p>This link expires in 24 hours. If you did not create an account, ignore this email.</p>");
    }

    private String buildPasswordResetHtml(String name, String link) {
        return wrap("Reset your password",
            "<p>Hi " + esc(name) + ",</p>" +
            "<p>We received a request to reset the password for your " + appName + " account.</p>" +
            button("Reset Password", link) +
            "<p>This link expires in 30 minutes. If you did not request a password reset, " +
            "you can safely ignore this email.</p>");
    }

    private String buildWelcomeHtml(String name) {
        return wrap("Welcome to " + appName,
            "<p>Hi " + esc(name) + ",</p>" +
            "<p>Your account is all set. " +
            "You can now manage your medications safely and get reminders when it's time to take them.</p>" +
            button("Go to " + appName, frontendUrl) +
            "<p>Take care,<br/>The " + appName + " team</p>");
    }

    private String buildDeletionHtml(String name) {
        return wrap("Account deleted",
            "<p>Hi " + esc(name) + ",</p>" +
            "<p>Your " + appName + " account and all associated data have been permanently deleted.</p>" +
            "<p>This action cannot be undone. If this was a mistake or you have any questions, " +
            "please contact our support team.</p>");
    }

    private String wrap(String title, String body) {
        return "<!doctype html><html><head><meta charset='utf-8'>" +
               "<style>body{font-family:sans-serif;color:#333;max-width:600px;margin:40px auto;padding:0 20px}" +
               "h1{font-size:22px;color:#2c7a4b}.btn{display:inline-block;margin:20px 0;padding:12px 24px;" +
               "background:#2c7a4b;color:#fff;text-decoration:none;border-radius:6px;font-weight:bold}" +
               ".footer{font-size:12px;color:#999;margin-top:40px;border-top:1px solid #eee;padding-top:16px}</style>" +
               "</head><body>" +
               "<h1>" + appName + "</h1>" +
               "<h2>" + title + "</h2>" +
               body +
               "<div class='footer'>This is an automated message from " + appName + ". Please do not reply.</div>" +
               "</body></html>";
    }

    private static String button(String label, String url) {
        return "<a class='btn' href='" + url + "'>" + label + "</a>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
