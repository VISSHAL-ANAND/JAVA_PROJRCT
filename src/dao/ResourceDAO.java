package dao;

import database.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ResourceDAO {
    public List<Integer> findAvailableResourceIds() throws SQLException {
        String sql = "SELECT id FROM resources WHERE available = TRUE AND quantity > 0 ORDER BY id";
        List<Integer> ids = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt("id"));
        }
        return ids;
    }
}
