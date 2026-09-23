package model;

import java.time.LocalDateTime;

public class Ticket {
    public enum Status { OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED }

    private final int id;
    private final Issue issue;
    private final int reportedBy;
    private Integer assignedTechnicianId;
    private Status status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Ticket(int id, Issue issue, int reportedBy) {
        this.id = id;
        this.issue = issue;
        this.reportedBy = reportedBy;
        this.status = Status.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public int getId() { return id; }
    public Issue getIssue() { return issue; }
    public int getReportedBy() { return reportedBy; }
    public Integer getAssignedTechnicianId() { return assignedTechnicianId; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void assignTechnician(int technicianId) {
        this.assignedTechnicianId = technicianId;
        this.status = Status.ASSIGNED;
        this.updatedAt = LocalDateTime.now();
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
