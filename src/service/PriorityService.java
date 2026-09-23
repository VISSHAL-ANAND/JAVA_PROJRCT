package service;

import model.Issue;
import model.Priority;

public class PriorityService {
    public Priority calculatePriority(Issue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("Issue is required");
        }

        if (issue.getPriority() != null) {
            return issue.getPriority();
        }

        String text = (issue.getTitle() + " " + issue.getDescription()).toLowerCase();

        if (containsAny(text, "fire", "gas leak", "electric shock", "security breach")) {
            return Priority.CRITICAL;
        }
        if (containsAny(text, "server down", "network down", "water leak", "power failure")) {
            return Priority.HIGH;
        }
        if (containsAny(text, "not working", "broken", "urgent")) {
            return Priority.MEDIUM;
        }
        return Priority.LOW;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
