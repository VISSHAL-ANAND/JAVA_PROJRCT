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
        return DriverManager.getConnection(
                properties.getProperty("db.url"),
                properties.getProperty("db.username"),
                properties.getProperty("db.password")
        );
    }

    private static Properties loadProperties() throws SQLException {
        Properties properties = new Properties();

        try (InputStream input = DBConnection.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new SQLException("Missing resources/config.properties");
            }

            properties.load(input);
        } catch (IOException e) {
            throw new SQLException("Unable to load database configuration", e);
        }

        return properties;
    }
}
