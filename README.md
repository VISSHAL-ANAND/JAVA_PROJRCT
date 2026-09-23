# CAMPUSOS

**Campus Operations & Emergency Management System**

CAMPUSOS is a Java-based campus operations platform for issue reporting, ticket management, smart technician assignment, maintenance work, resource booking, notifications, emergency operations and administrative reporting.

## 1. Architecture

Swing GUI → Java Socket / Network Layer → Service Layer → DAO → JDBC → MySQL

Core workflow: Report Issue → Priority Detection → Ticket Creation → Smart Assignment → Maintenance Work → Status Updates → Notifications → Reports.

## 2. Main Features

- Secure authentication with PBKDF2-HMAC-SHA256 password hashing.
- Role-based access control.
- Login rate limiting and request-rate-limiter foundation.
- Student, Faculty/Staff, Maintenance, Technician and Admin workflows.
- Issue reporting with category, location, severity, description and attachment path.
- Automatic priority detection: LOW, MEDIUM, HIGH, CRITICAL.
- Smart technician assignment using availability and specialization.
- Ticket states: OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED.
- Resource availability and booking.
- Notifications and emergency records.
- Swing desktop interface.
- Java Socket server and multithreaded client handling.
- JDBC/MySQL persistence.
- Admin dashboard and reporting.

## 3. User Roles

| Role | Main access |
|---|---|
| STUDENT | Dashboard, report issue, own tickets, resource booking, notifications, emergency |
| FACULTY | Dashboard, report issue, tickets, resource booking, notifications, emergency |
| MAINTENANCE | Dashboard, maintenance tasks, notifications, emergency |
| TECHNICIAN | Assigned technical work, ticket workflow, notifications, emergency |
| ADMIN | Dashboard, all tickets, resources, reports, notifications, emergency |

Role access is enforced through the authenticated session and `AccessControl`.

## 4. Project Structure

- `src/model` — users, roles, issues, tickets, resources, bookings, notifications and emergencies.
- `src/service` — authentication, priority, assignment, ticket and emergency business logic.
- `src/dao` — JDBC data-access classes.
- `src/database` — database connection and initialization.
- `src/gui` — Login and role-aware Swing screens.
- `src/network` — socket server, client and connection management.
- `src/security` — password hashing, role access and rate limiting.
- `src/exception` — application-specific exceptions.
- `resources/campusos.sql` — MySQL schema.
- `resources/config.properties` — local database configuration.
- `src/test/Phase8SmokeTest.java` — integration smoke test.

## 5. Requirements

- JDK 17+
- Maven 3.9+
- MySQL 8.x
- Git

Verify with `java -version`, `mvn -version` and `mysql --version`.

## 6. Clone

`git clone https://github.com/VISSHAL-ANAND/JAVA_PROJRCT.git`

`cd JAVA_PROJRCT`

## 7. Database Setup

1. Start MySQL.
2. Execute `resources/campusos.sql` using MySQL CLI or MySQL Workbench.
3. Open `resources/config.properties`.
4. Set your local credentials:

`db.url=jdbc:mysql://localhost:3306/campusos`

`db.username=root`

`db.password=YOUR_MYSQL_PASSWORD`

`db.pool.size=10`

Never commit a real database password to GitHub.

## 8. Build

Run from the project root:

`mvn clean compile`

Package with:

`mvn clean package`

## 9. Start the Socket Server

Terminal 1:

`mvn exec:java -Dexec.mainClass=network.ServerMain`

Default server: `localhost:5050`.

Custom port and thread pool:

`mvn exec:java -Dexec.mainClass=network.ServerMain -Dexec.args="5050 10"`

Arguments are `<port> <thread-pool-size>`.

## 10. Start the CAMPUSOS GUI

Terminal 2:

`mvn exec:java -Dexec.mainClass=gui.LoginFrame`

The split-screen CAMPUSOS login opens. Authentication is performed against MySQL and the user is routed to the role-specific dashboard.

## 11. Login Access

The login role selector supports `AUTO`, `STUDENT`, `FACULTY`, `MAINTENANCE` and `ADMIN`.

`AUTO` lets the database account determine the role. If a role is manually selected, it must match the authenticated account.

There are currently no guaranteed default/demo credentials in the repository. Create database users with passwords stored in the PBKDF2 format expected by `PasswordHasher`.

Do not insert plaintext passwords and expect secure authentication to work.

