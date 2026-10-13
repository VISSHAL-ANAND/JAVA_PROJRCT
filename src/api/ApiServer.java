package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.IssueDAO;
import dao.TicketDAO;
import dao.UserDAO;
import exception.AuthenticationException;
import model.Issue;
import model.Priority;
import model.User;
import service.AuthService;
import service.IssueService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiServer {
    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final IssueService issueService = new IssueService();
    private final TicketDAO ticketDAO = new TicketDAO();
    private HttpServer server;

    public ApiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/issues", this::handleIssues);
        server.createContext("/api/tickets/reporter", this::handleReporterTickets);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("CAMPUSOS HTTP API started on port " + port);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 204, ""); return; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed")); return;
        }
        try {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            User user = new AuthService(new UserDAO()).authenticate(
                    body.path("email").asText(""), body.path("password").asText(""));
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());
            sendJson(exchange, 200, Map.of("success", true, "message", "Login successful", "user", userData));
        } catch (AuthenticationException e) {
            sendJson(exchange, 401, Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Login request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Server error"));
        } finally { exchange.close(); }
    }

    private void handleIssues(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 204, ""); return; }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed")); return;
        }
        try {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            int reporterId = body.path("reporterId").asInt(0);
            String title = body.path("title").asText("").trim();
            String description = body.path("description").asText("").trim();
            String category = body.path("category").asText("").trim();
            String location = body.path("location").asText("").trim();

            if (reporterId <= 0 || title.isBlank() || description.isBlank() || category.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Title, description and category are required"));
                return;
            }

            Issue issue = new Issue(0, title, description, category, location, null, Priority.LOW);
            int ticketId = issueService.submit(issue, reporterId);

            sendJson(exchange, 201, Map.of(
                    "success", true,
                    "message", "Issue reported successfully",
                    "ticketId", ticketId,
                    "priority", issue.getPriority().name()
            ));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid issue data"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Issue submission failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to create issue"));
        } finally { exchange.close(); }
    }

    private void handleReporterTickets(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 204, ""); return; }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed")); return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String prefix = "/api/tickets/reporter/";
            if (!path.startsWith(prefix)) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Reporter id is required"));
                return;
            }

            final int reporterId;
            try {
                reporterId = Integer.parseInt(path.substring(prefix.length()));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Invalid reporter id"));
                return;
            }
            if (reporterId <= 0) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Invalid reporter id"));
                return;
            }

            sendJson(exchange, 200, Map.of("success", true, "tickets", ticketDAO.findByReporter(reporterId)));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to retrieve reporter tickets", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to retrieve tickets"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ticket retrieval failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to retrieve tickets"));
        } finally { exchange.close(); }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:5173");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        send(exchange, status, mapper.writeValueAsString(payload));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
