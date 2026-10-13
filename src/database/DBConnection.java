package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {
    private static final String CONFIG_FILE = "config.properties";
    private static volatile HikariDataSource dataSource;

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static void closePool() {
        HikariDataSource current = dataSource;
        if (current != null) {
            current.close();
            dataSource = null;
        }
    }

    private static HikariDataSource getDataSource() throws SQLException {
        HikariDataSource current = dataSource;
        if (current != null && !current.isClosed()) return current;
        synchronized (DBConnection.class) {
            current = dataSource;
            if (current != null && !current.isClosed()) return current;
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

            if (url.startsWith("jdbc:postgresql:") &&
                    !url.toLowerCase(java.util.Locale.ROOT).matches(".*[?&]sslmode=.*")) {
                url += url.contains("?") ? "&sslmode=require" : "?sslmode=require";
            }

            int maxPoolSize = parsePoolSize(setting(properties, "db.pool.size", "CAMPUSOS_DB_POOL_SIZE"));
            HikariConfig config = new HikariConfig();
            config.setPoolName("CAMPUSOS-JDBC");
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(Math.min(2, maxPoolSize));
            config.setConnectionTimeout(10_000);
            config.setValidationTimeout(5_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_500_000);
            config.setInitializationFailTimeout(1_000);

            try {
                dataSource = new HikariDataSource(config);
                return dataSource;
            } catch (RuntimeException e) {
                throw new SQLException("Unable to initialize the database connection pool", e);
            }
        }
    }

    private static int parsePoolSize(String value) throws SQLException {
        if (value == null || value.isBlank()) return 10;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 30) {
                throw new SQLException("CAMPUSOS_DB_POOL_SIZE must be between 1 and 30");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new SQLException("CAMPUSOS_DB_POOL_SIZE must be a valid integer", e);
        }
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
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) return properties;
            properties.load(input);
        } catch (IOException e) {
            throw new SQLException("Unable to load database configuration", e);
        }
        return properties;
    }
}
