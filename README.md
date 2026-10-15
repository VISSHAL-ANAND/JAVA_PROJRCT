# CAMPUSOS

**Campus Operations & Emergency Management System**

CAMPUSOS is a campus operations project designed to bring maintenance issue reporting, ticket tracking, technician assignment, resource booking, notifications, emergency records and administrative workflows into one platform.

The project uses a **React frontend**, a **Java backend**, and supports **MySQL for local development** plus **PostgreSQL/Supabase** as the planned hosted database. The Java API remains a separate service; Vercel is intended for the frontend only. It also retains a Java socket-server foundation for networking exercises and future integrations.

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
Issue and ticket are saved to the configured SQL database
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
DAO layer ── JDBC ── MySQL (local) / PostgreSQL (Supabase)

Java socket server (port 5050)
        └── Separate networking foundation
```

- **Frontend:** React components provide login, the dashboard, issue reporting and ticket listing.
- **HTTP API:** Java's built-in `HttpServer` exposes JSON endpoints.
- **Service layer:** Applies application rules such as priority calculation and issue submission.
- **DAO layer:** Executes parameterized SQL statements through JDBC.
- **Database:** JDBC connects to MySQL or PostgreSQL depending on `CAMPUSOS_DB_URL`; `resources/supabase_schema.sql` defines the hosted PostgreSQL schema.
- **Socket layer:** A separate Java TCP server remains available for socket-programming exercises and future integrations.

## Feature status

| Feature | Current status |
|---|---|
| React + Vite frontend | Implemented |
| Login against the configured SQL database | Implemented with signed bearer tokens |
| PBKDF2 password verification and login rate limiting | Implemented in the authentication service |
| Submit an issue from React | Implemented |
| Automatic priority calculation | Implemented |
| Persist issue and create ticket in one transaction | Implemented |
| Technician selection by availability and specialization | Implemented in issue submission |
| User ticket list from the configured SQL database | Implemented with ownership checks |
| Live student ticket counts | Implemented in the current dashboard |
| Technician ticket lifecycle (assigned → in progress → resolved → closed) | Implemented with role/assignment checks and status history; database integration testing pending |
| Admin overview, account creation, user directory, staff assignment, staff availability and ticket CSV export | Implemented; editing/deleting user identity and broader operational analytics remain incomplete |
| Resource inventory, reservations, personal booking list and future cancellation | Implemented; live database/concurrency testing pending |
| Notification inbox and mark-read | Implemented; live database testing pending |
| Emergency feed and admin-only broadcast | Implemented; live database testing pending |
| API authentication and route authorization | Signed bearer tokens and core role/ownership checks implemented; token revocation and comprehensive security testing remain |
| Automated tests | 15 tests pass in CI, including a PostgreSQL 17 workflow integration test; live Supabase verification remains |

A menu item or database table does not, by itself, mean its complete workflow is finished. This table is the intended source of truth for the current implementation.

## Technology stack

| Layer | Technologies |
|---|---|
| Frontend | React 19, JavaScript, HTML, CSS, Vite |
| Backend | Java 17, Java HTTP Server, Java TCP sockets |
| API format | HTTP, JSON |
| Database | MySQL 8 locally; PostgreSQL/Supabase target, JDBC |
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
- **MySQL 8.x** for the current local setup, or a Supabase PostgreSQL project for hosted-database work
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

Alternatively, open `resources/campusos.sql` in MySQL Workbench and execute it. For Supabase PostgreSQL, use the separate `resources/supabase_schema.sql` script in the Supabase SQL Editor; do not run the MySQL schema there.

For an **existing** database, do not blindly rerun the full MySQL schema (its original index statements are not all idempotent). Use the appropriate one-time migration instead:
- MySQL: `resources/migrations/mysql/001_ticket_history_and_query_indexes.sql` (run in MySQL Workbench or the MySQL CLI; it uses a temporary stored procedure to add indexes only when missing).
- PostgreSQL: `resources/migrations/postgres/001_ticket_history_and_query_indexes.sql` (review and apply to the existing CAMPUSOS database).

The script creates the `campusos` database and the core tables:

- `users`
- `issues`
- `tickets`
- `resources`
- `bookings`
- `notifications`
- `emergencies`

The schema scripts do not automatically create a known demo account with a valid password hash. Create a user through a secure registration flow when available, or insert a user whose password was hashed using the application's `PasswordHasher`. Do not store plaintext passwords.

## Configure the backend

For local development, you can copy `resources/config.properties.example` to `resources/config.properties` and set your local values. The tracked `resources/config.properties` on GitHub contains only a placeholder.

**Preferred for deployment:** supply credentials through environment variables. The Java backend reads these variables first and falls back to `resources/config.properties`:

| Environment variable | Purpose |
|---|---|
| `CAMPUSOS_DB_URL` | JDBC URL, e.g. `jdbc:mysql://localhost:3306/campusos` locally or `jdbc:postgresql://HOST:5432/postgres?sslmode=require` for PostgreSQL |
| `CAMPUSOS_DB_USERNAME` | Database username |
| `CAMPUSOS_DB_PASSWORD` | Database password |
| `CAMPUSOS_DB_POOL_SIZE` | Optional JDBC connection-pool maximum (1–30; defaults to `db.pool.size` or 10) |
| `CAMPUSOS_AUTH_SECRET` | At least 32 characters; signs access tokens. Required as a stable secret for hosted/multi-instance use. If omitted locally, a random process-only secret is generated and tokens expire on restart. |
| `CAMPUSOS_ENV` | The Docker image sets this to `production`, which makes startup fail if `CAMPUSOS_AUTH_SECRET` is missing or shorter than 32 characters. Leave unset only for local development. |
| `CAMPUSOS_CORS_ORIGINS` | Comma-separated exact allowed frontend origins; defaults to `http://localhost:5173` |

