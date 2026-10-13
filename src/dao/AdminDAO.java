package dao;

import database.DBConnection;
import java.sql.*;
import java.util.*;

public class AdminDAO {
    public Map<String, Object> getOverview() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        String sql = "SELECT (SELECT COUNT(*) FROM users) AS total_users, (SELECT COUNT(*) FROM tickets) AS total_tickets, (SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS')) AS active_tickets, (SELECT COUNT(*) FROM tickets WHERE status IN ('RESOLVED','CLOSED')) AS resolved_tickets, (SELECT COUNT(*) FROM issues WHERE priority='CRITICAL') AS critical_issues, (SELECT COUNT(*) FROM users WHERE role IN ('MAINTENANCE','TECHNICIAN') AND available=TRUE) AS available_staff";
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
}
