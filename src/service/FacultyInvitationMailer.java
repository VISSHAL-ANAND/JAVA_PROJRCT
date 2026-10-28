package service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class FacultyInvitationMailer {
    private FacultyInvitationMailer() {}

    public static void send(String recipient, String name, String rawToken) throws Exception {
        String host = required("CAMPUSOS_SMTP_HOST");
        String username = required("CAMPUSOS_SMTP_USERNAME");
        String password = required("CAMPUSOS_SMTP_PASSWORD");
        String from = env("CAMPUSOS_SMTP_FROM", username);
        String port = env("CAMPUSOS_SMTP_PORT", "587");
        String frontend = env("CAMPUSOS_FRONTEND_URL", "https://campusos-frontend-nine.vercel.app").replaceAll("/+$", "");
        String link = frontend + "/?facultyInvite=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false));
        message.setSubject("CAMPUSOS faculty account invitation", "UTF-8");
        message.setText("Hello " + name + ",\n\nThe HOD has invited you to CAMPUSOS. "
                + "Use the secure link below within 72 hours to create your faculty password.\n\n"
                + link + "\n\nIf you were not expecting this invitation, you can ignore this email.",
                "UTF-8");
        Transport.send(message);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Email service is not configured");
        return value.trim();
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
