package test;

import model.Issue;
import model.Priority;
import model.Student;
import model.Technician;
import model.Ticket;
import service.AssignmentService;
import service.PriorityService;
import service.TicketService;

import java.util.List;

public class Phase8SmokeTest {
    public static void main(String[] args) throws Exception {
        Student student = new Student(1, "Student", "student@campusos.com", "pass", "REG001", "CSE");
        Technician technician = new Technician(2, "Network Tech", "tech@campusos.com", "pass", "NETWORK", true);
        Issue issue = new Issue(1, "Network down", "Campus WiFi is down", "NETWORK", null);

        TicketService service = new TicketService(new PriorityService(), new AssignmentService());
        Ticket ticket = service.createTicket(1, issue, student, List.of(technician));

        if (issue.getPriority() != Priority.HIGH) throw new AssertionError("Priority calculation failed");
        if (ticket.getAssignedTechnicianId() == null ||
                ticket.getAssignedTechnicianId() != technician.getId()) {
            throw new AssertionError("Technician assignment failed");
        }
        if (ticket.getStatus() != Ticket.Status.ASSIGNED) {
            throw new AssertionError("Ticket status should be ASSIGNED");
        }

        System.out.println("PHASE_8_SMOKE_TEST_PASSED");
    }
}
