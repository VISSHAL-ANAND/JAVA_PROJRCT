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
import dao.TokenRevocationDAO;
import dao.LoginAttemptDAO;
import database.DBConnection;
import exception.AuthenticationException;
import model.Issue;
import model.User;
import security.AccessTokenService;
import security.PasswordHasher;
import security.RateLimiter;
import security.TokenRevocationStore;
import security.LoginAttemptStore;
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
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiServer {
    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String REQUEST_START_ATTRIBUTE = "campusos.requestStartNanos";
    private static final String REQUEST_RECORDED_ATTRIBUTE = "campusos.metricsRecorded";
    private static final Set<String> ISSUE_REPORTER_ROLES = Set.of("STUDENT", "FACULTY");
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final IssueService issueService = new IssueService();
    private final AuthService authService;
    private final UserDAO userDAO;
    private final TicketDAO ticketDAO = new TicketDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final EmergencyDAO emergencyDAO = new EmergencyDAO();
    private final EmergencyService emergencyService = new EmergencyService();
    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final BookingService bookingService = new BookingService();
    private final AccessTokenService tokens;
    private final RateLimiter loginRateLimiter;
    private final TokenRevocationStore tokenRevocations;
    private final LoginAttemptStore loginAttempts;
    private HttpServer server;
    private ExecutorService executor;
    private volatile long startedAtNanos;
    private final LongAdder completedResponses = new LongAdder();
    private final LongAdder clientErrorResponses = new LongAdder();
    private final LongAdder serverErrorResponses = new LongAdder();
    private final LongAdder totalResponseLatencyNanos = new LongAdder();
    private final LongAdder[] responseLatencyBuckets = {
            new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(),
            new LongAdder(), new LongAdder(), new LongAdder()
    };

    public ApiServer(int port) { this(port, new AccessTokenService()); }

    public ApiServer(int port, AccessTokenService tokens) {
        this(port, tokens, new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter) {
        this(port, tokens, loginRateLimiter, new UserDAO());
    }

    ApiServer(int port, AccessTokenService tokens, UserDAO userDAO) {
        this(port, tokens, new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)), userDAO);
    }

    ApiServer(int port, AccessTokenService tokens, UserDAO userDAO, TokenRevocationStore tokenRevocations) {
        this(port, tokens, new RateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                userDAO, new AuthService(userDAO), tokenRevocations);
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter, UserDAO userDAO) {
        this(port, tokens, loginRateLimiter, userDAO, new AuthService(userDAO));
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter,
              UserDAO userDAO, AuthService authService) {
        this(port, tokens, loginRateLimiter, userDAO, authService, new TokenRevocationDAO());
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter,
              UserDAO userDAO, AuthService authService, TokenRevocationStore tokenRevocations) {
        this(port, tokens, loginRateLimiter, userDAO, authService, tokenRevocations, new LoginAttemptDAO());
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter,
              UserDAO userDAO, AuthService authService, TokenRevocationStore tokenRevocations,
              LoginAttemptStore loginAttempts) {
        this.port = port;
        this.tokens = java.util.Objects.requireNonNull(tokens, "tokens");
        this.loginRateLimiter = java.util.Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
        this.userDAO = java.util.Objects.requireNonNull(userDAO, "userDAO");
        this.authService = java.util.Objects.requireNonNull(authService, "authService");
        this.tokenRevocations = java.util.Objects.requireNonNull(tokenRevocations, "tokenRevocations");
        this.loginAttempts = java.util.Objects.requireNonNull(loginAttempts, "loginAttempts");
    }

    ApiServer(int port, AccessTokenService tokens, RateLimiter loginRateLimiter,
              LoginAttemptStore loginAttempts) {
        this(port, tokens, loginRateLimiter, new UserDAO(), new AuthService(new UserDAO()),
                new TokenRevocationDAO(), loginAttempts);
    }

    public void start() throws IOException {
        // A zero backlog delegates to a small OS default and can reset connections during
        // legitimate bursts. Size the accept backlog to absorb a 1,000-client arrival burst.
        server = HttpServer.create(new InetSocketAddress(port), 2048);
        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/health/ready", this::handleReadiness);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/logout", this::handleLogout);
        server.createContext("/api/issues", this::handleIssues);
        server.createContext("/api/tickets", this::handleTicketHistory);
        server.createContext("/api/tickets/reporter", this::handleReporterTickets);
        server.createContext("/api/staff/tickets", this::handleStaffTickets);
        server.createContext("/api/notifications", this::handleNotifications);
        server.createContext("/api/emergencies", this::handleEmergencies);
        server.createContext("/api/resources", this::handleResources);
        server.createContext("/api/bookings", this::handleBookings);
        server.createContext("/api/admin/metrics", this::handleAdminMetrics);
        server.createContext("/api/admin", this::handleAdmin);
        int workerCount = configuredPositiveInt(
                "CAMPUSOS_HTTP_WORKER_THREADS",
                Math.max(4, Math.min(64, Runtime.getRuntime().availableProcessors() * 2)),
                4, 128);
        int queueCapacity = configuredPositiveInt("CAMPUSOS_HTTP_QUEUE_CAPACITY", 2048, 64, 10000);
        // Size the default pool at roughly two workers per available CPU, with a small
        // minimum. Password verification is CPU-bound; oversubscribing a small host with
        // 16+ workers by default increased 1,000-way login latency in CI. Operators can
        // override the count after measuring their actual host. The bounded queue absorbs
        // bursts, while CallerRunsPolicy applies back-pressure if the queue also saturates.
        executor = new java.util.concurrent.ThreadPoolExecutor(
                workerCount, workerCount, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(queueCapacity),
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        startedAtNanos = System.nanoTime();
        server.start();
        LOGGER.info("CAMPUSOS HTTP API started on port " + server.getAddress().getPort());
    }

    private static int configuredPositiveInt(String name, int defaultValue, int minimum, int maximum) {
        String configured = System.getenv(name);
        if (configured == null || configured.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a valid integer", e);
        }
    }

    public int getPort() {
        if (server == null) throw new IllegalStateException("API server has not started");
        return server.getAddress().getPort();
    }

    public void stop() {
        HttpServer currentServer = server;
        if (currentServer != null) {
            // Stop accepting new requests and give active exchanges up to five seconds to drain.
            currentServer.stop(5);
        }

        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdown();
            try {
                if (!currentExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    currentExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                currentExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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
        String email = "";
        try {
            if (handleOptionsOrMethod(exchange, "POST")) return;
            JsonNode body = readJson(exchange);
            JsonNode emailNode = body.path("email");
            JsonNode passwordNode = body.path("password");
            if (!emailNode.isTextual() || !passwordNode.isTextual()) {
                throw new BadRequestException("Email and password must be strings");
            }
            email = emailNode.asText().trim().toLowerCase(java.util.Locale.ROOT);
            String password = passwordNode.asText();
            // Key this outer limiter by source address AND account. Many campuses and
            // reverse proxies share one apparent IP; one user's failures must not lock out
            // every unrelated user behind that address. AuthService also limits per account.
            String sourceAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String rateLimitKey = sourceAddress + ":" + email;
            if (!loginRateLimiter.allow(rateLimitKey)) {
                exchange.getResponseHeaders().set("Retry-After", "900");
                sendJson(exchange, 429, Map.of("success", false, "message", "Too many login attempts. Try again in 15 minutes."));
                return;
            }
            try {
                if (!loginAttempts.allowAttempt(email)) {
                    exchange.getResponseHeaders().set("Retry-After", "900");
                    sendJson(exchange, 429, Map.of("success", false, "message", "Too many login attempts. Try again in 15 minutes."));
                    return;
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Shared login rate limiter unavailable");
                sendJson(exchange, 503, Map.of("success", false, "message", "Authentication service temporarily unavailable"));
                return;
            }
            User user = authService.authenticate(email, password);
            // A successful authentication clears shared account failures and only this
            // source/account bucket, never other users behind the same campus proxy.
            try {
                loginAttempts.clear(email);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Unable to clear successful login attempt history");
                sendJson(exchange, 503, Map.of("success", false, "message", "Authentication service temporarily unavailable"));
                return;
            }
            loginRateLimiter.reset(rateLimitKey);
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());
            sendJson(exchange, 200, Map.of("success", true, "message", "Login successful",
                    "token", tokens.issue(user), "expiresIn", 7200, "user", userData));
        } catch (BadRequestException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage()));
        } catch (exception.AuthenticationUnavailableException e) {
            // A database failure must not masquerade as invalid credentials or leave a
            // shared limiter entry behind after the failed infrastructure request.
            loginRateLimiter.reset(exchange.getRemoteAddress().getAddress().getHostAddress() + ":" + email);
            try {
                loginAttempts.clear(email);
            } catch (SQLException cleanupFailure) {
                LOGGER.log(Level.WARNING, "Unable to clear login limiter after authentication service failure");
            }
            sendJson(exchange, 503, Map.of("success", false, "message", "Authentication service temporarily unavailable"));
        } catch (AuthenticationException e) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Invalid email or password"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Login request failed", e);
            sendJson(exchange, 500, Map.of("success", false, "message", "Server error"));
        } finally { exchange.close(); }
    }


    private void handleLogout(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "POST")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String token = authorization.substring(7).trim();
            tokenRevocations.revoke(token, principal.expiresAt());
            sendJson(exchange, 200, Map.of("success", true, "message", "Signed out successfully"));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Unable to revoke access token");
            sendJson(exchange, 503, Map.of("success", false, "message", "Sign-out service temporarily unavailable"));
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
            for (String field : new String[] {"title", "description", "category", "location"}) {
                if (body.has(field) && !body.path(field).isTextual()) {
                    throw new BadRequestException("Issue fields must be strings");
                }
            }
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
            int page = 1;
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                for (String parameter : query.split("&")) {
                    if (parameter.startsWith("page=")) {
                        try {
                            page = Integer.parseInt(parameter.substring("page=".length()));
                        } catch (NumberFormatException e) {
                            sendJson(exchange, 400, Map.of("success", false, "message", "Page must be a positive integer"));
                            return;
                        }
                        break;
                    }
                }
            }
            if (page < 1 || page > 100_000_000) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Page must be between 1 and 100000000"));
                return;
            }
            final int pageSize = 20;
            long offset = (long) (page - 1) * pageSize;
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("tickets", ticketDAO.findByReporter(principal.userId(), pageSize, offset));
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.putAll(ticketDAO.getReporterTicketSummary(principal.userId()));
            sendJson(exchange, 200, response);
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
                recordResponse(exchange, 204);
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
                int page = queryInteger(exchange, "page", 1);
                int pageSize = queryInteger(exchange, "pageSize", 50);
                if (page < 1 || page > 100_000_000) {
                    throw new BadRequestException("Page must be a positive integer");
                }
                if (pageSize < 1 || pageSize > 100) {
                    throw new BadRequestException("Page size must be between 1 and 100");
                }
                int totalTickets = ticketDAO.countAssignedTo(principal.userId(), admin);
                long totalPages = Math.max(1L, (totalTickets + (long) pageSize - 1) / pageSize);
                if (page > totalPages) {
                    throw new BadRequestException("Page exceeds the available ticket pages");
                }
                long offset = (long) (page - 1) * pageSize;
                sendJson(exchange, 200, Map.of(
                        "success", true,
                        "tickets", ticketDAO.findAssignedTo(principal.userId(), admin, pageSize, offset),
                        "page", page,
                        "pageSize", pageSize,
                        "totalTickets", totalTickets,
                        "totalPages", totalPages));
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
            JsonNode statusNode = body.path("status");
            if (!statusNode.isTextual()) {
                throw new BadRequestException("Ticket status must be a string");
            }
            String nextStatus = statusNode.asText().trim().toUpperCase(java.util.Locale.ROOT);
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
            JsonNode titleNode = body.path("title");
            JsonNode messageNode = body.path("message");
            if (!titleNode.isTextual() || !messageNode.isTextual()) {
                throw new BadRequestException("Title and message must be strings");
            }
            String title = titleNode.asText().trim();
            String message = messageNode.asText().trim();
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
            if (!"/api/bookings".equals(path)) {
                sendJson(exchange, 404, Map.of("success", false, "message", "Route not found"));
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }
            JsonNode body = readJson(exchange);
            JsonNode resourceIdNode = body.path("resourceId");
            if (!resourceIdNode.isIntegralNumber() || !resourceIdNode.canConvertToInt() || resourceIdNode.asInt() <= 0) {
                throw new BadRequestException("A positive integer resourceId is required");
            }
            int resourceId = resourceIdNode.asInt();
            LocalDateTime start = LocalDateTime.parse(body.path("startTime").asText(""));
            LocalDateTime end = LocalDateTime.parse(body.path("endTime").asText(""));
            int bookingId = bookingService.book(resourceId, principal.userId(), start, end);
            sendJson(exchange, 201, Map.of("success", true, "message", "Resource booked successfully", "bookingId", bookingId));
        } catch (BadRequestException | IllegalArgumentException | java.time.format.DateTimeParseException e) {
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


    private void handleAdminMetrics(HttpExchange exchange) throws IOException {
        prepare(exchange);
        try {
            if (handleOptionsOrMethod(exchange, "GET")) return;
            AccessTokenService.Principal principal = requirePrincipal(exchange);
            if (principal == null) return;
            if (!"ADMIN".equals(principal.role())) {
                sendJson(exchange, 403, Map.of("success", false, "message", "Administrator access required"));
                return;
            }
            sendJson(exchange, 200, operationalMetrics());
        } finally {
            exchange.close();
        }
    }

    /**
     * Bounded, process-local operational counters. These contain no account identifiers,
     * credentials, SQL text, or connection URLs and are exposed only to authenticated admins.
     */
    private Map<String, Object> operationalMetrics() {
        long responseCount = completedResponses.sum();
        Map<String, Object> requestMetrics = new LinkedHashMap<>();
        requestMetrics.put("completedResponses", responseCount);
        requestMetrics.put("clientErrorResponses", clientErrorResponses.sum());
        requestMetrics.put("serverErrorResponses", serverErrorResponses.sum());
        requestMetrics.put("averageLatencyMs", responseCount == 0 ? 0.0
                : totalResponseLatencyNanos.sum() / 1_000_000.0 / responseCount);
        Map<String, Long> latencyBuckets = new LinkedHashMap<>();
        latencyBuckets.put("le50ms", responseLatencyBuckets[0].sum());
        latencyBuckets.put("le100ms", responseLatencyBuckets[1].sum());
        latencyBuckets.put("le250ms", responseLatencyBuckets[2].sum());
        latencyBuckets.put("le500ms", responseLatencyBuckets[3].sum());
        latencyBuckets.put("le1000ms", responseLatencyBuckets[4].sum());
        latencyBuckets.put("le2000ms", responseLatencyBuckets[5].sum());
        latencyBuckets.put("gt2000ms", responseLatencyBuckets[6].sum());
        requestMetrics.put("latencyBuckets", latencyBuckets);

        Map<String, Object> httpMetrics = new LinkedHashMap<>();
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor pool) {
            httpMetrics.put("workerCount", pool.getPoolSize());
            httpMetrics.put("activeWorkers", pool.getActiveCount());
            httpMetrics.put("queueSize", pool.getQueue().size());
            httpMetrics.put("queueRemainingCapacity", pool.getQueue().remainingCapacity());
            httpMetrics.put("completedTasks", pool.getCompletedTaskCount());
            httpMetrics.put("submittedTasks", pool.getTaskCount());
        } else {
            httpMetrics.put("workerCount", 0);
            httpMetrics.put("activeWorkers", 0);
            httpMetrics.put("queueSize", 0);
            httpMetrics.put("queueRemainingCapacity", 0);
            httpMetrics.put("completedTasks", 0);
            httpMetrics.put("submittedTasks", 0);
        }

        long uptimeSeconds = startedAtNanos == 0 ? 0
                : java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAtNanos);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", "this_instance");
        metrics.put("uptimeSeconds", uptimeSeconds);
        metrics.put("requests", requestMetrics);
        metrics.put("http", httpMetrics);
        metrics.put("database", DBConnection.poolMetrics());
        return Map.of("success", true, "metrics", metrics);
    }

    private void recordResponse(HttpExchange exchange, int status) {
        if (Boolean.TRUE.equals(exchange.getAttribute(REQUEST_RECORDED_ATTRIBUTE))) return;
        exchange.setAttribute(REQUEST_RECORDED_ATTRIBUTE, Boolean.TRUE);
        Object startValue = exchange.getAttribute(REQUEST_START_ATTRIBUTE);
        if (!(startValue instanceof Long startedAt)) return;
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
        completedResponses.increment();
        totalResponseLatencyNanos.add(elapsedNanos);
        if (status >= 400 && status < 500) clientErrorResponses.increment();
        if (status >= 500) serverErrorResponses.increment();
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        int bucket = elapsedMillis <= 50 ? 0
                : elapsedMillis <= 100 ? 1
                : elapsedMillis <= 250 ? 2
                : elapsedMillis <= 500 ? 3
                : elapsedMillis <= 1_000 ? 4
                : elapsedMillis <= 2_000 ? 5 : 6;
        responseLatencyBuckets[bucket].increment();
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
            if ("/api/admin/reports/tickets.csv".equals(path)) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"campusos-tickets.csv\"");
                send(exchange, 200, new AdminDAO().exportTicketReportCsv());
                return;
            }
            if ("/api/admin/overview".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("success", true, "overview", new AdminDAO().getOverview()));
                return;
            }
            if ("/api/admin/users".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                int page = queryInteger(exchange, "page", 1);
                int pageSize = queryInteger(exchange, "pageSize", 50);
                if (page < 1 || page > 100_000_000) {
                    throw new BadRequestException("Page must be a positive integer");
                }
                if (pageSize < 1 || pageSize > 100) {
                    throw new BadRequestException("Page size must be between 1 and 100");
                }
                UserDAO users = new UserDAO();
                int totalUsers = users.countUsers();
                long totalPages = Math.max(1L, (totalUsers + (long) pageSize - 1) / pageSize);
                if (page > totalPages) {
                    throw new BadRequestException("Page exceeds the available user pages");
                }
                long offset = (long) (page - 1) * pageSize;
                sendJson(exchange, 200, Map.of(
                        "success", true,
                        "users", users.findUsers(pageSize, offset),
                        "page", page,
                        "pageSize", pageSize,
                        "totalUsers", totalUsers,
                        "totalPages", totalPages));
                return;
            }
            if ("/api/admin/users".equals(path) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonNode body = readJson(exchange);
                for (String field : new String[] {"name", "email", "password", "role", "registerNumber", "department", "specialization"}) {
                    if (body.has(field) && !body.path(field).isTextual()) {
                        throw new BadRequestException("User fields must be strings");
                    }
                }
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
            if ("/api/admin/overview".equals(path) || "/api/admin/users".equals(path)) {
                sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
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
            JsonNode staffIdNode = readJson(exchange).path("staffId");
            if (!staffIdNode.isIntegralNumber() || !staffIdNode.canConvertToInt() || staffIdNode.asInt() <= 0) {
                throw new BadRequestException("A positive integer staffId is required");
            }
            int staffId = staffIdNode.asInt();
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

    private int queryInteger(HttpExchange exchange, String name, int defaultValue) throws BadRequestException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) return defaultValue;
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator < 0 || !name.equals(parameter.substring(0, separator))) continue;
            try {
                return Integer.parseInt(parameter.substring(separator + 1));
            } catch (NumberFormatException e) {
                throw new BadRequestException(name + " must be an integer");
            }
        }
        return defaultValue;
    }

    private void prepare(HttpExchange exchange) {
        exchange.setAttribute(REQUEST_START_ATTRIBUTE, System.nanoTime());
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
            recordResponse(exchange, 204);
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
        String token = authorization.substring(7).trim();
        AccessTokenService.Principal principal = tokens.verify(token);
        if (principal == null) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Session expired or invalid. Please sign in again."));
            return null;
        }
        try {
            if (tokenRevocations.isRevoked(token)) {
                sendJson(exchange, 401, Map.of("success", false, "message", "Session has been signed out. Please sign in again."));
                return null;
            }
            java.util.Optional<String> role = userDAO.findRoleById(principal.userId());
            if (role.isEmpty() || !tokens.hasCurrentRole(principal, role.get())) {
                sendJson(exchange, 401, Map.of("success", false, "message", "Account or permissions changed; sign in again."));
                return null;
            }
            return principal;
        } catch (SQLException exception) {
            sendJson(exchange, 503, Map.of("success", false, "message", "Authentication service temporarily unavailable"));
            return null;
        }
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
        recordResponse(exchange, status);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static final class BadRequestException extends Exception {
        private BadRequestException(String message) { super(message); }
    }
}
