package dao;

import database.DBConnection;
import util.CsvExport;
import java.sql.*;
import java.util.*;

public class AdminDAO {
    public Map<String, Object> getOverview() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "SELECT (SELECT COUNT(*) FROM users) AS total_users, (SELECT COUNT(*) FROM tickets) AS total_tickets, (SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS')) AS active_tickets, (SELECT COUNT(*) FROM tickets WHERE status IN ('RESOLVED','CLOSED')) AS resolved_tickets, (SELECT COUNT(*) FROM tickets t JOIN issues i ON i.id=t.issue_id WHERE i.priority='CRITICAL' AND t.status IN ('OPEN','ASSIGNED','IN_PROGRESS')) AS critical_issues, (SELECT COUNT(*) FROM users WHERE role IN ('MAINTENANCE','TECHNICIAN') AND available=TRUE) AS available_staff";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                result.put("totalUsers", rs.getInt("total_users"));
                result.put("totalTickets", rs.getInt("total_tickets"));
                result.put("activeTickets", rs.getInt("active_tickets"));
                result.put("resolvedTickets", rs.getInt("resolved_tickets"));
                result.put("criticalIssues", rs.getInt("critical_issues"));
                result.put("availableStaff", rs.getInt("available_staff"));
            }
        }
        return result;
    }
    /** Exports the 10,000 most recent tickets for an administrator's operational report. */
    public String exportTicketReportCsv() throws SQLException {
        String sql = """
                SELECT t.id, i.title, i.category, i.location, i.priority, t.status, t.created_at,
                       reporter.name AS reporter_name, reporter.email AS reporter_email,
                       assignee.name AS assigned_staff
                FROM tickets t
                JOIN issues i ON i.id = t.issue_id
                JOIN users reporter ON reporter.id = t.reported_by
                LEFT JOIN users assignee ON assignee.id = t.assigned_technician_id
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT 10000
                """;
        StringBuilder csv = new StringBuilder();
        csv.append(CsvExport.row("ticket_id", "title", "category", "location", "priority",
                "status", "created_at", "reporter_name", "reporter_email", "assigned_staff"));
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                csv.append(CsvExport.row(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("category"),
                        rs.getString("location"),
                        rs.getString("priority"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getString("reporter_name"),
                        rs.getString("reporter_email"),
                        rs.getString("assigned_staff")));
            }
        }
        return csv.toString();
    }

}
