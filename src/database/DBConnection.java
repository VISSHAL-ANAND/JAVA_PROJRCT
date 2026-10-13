package database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {
    private static final String CONFIG_FILE = "config.properties";

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        Properties properties = loadProperties();

        String url = setting(properties, "db.url", "CAMPUSOS_DB_URL");
        String username = setting(properties, "db.username", "CAMPUSOS_DB_USERNAME");
        String password = setting(properties, "db.password", "CAMPUSOS_DB_PASSWORD");

        if (isMissing(url) || isMissing(username) || isMissing(password)) {
            throw new SQLException(
                    "Database configuration is incomplete. Set CAMPUSOS_DB_URL, " +
                    "CAMPUSOS_DB_USERNAME, and CAMPUSOS_DB_PASSWORD, or configure " +
                    "the corresponding values in resources/config.properties."
            );
        }

        return DriverManager.getConnection(url, username, password);
    }

    private static String setting(Properties properties, String propertyName, String environmentName) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        String propertyValue = properties.getProperty(propertyName);
        return propertyValue == null ? null : propertyValue.trim();
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || "CHANGE_ME".equalsIgnoreCase(value);
    }

    private static Properties loadProperties() throws SQLException {
        Properties properties = new Properties();

        try (InputStream input = DBConnection.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                // Environment-only configuration is allowed when the packaged resource is absent.
                return properties;
            }

            properties.load(input);
        } catch (IOException e) {
            throw new SQLException("Unable to load database configuration", e);
        }

        return properties;
    }
}
