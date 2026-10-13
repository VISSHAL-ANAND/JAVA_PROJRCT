package service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketStatusRulesTest {
    @Test
    void permitsOnlyForwardLifecycleTransitions() {
        assertTrue(TicketStatusRules.canTransition("ASSIGNED", "IN_PROGRESS"));
        assertTrue(TicketStatusRules.canTransition("IN_PROGRESS", "RESOLVED"));
        assertTrue(TicketStatusRules.canTransition("RESOLVED", "CLOSED"));
    }

    @Test
    void rejectsSkippedBackwardAndUnknownTransitions() {
        assertFalse(TicketStatusRules.canTransition("OPEN", "RESOLVED"));
        assertFalse(TicketStatusRules.canTransition("ASSIGNED", "CLOSED"));
        assertFalse(TicketStatusRules.canTransition("CLOSED", "IN_PROGRESS"));
        assertFalse(TicketStatusRules.canTransition(null, "CLOSED"));
        assertFalse(TicketStatusRules.canTransition("OPEN", null));
    }
}
