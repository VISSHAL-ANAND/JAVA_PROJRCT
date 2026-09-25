package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.UserDAO;
import exception.AuthenticationException;
import model.User;
import service.AuthService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class ApiServer {
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    public ApiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/auth/login", this::handleLogin);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("CAMPUSOS HTTP API started on port " + port);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            return;
        }

        try {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            String email = body.path("email").asText("");
            String password = body.path("password").asText("");

            User user = new AuthService(new UserDAO()).authenticate(email, password);

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());

            sendJson(exchange, 200, Map.of(
                    "success", true,
                    "message", "Login successful",
                    "user", userData
            ));
        } catch (AuthenticationException e) {
            sendJson(exchange, 401, Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("success", false, "message", "Server error"));
        } finally {
            exchange.close();
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:5173");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, mapper.writeValueAsString(payload));
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
