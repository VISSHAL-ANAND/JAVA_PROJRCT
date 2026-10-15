package integration;

import dao.UserDAO;
import database.DBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import security.PasswordHasher;
import service.EmergencyService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cross-database test that verifies emergency notification fan-out for a large campus population. */
@EnabledIfEnvironmentVariable(named = "CAMPUSOS_BROADCAST_SCALE_TEST_ENABLED", matches = "true")
class EmergencyBroadcastScaleTest {
    private static final int RECIPIENT_FIXTURE_COUNT = 1_000;

    @Test
    void emergencyBroadcastNotifiesEveryOtherUserAcrossOneThousandAccounts() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String emailPrefix = "broadcast-load-" + suffix + "-";
        String title = "Scale Test " + suffix;
        String message = "Emergency fan-out test " + suffix;
        String notificationMessage = "EMERGENCY: " + title + " - " + message;
        UserDAO users = new UserDAO();
        EmergencyService emergencies = new EmergencyService();
        int creatorId = 0;
        int emergencyId = 0;

        try {
            String passwordHash = PasswordHasher.hash("Broadcast!LoadTest-Only-2026");
            for (int i = 0; i < RECIPIENT_FIXTURE_COUNT; i++) {
                String role = i == 0 ? "ADMIN" : "STUDENT";
                int userId = users.createUser(
                        "Broadcast Load User " + i,
                        emailPrefix + i + "@example.test",
                        passwordHash,
                        role,
                        "BCAST" + suffix + i,
                        "CSE",
                        null);
                if (i == 0) creatorId = userId;
            }

            int expectedRecipients;
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM users WHERE id <> ?")) {
                statement.setInt(1, creatorId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    expectedRecipients = result.getInt(1);
                }
            }
            assertTrue(expectedRecipients >= RECIPIENT_FIXTURE_COUNT - 1,
                    "All 999 non-creator fixture accounts must be included");

            emergencyId = emergencies.createEmergency(creatorId, title, message);

            int actualNotifications;
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM notifications WHERE message = ?")) {
                statement.setString(1, notificationMessage);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    actualNotifications = result.getInt(1);
                }
            }

            assertEquals(expectedRecipients, actualNotifications,
                    "The set-based broadcast must notify every other database user exactly once");
        } finally {
            try (Connection admin = openAdminConnection()) {
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM notifications WHERE message = ?")) {
                    statement.setString(1, notificationMessage);
                    statement.executeUpdate();
                }
                if (emergencyId > 0) {
                    try (PreparedStatement statement = admin.prepareStatement(
                            "DELETE FROM emergencies WHERE id = ?")) {
                        statement.setInt(1, emergencyId);
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = admin.prepareStatement(
                        "DELETE FROM users WHERE email LIKE ?")) {
                    statement.setString(1, emailPrefix + "%@example.test");
                    statement.executeUpdate();
                }
            } finally {
                DBConnection.closePool();
            }
        }
    }

    private Connection openAdminConnection() throws SQLException {
        String url = System.getenv("CAMPUSOS_IT_ADMIN_DB_URL");
        String username = System.getenv("CAMPUSOS_IT_ADMIN_DB_USERNAME");
        String password = System.getenv("CAMPUSOS_IT_ADMIN_DB_PASSWORD");
        if (url == null || url.isBlank()) url = System.getenv("CAMPUSOS_DB_URL");
        if (username == null || username.isBlank()) username = System.getenv("CAMPUSOS_DB_USERNAME");
        if (password == null) password = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (url == null || username == null || password == null) {
            throw new SQLException("Database admin connection settings are incomplete");
        }
        return DriverManager.getConnection(url, username, password);
    }
}
