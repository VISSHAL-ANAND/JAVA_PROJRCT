package dao;

import database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmergencyDAO {
    public List<Map<String, Object>> findRecent() throws SQLException {
        List<Map<String, Object>> emergencies = new ArrayList<>();
        String sql = """
                SELECT e.id, e.title, e.message, e.created_at, u.name AS created_by_name
                FROM emergencies e
                JOIN users u ON u.id = e.created_by
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT 50
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("title", rs.getString("title"));
                item.put("message", rs.getString("message"));
                item.put("createdAt", rs.getTimestamp("created_at").toString());
                item.put("createdBy", rs.getString("created_by_name"));
                emergencies.add(item);
            }
        }
        return emergencies;
    }
}
