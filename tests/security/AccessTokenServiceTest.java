package security;

import model.Student;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AccessTokenServiceTest {
    @Test
    void issuesAndVerifiesTokenForUser() {
        AccessTokenService service = new AccessTokenService();
        String token = service.issue(new Student(42, "Test Student", "student@example.test", "", "TEST42", "CSE"));

        AccessTokenService.Principal principal = service.verify(token);

        assertNotNull(principal);
        assertEquals(42, principal.userId());
        assertEquals("STUDENT", principal.role());
        assertTrue(principal.expiresAt() > System.currentTimeMillis() / 1000);
    }

    @Test
    void rejectsModifiedAndMalformedTokens() {
        AccessTokenService service = new AccessTokenService();
        String token = service.issue(new Student(7, "Test", "test@example.test", "", "T7", "CSE"));
        String modified = (token.startsWith("A") ? "B" : "A") + token.substring(1);

        assertNull(service.verify(modified));
        assertNull(service.verify("not-a-token"));
        assertNull(service.verify(null));
    }

    @Test
    void rejectsTokensWhenDatabaseRoleChangesOrAccountIsMissing() {
        AccessTokenService service = new AccessTokenService();
        String token = service.issue(new Student(42, "Test Student", "student@example.test", "", "TEST42", "CSE"));
        AccessTokenService.Principal principal = service.verify(token);

        assertNotNull(principal);
        assertTrue(service.hasCurrentRole(principal, "STUDENT"));
        assertFalse(service.hasCurrentRole(principal, "ADMIN"));
        assertFalse(service.hasCurrentRole(principal, null));
        assertFalse(service.hasCurrentRole(null, "STUDENT"));
    }

    @Test
    void productionRequiresConfiguredSecret() {
        assertThrows(IllegalStateException.class, () -> AccessTokenService.resolveSecret(null, true));
        assertThrows(IllegalStateException.class, () -> AccessTokenService.resolveSecret("too-short", true));
    }

    @Test
    void acceptsStableProductionSecretAndAllowsLocalFallback() {
        String configured = "campusos-test-secret-with-at-least-32-characters";
        assertArrayEquals(
                configured.getBytes(StandardCharsets.UTF_8),
                AccessTokenService.resolveSecret(configured, true)
        );
        assertEquals(32, AccessTokenService.resolveSecret(null, false).length);
    }
}
