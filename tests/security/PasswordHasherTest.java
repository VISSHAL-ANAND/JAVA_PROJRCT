package security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test
    void hashesVerifyOnlyTheOriginalPasswordAndUseFreshSalts() {
        String first = PasswordHasher.hash("CampusOS-Strong!Password-2026");
        String second = PasswordHasher.hash("CampusOS-Strong!Password-2026");

        assertNotEquals(first, second, "Random salts should make equal passwords hash differently");
        assertTrue(PasswordHasher.verify("CampusOS-Strong!Password-2026", first));
        assertTrue(PasswordHasher.verify("CampusOS-Strong!Password-2026", second));
        assertFalse(PasswordHasher.verify("wrong-password", first));
    }

    @Test
    void rejectsMalformedAndUnboundedStoredHashesWithoutExpensiveDerivation() {
        assertFalse(PasswordHasher.verify("password", null));
        assertFalse(PasswordHasher.verify("password", "not-a-hash"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2$999999999$YWJj$YWJj"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2$1$YWJj$YWJj"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2$120000$%%%$YWJj"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2$120000$YWJj$YWJj"));
    }

    @Test
    void rejectsBlankPasswordsWhenCreatingHash() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("   "));
    }
}