## 12. Security

### Password hashing

`PBKDF2WithHmacSHA256`, 120,000 iterations, 16-byte random salt and 256-bit derived key.

Stored format: `pbkdf2$iterations$salt$hash`.

### Login rate limiting

Current configuration: 5 attempts within 5 minutes, followed by a 10-minute temporary block.

### Request rate limiting

`RequestRateLimiter` provides a 60 requests/minute foundation for network/API operations.

### Role-based access

`AccessControl` checks the current authenticated user's role before protected operations are exposed.

### Database security

DAO operations use `PreparedStatement` rather than concatenating user input into SQL.

## 13. UI Screens

1. Login
2. Student Dashboard
3. Faculty / Staff Dashboard
4. Report Issue
5. Ticket Management
6. Resource Booking
7. Emergency Report
8. Maintenance Staff Dashboard
9. Admin Dashboard
10. Notifications
11. Reports

The dashboard navigation changes according to the authenticated role.

## 14. Issue and Ticket Logic

Priority detection currently recognizes examples such as:

- CRITICAL: fire, gas leak, electric shock, security breach.
- HIGH: server down, network down, water leak, power failure.
- MEDIUM: not working, broken, urgent.
- Otherwise LOW.

Technician assignment filters available technicians, prefers specialization matching the issue category, and uses technician name as the deterministic tie-breaker.

## 15. Socket Layer

Important network classes:

- `CampusServer`
- `ClientHandler`
- `CampusClient`
- `ConnectionManager`
- `ServerMain`
- `ClientMain`

Basic socket test:

Terminal 1: `mvn exec:java -Dexec.mainClass=network.ServerMain`

Terminal 2: `mvn exec:java -Dexec.mainClass=network.ClientMain`

Expected basic response: `Server response: PONG`.

## 16. Testing

Compile:

`mvn clean compile`

Run the Phase 8 smoke test:

`mvn exec:java -Dexec.mainClass=test.Phase8SmokeTest`

Expected output: `PHASE_8_SMOKE_TEST_PASSED`.

The smoke test checks priority calculation, technician assignment, ticket creation and ticket status.

## 17. Recommended Full Demo

1. Start MySQL.
2. Start the CAMPUSOS socket server.
3. Open the GUI.
4. Login as Student.
5. Report an issue.
6. Verify ticket creation and priority.
7. Login as Technician/Maintenance.
8. Process the assigned ticket.
9. Login as Admin.
10. Review tickets and reports.
11. Demonstrate resource booking, notifications and emergency workflow.

## 18. Java Concepts Demonstrated

OOP, inheritance, abstraction, encapsulation, polymorphism, enums, collections, generics, streams, lambdas, exceptions, custom exceptions, JDBC, MySQL, Swing, socket programming, multithreading, ExecutorService, synchronization, ConcurrentHashMap, authentication, password hashing, RBAC and rate limiting.

## 19. Development Phases

1. Core Foundation
2. Database Layer
3. Authentication
4. Ticket System
5. Socket Server + Multithreaded Clients
6. Resources + Bookings + Notifications + Emergency
7. Swing GUI
8. Integration + Testing + Security Hardening

## 20. Current Status

The repository contains the project foundation, database layer, services, networking layer, role-aware Swing UI and security foundation.

Before calling the system production-ready, run the complete compile, database integration, socket integration, authentication, RBAC, rate-limit and end-to-end tests. This is currently an academic/client-project implementation and is not security-certified production software.

## 21. Troubleshooting

### MySQL connection error

Check that MySQL is running, the `campusos` database exists, credentials in `resources/config.properties` are correct and port 3306 is available.

### Login failure

Check that the user exists, the role is valid, the password is stored using the PBKDF2 format expected by `PasswordHasher`, and the account has not been temporarily rate-limited.

### GUI does not start

Run `mvn clean compile`, then `mvn exec:java -Dexec.mainClass=gui.LoginFrame`.

### Port 5050 is busy

Use another port, for example `mvn exec:java -Dexec.mainClass=network.ServerMain -Dexec.args="6060 10"`.

## 22. Repository

https://github.com/VISSHAL-ANAND/JAVA_PROJRCT

## 23. Project Goal

CAMPUSOS brings campus issues, ticket management, smart assignment, maintenance, resource booking, notifications, emergency operations, administration and reporting into one Java-based platform.