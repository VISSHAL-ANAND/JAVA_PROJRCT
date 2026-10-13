# CAMPUSOS

**Campus Operations & Emergency Management System**

CAMPUSOS is a campus operations project designed to bring maintenance issue reporting, ticket tracking, technician assignment, resource booking, notifications, emergency records and administrative workflows into one platform.

The project uses a **React frontend**, a **Java backend**, and **MySQL** for persistent data. It also retains a Java socket-server foundation for networking exercises and future integrations.

> **Project status:** Active academic project. React login, issue submission, automatic priority calculation, ticket creation/assignment logic, and a student's own ticket list are implemented. Other areas shown in navigation or represented by backend classes may still be incomplete. See [Feature Status](#feature-status) before treating a workflow as complete.

---

## Table of contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Feature status](#feature-status)
- [Technology stack](#technology-stack)
- [Roles](#roles)
- [Repository structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
- [Database setup](#database-setup)
- [Configure the backend](#configure-the-backend)
- [Run the application](#run-the-application)
- [API reference](#api-reference)
- [Issue priority and assignment](#issue-priority-and-assignment)
- [Database tables](#database-tables)
- [Security notes](#security-notes)
- [Testing and verification](#testing-and-verification)
- [Troubleshooting](#troubleshooting)
- [Development roadmap](#development-roadmap)
- [Contributing](#contributing)
- [License](#license)

## Overview

Campus maintenance requests are often scattered across messages, calls, and informal follow-ups. CAMPUSOS provides a shared workflow where a campus user can report an issue and track its progress.

### Core workflow

```text
Student reports an issue
        ↓
Java API validates the request
        ↓
Priority is calculated
        ↓
Issue and ticket are saved to MySQL
        ↓
An available technician may be assigned
        ↓
Ticket is visible in the student's ticket list
```

The longer-term workflow includes technician status updates, resource reservations, notifications, emergency broadcasts and administration.

## Architecture

```text
React + Vite (frontend)
        │ HTTP / JSON
        ▼
Java HTTP API (port 8080)
        │
        ▼
Service layer
        │
        ▼
DAO layer ── JDBC ── MySQL (port 3306)

Java socket server (port 5050)
        └── Separate networking foundation
```

- **Frontend:** React components provide login, the dashboard, issue reporting and ticket listing.
- **HTTP API:** Java's built-in `HttpServer` exposes JSON endpoints.
- **Service layer:** Applies application rules such as priority calculation and issue submission.
- **DAO layer:** Executes parameterized SQL statements through JDBC.
- **Database:** MySQL stores users, issues, tickets, resources, bookings, notifications and emergencies.
- **Socket layer:** A separate Java TCP server remains available for socket-programming exercises and future integrations.

## Feature status

| Feature | Current status |
|---|---|
| React + Vite frontend | Implemented |
| Login against MySQL user records | Implemented |
| PBKDF2 password verification and login rate limiting | Implemented in the authentication service |
| Submit an issue from React | Implemented |
| Automatic priority calculation | Implemented |
| Persist issue and create ticket in one transaction | Implemented |
| Technician selection by availability and specialization | Implemented in issue submission |
| Student ticket list from MySQL | Implemented |
| Live student ticket counts | Implemented in the current dashboard |
| Technician ticket lifecycle UI and API | In progress |
| Admin management and reporting | In progress |
| Resource booking UI/API integration | In progress |
| Notifications UI/API integration | In progress |
| Emergency broadcast UI/API integration | In progress |
| Production-grade API authorization/session management | Not yet complete |
| Automated full end-to-end test suite | Not yet complete |

A menu item or database table does not, by itself, mean its complete workflow is finished. This table is the intended source of truth for the current implementation.

## Technology stack

| Layer | Technologies |
|---|---|
| Frontend | React 19, JavaScript, HTML, CSS, Vite |
| Backend | Java 17, Java HTTP Server, Java TCP sockets |
| API format | HTTP, JSON |
| Database | MySQL 8, JDBC |
| JSON processing | Jackson Databind |
| Build | Maven |
| Version control | Git and GitHub |

## Roles

CAMPUSOS defines the following user roles:

| Role | Intended responsibility |
|---|---|
| `STUDENT` | Report campus issues and track submitted tickets |
| `FACULTY` | Report and track campus issues |
| `MAINTENANCE` | Handle maintenance work |
| `TECHNICIAN` | Handle assigned technical tickets |
| `ADMIN` | Oversee tickets and campus operations |

Role-specific workflows are being developed incrementally. Do not assume every role already has a complete, independently authorized web dashboard.

## Repository structure

```text
JAVA_PROJRCT/
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       └── styles.css
├── resources/
│   ├── campusos.sql
│   └── config.properties
├── src/
│   ├── api/          # Java HTTP API
│   ├── client/       # Client session and socket client code
│   ├── dao/          # JDBC data-access classes
│   ├── database/     # Database connection and initialization
│   ├── exception/    # Application-specific exceptions
│   ├── gui/          # Legacy Swing interface
│   ├── model/        # Domain models and enums
│   ├── network/      # TCP server/client classes
│   ├── security/     # Password hashing, access control, rate limiters
│   ├── service/      # Application and business logic
│   └── util/         # Validation utilities
├── pom.xml
└── README.md
```

Some classes belong to the earlier Swing version and remain in the repository while the React web interface is being built.

## Prerequisites

Install the following:

- **JDK 17 or newer**
- **Maven 3.9 or newer**
- **MySQL 8.x**
- **Node.js LTS and npm**
- **Git**

Check your installation:

```powershell
java -version
mvn -version
mysql --version
node --version
npm --version
git --version
```

## Getting started

Clone the repository and enter its root directory:

```powershell
git clone https://github.com/VISSHAL-ANAND/JAVA_PROJRCT.git
cd JAVA_PROJRCT
```

If you already have a local copy, update it with:

```powershell
git pull origin main
```

**Important:** If Git reports that local changes would be overwritten, inspect `git status` first. Preserve your local database configuration before discarding or restoring any configuration file.

## Database setup

1. Start your MySQL server.
2. Open MySQL Workbench or the MySQL command-line client.
3. Run the schema script from the project root:

```powershell
mysql -u root -p < resources/campusos.sql
```

Alternatively, open `resources/campusos.sql` in MySQL Workbench and execute it.

The script creates the `campusos` database and the core tables:

- `users`
- `issues`
- `tickets`
- `resources`
- `bookings`
- `notifications`
- `emergencies`

The database schema does not automatically create a known demo account with a valid password hash. Create a user through a secure registration flow when available, or insert a user whose password was hashed using the application's `PasswordHasher`. Do not store plaintext passwords.

## Configure the backend

Open `resources/config.properties`. Set the values for your local MySQL instance:

```properties
db.url=jdbc:mysql://localhost:3306/campusos
db.username=root
db.password=YOUR_LOCAL_MYSQL_PASSWORD
db.pool.size=10
```

Replace `YOUR_LOCAL_MYSQL_PASSWORD` with your own MySQL password.

**Never commit your real database password, API keys, or other secrets to GitHub.** Keep machine-specific configuration local. Before pushing changes, check:

```powershell
git status
git diff -- resources/config.properties
```

The repository's tracked configuration is intended as a template. If your local file contains real credentials, do not overwrite it without first backing it up.

## Run the application

Open **three terminals** from the repository root.

### 1. Compile the Java backend

```powershell
mvn clean compile
```

Continue only if Maven reports `BUILD SUCCESS`.

### 2. Start the Java servers

```powershell
mvn exec:java "-Dexec.mainClass=network.ServerMain"
```

By default, the HTTP API listens on port **8080** and the TCP socket server listens on port **5050**. Keep this terminal running.

### 3. Install frontend dependencies and start React

In another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite, normally:

```text
http://localhost:5173
```

The Vite development server proxies no API routes by default; the current frontend calls the Java API at `http://localhost:8080`.

### Build the frontend for production

From the `frontend` directory:

```powershell
npm run build
```

This creates the optimized frontend output in `frontend/dist`. A production web server and deployment configuration are still required to serve it and route API requests securely.

## API reference

The current HTTP API accepts and returns JSON. The frontend is configured for local development at `http://localhost:5173`.

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Request:

```json
{
  "email": "student@example.com",
  "password": "your-password"
}
```

Success response (example shape):

```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": 1,
    "name": "Example Student",
    "email": "student@example.com",
    "role": "STUDENT"
  }
}
```

Invalid credentials return HTTP `401`. This endpoint verifies credentials but the current web implementation does not yet establish a production-grade server-side session or token.

### Submit an issue

```http
POST /api/issues
Content-Type: application/json
```

Request:

```json
{
  "reporterId": 1,
  "title": "Projector not working",
  "description": "The projector does not turn on.",
  "category": "Electrical",
  "location": "AB1 Room 204"
}
```

The API validates required fields, calculates priority, creates the issue and ticket, and attempts to assign an available technician. The response includes the created ticket ID and calculated priority.

### List tickets reported by a student

```http
GET /api/tickets/reporter/{reporterId}
```

Example:

```http
GET /api/tickets/reporter/1
```

Returns a JSON object containing the ticket list, including fields such as ID, title, category, location, priority, status and creation time.

> **API security limitation:** The current issue and ticket endpoints accept a client-supplied `reporterId`. They are suitable for local development, not untrusted public deployment. Before production use, derive the user identity from a verified session/token, enforce role and ownership checks on every endpoint, validate all input, and configure appropriate CORS and transport security.

## Issue priority and assignment

Priority is calculated from keywords in the issue title and description. Current rules include:

| Priority | Example keywords |
|---|---|
| `CRITICAL` | `fire`, `gas leak`, `electric shock`, `security breach` |
| `HIGH` | `server down`, `network down`, `water leak`, `power failure` |
| `MEDIUM` | `not working`, `broken`, `urgent` |
| `LOW` | Default when no higher-priority rule matches |

This is a keyword-based rule system, not an AI classifier. The current assignment query prefers an available maintenance/technician user whose specialization matches the issue category; otherwise, it can select another available user. Assignment is skipped if no suitable available user is found.

## Database tables

| Table | Purpose |
|---|---|
| `users` | User identity, role, department and technician availability |
| `issues` | Issue description, category, location, attachment path and priority |
| `tickets` | Issue tracking, reporter, assigned technician and lifecycle status |
| `resources` | Campus resources and availability |
| `bookings` | Resource reservations and time ranges |
| `notifications` | User notification records |
| `emergencies` | Emergency messages and creator information |

## Security notes

The codebase includes security-related foundations:

- **Password hashing:** PBKDF2-HMAC-SHA256 with a random salt and a stored hash format of `pbkdf2$iterations$salt$hash`.
- **Login rate limiting:** The authentication service limits repeated login attempts.
- **Prepared statements:** DAO queries use parameterized SQL for database operations.
- **Role and access-control classes:** Role checks are represented in the security layer.

These foundations do **not** mean the web application is production-secure. The current React login stores the returned user only in frontend state; API requests are not yet protected by a verified session/token. There is also no complete CSRF/session lifecycle, deployment hardening, audit trail, or comprehensive authorization test suite. Do not expose this development server to the public internet with real campus or personal data.

## Testing and verification

Run the backend compilation check:

```powershell
mvn clean compile
```

Run the frontend production build:

```powershell
cd frontend
npm run build
```

Manual end-to-end check for the currently implemented student workflow:

1. Confirm MySQL is running and `resources/config.properties` is correct.
2. Start the Java servers.
3. Start the Vite frontend.
4. Log in with a database account whose password is stored in the expected PBKDF2 format.
5. Open **Report Issue** and submit a test issue.
6. Confirm a ticket ID and priority are returned.
7. Confirm the ticket appears in the student's ticket list.
8. Check the `issues`, `tickets`, and, where assignment occurs, `notifications` tables in MySQL.

Do not mark a workflow as tested until it has been run against the local database.

## Troubleshooting

### Maven says `BUILD FAILURE`

Read the first compiler error, fix that issue, and run:

```powershell
mvn clean compile
```

Do not start the server until compilation succeeds.

### MySQL connection fails

- Confirm the MySQL service is running.
- Verify the database name, username, password and port in `resources/config.properties`.
- Confirm the schema script was executed.
- Check that port `3306` is available.

### Login returns “Invalid email or password”

- Confirm the user exists in the `users` table.
- Confirm the role matches one of the supported role values.
- Ensure the database password column contains a valid PBKDF2 hash created by the application, not plaintext.
- Check whether the login rate limiter temporarily blocked repeated attempts.

### The frontend cannot reach the API

- Confirm the Java server started and printed that the HTTP API is listening on port `8080`.
- Open the frontend at `http://localhost:5173`.
- Check the browser developer console and terminal for CORS or connection errors.
- Ensure no other application is occupying port `8080`.

### Port is already in use

Stop the process using the port or change the server arguments. The current argument order for `ServerMain` is:

```text
<socket-port> <thread-pool-size> <http-api-port>
```

For example:

```powershell
mvn exec:java "-Dexec.mainClass=network.ServerMain" "-Dexec.args=5051 10 8081"
```

If you change the HTTP API port, update the frontend API URLs as well.

### Git warns about local changes

Run `git status` and review the affected files. Do not use `git restore` on `resources/config.properties` unless you have saved your local credentials elsewhere.

## Development roadmap

Development is intended to proceed in small, testable phases:

1. **Core foundation:** domain models, enums and validation.
2. **Database layer:** schema, JDBC connection and DAOs.
3. **Authentication:** password hashing, login and role model.
4. **Ticket foundation:** priority calculation, ticket creation and technician assignment.
5. **Networking:** socket server and client handling.
6. **Campus operations:** resources, bookings, notifications and emergency records.
7. **Interface:** original Swing interface and React web frontend.
8. **Integration and hardening:** end-to-end testing, authorization, lifecycle enforcement and reliability.
9. **Technician workflow:** technician dashboard, assigned ticket list and controlled status transitions.
10. **Admin workflow:** user/ticket oversight and operational reports.

The roadmap may change as features are implemented and verified.

## Contributing

For each change:

1. Pull the latest `main` branch.
2. Make one focused change.
3. Run `mvn clean compile` for backend changes.
4. Run `npm run build` for frontend changes.
5. Test the affected workflow manually or with automated tests.
6. Review `git diff` and confirm no secrets or generated folders are included.
7. Commit with a clear message.

Do not commit `node_modules/`, `target/`, `frontend/dist/`, local credentials, or other machine-specific files.

## License

No license has been specified in this repository yet. Unless a license is added, reuse and redistribution are not automatically granted.

## Repository

[VISSHAL-ANAND/JAVA_PROJRCT on GitHub](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT)
