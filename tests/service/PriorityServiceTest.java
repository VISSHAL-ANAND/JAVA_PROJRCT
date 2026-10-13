package service;

import model.Issue;
import model.Priority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriorityServiceTest {
    private final PriorityService service = new PriorityService();

    @Test
    void classifiesCriticalSafetyIssues() {
        assertEquals(Priority.CRITICAL, service.calculatePriority(issue("Fire alarm triggered", "Smoke reported")));
        assertEquals(Priority.CRITICAL, service.calculatePriority(issue("Power failure", "Possible electric shock")));
    }

    @Test
    void classifiesHighPriorityOperationalIssues() {
        assertEquals(Priority.HIGH, service.calculatePriority(issue("Network down", "Entire block is offline")));
        assertEquals(Priority.HIGH, service.calculatePriority(issue("Leak", "Water leak in corridor")));
    }

    @Test
    void classifiesMediumPriorityIssues() {
        assertEquals(Priority.MEDIUM, service.calculatePriority(issue("Projector broken", "Lecture room equipment")));
        assertEquals(Priority.MEDIUM, service.calculatePriority(issue("Urgent repair", "Door is not working")));
    }

    @Test
    void defaultsUnmatchedIssuesToLowPriority() {
        assertEquals(Priority.LOW, service.calculatePriority(issue("Need a new chair", "Room 204")));
    }

    @Test
    void criticalKeywordsTakePrecedenceOverLowerPriorityKeywords() {
        assertEquals(Priority.CRITICAL, service.calculatePriority(issue("Broken equipment", "There is a gas leak")));
    }

    @Test
    void rejectsNullIssue() {
        assertThrows(IllegalArgumentException.class, () -> service.calculatePriority(null));
    }

    private Issue issue(String title, String description) {
        return new Issue(0, title, description, "General", null, null, null);
    }
}
