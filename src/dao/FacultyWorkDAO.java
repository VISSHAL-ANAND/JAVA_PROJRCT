package dao;

import database.DBConnection;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class FacultyWorkDAO {
    public Map<String,Object> createInvitation(String name, String email, String department,
                                                String tokenHash, int invitedBy) throws SQLException {
        String sql = "INSERT INTO faculty_work_invitations(name,email,department,token_hash,invited_by,expires_at) "
                + "VALUES(?,?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, name);
            s.setString(2, email);
            s.setString(3, department);
            s.setString(4, tokenHash);
            s.setInt(5, invitedBy);
            s.setTimestamp(6, Timestamp.from(Instant.now().plusSeconds(72 * 60 * 60)));
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (keys.next()) return Map.of("id", keys.getInt(1), "email", email);
            }
        }
        throw new SQLException("Unable to create invitation");
    }

    public void deleteInvitation(String tokenHash) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement s = c.prepareStatement("DELETE FROM faculty_work_invitations WHERE token_hash=? AND accepted_at IS NULL")) {
            s.setString(1, tokenHash);
            s.executeUpdate();
        }
    }

    public int acceptInvitation(String tokenHash, String passwordHash) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                String select = "SELECT id,name,email,department,expires_at,accepted_at FROM faculty_work_invitations "
                        + "WHERE token_hash=? FOR UPDATE";
                int invitationId;
                String name;
                String email;
                String department;
                try (PreparedStatement s = c.prepareStatement(select)) {
                    s.setString(1, tokenHash);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Invitation is invalid or has expired");
                        invitationId = rs.getInt("id");
                        name = rs.getString("name");
                        email = rs.getString("email");
                        department = rs.getString("department");
                        Timestamp expiry = rs.getTimestamp("expires_at");
                        if (rs.getTimestamp("accepted_at") != null || expiry == null || !expiry.toInstant().isAfter(Instant.now())) {
                            throw new IllegalArgumentException("Invitation is invalid or has expired");
                        }
                    }
                }
                try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM users WHERE email=?")) {
                    s.setString(1, email);
                    try (ResultSet rs = s.executeQuery()) {
                        if (rs.next()) throw new IllegalArgumentException("An account already exists for this email");
                    }
                }
                int userId;
                String insert = "INSERT INTO users(name,email,password,role,register_number,department,specialization,available) "
                        + "VALUES(?,?,?,'FACULTY',NULL,?,NULL,TRUE)";
                try (PreparedStatement s = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, name);
                    s.setString(2, email);
                    s.setString(3, passwordHash);
                    s.setString(4, department);
                    s.executeUpdate();
                    try (ResultSet keys = s.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Unable to create faculty account");
                        userId = keys.getInt(1);
                    }
                }
                try (PreparedStatement s = c.prepareStatement("UPDATE faculty_work_invitations SET accepted_at=CURRENT_TIMESTAMP WHERE id=? AND accepted_at IS NULL")) {
                    s.setInt(1, invitationId);
                    if (s.executeUpdate() != 1) throw new SQLException("Invitation was already used");
                }
                c.commit();
                return userId;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<Map<String,Object>> listAssignments(Integer facultyId) throws SQLException {
        String sql = "SELECT a.id,a.title,a.description,a.assigned_to,a.assigned_by,a.due_at,a.status,"
                + "a.progress_percent,a.progress_note,a.created_at,a.updated_at,u.name AS faculty_name,u.email AS faculty_email "
                + "FROM faculty_work_assignments a JOIN users u ON u.id=a.assigned_to "
                + (facultyId == null ? "" : "WHERE a.assigned_to=? ") + "ORDER BY "
                + "CASE WHEN a.status='COMPLETED' THEN 1 ELSE 0 END, a.due_at NULLS LAST, a.created_at DESC";
        // MySQL does not support PostgreSQL's NULLS LAST syntax; order nullable deadlines portably.
        sql = sql.replace("a.due_at NULLS LAST", "(a.due_at IS NULL), a.due_at");
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            if (facultyId != null) s.setInt(1, facultyId);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("title", rs.getString("title"));
                    item.put("description", rs.getString("description"));
                    item.put("assignedTo", rs.getInt("assigned_to"));
                    item.put("assignedBy", rs.getInt("assigned_by"));
                    item.put("dueAt", rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant().toString());
                    item.put("status", rs.getString("status"));
                    item.put("progressPercent", rs.getInt("progress_percent"));
                    item.put("progressNote", rs.getString("progress_note"));
                    item.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    item.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
                    item.put("facultyName", rs.getString("faculty_name"));
                    item.put("facultyEmail", rs.getString("faculty_email"));
                    out.add(item);
                }
            }
        }
        return out;
    }

    public int createAssignment(String title, String description, int assignedTo, int assignedBy,
                                Timestamp dueAt) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM users WHERE id=? AND role='FACULTY' AND available=TRUE")) {
                check.setInt(1, assignedTo);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Choose an active faculty member");
                }
            }
            String sql = "INSERT INTO faculty_work_assignments(title,description,assigned_to,assigned_by,due_at) VALUES(?,?,?,?,?)";
            try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, title);
                s.setString(2, description);
                s.setInt(3, assignedTo);
                s.setInt(4, assignedBy);
                s.setTimestamp(5, dueAt);
                s.executeUpdate();
                try (ResultSet keys = s.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Unable to create assignment");
    }

    public boolean updateProgress(int assignmentId, int facultyId, String status, int progress, String note) throws SQLException {
        String sql = "UPDATE faculty_work_assignments SET status=?,progress_percent=?,progress_note=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE id=? AND assigned_to=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, status);
            s.setInt(2, progress);
            s.setString(3, note);
            s.setInt(4, assignmentId);
            s.setInt(5, facultyId);
            return s.executeUpdate() == 1;
        }
    }

    public List<Map<String,Object>> listFaculty() throws SQLException {
        List<Map<String,Object>> out = new ArrayList<>();
        String sql = "SELECT id,name,email,department,available FROM users WHERE role='FACULTY' ORDER BY name,id LIMIT 500";
        try (Connection c = DBConnection.getConnection(); PreparedStatement s = c.prepareStatement(sql); ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("id",rs.getInt("id")); item.put("name",rs.getString("name"));
                item.put("email",rs.getString("email")); item.put("department",rs.getString("department"));
                item.put("available",rs.getBoolean("available")); out.add(item);
            }
        }
        return out;
    }
}
