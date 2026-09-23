package model;

public class MaintenanceStaff extends User {
    private String specialization;
    private boolean available;

    public MaintenanceStaff(int id, String name, String email, String password,
                            String specialization, boolean available) {
        super(id, name, email, password);
        this.specialization = specialization;
        this.available = available;
    }

    public String getSpecialization() { return specialization; }
    public boolean isAvailable() { return available; }

    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public void setAvailable(boolean available) { this.available = available; }

    @Override
    public String getRole() { return Role.MAINTENANCE.name(); }
}
