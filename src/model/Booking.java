package model;

import java.time.LocalDateTime;

public class Booking {
    private final int id;
    private final int resourceId;
    private final int bookedBy;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public Booking(int id, int resourceId, int bookedBy,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.resourceId = resourceId;
        this.bookedBy = bookedBy;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getId() { return id; }
    public int getResourceId() { return resourceId; }
    public int getBookedBy() { return bookedBy; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}
