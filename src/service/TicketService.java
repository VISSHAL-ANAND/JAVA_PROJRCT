package service;

import exception.TicketException;
import model.Issue;
import model.Priority;
import model.Student;
import model.Technician;
import model.Ticket;

import java.util.List;

public class TicketService {
    private final PriorityService priorityService;
    private final AssignmentService assignmentService;

    public TicketService(PriorityService priorityService, AssignmentService assignmentService) {
        this.priorityService = priorityService;
        this.assignmentService = assignmentService;
    }

    public Ticket createTicket(int ticketId, Issue issue, Student reporter,
                               List<Technician> technicians) throws TicketException {
        if (issue == null || reporter == null) {
            throw new TicketException("Issue and reporter are required");
        }

        Priority priority = priorityService.calculatePriority(issue);
        issue.setPriority(priority);

        Ticket ticket = new Ticket(ticketId, issue, reporter);
        assignmentService.findBestTechnician(ticket, technicians)
                .ifPresent(ticket::assignTechnician);

        return ticket;
    }

    public void assignTicket(Ticket ticket, List<Technician> technicians) throws TicketException {
        if (ticket == null) {
            throw new TicketException("Ticket is required");
        }

        assignmentService.findBestTechnician(ticket, technicians)
                .ifPresentOrElse(
                        ticket::assignTechnician,
                        () -> {
                            throw new RuntimeException("No available technician found");
                        });
    }
}
