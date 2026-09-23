package service;

import model.Technician;
import model.Ticket;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AssignmentService {
    public Optional<Technician> findBestTechnician(Ticket ticket, List<Technician> technicians) {
        if (ticket == null || technicians == null) {
            return Optional.empty();
        }

        String category = ticket.getIssue().getCategory();

        return technicians.stream()
                .filter(Technician::isAvailable)
                .filter(t -> t.getSpecialization() != null)
                .sorted(Comparator
                        .comparing((Technician t) ->
                                !t.getSpecialization().equalsIgnoreCase(category))
                        .thenComparing(Technician::getName))
                .findFirst();
    }
}
