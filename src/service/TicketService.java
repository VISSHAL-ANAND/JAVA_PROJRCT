package service;

import exception.TicketException;
import model.*;
import java.util.List;

public class TicketService {
    private final PriorityService priorityService;
    private final AssignmentService assignmentService;
    public TicketService(PriorityService priorityService,AssignmentService assignmentService){this.priorityService=priorityService;this.assignmentService=assignmentService;}

    public Ticket createTicket(int ticketId,Issue issue,Student reporter,List<Technician> technicians)throws TicketException{
        if(issue==null||reporter==null)throw new TicketException("Issue and reporter are required");
        issue.setPriority(priorityService.calculatePriority(issue));
        Ticket ticket=new Ticket(ticketId,issue,reporter.getId());
        assignmentService.findBestTechnician(ticket,technicians).ifPresent(t->ticket.assignTechnician(t.getId()));
        return ticket;
    }

    public void assignTicket(Ticket ticket,List<Technician> technicians)throws TicketException{
        if(ticket==null)throw new TicketException("Ticket is required");
        assignmentService.findBestTechnician(ticket,technicians).ifPresentOrElse(
            t->ticket.assignTechnician(t.getId()),
            ()->{throw new IllegalStateException("No available technician found");});
    }
}
