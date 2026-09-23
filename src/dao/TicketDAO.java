package dao;

import database.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TicketDAO {

    public List<Integer> findOpenTicketIds() throws SQLException {
        String sql = "SELECT id FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') ORDER BY created_at";
        List<Integer> ids = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        }
        return ids;
    }
}
