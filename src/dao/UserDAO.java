package dao;

import database.DBConnection;
import model.AuthUser;
import model.Role;
import java.sql.*;
import java.util.Optional;

public class UserDAO {
    public Optional<Integer> findIdByEmail(String email) throws SQLException {
        String sql="SELECT id FROM users WHERE email=?";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email); try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(r.getInt("id")):Optional.empty();}
        }
    }
    public boolean existsByEmail(String email) throws SQLException {
        String sql="SELECT 1 FROM users WHERE email=? LIMIT 1";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email); try(ResultSet r=s.executeQuery()){return r.next();}
        }
    }
    public Optional<String> findRoleById(int userId) throws SQLException {
        String sql = "SELECT role FROM users WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString("role")) : Optional.empty();
            }
        }
    }

    public Optional<AuthUser> findForAuthentication(String email) throws SQLException {
        String sql="SELECT id,name,email,password,role,register_number,department,specialization,available FROM users WHERE email=?";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,email);
            try(ResultSet r=s.executeQuery()){
                if(!r.next()) return Optional.empty();
                return Optional.of(new AuthUser(r.getInt("id"),r.getString("name"),r.getString("email"),
                    r.getString("password"),Role.valueOf(r.getString("role")),r.getString("register_number"),
                    r.getString("department"),r.getString("specialization"),r.getBoolean("available")));
            }
        }
    }

    public java.util.List<java.util.Map<String, Object>> findAssignableStaff() throws SQLException {
        java.util.List<java.util.Map<String, Object>> staff = new java.util.ArrayList<>();
        String sql = "SELECT id, name, role, specialization FROM users WHERE role IN ('MAINTENANCE','TECHNICIAN') AND available = TRUE ORDER BY role, name, id LIMIT 100";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("name", rs.getString("name"));
                item.put("role", rs.getString("role"));
                item.put("specialization", rs.getString("specialization"));
                staff.add(item);
            }
        }
        return staff;
    }


    public java.util.List<java.util.Map<String, Object>> findUsers() throws SQLException {
        java.util.List<java.util.Map<String, Object>> users = new java.util.ArrayList<>();
        String sql = "SELECT id, name, email, role, department, specialization, available, created_at FROM users ORDER BY role, name, id LIMIT 200";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("name", rs.getString("name"));
                item.put("email", rs.getString("email"));
                item.put("role", rs.getString("role"));
                item.put("department", rs.getString("department"));
                item.put("specialization", rs.getString("specialization"));
                item.put("available", rs.getBoolean("available"));
                item.put("createdAt", rs.getTimestamp("created_at").toString());
                users.add(item);
            }
        }
        return users;
    }

    public boolean setStaffAvailability(int userId, boolean available) throws SQLException {
        String sql = "UPDATE users SET available = ? WHERE id = ? AND role IN ('MAINTENANCE','TECHNICIAN')";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, available);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        }
    }


    public int createUser(String name, String email, String passwordHash, String role,
                          String registerNumber, String department, String specialization) throws SQLException {
        String sql = "INSERT INTO users(name,email,password,role,register_number,department,specialization,available) VALUES(?,?,?,?,?,?,?,TRUE)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.setString(5, registerNumber);
            statement.setString(6, department);
            statement.setString(7, specialization);
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Unable to create user");
    }

}