Example PowerShell session for local testing:

```powershell
$env:CAMPUSOS_DB_URL = "jdbc:mysql://localhost:3306/campusos"
$env:CAMPUSOS_DB_USERNAME = "root"
$env:CAMPUSOS_DB_PASSWORD = "YOUR_LOCAL_PASSWORD"
mvn exec:java "-Dexec.mainClass=network.ServerMain"
```

Use a dedicated least-privilege database account outside local development; do not use the MySQL root account in production. Never commit real passwords, API keys, or other secrets. Before pushing, inspect `git status` and `git diff`, especially changes to configuration files.

### Supabase JDBC connection notes

For the persistent Java API, use the **Supabase session pooler** (port `5432`) if the hosting network is IPv4-only, and require TLS. The direct database host may require IPv6. Avoid the transaction pooler (port `6543`) for the current JDBC implementation because it uses prepared statements.

Example environment values (use the actual host and project-specific username from Supabase's Connect dialog):

```text
CAMPUSOS_DB_URL=jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require
CAMPUSOS_DB_USERNAME=campusos_app.<project-ref>
CAMPUSOS_DB_PASSWORD=<database-role-password>
CAMPUSOS_DB_POOL_SIZE=5
```

1. Apply `resources/supabase_schema.sql` to a **dedicated CAMPUSOS Supabase project**, not an unrelated project.
2. Review and run `resources/supabase_app_role.sql.example` with a strong password placeholder replaced in the Supabase SQL Editor. Do not commit the real password.
3. Configure these values only on the Java API host, not as `VITE_` frontend variables.
4. Set `CAMPUSOS_AUTH_SECRET` to a separate stable random secret and set `CAMPUSOS_CORS_ORIGINS` to the exact deployed Vercel origin.
5. Test `/api/health/ready` after configuration; it must return HTTP 200 before database-backed workflows can work.

The JDBC connection pool is bounded to protect the database. Keep the pool size small enough for the Supabase project's connection limit and the number of Java API instances.

### Continuous integration

GitHub Actions currently verifies:
- Java 17 compilation, unit tests, and packaging.
- React dependency installation and Vite production build.
- Docker image build for the HTTP-only Java API.
- PostgreSQL 17 schema application and database-backed workflow test (user creation/authentication, issue priority, assignment/status transitions/history, notifications, emergency broadcast, and booking capacity/cancellation).

The PostgreSQL service test is a clean local PostgreSQL compatibility check, **not** a connection test against the actual Supabase project.

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

The frontend reads its API origin from `VITE_API_BASE_URL` and defaults to `http://localhost:8080` for local development. Copy `frontend/.env.example` to `frontend/.env.local` if you need to override it locally. For a hosted frontend, configure `VITE_API_BASE_URL` in the frontend host's build environment to the reachable Java API origin. This value is public browser configuration, not a secret. The Java API must separately allow the deployed frontend origin through CORS.

### Build the frontend for production

From the `frontend` directory:

```powershell
npm run build
```

This creates the optimized frontend output in `frontend/dist`. A production web server and deployment configuration are still required to serve it and route API requests securely.

### Frontend API URL configuration

The optional file `frontend/.env.example` documents the local default. For local overrides, create `frontend/.env.local`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Do not commit `.env.local`. For production, set `VITE_API_BASE_URL` in the frontend host's project settings before building. The current Java API still needs a Java-capable host and production CORS configuration; this setting alone does not make the backend deployable on Vercel.

## Hosting preparation (not deployed)

The intended hosting split is:

- **Frontend:** Vercel, with the repository root as the project root. The root `vercel.json` installs the locked frontend dependencies, builds `frontend`, publishes `frontend/dist`, and routes browser navigation to the SPA entry point.
- **Java API:** a Java/container-capable host using the repository's `Dockerfile`. Vercel's static frontend deployment does not host this long-running Java HTTP server.
- **Database:** a dedicated Supabase PostgreSQL project, using the reviewed schema and a restricted JDBC role.

Before hosting, configure these values on the **Java API host** only: `CAMPUSOS_DB_URL`, `CAMPUSOS_DB_USERNAME`, `CAMPUSOS_DB_PASSWORD`, `CAMPUSOS_DB_POOL_SIZE`, `CAMPUSOS_AUTH_SECRET`, and `CAMPUSOS_CORS_ORIGINS`. Set `VITE_API_BASE_URL` in Vercel to the public Java API origin using HTTPS. Production builds now fail closed if this variable is missing rather than silently trying the developer's localhost. Never put database credentials or the auth secret in Vercel's `VITE_*` variables.

Use `/api/health` as a liveness check and `/api/health/ready` as a database-aware readiness check. Hosting has not been configured or deployed; the Supabase schema and connection still require verification against a dedicated CAMPUSOS project. Do not run database keep-alive jobs against a live project until its plan, supported policy, and actual pause behavior are confirmed.

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
  "token": "<signed bearer token>",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "name": "Example Student",
    "email": "student@example.com",
    "role": "STUDENT"
  }
}
```

Invalid credentials return HTTP `401`. The response includes a signed bearer token valid for two hours. Protected requests must send `Authorization: Bearer <token>`. The browser keeps the token in `sessionStorage` for the current tab session. Configure a strong `CAMPUSOS_AUTH_SECRET` for hosted use; without it, the local development token key changes on process restart.

### Submit an issue

```http
POST /api/issues
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "title": "Projector not working",
  "description": "The projector does not turn on.",
  "category": "Electrical",
  "location": "AB1 Room 204"
}
```

The API validates required fields, derives the reporter identity from the verified token (it ignores any client-supplied reporter ID), calculates priority, creates the issue and ticket, and attempts to assign an available technician. The response includes the created ticket ID and calculated priority.

### List tickets reported by a student

```http
GET /api/tickets/reporter/{yourUserId}\nAuthorization: Bearer <token>
```

Example:

```http
GET /api/tickets/reporter/1
```

Returns a JSON object containing the ticket list, including fields such as ID, title, category, location, priority, status and creation time.

The ticket endpoint requires a valid bearer token and only returns tickets when the requested reporter ID matches the authenticated user. Issue submission is limited to `STUDENT` and `FACULTY` tokens. The API includes staff ticket workflow, ticket history, notifications, resource booking/cancellation, emergency feed/broadcast, and admin user/assignment routes. Token revocation, verification against a dedicated live Supabase project, and rate-limit hardening across instances remain release blockers.

### Other authenticated API routes

| Method | Route | Access |
|---|---|---|
| GET | `/api/health` | Public liveness check |
| GET | `/api/health/ready` | Public readiness check; returns 503 when the database is unavailable |
| GET | `/api/tickets/{ticketId}/history` | Ticket reporter, assigned staff, or admin |
| GET | `/api/staff/tickets` | Maintenance/technician see assigned queue; admin sees all |
| POST | `/api/staff/tickets/{ticketId}/status` | Assigned staff or admin; forward lifecycle transitions only |
| GET | `/api/notifications/me` | Authenticated user; own inbox |
| POST | `/api/notifications/{notificationId}/read` | Authenticated user; own notification |
| GET | `/api/resources` | Authenticated user; available inventory |
| GET | `/api/bookings/mine` | Authenticated user; own reservations |
| POST | `/api/bookings` | Authenticated user; create reservation |
| POST | `/api/bookings/{bookingId}/cancel` | Owner can cancel future reservation |
| GET | `/api/emergencies` | Authenticated user |
| POST | `/api/emergencies` | ADMIN only; creates alert and notifications |
| GET | `/api/admin/overview` | ADMIN only |
| GET | `/api/admin/reports/tickets.csv` | ADMIN only; downloads the 10,000 most recent tickets with spreadsheet-formula protection |
| GET | `/api/admin/users` | ADMIN only |
| POST | `/api/admin/users` | ADMIN only; password is hashed server-side and minimum length is 12 characters |
| POST | `/api/admin/users/{userId}/availability` | ADMIN only; staff availability |
| GET | `/api/admin/staff` | ADMIN only; assignable staff |
| POST | `/api/admin/tickets/{ticketId}/assign` | ADMIN only; assign an OPEN ticket |

Status changes and assignments are recorded in `ticket_history`. Database-backed behavior still needs verification against the actual selected database.

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
| `ticket_history` | Ticket status transitions and audit timestamps |

## Security notes

The codebase includes security-related foundations:

- **Password hashing:** PBKDF2-HMAC-SHA256 with a random salt and a stored hash format of `pbkdf2$iterations$salt$hash`.
- **Login rate limiting:** The authentication service limits repeated login attempts.
- **Prepared statements:** DAO queries use parameterized SQL for database operations.
- **Role and access-control classes:** Role checks are represented in the security layer.

These foundations do **not** mean the web application is production-secure. The React client stores a signed bearer token in `sessionStorage`, which is accessible to same-origin JavaScript and increases the impact of any cross-site scripting (XSS) flaw. The API verifies tokens and checks current database roles plus route-specific ownership/role rules, and CI includes unit and PostgreSQL-backed HTTP integration tests. Production TLS/CORS configuration, token revocation, an audit trail, distributed rate limiting, and a complete security review remain outstanding. Do not expose this development server to the public internet with real campus or personal data.

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
