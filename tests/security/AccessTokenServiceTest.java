package security;

import model.Student;
import org.junit.jupiter.api.Test;

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
}
