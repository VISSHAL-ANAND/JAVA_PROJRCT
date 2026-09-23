package dao;

import database.DBConnection;
import model.Issue;

import java.sql.*;

public class IssueDAO {
    public int createIssue(Issue issue) throws SQLException {
        String sql = "INSERT INTO issues (title, description, category, priority) VALUES (?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, issue.getTitle());
            statement.setString(2, issue.getDescription());
            statement.setString(3, issue.getCategory());
            statement.setString(4, issue.getPriority().name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Unable to create issue");
    }
}
