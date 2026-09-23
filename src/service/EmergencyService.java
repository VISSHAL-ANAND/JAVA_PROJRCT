package service;

import database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EmergencyService {

    public int createEmergency(int adminId, String title, String message) throws SQLException {
        String sql = "INSERT INTO emergencies (title, message, created_by) VALUES (?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, title);
            statement.setString(2, message);
            statement.setInt(3, adminId);
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        throw new SQLException("Unable to create emergency");
    }
}
