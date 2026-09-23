package model;

import java.time.LocalDateTime;

public class Emergency {
    private final int id;
    private final String title;
    private final String message;
    private final int createdBy;
    private final LocalDateTime createdAt;

    public Emergency(int id, String title, String message, int createdBy) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public int getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
