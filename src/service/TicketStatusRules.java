package service;

import java.util.Map;
import java.util.Set;

public final class TicketStatusRules {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "ASSIGNED", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("RESOLVED"),
            "RESOLVED", Set.of("CLOSED")
    );

    private TicketStatusRules() {}

    public static boolean canTransition(String current, String next) {
        if (current == null || next == null) return false;
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }
}
