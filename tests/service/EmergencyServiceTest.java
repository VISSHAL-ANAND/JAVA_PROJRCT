package service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EmergencyServiceTest {
    private final EmergencyService service = new EmergencyService();

    @Test
    void rejectsTitleLongerThanDatabaseColumnLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createEmergency(1, "T".repeat(201), "Safety message"));
    }

    @Test
    void rejectsMessageLongerThanConfiguredBroadcastLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createEmergency(1, "Safety alert", "M".repeat(3001)));
    }

    @Test
    void rejectsMissingEmergencyDetailsBeforeOpeningDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createEmergency(0, "Safety alert", "Safety message"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createEmergency(1, " ", "Safety message"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createEmergency(1, "Safety alert", " "));
    }
}
