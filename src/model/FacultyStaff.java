package model;

public class FacultyStaff extends User {
    private String department;
    public FacultyStaff(int id, String name, String email, String password, String department) {
        super(id, name, email, password); this.department = department;
    }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    @Override public String getRole() { return Role.FACULTY.name(); }
}
