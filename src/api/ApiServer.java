package api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.TicketDAO;
import dao.UserDAO;
import dao.NotificationDAO;
import dao.EmergencyDAO;
import dao.ResourceDAO;
import dao.BookingDAO;
import dao.AdminDAO;
import database.DBConnection;
import exception.AuthenticationException;
import model.Issue;
import model.User;
import security.AccessTokenService;
import security.PasswordHasher;
import security.RateLimiter;
import service.AuthService;
import service.IssueService;
import service.EmergencyService;
import service.BookingService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiServer {
    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Set<String> ISSUE_REPORTER_ROLES = Set.of("STUDENT", "FACULTY");
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final IssueService issueService = new IssueService();
    private final AuthService authService = new AuthService(new UserDAO());
    private final TicketDAO ticketDAO = new TicketDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final EmergencyDAO emergencyDAO = new EmergencyDAO();
    private final EmergencyService emergencyService = new EmergencyService();
    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final BookingService bookingService = new BookingService();
    private final AccessTokenService tokens;
    private final RateLimiter loginRateLimiter;
    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(int port) { this(port, new AccessTokenService()); }

    public ApiServer(int port, AccessTokenService tokens) {
        this(port, tokens, new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter) {
        this.port = port;
        this.tokens = java.util.Objects.requireNonNull(tokens, "tokens");
        this.loginRateLimiter = java.util.Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/health/ready", this::handleReadiness);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/issues", this::handleIssues);
        server.createContext("/api/tickets", this::handleTicketHistory);
        server.createContext("/api/tickets/reporter", this::handleReporterTickets);
        server.createContext("/api/staff/tickets", this::handleStaffTickets);
        server.createContext("/api/notifications", this::handleNotifications);
        server.createContext("/api/emergencies", this::handleEmergencies);
        server.createContext("/api/resources", this::handleResources);
        server.createContext("/api/bookings", this::handleBookings);
        server.createContext("/api/admin", this::handleAdmin);
        executor = Executors.newFixedThreadPool(Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)));
        server.setExecutor(executor);
        server.start();
        LOGGER.info("CAMPUSOS HTTP API started on port " + server.getAddress().getPort());
    }

    public int getPort() {
        if (server == null) throw new IllegalStateException("API server has not started");
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }


    private void handleReadiness(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            try (java.sql.Connection ignored = DBConnection.getConnection()) {
                sendJson(exchange, 200, Map.of("success", true, "status", "ready", "database", "connected"));
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "CAMPUSOS readiness check failed: database unavailable");
                sendJson(exchange, 503, Map.of("success", false, "status", "not_ready", "database", "unavailable"));
            }
        } finally { exchange.close(); }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            sendJson(exchange, 200, Map.of("success", true, "status", "ok"));
        } finally { exchange.close(); }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "POST")) return;
            JsonNode body = readJson(exchange);
            String rateLimitKey = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!loginRateLimiter.allow(rateLimitKey)) {
                exchange.getResponseHeaders().set("Retry-After", "900");
                sendJson(exchange, 429, Map.of("success", false, "message", "Too many login attempts. Try again in 15 minutes."));
                return;
            }
            String email = body.path("email").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            String password = body.path("password").asText("");
            User user = authService.authenticate(email, password);
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());
            sendJson(exchange, 200, Map.of("success", true, "message", "Login successful",
                    "token", tokens.issue(user), "expiresIn", 7200, "user", userData));
        } catch (BadRequestException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage()));
        } catch (AuthenticationException e) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Invalid email or password"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Login request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Server error"));
        } finally { exchange.close(); }
    }

    private void handleIssues(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "POST")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            if (!ISSUE_REPORTER_ROLES.contains(principal.role())) {
                sendJson(exchange, 403, Map.of("success", false, "message", "Your role cannot report issues"));
                return;
            }

            JsonNode body = readJson(exchange);
            String title = body.path("title").asText("").trim();
            String description = body.path("description").asText("").trim();
            String category = body.path("category").asText("").trim();
            String location = body.path("location").asText("").trim();
            if (title.isBlank() || description.isBlank() || category.isBlank()) {
                throw new BadRequestException("Title, description and category are required");
            }
            if (title.length() > 200 || description.length() > 5000 || category.length() > 100 || location.length() > 200) {
                throw new BadRequestException("One or more fields exceed the allowed length");
            }
            Issue issue = new Issue(0, title, description, category, location, null, null);
            int ticketId = issueService.submit(issue, principal.userId());
            sendJson(exchange, 201, Map.of("success", true, "message", "Issue reported successfully",
                    "ticketId", ticketId, "priority", issue.getPriority().name()));
        } catch (BadRequestException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "Invalid issue data" : e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Issue submission failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to create issue"));
        } finally { exchange.close(); }
    }

    private void handleReporterTickets(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
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
            if (reporterId != principal.userId()) {
                sendJson(exchange, 403, Map.of("success", false, "message", "You can only view your own tickets"));
                return;
            }
            sendJson(exchange, 200, Map.of("success", true, "tickets", ticketDAO.findByReporter(principal.userId())));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Unable to retrieve reporter tickets", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to retrieve tickets"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ticket retrieval failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to retrieve tickets"));
        } finally { exchange.close(); }
    }



    private void handleTicketHistory(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^/api/tickets/(\\d+)/history$").matcher(exchange.getRequestURI().getPath());
            if (!matcher.matches()) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            int ticketId = Integer.parseInt(matcher.group(1));
            if (ticketId <= 0 || !ticketDAO.canViewTicket(ticketId, principal.userId(), principal.role())) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Ticket not found"));
                return;
            }
            sendJson(exchange, 200, Map.of("success", true, "history", ticketDAO.findHistory(ticketId)));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid ticket ID"));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Ticket history request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to load ticket history"));
        } finally { exchange.close(); }
    }

    private void handleStaffTickets(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            String path = exchange.getRequestURI().getPath();
            boolean admin = "ADMIN".equals(principal.role());
            boolean staff = "MAINTENANCE".equals(principal.role()) || "TECHNICIAN".equals(principal.role());

            if ("/api/staff/tickets".equals(path)) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                if (!admin && !staff) {
                    sendJson(exchange, 403, Map.of("success", false, "message", "Staff access required"));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true,
                        "tickets", ticketDAO.findAssignedTo(principal.userId(), admin)));
                return;
            }

            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^/api/staff/tickets/(\\d+)/status$").matcher(path);
            if (!matcher.matches()) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }
            if (!admin && !staff) {
                sendJson(exchange, 403, Map.of("success", false, "message", "Staff access required"));
                return;
            }
            int ticketId = Integer.parseInt(matcher.group(1));
            JsonNode body = readJson(exchange);
            String nextStatus = body.path("status").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            ticketDAO.updateStatus(ticketId, principal.userId(), principal.role(), nextStatus);
            sendJson(exchange, 200, Map.of("success", true, "message", "Ticket status updated", "status", nextStatus));
        } catch (BadRequestException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "Invalid request" : e.getMessage()));
        } catch (SecurityException e) {
            sendJson(exchange, 403, Map.of("success", false, "message", "Ticket is not assigned to your account"));
        } catch (java.util.NoSuchElementException e) {
            sendJson(exchange, 404, Map.of("success", false, "message", "Ticket not found"));
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, Map.of("success", false, "message", "Invalid ticket status transition"));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Staff ticket request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to process ticket request"));
        } finally { exchange.close(); }
    }


    private void handleNotifications(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            String path = exchange.getRequestURI().getPath();
            if ("/api/notifications/me".equals(path)) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true,
                        "notifications", notificationDAO.findByUser(principal.userId())));
                return;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^/api/notifications/(\\d+)/read$").matcher(path);
            if (!matcher.matches()) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }
            int notificationId = Integer.parseInt(matcher.group(1));
            if (!notificationDAO.markRead(principal.userId(), notificationId)) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Notification not found"));
                return;
            }
            sendJson(exchange, 200, Map.of("success", true, "message", "Notification marked as read"));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid notification ID"));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Notification request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to load notifications"));
        } finally { exchange.close(); }
    }

    private void handleEmergencies(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, Map.of("success", true, "emergencies", emergencyDAO.findRecent()));
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }
            if (!"ADMIN".equals(principal.role())) {
                sendJson(exchange, 403, Map.of("success", false, "message", "Administrator access required"));
                return;
            }
            JsonNode body = readJson(exchange);
            String title = body.path("title").asText("").trim();
            String message = body.path("message").asText("").trim();
            if (title.isBlank() || message.isBlank() || title.length() > 200 || message.length() > 3000) {
                throw new BadRequestException("A title and message are required within the allowed length");
            }
            int id = emergencyService.createEmergency(principal.userId(), title, message);
            sendJson(exchange, 201, Map.of("success", true, "message", "Emergency broadcast created", "id", id));
        } catch (BadRequestException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Emergency request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to process emergency request"));
        } finally { exchange.close(); }
    }


    private void handleResources(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            sendJson(exchange, 200, Map.of("success", true, "resources", resourceDAO.findAvailableResources()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Resource inventory request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to load resources"));
        } finally { exchange.close(); }
    }

    private void handleBookings(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            String path = exchange.getRequestURI().getPath();
            if ("/api/bookings/mine".equals(path)) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true, "bookings", bookingDAO.findByUser(principal.userId())));
                return;
            }
            java.util.regex.Matcher cancelMatcher = java.util.regex.Pattern
                    .compile("^/api/bookings/(\\d+)/cancel$").matcher(path);
            if (cancelMatcher.matches()) {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                int bookingId = Integer.parseInt(cancelMatcher.group(1));
                if (!bookingDAO.cancelFutureBooking(bookingId, principal.userId())) {
                    sendJson(exchange, 409, Map.of("success", false, "message", "Booking could not be cancelled; it may have started already or does not belong to you."));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true, "message", "Booking cancelled"));
                return;
            }
            if (!"/api/bookings".equals(path) || !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            JsonNode body = readJson(exchange);
            int resourceId = body.path("resourceId").asInt(0);
            LocalDateTime start = LocalDateTime.parse(body.path("startTime").asText(""));
            LocalDateTime end = LocalDateTime.parse(body.path("endTime").asText(""));
            int bookingId = bookingService.book(resourceId, principal.userId(), start, end);
            sendJson(exchange, 201, Map.of("success", true, "message", "Resource booked successfully", "bookingId", bookingId));
        } catch (BadRequestException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid booking data. Check the resource and start/end times."));
        } catch (SQLException e) {
            if ("23P01".equals(e.getSQLState()) || (e.getMessage() != null && e.getMessage().contains("already booked"))) {
                sendJson(exchange, 409, Map.of("success", false, "message", "This resource has reached its booking capacity for that time."));
            } else {
                LOGGER.log(Level.SEVERE, "Booking request failed", e);
                sendJson(exchange, 500, Map.of("success", false, "message", "Unable to create booking"));
            }
        } finally { exchange.close(); }
    }


    private void handleAdmin(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            if (!"ADMIN".equals(principal.role())) {
                sendJson(exchange, 403, Map.of("success", false, "message", "Administrator access required"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/api/admin/overview".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("success", true, "overview", new AdminDAO().getOverview()));
                return;
            }
            if ("/api/admin/users".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("success", true, "users", new UserDAO().findUsers()));
                return;
            }
            if ("/api/admin/users".equals(path) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonNode body = readJson(exchange);
                String name = body.path("name").asText("").trim();
                String email = body.path("email").asText("").trim().toLowerCase(java.util.Locale.ROOT);
                String password = body.path("password").asText("");
                String role = body.path("role").asText("").trim().toUpperCase(java.util.Locale.ROOT);
                String registerNumber = body.path("registerNumber").asText("").trim();
                String department = body.path("department").asText("").trim();
                String specialization = body.path("specialization").asText("").trim();
                if (name.isBlank() || name.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                        email.length() > 150 || password.length() < 12 || password.length() > 128 ||
                        !Set.of("STUDENT","FACULTY","MAINTENANCE","TECHNICIAN","ADMIN").contains(role)) {
                    throw new BadRequestException("Enter valid user details and a password of at least 12 characters");
                }
                if ("STUDENT".equals(role) && registerNumber.isBlank()) {
                    throw new BadRequestException("Register number is required for students");
                }
                if (department.length() > 100 || registerNumber.length() > 50 || specialization.length() > 100) {
                    throw new BadRequestException("One or more user fields exceed the allowed length");
                }
                int userId = new UserDAO().createUser(name, email, PasswordHasher.hash(password), role,
                        registerNumber.isBlank() ? null : registerNumber,
                        department.isBlank() ? null : department,
                        specialization.isBlank() ? null : specialization);
                sendJson(exchange, 201, Map.of("success", true, "message", "User created", "userId", userId));
                return;
            }
            java.util.regex.Matcher availabilityMatcher = java.util.regex.Pattern
                    .compile("^/api/admin/users/(\\d+)/availability$").matcher(path);
            if (availabilityMatcher.matches()) {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                int userId = Integer.parseInt(availabilityMatcher.group(1));
                JsonNode body = readJson(exchange);
                if (!body.has("available") || !body.get("available").isBoolean()) {
                    throw new BadRequestException("The available field must be true or false");
                }
                if (!new UserDAO().setStaffAvailability(userId, body.get("available").asBoolean())) {
                    sendJson(exchange, 404, Map.of("success", false, "message", "Assignable staff member not found"));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true, "message", "Staff availability updated"));
                return;
            }
            if ("/api/admin/staff".equals(path)) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                sendJson(exchange, 200, Map.of("success", true, "staff", new UserDAO().findAssignableStaff()));
                return;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^/api/admin/tickets/(\\d+)/assign$").matcher(path);
            if (!matcher.matches()) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }
            int ticketId = Integer.parseInt(matcher.group(1));
            int staffId = readJson(exchange).path("staffId").asInt(0);
            ticketDAO.assignTicketToStaff(ticketId, staffId, principal.userId());
            sendJson(exchange, 200, Map.of("success", true, "message", "Ticket assigned successfully"));
        } catch (BadRequestException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "Invalid request" : e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            sendJson(exchange, 404, Map.of("success", false, "message", "Ticket not found"));
        } catch (IllegalStateException e) {
            sendJson(exchange, 409, Map.of("success", false, "message", "Ticket is no longer open or cannot be assigned"));
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()) || "23000".equals(e.getSQLState())) {
                sendJson(exchange, 409, Map.of("success", false, "message", "A user with that email already exists"));
                return;
            }
            LOGGER.log(Level.SEVERE, "Admin request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Unable to process administrator request"));
        } finally { exchange.close(); }
    }

    private void prepare(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && allowedOrigins().contains(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
    }

    private Set<String> allowedOrigins() {
        String configured = System.getenv("CAMPUSOS_CORS_ORIGINS");
        if (configured == null || configured.isBlank()) return Set.of("http://localhost:5173");
        return Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean handleOptionsOrMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        if (!expectedMethod.equalsIgnoreCase(method)) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            return true;
        }
        return false;
    }

    private AccessTokenService.Principal requirePrincipal(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Authentication required"));
            return null;
        }
        AccessTokenService.Principal principal = tokens.verify(authorization.substring(7).trim());
        if (principal == null) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Session expired or invalid. Please sign in again."));
        }
        return principal;
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException, BadRequestException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
        if (!"application/json".equalsIgnoreCase(mediaType)) {
            throw new BadRequestException("Content-Type must be application/json");
        }
        byte[] bytes;
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) throw new BadRequestException("Request body is too large");
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }
        if (bytes.length == 0) throw new BadRequestException("JSON request body is required");
        try {
            JsonNode body = mapper.readTree(bytes);
            if (body == null || !body.isObject()) throw new BadRequestException("JSON object is required");
            return body;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException("Malformed JSON");
        }
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

    private static final class BadRequestException extends Exception {
        private BadRequestException(String message) { super(message); }
    }
}
