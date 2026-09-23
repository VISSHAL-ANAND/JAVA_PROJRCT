package model;

import java.time.LocalDateTime;

public class Notification {
    private final int id;
    private final int userId;
    private final String message;
    private boolean read;
    private final LocalDateTime createdAt;

    public Notification(int id, int userId, String message) {
        this.id = id;
        this.userId = userId;
        this.message = message;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void markAsRead() { this.read = true; }
}
