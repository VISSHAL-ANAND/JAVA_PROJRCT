package util;

import database.DBConnection;
import security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class SeedUsers {
    public static void main(String[] args) {
        System.out.println("Connecting to database and seeding demo accounts...");
        try (Connection conn = DBConnection.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM users WHERE email = ?";
            String insertSql = "INSERT INTO users (name, email, password, role, register_number, department, specialization, available) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            Object[][] demoUsers = {
                {"System Admin", "admin@campusos.com", "admin123", "ADMIN", null, "Administration", null, true},
                {"John Student", "student@campusos.com", "student123", "STUDENT", "REG2026001", "Computer Science", null, true},
                {"Dr. Smith", "faculty@campusos.com", "faculty123", "FACULTY", null, "Electrical Engineering", null, true},
                {"Bob Maintenance", "maintenance@campusos.com", "maint123", "MAINTENANCE", null, "Campus Facilities", "General", true},
                {"Alice Tech", "tech@campusos.com", "tech123", "TECHNICIAN", null, "IT Department", "Network", true}
            };

            for (Object[] u : demoUsers) {
                String name = (String) u[0];
                String email = (String) u[1];
                String rawPass = (String) u[2];
                String role = (String) u[3];
                String reg = (String) u[4];
                String dept = (String) u[5];
                String spec = (String) u[6];
                boolean avail = (boolean) u[7];

                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, email);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            System.out.println("User already exists: " + email);
                            continue;
                        }
                    }
                }

                String hashed = PasswordHasher.hash(rawPass);
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, name);
                    insertStmt.setString(2, email);
                    insertStmt.setString(3, hashed);
                    insertStmt.setString(4, role);
                    insertStmt.setString(5, reg);
                    insertStmt.setString(6, dept);
                    insertStmt.setString(7, spec);
                    insertStmt.setBoolean(8, avail);
                    insertStmt.executeUpdate();
                    System.out.println("Created " + role + " -> " + email + " (password: " + rawPass + ")");
                }
            }
            System.out.println("\nDemo user seeding completed successfully!");
        } catch (Exception e) {
            System.err.println("Seeding error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
