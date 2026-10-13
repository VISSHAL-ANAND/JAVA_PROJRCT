package api;

import database.DBConnection;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiServerMain {
    private static final Logger LOGGER = Logger.getLogger(ApiServerMain.class.getName());

    private ApiServerMain() {}

    public static void main(String[] args) {
        int port = resolvePort(args);
        ApiServer server = new ApiServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            DBConnection.closePool();
        }, "campusos-api-shutdown"));

        try {
            server.start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to start CAMPUSOS HTTP API", e);
            DBConnection.closePool();
            System.exit(1);
        }
    }

    private static int resolvePort(String[] args) {
        String value = args.length > 0 ? args[0] : System.getenv().getOrDefault("PORT", "8080");
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port is out of range");
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a valid TCP port", e);
        }
    }
}
