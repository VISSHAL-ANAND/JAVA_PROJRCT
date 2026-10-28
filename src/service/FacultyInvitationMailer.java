package service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class FacultyInvitationMailer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private FacultyInvitationMailer() {}

    public static void send(String recipient, String name, String rawToken) throws Exception {
        String apiKey = required("RESEND_API_KEY");
        String from = env("RESEND_FROM_EMAIL", "onboarding@resend.dev");
        String frontend = env("CAMPUSOS_FRONTEND_URL",
                "https://campusos-frontend-nine.vercel.app").replaceAll("/+$", "");
        String link = frontend + "/?facultyInvite="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        String safeName = escapeHtml(name);
        String safeLink = escapeHtml(link);
        String html = "<p>Hello " + safeName + ",</p>"
                + "<p>The HOD has invited you to CAMPUSOS. Use the secure link below within "
                + "72 hours to create your faculty password.</p>"
                + "<p><a href=\"" + safeLink + "\">Accept faculty invitation</a></p>"
                + "<p>If you were not expecting this invitation, you can ignore this email.</p>";
        String text = "Hello " + name + ",\n\nThe HOD has invited you to CAMPUSOS. "
                + "Use the secure link below within 72 hours to create your faculty password.\n\n"
                + link + "\n\nIf you were not expecting this invitation, you can ignore this email.";

        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(recipient),
                "subject", "CAMPUSOS faculty account invitation",
                "html", html,
                "text", text
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Do not log the API key or the provider's potentially sensitive response body.
            throw new IllegalStateException("Resend email API returned HTTP " + response.statusCode());
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Email service is not configured");
        }
        return value.trim();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
