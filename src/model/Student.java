package model;

public class Student extends User {
    private String registerNumber;
    private String department;

    public Student(int id, String name, String email, String password,
                   String registerNumber, String department) {
        super(id, name, email, password);
        this.registerNumber = registerNumber;
        this.department = department;
    }

    public String getRegisterNumber() { return registerNumber; }
    public String getDepartment() { return department; }

    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }
    public void setDepartment(String department) { this.department = department; }

    @Override
    public String getRole() { return "STUDENT"; }
}
