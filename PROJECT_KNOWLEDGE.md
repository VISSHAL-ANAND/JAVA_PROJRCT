# CAMPUSOS — Project Knowledge & Engineering Log

> **Purpose:** This is the persistent handover document for CAMPUSOS. Update it after every meaningful investigation, code change, test, bug fix, design decision, and deployment change. It records what is known, what is verified, what remains incomplete, and the next safe step.
>
> **Last reviewed:** 2026-10-15  
> **Primary branch:** `main`  
> **Repository:** https://github.com/VISSHAL-ANAND/JAVA_PROJRCT

## 1. Product vision

CAMPUSOS (Campus Operations & Emergency Management System) is intended to be a dependable campus operations product, not merely a classroom demo. The long-term goal is a secure, maintainable, deployable web application for students, faculty, maintenance staff, technicians, and administrators.

Core intended workflows:
1. Users sign in and receive access appropriate to their role.
2. A campus user reports an issue with category, location, and description.
3. The system validates the request, calculates a transparent priority, creates a ticket, and assigns suitable staff where possible.
4. Users can follow ticket status and receive notifications.
5. Authorized staff manage assigned work; administrators oversee users, tickets, reports, resources, and emergency operations.
6. The system is tested, observable, recoverable, and safe to deploy.

**Production rule:** Do not describe a feature as complete just because a navigation item, database table, or backend class exists. A workflow is complete only when its UI, API, authorization, persistence, failure handling, and tests work together.

## 2. Non-negotiable development rules

- Work on **`main`** unless the owner explicitly asks otherwise.
- Do not blindly merge `side`; it diverged from `main` as of 2026-10-13.
- Before editing, inspect the current source and understand its callers/schema.
- Use small, reviewable changes. Avoid replacing working UI or unrelated code without a reason.
- Follow: **Inspect → Plan → Implement → Build/Test → Review diff → Commit → Update this log**.
- Never claim a test passed unless it was actually run and its result observed.
- Never commit passwords, tokens, local IDE paths, generated binaries, build output, or local machine configuration.
- Preserve existing user data. Database migrations must be explicit and reversible where practical.
- Use prepared SQL statements; validate input on the server; return safe error messages; keep internal exception details in server logs, not public API responses.
- Keep README and this file aligned with actual implementation.
- After every work session, update **Section 12 — Engineering log** and **Section 13 — Next actions**.

## 3. Current architecture

```text
React 19 + Vite
      │ HTTP / JSON
      ▼
Java HTTP API (port 8080)
      │
      ▼
Service layer (business rules)
      │
      ▼
DAO layer / JDBC
      │
      ▼
MySQL 8 (local) or Supabase PostgreSQL (hosted target)

Separate Java TCP socket server (port 5050)
```

The Java backend is a Maven project. Java source is under `src/`; `resources/` is copied to the classpath by Maven. The HTTP API currently uses Java's built-in `com.sun.net.httpserver.HttpServer` and Jackson for JSON.

### Main source areas

- `frontend/src/App.jsx` — current React login and student dashboard/issue reporting UI.
- `frontend/src/styles.css` — frontend styling.
- `frontend/src/main.jsx` — React entry point.
- `frontend/vite.config.js` — Vite configuration.
- `src/api/ApiServer.java` — HTTP routes and request/response handling.
- `src/service/` — authentication, issue, priority, ticket, booking, notification, resource and emergency business logic.
- `src/dao/` — SQL access.
- `src/database/DBConnection.java` — MySQL configuration and JDBC connection creation.
- `src/model/` — domain models and roles.
- `src/security/` — password hashing, rate limiting, and access-control-related classes.
- `src/network/` — TCP socket server/client.
- `resources/campusos.sql` — MySQL local-development schema/setup SQL.
- `resources/supabase_schema.sql` — initial PostgreSQL schema for Supabase; must be applied and verified against the connected project before use.
- `resources/config.properties` — local database configuration. The tracked GitHub version on `main` currently contains a placeholder password; never replace it with a real secret.
- `pom.xml` — Maven build and dependencies.
- `README.md` — public setup, feature status, API, and contributor-facing documentation.
- `.github/workflows/ci.yml` — GitHub Actions checks for Java `mvn clean verify` and frontend `npm ci` + `npm run build`.

## 4. Technology and known local setup

- Java 17
- Maven 3.9.x
- React 19, Vite 7
- MySQL 8 for local development; Supabase PostgreSQL is the selected hosted database target (migration script added, not yet live-verified)
- MySQL Connector/J 9.4.0 and PostgreSQL JDBC driver 42.7.7
- Jackson Databind 2.20.0
- Windows development machine has been used.

Local paths previously used (machine-specific; do not hardcode into the project):
- Repository: `A:\JAVA_PROJECT\`
- JDK: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Maven: `%USERPROFILE%\apache-maven-3.9.16`

If PowerShell picks the wrong Java version, a temporary session can set:
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:USERPROFILE\apache-maven-3.9.16\bin;$env:Path"
java -version
mvn -version
```

### Start commands (local development)

Backend (repository root, with MySQL running and local config set):
```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=network.ServerMain"
```
Expected server messages include HTTP API port 8080 and TCP socket port 5050.

Frontend (second terminal):
```powershell
cd frontend
npm install
npm run dev
```
Vite normally serves `http://localhost:5173`.

Frontend production build check:
```powershell
npm run build
```

These are documented commands, not a claim that they have all been re-run against the latest commit. Re-run and record results after relevant changes.

## 5. Implemented baseline as last inspected

The following was observed in the `main` source on 2026-10-13:

### Frontend
- Login form calls `POST /api/auth/login`.
- On successful login, the UI stores the returned user object in React state and displays the dashboard.
- Navigation includes Overview, Tickets, Report Issue, Resources, Notifications, and Emergency.
- Issue form posts title, description, category, location, and client-supplied `reporterId`.
- Student ticket list fetches `GET /api/tickets/reporter/{reporterId}`.
- Open/resolved counts are derived from fetched ticket statuses.
- Several navigation views are placeholders or reuse the general dashboard content; do not mark them production-ready without completing and testing each flow.
- Login persistence/session restoration across refreshes has not been established as implemented.

### HTTP API routes
- `POST /api/auth/login`
- `POST /api/issues`
- `GET /api/tickets/reporter/{reporterId}`

CORS currently allows the Vite development origin `http://localhost:5173`, and the API uses a fixed thread pool. Review exact implementation before changing route behavior.

### Authentication
- `AuthService.authenticate` checks blank inputs, uses `UserDAO.findForAuthentication`, verifies passwords with `PasswordHasher.verify`, and applies a rate limiter.
- The API returns user id, name, email, and role after successful login.
- **Latest change:** API login now issues a signed HMAC-SHA256 bearer token with a two-hour expiry. Issue creation and ticket listing require a valid token; issue creation is limited to STUDENT/FACULTY, the reporter ID comes from the token, and ticket listing checks the path ID matches the token identity. Frontend sends the bearer token and keeps it in sessionStorage.
- CAMPUSOS_AUTH_SECRET must be configured to a strong stable value (32+ characters) for hosted use. If absent, a random per-process key is generated for local development, so tokens stop validating after restart. Every authenticated API request now rechecks the token's user ID and role against the database, so deleted accounts and changed roles are rejected immediately. Individual token revocation while the same account and role remain active is not implemented; full route-by-route role coverage and security tests remain required.

### Issue submission
- `IssueService.submit` validates the issue/reporter ID, calculates priority, and creates issue + ticket inside a JDBC transaction.
- It chooses an available `MAINTENANCE` or `TECHNICIAN` user, preferring a specialization matching the category, then creates notifications if a staff member is assigned.
- If no staff member is available, the ticket can be created without an assignment.
- Priority currently uses keyword rules; it is not an AI model.

### Priority rules (verify in `PriorityService` before changing)
Known rules from project history:
- CRITICAL: fire, gas leak, electric shock, security breach
- HIGH: server down, network down, water leak, power failure
- MEDIUM: not working, broken, urgent
- Otherwise LOW

## 6. Production blockers — treat as release gates

These are not optional polish. Address them before calling CAMPUSOS production-ready.

### P0 — Security and data integrity
- Implement real authentication state for HTTP requests (secure session cookie or properly designed signed-token flow).
- Derive the acting user from verified authentication state; never trust a client-supplied reporter/user ID.
- Add role-based authorization to every protected route and enforce ticket ownership/assignment in the service layer.
- Move credentials out of tracked config; load secrets from environment/secret manager and provide a safe example config.
- Review public error responses: do not return SQL/internal exception messages or stack traces to clients.
- Validate HTTP methods, content types, body size, field lengths, allowed enum values, path IDs, and malformed JSON consistently.
- Configure CORS by environment and add appropriate security headers where applicable.
- Review rate limiting under concurrency and across multiple server instances; add abuse protection.
- Ensure transactions, assignment selection, and notifications remain consistent under concurrent requests.
- Check that no secrets or compiled artifacts exist in Git history; rotate any real credential that was ever exposed.

### P1 — Reliability and maintainability
- Add automated tests for services, API endpoints, priority, authorization, and transaction rollback.
- Add schema migrations/versioning and repeatable database bootstrap instructions.
- Add structured logging, request IDs, health/readiness endpoints, and useful operational metrics.
- Add timeouts, graceful shutdown, controlled executor sizing, and resource cleanup.
- Add pagination and predictable sorting for lists; avoid unbounded queries.
- Define consistent API response/error formats.
- Add frontend loading, empty, error, retry, and unauthorized states for each workflow.
- Frontend API URL is now configurable via VITE_API_BASE_URL; CORS allowlist via CAMPUSOS_CORS_ORIGINS.
- Use dependency/security scanning and pin reproducible dependency versions.

### P2 — Product completeness
- Finish role-specific dashboards and ticket lifecycle: assigned → in progress → resolved/closed, with authorization and audit history.
- Complete notification inbox/read state and emergency workflow.
- Complete resource inventory and reservation workflow, including conflict prevention and transaction safety.
- Build admin user/ticket/report views and search/filter/export where required.
- Add accessibility, responsive/mobile checks, consistent design system, and clear form validation.
- Add deployment configuration, CI checks, backup/restore runbook, monitoring, and rollback plan.

Do not attempt all blockers in one giant change. Work in small phases and test each phase.

## 7. Branch and collaboration status

As checked on 2026-10-13:
- `main` is the primary working branch and includes the current React/API implementation and rewritten README.
- `side` exists and has diverged from `main`: comparison reported one unique commit on each side.
- The `side`-only commit added `src/util/SeedUsers.java`, `run_frontend.bat`, `run_gui.bat`, `run_server.bat`, and many `target/classes/**` compiled artifacts.
- The batch scripts hardcode the contributor's local Java/Maven paths and should not be copied unchanged.
- `SeedUsers.java` creates demo roles and prints demo passwords to console; review/fix before adopting.
- Do not merge `side` blindly. Review the actual diff and selectively port useful source changes to `main`. Never commit `target/`.

Comparison: https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/compare/main...side

## 8. Development history — known milestones

This is a reconstruction from the project work recorded so far; it is not a substitute for the Git commit history.

1. **Original foundation:** Java campus operations project with Swing GUI, TCP client/server, JDBC/MySQL, service/DAO/model layers, and multiple campus-operation domain classes.
2. **Build stabilization:** Maven configuration aligned to Java 17; dependencies include MySQL Connector/J and Jackson. A BookingDAO parse/build issue was fixed. A prior `mvn clean compile` succeeded with 58 Java source files at that point.
3. **React migration, Phase 1:** React + Vite frontend scaffolded to replace the primary Swing experience while retaining Java backend and TCP server.
4. **HTTP API, Phase 2.1:** Java HTTP API and React login connected to existing authentication service.
5. **Student issue workflow, Phase 3:** Added issue submission API, student ticket retrieval, dashboard counts, issue form, ticket listing, and styling.
6. **Documentation pass:** README rewritten to document architecture, setup, APIs, feature status, limitations, and roadmap.
7. **Branch review:** `main` vs `side` divergence discovered. No merge performed.

Known main-branch commits include:
- `7af86312d5b33fe2578b646cbe6dde149b44800d` — Rewrite README with accurate setup and feature documentation.
- `4b72a4eccece0363b3d0a816b2e92fb70a8a1705` — Style student issue reporting and ticket list.
- `3b12a598b8d771026659eda9c222c22cef1fc2e9` — React student issue reporting + live tickets.
- `30873375e6e334117cd0089de8fc48c243c77702` — Issue and student ticket APIs.
- `8ede6627d2f0722e79db36947be956e138cff31f` — Student ticket retrieval.
- `f36aa2d2b16d61d02cc1cd94ddbd7ecc372a8b82` — Fix BookingDAO parse error.

## 9. Working agreement for every future session

At the start of a session:
1. Read this file, README, recent commits, and the exact files involved.
2. Confirm the current branch and whether the worktree may contain local changes (the assistant cannot assume the user's local worktree is clean).
3. Identify the smallest safe phase and its acceptance criteria.
4. Inspect schema, services, API, and UI dependencies before changing behavior.

At the end of a session:
1. Report files changed and why.
2. List build/test commands actually run and exact outcomes.
3. List known failures, limitations, and risks.
4. Append a dated entry to Section 12.
5. Update Section 13 so the next session starts with the right task.
6. Commit only intended, reviewed files to `main`; do not claim a remote change if the write did not succeed.

## 10. Definition of done

A phase is complete only when:
- The intended user workflow is clear and implemented end-to-end.
- Server-side validation and authorization are present where required.
- Success and failure cases are handled.
- Relevant tests/builds pass, or an unrun test is explicitly listed as a blocker.
- No credentials, local paths, or generated build artifacts were introduced.
- README and this knowledge file match the implementation.
- The change is committed to the intended branch and the commit is recorded here.

## 11. Product decisions / constraints

- Primary branch: `main`.
- Keep the Java backend for now; do not rewrite the stack without a measured reason.
- **Locked deployment architecture (2026-10-13):** Vercel for the React/Vite frontend; Java API as a separate container/service on a Java-capable host; Supabase PostgreSQL accessed by JDBC. Keep the Java stack. No hosting/deployment changes are authorized until the owner explicitly says to host.
- React is the primary web interface; the old Swing GUI and TCP server may remain while their long-term role is evaluated.
- Build a secure, testable product incrementally rather than adding more UI placeholders.
- Prefer explicit API contracts, database constraints, migration scripts, and tests over hidden behavior.
- Treat local developer setup and production configuration as separate environments.

## 12. Engineering log

### 2026-10-13 — Repository baseline and production plan
- Confirmed `main` and `side` both exist and have diverged.
- Inspected the main-branch README, Maven configuration, API server, authentication service, issue service, database connection helper, and frontend package/App.
- Confirmed current core workflow: login, issue submission, priority calculation, ticket creation, and student ticket listing.
- Identified production-critical gap: API endpoints trust client-supplied reporter IDs and do not verify an authenticated session/token on protected requests.
- Identified unsafe error handling to review in API handlers; issue handler currently risks returning exception text to clients.
- Added this knowledge file and repository hygiene rules as the first documentation/engineering baseline.
- Added `.gitignore` rules for Maven output, Node dependencies/build output, IDE files, logs, and local files.
- Updated `DBConnection` so `CAMPUSOS_DB_URL`, `CAMPUSOS_DB_USERNAME`, and `CAMPUSOS_DB_PASSWORD` override classpath properties; missing/placeholder values fail with a configuration error. Environment-only configuration is supported when the properties resource is absent.
- Added `resources/config.properties.example` and documented environment-based database configuration in README.
- Updated API handlers to log internal failures server-side and return generic 500 messages instead of exposing exception text/stack traces to clients. Ticket lookup now distinguishes invalid IDs (400) from database/server failures (500).
- **Tests:** No Java build or frontend build was run after these edits; changes were committed remotely through GitHub. Compile and runtime verification remain required.
- **Status:** Initial repository hygiene, database-secret configuration, and safer API error responses committed; API session/authorization hardening remains unimplemented.

### 2026-10-13 — Deployment target review and CI bootstrap
- Re-inspected `ApiServer.java`, `AuthService.java`, `DBConnection.java`, `frontend/src/App.jsx`, `pom.xml`, the frontend package/lockfile, README, and this log on `main`.
- Confirmed the browser UI originally hardcoded `http://localhost:8080` in login, issue submission, and ticket fetching. Updated `frontend/src/App.jsx` to use `VITE_API_BASE_URL` with a local default, added `frontend/.env.example`, and documented local/hosted configuration in README (commits `9433bdd3b2e43baa150a4d61dc6bea13fd467a22`, `8bfe05db1c971ff2f073aeb2e8d339a29335f595`, `48a7240abc7564654682dd681bece94b0a9174b9`). CI for the latest API URL change is still running/pending at last check; don't mark that exact revision verified until its run finishes.
- Removed the accidentally tracked `frontend/node_modules` tree in commit `4584130797a34efe4c6e73684e21e0cb96381ad9`. A recursive main-branch tree inspection afterward showed 91 entries and no tracked `node_modules`, `target/`, `.class`, `.jar`, `.env`, or obvious key/certificate artifacts. `resources/config.properties` is still tracked (the remote version is a placeholder); remove it safely later with explicit local-config migration guidance.
- Confirmed the current backend is a long-running Java `HttpServer` plus a separate TCP server and the JDBC/schema layer targets MySQL. Supabase's managed database is PostgreSQL, so using it requires a deliberate database migration or retaining a separate MySQL host. Vercel frontend hosting alone does not host this Java server as-is.
- Added `.github/workflows/ci.yml` (commit `e076445b66c17273f574f54cb40a1aa42ff2059d`) to run `mvn --batch-mode --no-transfer-progress clean verify` on Java 17 and `npm ci` + `npm run build` on Node 22 for pushes/PRs to `main`.
- Observed GitHub Actions run `38427076213` completed successfully for the CI/docs tree; frontend `npm ci` + `npm run build` succeeded and Java `mvn clean verify` succeeded. Run `38427149596` after removing tracked `frontend/node_modules` also completed successfully. The latest frontend API URL configuration passed CI in run `38427412737`.
- Found a core functional bug during priority review: the API constructed every new issue with `Priority.LOW`, while `PriorityService` preserves any already-set priority. This bypassed keyword priority calculation for all reports. Fixed the API to pass `null` priority so the service calculates it.
- Added JUnit 5/Surefire and `tests/service/PriorityServiceTest.java` with tests for CRITICAL/HIGH/MEDIUM/LOW classification, precedence, and null input. The CI run that includes these new tests (`38427530826`) is pending at last check; do not claim the tests have passed until the run finishes.
- This is still not an end-to-end smoke test; DB-backed API, authentication, authorization, and workflow tests remain to be added.
- No hosting/deployment was performed. Awaiting a deliberate runtime/database architecture choice before changing persistence or auth assumptions.

### 2026-10-13 — Auth foundation, Supabase schema, and frontend performance
- Locked the target architecture per owner approval: Vercel will host the React/Vite frontend; the existing Java API will run on a Java-capable host; Supabase PostgreSQL is the target hosted database. No hosting/deployment has been performed.
- Added security.AccessTokenService: HMAC-SHA256 signed bearer tokens with user ID/role claims, two-hour expiry, constant-time signature comparison, and malformed/tampered token rejection. CAMPUSOS_AUTH_SECRET must be a strong stable 32+ character secret in hosted environments. A random per-process secret is generated only as a local-development fallback.
- Hardened HTTP routes: added GET /api/health; bearer-token requirement for issue creation/ticket listing; reporter ID derived from token for issue creation; own-ticket ID match check; issue-reporting role restriction to STUDENT/FACULTY; 16 KiB JSON body limit; content-type and malformed JSON validation; CORS allowlist configured with CAMPUSOS_CORS_ORIGINS; security/cache headers; bounded executor sizing and stop/getPort hooks for smoke tests.
- Fixed an existing login rate-limiting flaw: AuthService had been instantiated on each login request, resetting its limiter every time. It is now held by the API server instance.
- Updated the frontend to send bearer tokens, store the token/user in sessionStorage for the tab session, clear both on logout, and display a retry action when ticket loading fails. Removed the external Google Fonts CSS import to avoid an extra render-blocking network dependency and use local system fonts.
- Added resources/supabase_schema.sql for PostgreSQL with role/status/priority checks, foreign keys, useful indexes, updated-at trigger, and an exclusion constraint to prevent overlapping bookings for the same resource. Added PostgreSQL JDBC driver while retaining MySQL for local compatibility.
- Updated README for Supabase schema selection, auth headers, token secret, CORS, and current deployment constraints.
- Added tests/security/AccessTokenServiceTest.java and tests/api/ApiServerSmokeTest.java; the smoke test covers health plus anonymous rejection for issue/ticket routes. CI run 38428705148 completed successfully on commit aa24de0e080f430e3fc85288182b55186a5e3e53: Java mvn clean verify passed, frontend npm ci + npm run build passed; JUnit result was 9 tests, 0 failures, 0 errors, 0 skipped (2 token tests, 6 priority tests, 1 API smoke test). A prior run 38428340545 failed to compile due to an unreachable catch in ApiServer; that catch was removed in commit d32f5155475dabe25b0d5d68c3c0f63774f081ed. The successful run predates only the knowledge-log update commit, not the source/tests included in that run.
- Supabase schema has not been applied to a live Supabase project, and no real database integration test has run. Existing DAO code has not yet been verified against PostgreSQL.
- Remaining blockers: confirm CI; test JDBC/DAO compatibility against Supabase; expand staff/admin APIs and route-level authorization; fix ticket lifecycle, notifications, resource booking/emergency UI; build comprehensive integration/smoke/security tests; verify final CORS and hosted secrets.

### 2026-10-13 — Ticket lifecycle, notifications, resources, and admin operations
- Added staff ticket queue API (assigned-to-self for MAINTENANCE/TECHNICIAN; all tickets for ADMIN) and role-checked status updates. Valid transitions are ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED; transitions are recorded in ticket_history and the reporter receives a notification in the same database transaction.
- Added admin-only staff lookup and transactional ticket assignment for OPEN tickets. Assignment validates the target's role and availability, updates status, records history, and notifies both staff and reporter.
- Added admin overview metrics (users, total/active/resolved tickets, critical issues, available staff), user directory, and staff availability toggles. The directory is read-only for general user data; only staff availability is editable.
- Added per-user notification inbox and mark-read endpoint; added emergency feed for authenticated users and admin-only emergency broadcast that writes an emergency record and sends notifications.
- Added authenticated resource inventory, personal bookings list, and reservation creation. Booking logic locks the resource row before counting overlapping reservations so concurrent requests respect resource quantity; PostgreSQL schema no longer uses an over-restrictive no-overlap exclusion that would block quantities greater than one.
- Updated frontend navigation by role; students/faculty can report issues, staff get a work queue, admins get overview/users/all-ticket assignment, and notifications/resources/emergency views now call APIs with loading/error/empty states. Added responsive admin assignment and user-directory styling.
- Fixed authentication role mapping: TECHNICIAN previously became a MaintenanceStaff model and could receive a MAINTENANCE token. AuthService now returns a Technician model for TECHNICIAN users.
- Added Supabase schema privilege revocations for Supabase REST roles anon/authenticated so browser-side REST access to CAMPUSOS tables is not assumed; the intended data path is Java JDBC only. Review this against the connected project's exact role/privilege defaults before applying.
- Latest observed CI run 38430261685 succeeded on commit bb73ce9802c66cc12f7a399afebcf4f5a32696b6: Java mvn clean verify passed, frontend npm ci + npm run build passed, and 11 JUnit/smoke tests passed (0 failures/errors/skips). Tests cover priority rules, signed-token tampering/validation, lifecycle transitions, health endpoint, and anonymous rejection on protected routes. They do not yet test live database operations.
- Remaining high-risk gaps: Supabase schema has not been applied to a live project; no live PostgreSQL JDBC test exists; no DB-backed end-to-end tests; tokens cannot be revoked before expiry; user role/availability changes do not invalidate existing tokens; admin can view users but cannot create/edit user identity; staff ticket history is stored but not yet exposed as a timeline UI; booking cancellation is not implemented; test coverage for wrong-role/ownership/assignment/concurrent booking still needed.

### 2026-10-13 — User administration, ticket timeline, booking cancellation, readiness
- Added admin-only user creation API and UI. Server validates role/email/field lengths, requires a 12+ character initial password, hashes it with the existing PBKDF2 PasswordHasher, and handles duplicate email as HTTP 409. Admin can list users and toggle maintenance/technician availability; full identity editing/deletion remains unimplemented.
- Added ticket-history query with access checks for the reporter, assigned staff, or admin, and a student-facing timeline UI. System-generated assignments are recorded with a nullable changed_by (shown as CAMPUSOS system); the MySQL and PostgreSQL schema scripts now support this.
- Added future-booking cancellation restricted to the booking owner. Reservation capacity checks lock the resource row before counting overlapping reservations, so concurrent requests serialize against resource quantity.
- Added database readiness endpoint GET /api/health/ready, which returns 503 when JDBC cannot connect, and a JVM shutdown hook to stop the HTTP API executor gracefully.
- Added an API test that issues a real signed STUDENT token to verify ADMIN and staff routes reject the wrong role. Expanded smoke checks to include ticket history and database readiness.
- CI run 38431267760 completed successfully on source commit 94ac69d57dbecd5b65fb2f72314470b191c013e3: Java Maven verify succeeded, Vite production build succeeded, and 12 tests passed with 0 failures/errors/skips. The readiness test intentionally observes 503 in CI because no database service is configured. This does not verify live Supabase connectivity.
- README now documents implemented routes and updated feature status. No hosting has occurred.
- Still unverified: apply resources/supabase_schema.sql to a real Supabase project; confirm JDBC connectivity and generated-key behavior with PostgreSQL; run DB-backed tests for all DAO operations and concurrent booking; review the Supabase role revocations on the actual project; add token revocation/role freshness and cross-instance rate limiting; complete history UI for staff/admin, full admin CRUD/report exports, and release security review.
### 2026-10-13 — JDBC pooling and hosting preparation
- Replaced per-query DriverManager connections with a bounded HikariCP pool. Pool size is configurable with CAMPUSOS_DB_POOL_SIZE (1–30), defaults to db.pool.size/10, with connection/validation timeouts, idle/lifetime limits, and pool cleanup on shutdown. PostgreSQL JDBC URLs without an explicit sslmode now default to sslmode=require.
- Added HTTP-only entry point api.ApiServerMain that honors PORT and closes the API executor/JDBC pool on shutdown. Added a multi-stage Dockerfile that runs Maven verification before packaging and a slim Java 17 JRE image. .dockerignore excludes local resources/config.properties and generated artifacts. Maven Shade creates an executable dependency-inclusive jar with JDBC service metadata merged.
- Added CI container-build job to docker build the image. Latest workflow after these changes is pending at last inspection; previous source CI run 38431619825 passed 12 tests, and run 38431267760 also passed 12 tests before Hikari/container additions. Do not mark the current tree fully verified until the new Java/frontend/container CI run succeeds.
- Added bounded-query indexes to MySQL and PostgreSQL schema scripts for ticket lookups, booking lists/capacity, and emergency feed. Admin critical-issue metric now counts active critical tickets only. Frontend avoids fetching a student's own-ticket list for staff/admin roles.
- Added resources/supabase_app_role.sql.example and README notes for the persistent Java backend's Supabase session pooler (port 5432, SSL required); transaction pooler port 6543 is not the chosen mode because the JDBC implementation uses prepared statements. The database password and auth secret belong only on the Java API host.
- Supabase connector discovery currently shows only an existing project named TRAVEL_CRM, not a CAMPUSOS project. It has not been inspected or modified for CAMPUSOS. Do not apply CAMPUSOS schema or migrations to that unrelated project; a dedicated project must be connected/created with owner approval.
- No Vercel project or API container has been deployed. README now documents the intended frontend root/build/output, Java container, environment split, and readiness endpoint.

### 2026-10-13 — PostgreSQL end-to-end verification and final CI checkpoint
- Added a PostgreSQL 17 service job to CI. It creates the Supabase API roles used by the schema, applies resources/supabase_schema.sql, and runs the gated PostgresWorkflowTest against a real JDBC connection.
- Integration coverage creates users and authenticates a TECHNICIAN; creates a critical issue and verifies automatic priority/assignment/history; rejects an unauthorized status update; transitions a ticket through IN_PROGRESS → RESOLVED → CLOSED; tests an unassigned ticket and admin assignment; checks/marks notifications; creates and reads an emergency broadcast; books a resource; verifies overlapping capacity conflict; cancels a future booking; and checks admin metrics.
- First integration attempt failed because the test fixture explicitly pre-set Priority.LOW, which correctly prevents automatic reclassification. Fixed the fixture to pass null priority, matching the real API path. The next full CI run passed.
- Final verified source run: GitHub Actions run 38433444433, commit 41a2a2fd53e808e973cc41cb362283ca2f843563, conclusion success. Java verification passed, frontend production build passed, Docker image build passed, and the PostgreSQL-backed suite passed all 15 tests with 0 failures/errors/skips.
- The Postgres 17 integration test validates the schema/DAO behavior against PostgreSQL; it does **not** prove the connection string, role setup, network access, or project-specific configuration on the actual Supabase project. The connected Supabase account currently lists only TRAVEL_CRM; that unrelated project was not modified. A dedicated CAMPUSOS project is still required before live Supabase verification.
- Added safe MySQL/PostgreSQL migration scripts for ticket history and query indexes. Added rate limiter memory bounds and normalized login keys. Added container build and non-root runtime, with local config excluded from the Docker context.
- No hosting was performed. Vercel frontend/API environment settings and the Java container are documented but not deployed.

### 2026-10-15 — Notification inbox query stability and index
- Audited DAO list queries after the reporter-ticket pagination change. The notification inbox is explicitly capped at 100 rows but ordered only by `created_at`, so rows with equal timestamps could shift unpredictably between requests.
- Added `id DESC` as a deterministic tie-breaker to the notification inbox query.
- Added a composite `(user_id, created_at DESC, id DESC)` index to both PostgreSQL and MySQL bootstrap schemas so the inbox query's per-user filter and newest-first order have a matching index. The existing unread-only index remains because it serves a different filter.
- No live database was changed; schema changes are for fresh/bootstrap setup and must be applied through an approved migration process to an existing deployment.
- CI status for this commit: pending until the new run completes. No hosting or production DB changes were made.

### 2026-10-15 — Booking history ordering and index
- Continued the bounded-query audit after notification inbox optimization. The personal booking history was already capped at 100 rows, but sorting only by start_time could produce unstable ordering when bookings share a start time.
- Added booking ID as a descending tie-breaker and added matching per-user ordering indexes to both PostgreSQL and MySQL bootstrap schemas.
- Existing resource/user booking indexes remain; no live database was modified. Existing deployments require a reviewed migration to gain the new index.
- CI for this commit is pending until the new GitHub Actions run completes. No hosting or production DB changes were made.

<!-- APPEND NEW DATED ENTRIES BELOW; keep older entries intact. -->

## 13. Next actions — ordered backlog

### Phase 0 — Repository safety and reproducibility
- [x] Add/verify `.gitignore` for Maven output, Node dependencies/build output, IDE files, and logs.
- [x] Add a safe config example and environment-based database credential overrides; document them in README.
- [ ] Complete a deeper Git history secret scan; current tracked tree scan did not show obvious credential artifacts.
- [x] Inspect initial GitHub Actions build steps: Java `mvn clean verify` and frontend `npm ci` + `npm run build` both succeeded on the first workflow run; add actual API/database/security smoke tests next.
- [x] Remove accidentally tracked `frontend/node_modules`; verify the main tree no longer includes it. Keep checking future diffs because `.gitignore` alone does not untrack existing files.
- [ ] Scan tracked files and Git history for generated artifacts/secrets; rotate any exposed real credential.
- [x] Replace raw exception text in API responses with safe messages and log server-side failures (initial pass; malformed JSON/body-size handling still needs work).
- [ ] Review the `side` diff separately; do not merge its generated `target/` files or machine-specific batch paths.

### Phase 1 — API security foundation (highest priority)
- [x] Choose and document a signed bearer-token strategy with expiry; stable hosted secret required. Individual token revocation is not implemented.
- [x] Implement token-verified identity for issue submission and reporter ticket reads; role checks for issue creation. Staff/admin route authorization remains to do.
- [x] Derive issue reporter identity from token and verify ticket route ID matches authenticated user.
- [x] Enforce reporter ticket ownership at the HTTP route; add a smoke test for cross-user reporter-ID access. The authenticated HTTP-to-PostgreSQL success/ownership workflow also passes CI run 38608976116. Continue auditing all read/update routes.
- [x] Enforce a 16 KiB JSON request-body limit and exact application/json media type (parameters such as charset remain accepted); tests cover application/jsonp rejection and oversized bodies.
- [x] CI run 38433702324 passed Java verification, frontend production build, Docker image build, and PostgreSQL-backed workflow integration tests. Earlier CI run 38428705148 passed Java verify, frontend build, and 9 unit/smoke tests.
- [x] Add wrong-role, anonymous-access, priority, and PostgreSQL-backed success/workflow tests; expand invalid-input and HTTP authenticated ownership coverage.

### Phase 2 — Test foundation and API quality
- [x] Introduce JUnit 5 and the Maven Surefire plugin; priority classification tests are included in passing CI.
- [x] Add PostgreSQL-backed integration test for authentication, issue priority/assignment/status history, wrong-owner status rejection, notifications, emergency broadcast, booking conflict/cancellation, and admin metrics. Anonymous/wrong-role smoke tests pass in CI.
- [x] Expand PostgreSQL integration coverage for cross-user booking cancellation denial and cross-technician ticket status-update denial; the existing parallel capacity-one booking test remains in place. `HttpPostgresWorkflowTest` now covers login → authenticated issue creation → persisted reporter retrieval → cross-reporter denial against disposable PostgreSQL. Live Supabase remains unverified.
- [x] Add HTTP validation tests for malformed JSON, wrong Content-Type, JSONP-like media types, and oversized bodies; CI verification pending.
- [ ] Define consistent JSON response/error contracts.
- [x] Add liveness/readiness endpoints and HTTP API executor shutdown hook; structured request IDs/metrics remain.
- [x] Move frontend API base URL to `VITE_API_BASE_URL`; document the local example and hosting configuration. CORS is environment-configurable via `CAMPUSOS_CORS_ORIGINS`.

### Phase 3 — Complete student and staff ticket lifecycle
- [x] Add staff status transitions with transactional ticket_history writes; timeline read API/UI remains to do.
- [ ] Add authorized technician queue and status transitions.
- [x] Admin assignment and status transitions use transactions and notify affected users. Concurrency/load tests remain to do.
- [ ] Add empty/loading/error/retry states and integration tests.

### Phase 4 — Role-specific product modules
- [x] Add admin overview metrics, user directory, staff availability toggles, and open-ticket assignment. Full user CRUD/report exports remain incomplete.
- [ ] Complete notification inbox/read status.
- [x] Implement resource inventory and personal booking UI/API; booking checks serialize on the resource row. Live DB/concurrency tests still required.
- [x] Implement authenticated emergency feed and admin-only emergency broadcast; DB-backed verification still required.

### Phase 5 — Production deployment readiness
- [x] Decide deployment architecture and add resources/supabase_schema.sql; PostgreSQL-backed workflow CI passes. Live Supabase connectivity and existing-data migration remain to be tested.
- [ ] Review the user's existing Supabase keep-alive GitHub Actions YAML before adopting it; do not assume scheduled pings prevent pausing or replace monitoring.
- [x] Add initial CI build checks for Java and frontend (first run still needs review).
- [ ] Add automated API smoke tests, dependency scanning, backups, restore test, monitoring, and rollback plan.
- [ ] Verify HTTPS, secrets management, migrations, least-privilege DB account, and production CORS.
- [ ] Run end-to-end acceptance tests and document release procedure.

**Immediate next task:** Add HTTP/PostgreSQL regression coverage for admin-created users and staff availability, including invalid-role and non-admin denial cases. The targeted credential-history review is recorded below. The connected GitHub interface does not expose the repository's secret-scanning alert dashboard, so the owner must check Security → Secret scanning for server-side alerts. A live Supabase smoke test remains blocked until a dedicated CAMPUSOS project and authorized server-side connection settings are available. No deployment without explicit authorization. Architecture is locked: Vercel frontend + Java API container + Supabase PostgreSQL. `resources/config.properties` was removed from tracking because it is local-only; the owner must back up their local file before pulling, then use ignored `resources/config.properties` or environment variables. Do not host until explicitly authorized.


### 2026-10-13 — Architecture locked, PostgreSQL CI, and frontend resilience
- Owner approved: Vercel frontend, Java API on a Java-capable container host, Supabase PostgreSQL. No deployment performed.
- Confirmed repository includes resources/supabase_schema.sql, PostgreSQL migration scripts, a Dockerfile running as a non-root user, and PostgreSQL-backed integration tests.
- Confirmed GitHub Actions run 38433702324 succeeded for Java Maven verification, frontend install/build, Docker image build, and PostgreSQL workflow integration tests.
- Removed resources/config.properties from version control and added it to .gitignore; the remote copy contained only placeholders. LOCAL USER ACTION BEFORE PULLING: back up local resources/config.properties, then pull the deletion and recreate the ignored local config from resources/config.properties.example, or use environment variables.
- Added a 12-second frontend API request timeout and clearer network/timeout errors in frontend/src/App.jsx (commit f154e04093d59fd457ff319dc44ebca27e778ae5). Its CI run was superseded/cancelled by subsequent commits before final verification.
- Added tests/api/ApiInputValidationTest.java to verify authenticated malformed JSON and wrong Content-Type requests return HTTP 400 without needing a database (commit ddce98951383e196faa2af9db98be9d54d5ab581). Latest CI run 38444090971 was pending at last check.
- Remaining blockers: require a stable signing secret in production, complete CI on latest main, expand HTTP-level authenticated success/ownership tests, run real Supabase connectivity smoke test when credentials are configured, and inspect/validate the user's Supabase keep-alive workflow before use.
- Performance principle: keep database credentials server-side, use bounded requests and indexed SQL, keep connection pools small, and avoid unnecessary frontend dependencies.


### 2026-10-13 — Production token-secret guard
- Latest CI checkpoint before this change: run 38444181828 completed successfully for commit cd092b2e57b99039f0ab7357c643a11d3ae9c562.
- Hardened AccessTokenService: when CAMPUSOS_ENV=production, startup now fails unless CAMPUSOS_AUTH_SECRET is configured and contains at least 32 characters. Local development retains the random per-process fallback.
- Added unit tests for missing/short production secrets, accepted stable secrets, and local fallback behavior.
- Commits: 23bf4c5f0312d3cbd9c9ac3b65db91c1c57e1fc7 (security guard), 1cd730572debffbb8bebfa49ce4e5956da7b3304 (tests).
- Verification status: GitHub Actions run 38446105484 completed successfully; Java verification, Docker build, frontend production build, and PostgreSQL workflow integration tests all passed.


### 2026-10-13 — HTTP ownership and request-validation hardening
- Added an HTTP smoke test proving an authenticated student cannot request another reporter's ticket list (commit 5de930d227b3ffbaca3102131b1ed4a02f7bc85f).
- Tightened JSON Content-Type validation: only the `application/json` media type is accepted, while valid parameters such as `; charset=UTF-8` remain allowed. This closes the previous prefix-match gap that accepted values such as `application/jsonp` (commit 80f47bb406d1c6d7c961e0ee1c95bb6965f0193e).
- Added HTTP validation tests rejecting `application/jsonp` and request bodies exceeding the 16 KiB limit (commit 05393e0ba8f144d2228b27578c8a6a31e14e92db).
- CI verification passed on the latest tree at https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38446105484 (all four jobs succeeded).
- Next: continue reviewing API ownership/role boundaries and prepare a safe live Supabase smoke test. No deployment performed.


### 2026-10-14 — Expanded HTTP authorization regression coverage
- Audited API route gates and DAO ownership filters for tickets/history, staff status changes, notifications, bookings, emergency broadcasts, and admin operations.
- Expanded the HTTP smoke test proving a STUDENT token receives HTTP 403 for staff status changes, admin ticket assignment, staff availability changes, and staff directory access. These checks run before database access, so the regression test does not require a live database.
- Existing data-access checks remain owner-scoped for notification reads, booking listing/cancellation, and ticket history; staff status changes require assignment unless the actor is an admin.
- Verification passed in GitHub Actions run 38448175228: Java compile/tests, frontend production build, Docker image build, and PostgreSQL workflow integration tests all succeeded. Authorization-test JSON literals were corrected in commit 0799d07e3ad95a89db25895d40604564adb4d940 before this successful run.
- Live Supabase connectivity remains unverified until a dedicated CAMPUSOS project and server-side connection environment are configured. Do not use the unrelated TRAVEL_CRM project or deploy without explicit authorization.


### 2026-10-14 — Supabase database-role least privilege
- The audit found the example CAMPUSOS application-role script granted DELETE on every table and broad default table privileges, although the API only needs DELETE for a user's own future bookings.
- Tightened resources/supabase_app_role.sql.example to revoke previous broad grants and grant table-specific operations: DELETE only on bookings; read-only access to resources; no UPDATE/DELETE on issues, ticket history, or emergencies; and no automatic table grants for future tables.
- Kept sequence permissions needed for PostgreSQL identity-generated IDs. New tables require explicit grants in their migration.
- This changes only the setup example; it has not been applied to any Supabase project. Live verification still requires a dedicated CAMPUSOS project and its server-side connection settings. Never use the unrelated TRAVEL_CRM project.
- Verification passed in GitHub Actions run 38448857353: all four jobs succeeded, including execution of the role setup SQL and the checks that the app role can delete bookings but cannot delete users. This was an ephemeral CI database only.


### 2026-10-14 — CI verifies least-privilege database role
- Extended the PostgreSQL CI job to execute resources/supabase_app_role.sql.example against its disposable PostgreSQL instance.
- Added a runtime privilege check: the app role can read users and delete bookings, but cannot delete users. This tests the intended table-level permission boundary without touching any real Supabase project.
- The CI job uses a disposable local database and a placeholder-only password. It does not validate live Supabase connectivity or apply changes to a hosted database; those remain pending a dedicated CAMPUSOS project.
- Verification passed in GitHub Actions run 38448857353: all four jobs succeeded, including execution of the role setup SQL and the checks that the app role can delete bookings but cannot delete users. This was an ephemeral CI database only.


### 2026-10-14 — Login endpoint brute-force throttling
- Connected the existing bounded in-memory RateLimiter to the login endpoint. Requests are limited to five attempts per source IP in a 15-minute window; excess requests receive HTTP 429 and Retry-After: 900.
- Rate limiting is applied after bounded JSON parsing and before database authentication. The API uses the socket peer address rather than trusting caller-supplied X-Forwarded-For headers.
- Added an HTTP regression test with an injected one-attempt limiter, verifying the next login attempt returns 429 and Retry-After without requiring a live database.
- Limitation: this limiter is process-local; deployments with multiple API replicas need a shared gateway/distributed rate limit to enforce a global limit.
- Verification passed in GitHub Actions run 38594858380: all four jobs succeeded, including the HTTP login-throttling regression test. The first run found and exposed an incorrect expected status in the test (401 is correct); the test was corrected before the successful run.


### 2026-10-14 — Column-level database write permissions
- Tightened the CAMPUSOS app-role example further: users can only be updated in the available column; tickets only in assigned_technician_id, status, and updated_at; notifications only in is_read. SELECT/INSERT grants remain table-specific, and DELETE remains limited to bookings.
- Expanded the disposable PostgreSQL CI privilege checks to prove permitted column updates work and writes to users.role and notifications.user_id are rejected.
- These are setup/CI-only changes; no hosted database has been modified. CI verification is pending.


### 2026-10-14 — Run integration tests using the least-privilege database role
- Changed the PostgreSQL CI workflow to run the full Maven/PostgreSQL integration suite as campusos_app instead of the postgres superuser. This ensures application queries are actually compatible with the intended production permissions.
- The stricter role exposed a design constraint in booking capacity checks: SELECT ... FOR UPDATE requires UPDATE privilege on resources. Replaced that table-row lock with a transaction-scoped PostgreSQL advisory lock per resource, so concurrent booking attempts remain serialized without granting the application permission to modify resource inventory.
- Added a concurrent capacity-one booking regression test: two overlapping simultaneous attempts must yield exactly one booking and one 23P01 capacity conflict.
- Verification passed in GitHub Actions run 38595953191: all four jobs succeeded. The full PostgreSQL workflow suite now runs as campusos_app; only temporary resource-fixture creation uses the separate test-only admin connection. The concurrent capacity-one booking test passed. Previous column-level privilege checks passed in run 38595255824. No hosted database has been touched.


### 2026-10-14 — Least-privilege integration test fixture isolation
- The first attempt to run the full integration suite as campusos_app correctly rejected a test fixture INSERT into resources. The API app role is intentionally read-only for resource inventory.
- Kept that least-privilege boundary. The integration test now creates its temporary resource fixture through a separate test-only admin JDBC connection, while all application workflow operations still use campusos_app.
- CI passes the fixture-admin settings only to the test step in the disposable PostgreSQL job; these are test container credentials, not live project secrets. The role-backed suite is being rerun.
- Previous run 38595625011 failed at the expected fixture permission boundary; do not mark the new run verified until all jobs pass.



### 2026-10-14 — Revalidate token roles against the database
- Fixed a stale-privilege gap: signed tokens still carry the role that was assigned at login, so role changes could otherwise take effect only after token expiry.
- Added a current-role lookup by user ID and made every authenticated API request compare the signed token role with the user's current database role. A deleted account or role mismatch now receives HTTP 401; database lookup failures fail closed with HTTP 503 rather than authorizing from stale claims.
- Added unit coverage for matching roles, changed roles, missing users, and null principals at the role-comparison boundary.
- This adds a database read to each authenticated request; monitor latency and connection-pool capacity during live smoke testing.
- Verification: CI was triggered by these commits; check the latest run before treating this change as verified. No deployment or hosted database change was performed.
- Next: verify all CI jobs, then audit ticket history, staff ticket actions, emergency reads, and booking ownership at the HTTP/API boundary. Keep rate limiting and distributed-deployment concerns on the release checklist.


## 2026-10-14 — Live-role validation regression and correction

- The first CI run after current-role token validation failed because HTTP unit tests used synthetic student tokens while the server attempted a real database role lookup. Those tests were intended to verify route authorization/input validation and were not integration tests; the database lookup made them dependent on an unavailable or unseeded user record.
- Added constructor injection for the current-role `UserDAO` lookup in `ApiServer`. Production constructors still use a real `UserDAO`; only targeted HTTP tests inject a deterministic test DAO returning `STUDENT`. This keeps the production fail-closed behavior (role lookup SQL failure returns 503; missing/changed account returns 401) while making tests isolate the behavior they intend to verify.
- Updated `ApiServerSmokeTest` and `ApiInputValidationTest` to inject the deterministic role lookup for synthetic-token tests.
- CI failure evidence on run 38599972954: the frontend build passed; Java verification, API container build, and PostgreSQL workflow tests failed due to role lookup returning 401/503 for synthetic test identities. The tests have been corrected and a fresh CI run is required before marking the change verified.
- Not yet verified: final CI result for the correction, full end-to-end role-change revocation against a real database, deployment configuration, and live Supabase connectivity.

- Added an HTTP-level regression test proving that a token originally issued as `STUDENT` is rejected with 401 when the injected current database role is `ADMIN`. This complements the token helper unit tests and verifies enforcement at the API boundary.

- The first verification attempt after injection still found one synthetic-token test using the default database-backed constructor (`authenticatedUserCannotReadAnotherUsersTickets`). Updated that remaining test to inject the deterministic role lookup as well. The newest CI run must pass before this work is considered verified.

- Additional privilege audit: narrowed `campusos_app` SELECT access on `users` from all columns to the specific columns currently used by the API (including the password hash needed for login). INSERT remains table-granted and `available` remains the only user UPDATE column. CI must confirm every DAO query still works under this role.


## 2026-10-14 — Cross-owner and cross-technician authorization regression tests

- Re-inspected current `main` at `3afe83d822c34ae308611229eadff57a8c1cc484` and reviewed the ticket status DAO and PostgreSQL integration workflow.
- Added an integration regression asserting a user other than the booking owner cannot cancel the booking; it confirms the owner's booking remains present after the denied attempt.
- Added a second technician workflow: a ticket is assigned to that technician, a different technician is denied a status update with `SecurityException`, and the assigned technician can update it.
- Kept changes scoped to `tests/integration/PostgresWorkflowTest.java`; no application behavior, schema, credentials, or deployment configuration changed.
- Verification status at edit time: GitHub Actions has not yet run against this test change. Await the new CI result; PostgreSQL-specific assertions only execute when `CAMPUSOS_IT_ENABLED=true` is configured in CI.
- Next: verify the CI run, then continue checking API authorization and booking time/capacity boundaries. Do not deploy until separately authorized and the dedicated hosted database is verified.


### 2026-10-14 — Correct booking capacity using peak simultaneous occupancy

- Fixed `src/dao/BookingDAO.java`: reservation capacity is now checked against peak simultaneous occupancy, evaluated at the requested start and each existing booking start strictly inside the requested interval. The interval convention is half-open `[start, end)`, so adjacent bookings do not count as simultaneous.
- Preserved the transaction-scoped PostgreSQL advisory lock per resource; booking attempts for the same resource remain serialized without requiring UPDATE privileges on `resources`.
- Added PostgreSQL integration coverage in `tests/integration/PostgresWorkflowTest.java`: for a quantity-2 resource, existing 09:00–10:00 and 10:00–11:00 bookings allow a new 09:00–11:00 booking; a further 09:30–10:30 booking is rejected with SQLSTATE `23P01` because it would exceed capacity. The existing capacity-one parallel-booking test remains unchanged.
- CI verification for code and regression tests: GitHub Actions run [38606883935](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38606883935) completed successfully. All four jobs passed: Java verification, frontend production build, Docker image build, and PostgreSQL workflow integration tests.
- No deployment or hosted database changes were made. Live Supabase connectivity remains unverified.


### 2026-10-14 — Emergency broadcast recipient regression coverage

- Expanded `tests/integration/PostgresWorkflowTest.java` to assert that an emergency broadcast creates exactly one matching notification for each tested non-creator account (student and two technicians) and no matching notification for the admin who created the emergency.
- The test also continues to verify that the emergency record is persisted and visible through `EmergencyDAO.findRecent()`. It runs only in the disposable PostgreSQL integration workflow when `CAMPUSOS_IT_ENABLED=true`.
- Implementation review: `EmergencyService.createEmergency` inserts the emergency and broadcast notifications in the same transaction, selecting every user except `createdBy`; a failure rolls back both writes. The new test adds recipient-level assertions, not a change to production behavior.
- Verification: GitHub Actions run [38608527392](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38608527392) completed successfully on the documentation commit whose parent contains this regression; all four jobs passed, including PostgreSQL workflow integration tests. The test-only commit's own run was cancelled by the workflow concurrency policy and is covered by this subsequent successful run.
- No live Supabase connection was attempted, and no deployment or hosted database changes were made.


### 2026-10-14 — Authenticated HTTP-to-PostgreSQL workflow regression

- Added `tests/integration/HttpPostgresWorkflowTest.java`, gated by `CAMPUSOS_IT_ENABLED=true`, to exercise the real HTTP server and disposable PostgreSQL app role together.
- The test creates isolated student accounts, logs in through `POST /api/auth/login`, submits an issue through authenticated `POST /api/issues`, retrieves the created ticket through `GET /api/tickets/reporter/{id}`, and confirms the same token cannot read another student's ticket list (HTTP 403).
- This closes the previous test-coverage gap between mocked HTTP authorization tests and service/DAO-only PostgreSQL integration tests. It does not contact or modify live Supabase.
- Commit: [f658bd9](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/f658bd95cf5cf5fcda45c0549cf7776741b9f2a2).
- Verification passed in GitHub Actions run [38608976116](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38608976116): Java compile/verification, PostgreSQL workflow integration tests, Java API container build, and frontend production build all succeeded.
- Local Maven tests were not run in this environment because cloning the repository previously failed due to unavailable DNS/network access; CI executed the tests against disposable PostgreSQL successfully.
- Next priority: add a database-backed HTTP regression for token invalidation after a real user-role change. No deployment or hosted database changes were made.


### 2026-10-14 — Database-backed role-change token invalidation

- Extended `tests/integration/HttpPostgresWorkflowTest.java` with a second end-to-end security regression.
- The test logs in as a student through the HTTP API, changes that test-created account's role to `FACULTY` through the disposable CI database's fixture-admin connection, and verifies that the previously issued `STUDENT` token receives HTTP 401 on the next protected request with the role-change message.
- This verifies current-role validation across HTTP, token verification, and PostgreSQL; it complements the existing mocked-role HTTP test. The privileged fixture connection is available only to the integration-test step and only modifies the unique account created by this test.
- Commit: [b292ec6](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/b292ec6b48d3f9ada2de26911d1e948f99b5e83d).
- Verification passed in GitHub Actions run [38609335639](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38609335639): Java compile/verification, PostgreSQL integration tests, API container build, and frontend production build all succeeded.
- Local tests were not run because this environment cannot clone the repository due to DNS/network unavailability; the GitHub Actions PostgreSQL workflow executed the regression successfully. No live Supabase connection or deployment was attempted.
- Next: audit the remaining HTTP authorization boundaries (ticket history, staff actions, emergency reads, and booking operations), then run a deeper Git-history secret scan.


### 2026-10-14 — Ticket history ownership at the HTTP boundary

- Expanded `tests/integration/HttpPostgresWorkflowTest.java` to verify the reporter can retrieve their ticket history over HTTP and a separately authenticated student receives HTTP 404 when attempting to read that ticket's history.
- The test exercises the real API, bearer-token identity, `TicketDAO.canViewTicket`, and the disposable PostgreSQL app role; the existing cross-reporter list test remains in place.
- Commit: [7e6fa09](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/7e6fa09215a0cffff52ce8f7820481a79937ff36).
- Verification passed in GitHub Actions run [38609797983](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38609797983): Java verification, PostgreSQL integration tests, API container build, and frontend production build all succeeded.
- No production code, hosted database, or deployment configuration changed. Local test execution remains unavailable in this environment due repository-clone DNS/network failure; CI is the verified test runner.
- Next: exercise staff ticket status authorization over HTTP/PostgreSQL for both the assigned technician and a different technician, then continue the route audit.


### 2026-10-14 — HTTP ticket history ownership regression

- Added assertions to `tests/integration/HttpPostgresWorkflowTest.java`: the authenticated reporter can retrieve their ticket history, while a separately logged-in student receives HTTP 404 for that ticket's history.
- The test uses the actual API and disposable PostgreSQL-backed application role; existing reporter-list isolation assertions remain intact.
- Verification passed in GitHub Actions run [38609974850](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38609974850): Java verification, PostgreSQL integration tests, Java API container build, and frontend production build all succeeded.
- Commit: [7e6fa09](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/7e6fa09215a0cffff52ce8f7820481a79937ff36).
- No production logic, hosted database, or deployment configuration changed. Local test execution remains unavailable here because repository cloning fails due to DNS/network access; CI ran the PostgreSQL-backed tests.
- Next priority: add HTTP/PostgreSQL coverage for staff ticket status changes, including assigned-technician success and denial for a different technician and a student.


### 2026-10-14 — Staff ticket status authorization over HTTP

- Expanded `tests/integration/HttpPostgresWorkflowTest.java` to create two technicians with different specializations and a student, then submit a Facilities issue through the authenticated API.
- The regression verifies the assigned technician can move the ticket to `IN_PROGRESS` (HTTP 200), a different technician cannot update that ticket (HTTP 403), and the student cannot invoke the staff status endpoint (HTTP 403).
- The first CI attempt [38610290512](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38610290512) exposed malformed Java escaping in the new JSON test bodies; corrected it in [047c73d](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/047c73de3e2675f6659cd1d2a7ec190b4b173994).
- Verification passed after the correction in GitHub Actions run [38610433866](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38610433866): Java verification, PostgreSQL integration tests, Java API container build, and frontend production build all succeeded.
- This is test-only coverage against disposable PostgreSQL. No live database or deployment was touched. Local tests remain unavailable because repository cloning fails due DNS/network restrictions in this environment.
- Next priority: test the complete HTTP booking lifecycle and cross-user cancellation boundary against disposable PostgreSQL.


### 2026-10-14 — HTTP booking lifecycle and owner-only cancellation

- Added `httpBookingLifecycleEnforcesOwnerOnlyCancellation` to `tests/integration/HttpPostgresWorkflowTest.java`.
- The test creates a unique resource using the disposable CI fixture-admin connection, logs in as two separate students, creates a booking over HTTP, verifies it appears in the owner's booking list, and confirms the other student cannot cancel it (HTTP 409) and the booking remains intact.
- The owner can then cancel the same booking through HTTP (HTTP 200), and it disappears from the owner's booking list.
- Commit: [331289a](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/331289acc09af8e303e9072cd5ffb7c2a4e5a0b1).
- Verification passed in GitHub Actions run [38610705111](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38610705111): Java verification, PostgreSQL workflow integration tests, Java API container build, and frontend production build all succeeded.
- The privileged fixture connection only creates a disposable test resource; booking operations run through the least-privilege application role. No live Supabase connection or deployment occurred.
- Next priority: exercise administrator emergency creation and recipient notification through the authenticated HTTP/PostgreSQL path.


### 2026-10-14 — Emergency broadcast through authenticated HTTP

- Added `httpEmergencyBroadcastPersistsAndNotifiesRecipients` to `tests/integration/HttpPostgresWorkflowTest.java`.
- The test creates a disposable admin and student, authenticates through the HTTP login endpoint, verifies admin-only emergency creation returns HTTP 201, and confirms another authenticated user can retrieve the persisted emergency feed.
- It checks the student receives exactly one matching broadcast notification, the creator admin receives none, and a student attempting to create an emergency receives HTTP 403.
- Commit: [ea23c3d](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/ea23c3d219215194c9ae8b452b1dd561288b1076).
- Verification passed in GitHub Actions run [38611005434](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38611005434): Java verification, PostgreSQL integration tests, API container build, and frontend production build all succeeded.
- The test runs only against the disposable PostgreSQL CI database; no hosted database or deployment was touched.
- Next priority: verify notification read ownership at the HTTP boundary, then continue the Git-history secret scan and route audit.


### 2026-10-14 — Notification read ownership over authenticated HTTP

- Extended `httpEmergencyBroadcastPersistsAndNotifiesRecipients` to capture the notification ID for a test-created recipient and exercise `POST /api/notifications/{id}/read`.
- A different authenticated student receives HTTP 404 when attempting to mark that recipient's notification read; the intended recipient receives HTTP 200. This verifies the API route and `NotificationDAO.markRead` ownership predicate together.
- Commit: [a897483](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/a897483e3e9c4c32e6ce10ede2ecaed3819db68b).
- Verification passed in GitHub Actions run [38611350005](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38611350005): Java verification, PostgreSQL integration tests, API container build, and frontend production build all succeeded.
- The test uses only the disposable PostgreSQL CI database; no live database or deployment was touched. Local tests were not run because cloning is unavailable in this environment.
- Next: conduct a deeper Git-history secret scan without printing any secret values, then continue the remaining API route/security audit.


### 2026-10-14 — Targeted Git-history credential review

- Reviewed commit history for the credential-sensitive paths `src/database/DBConnection.java` (3 commits), `src/security/AccessTokenService.java` (3), `resources/config.properties` (2), `resources/config.properties.example` (1), `.gitignore` (2), `.github/workflows/ci.yml` (7), and `frontend/.env.example` (1). Inspected the corresponding diffs without printing candidate values.
- No high-confidence private-key, GitHub-token, AWS-key, JWT-literal, or database-URL-with-embedded-credentials pattern was found in those inspected changes.
- The historical `resources/config.properties` addition used a loopback database URL and a placeholder password; it was removed from tracking in commit [95cd9c5](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/95cd9c575d5f575df36f30abd7a84a1b053c1805). The current Git tree does not contain that local file, and `.gitignore` excludes it; only the placeholder `resources/config.properties.example` is tracked.
- The current token service obtains its secret from `CAMPUSOS_TOKEN_SECRET`, rejects secrets shorter than 32 characters, fails closed when production has no configured secret, and generates a random process-local key only for local development. Workflow credential-like assignments are test/fixture values or environment references, not production credentials.
- Scope limitation: this was a targeted review of sensitive-path history, not a formal scan of every historical diff. The connected GitHub interface does not expose the repository's Secret Scanning alerts endpoint, so server-side alert status is unverified. No discovered value was reproduced, no credential was rotated, and no external system was changed.
- Next: audit HTTP authorization for admin ticket assignment and user/staff-management routes. No deployment or live database access was performed.



### 2026-10-14 — Admin ticket assignment HTTP regression verified

- Added `adminCanAssignOpenTicketOverHttpAndStudentsCannot` to `tests/integration/HttpPostgresWorkflowTest.java`. It creates a test issue and open ticket, assigns it to an available technician through the authenticated admin endpoint, verifies a student receives HTTP 403 when attempting the same operation, and checks the technician's queue and the reporter-visible assignment history.
- Commit: [575a242](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/575a24278ea31641f130f004651771ed94387a77).
- The initial workflow run [38612498688](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38612498688) had its PostgreSQL job stuck queued; the follow-up run [38612923042](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38612923042) completed successfully on the latest documentation commit. All four jobs passed: Java verification, PostgreSQL integration tests, API container build, and frontend production build.
- No live database or deployment was touched. Local tests remain unavailable because repository cloning fails due DNS/network restrictions.
- Next: test admin-created users and staff availability over HTTP, then continue the route/security audit.

### 2026-10-14 — CI verified after HTTP staff fixture isolation

- Rechecked the latest push-triggered workflow directly through the GitHub Actions API, rather than relying only on the repository's commit-status summary.
- Latest verified main commit at audit time: [37acddc](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/37acddc903805f5abef7bacca925fb864b8e811c), `fix: isolate HTTP staff status fixture assignment`.
- CI run [38613721895](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38613721895) completed with conclusion `success`. All four jobs passed: Java compile/verification, PostgreSQL workflow integration tests, frontend production build, and Java API container build. The PostgreSQL job's schema setup, least-privilege checks, and Maven integration-test step all passed.
- The immediately preceding run [38613285616](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38613285616) failed only in the PostgreSQL workflow test job; the other three jobs passed. The failing HTTP staff-status test was using a ticket fixture that was not assigned to the authenticated technician. Commit [6f0a306](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/6f0a3065e869b75c8c7900a46132cd1be341bf15) corrected the assertion/visibility expectation, and [37acddc](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/37acddc903805f5abef7bacca925fb864b8e811c) gave the status authorization check a dedicated ticket assigned to that technician. The later run confirms the combined fix.
- The current PostgreSQL HTTP integration suite includes emergency broadcast recipient/creator exclusion checks, admin ticket assignment authorization, admin user creation/listing, staff availability authorization and visibility, and rejection of tokens after a database role change.
- Local tests remain unrun because cloning the repository in this environment fails at DNS resolution for GitHub. The successful CI run is the verified test evidence; it does not imply a local run or production database check. No live database or deployment was touched.
- Next priority: continue the route-by-route API security audit, focusing on HTTP method handling, role/ownership checks, and malformed input behavior; add focused regression coverage for any concrete gap before changing production code.

### 2026-10-14 — Admin assignment input validation verified

- Route audit found that `POST /api/admin/tickets/{id}/assign` read `staffId` using Jackson's `asInt(0)`, which can coerce a fractional JSON number instead of rejecting malformed input.
- Updated `src/api/ApiServer.java` to require `staffId` to be an integral JSON number, convertible to a Java `int`, and greater than zero; invalid values now produce HTTP 400 before the DAO is called.
- Added an HTTP/PostgreSQL integration regression in `adminCanAssignOpenTicketOverHttpAndStudentsCannot`: a fractional `staffId` is rejected with 400, while the valid admin assignment and student-denial assertions still run.
- Commits: [e99c4ea](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/e99c4ea9467c9fe045a65e07ff46c93e68657c0e) (validation) and [e90a898](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/e90a8983f48f9a7fe51639376543c372c72758c5) (regression test).
- Verified CI: [run 38614244817](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38614244817) completed successfully on `e90a898`. All four jobs passed, including the PostgreSQL-backed integration test step. The previous known-good run [38613721895](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38613721895) also passed all four jobs before this change.
- Local tests remain unrun because repository cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: continue the API route audit, especially consistent method handling and malformed input at booking/resource boundaries; only change behavior where a concrete defect and regression test are established.

### 2026-10-14 — Booking endpoint method and identifier validation verified

- Continued the API route audit and corrected two concrete input/HTTP-contract defects in the booking collection route:
  - A request to the exact `/api/bookings` path using a method other than POST now returns HTTP 405; unknown paths still return 404.
  - `resourceId` must be a positive integral JSON number representable as a Java `int`. Fractional values are rejected with HTTP 400 rather than being silently truncated by Jackson's `asInt(0)`.
- Added HTTP/PostgreSQL integration assertions in `httpBookingLifecycleEnforcesOwnerOnlyCancellation` for PUT returning 405 and a fractional resource ID returning 400; the valid booking lifecycle assertions remain in the same test.
- Commits: [767e585](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/767e585afd2a160d02b610a67113fc40ddd29995) (method semantics), [3a9dbed](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/3a9dbedf773e1caad18b994828999a12a8b99aa9) (resource ID validation), [f802487](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/f802487698bcbe64c3ddf7375d1eb85734f87460) (method regression), and [8425ae2](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/8425ae2af63e66817fcdd0c877939f43859d24d) (fractional ID regression).
- Verified CI: [run 38614640124](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38614640124) completed successfully on `8425ae2`; Java verification, PostgreSQL integration tests, frontend production build, and API container build all passed.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: expand the HTTP booking input regression coverage for invalid or reversed timestamps and verify malformed requests do not create rows; continue the remaining API audit only where concrete correctness or security gaps are found.

### 2026-10-14 — Malformed booking timestamp handling verified

- The booking route parsed timestamps with `LocalDateTime.parse` but did not handle `DateTimeParseException`, so malformed client input could escape the route's 400-response path. Added the parse exception to the booking handler's validation catch.
- Expanded `httpBookingLifecycleEnforcesOwnerOnlyCancellation` to verify malformed timestamps and reversed intervals return HTTP 400, and that these invalid requests create no booking rows. Existing assertions also verify PUT returns 405 and fractional resource IDs return 400.
- Commits: [ed55c9b](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/ed55c9b80eb14d9b60d71050cad6e7ab39a76326) (parse-error handling) and [93ee2f3](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/93ee2f36335a2dd06dbf31c7cc72515542f43ae0) (timestamp/interval regression tests).
- Verified CI: [run 38614944835](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38614944835) completed successfully on `93ee2f3`. All four jobs passed: Java verification, PostgreSQL workflow integration tests, frontend production build, and API container build.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: finish the remaining admin-route HTTP method audit and add regression coverage for any known route/method mismatch; avoid changing behavior without a concrete test.

### 2026-10-14 — Admin collection method handling verified

- The route audit found that unsupported methods on exact `/api/admin/users` and `/api/admin/overview` paths fell through to the generic route-not-found response (404). Added a method-not-allowed response (405) for those recognized collection paths while preserving their allowed GET/POST behavior and existing authorization checks.
- Added HTTP/PostgreSQL integration assertions that authenticated admins receive 405 for PUT requests to both paths; the existing admin user-management and staff-availability checks continue to run.
- Commits: [6fb201c](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/6fb201c43ed170c998d3f90297ef2ac7d40d17fc) (route behavior) and [43eec57](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/43eec57cf015a92a1d557e7cda70b340022da108) (HTTP regression tests).
- Verified CI: [run 38615297944](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38615297944) completed successfully on `43eec57`. All four jobs passed, including the PostgreSQL workflow integration tests.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: review emergency broadcast request validation against database column constraints and ensure invalid oversized inputs return 400 rather than surfacing as server errors.

### 2026-10-14 — Emergency broadcast size-limit regression coverage verified

- Confirmed `POST /api/emergencies` already enforces nonblank title/message, a 200-character title limit, and a 3,000-character message limit before calling the transactional service. No production change was necessary for this task.
- Added integration checks that an over-limit title (201 characters) and over-limit message (3,001 characters) each return HTTP 400. The existing successful broadcast, recipient notification, creator exclusion, and notification-read ownership checks remain in the same workflow suite.
- Test commit: [c5867bb](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/c5867bbcddeff4a3d39b7636ae3a54a41d67a986).
- Verified CI: [run 38615712017](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38615712017) completed successfully on `c5867bb`; Java verification, PostgreSQL integration tests, frontend production build, and API container build all passed.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: continue a focused API input/authorization audit, starting with service-level validation for emergency creation and checking that all user-facing handlers convert malformed JSON types into client errors rather than unhandled exceptions.

### 2026-10-14 — Emergency service and JSON field validation verified

- Added defense-in-depth validation to `EmergencyService.createEmergency`: title is capped at 200 characters and message at 3,000, matching the HTTP contract and database title column. Invalid details are rejected before opening a database connection.
- Added `tests/service/EmergencyServiceTest.java` to cover missing details and both length limits without needing a database.
- Updated `POST /api/emergencies` to require JSON string values for `title` and `message`; numbers and booleans now return HTTP 400 instead of being coerced to text.
- Expanded `httpEmergencyBroadcastPersistsAndNotifiesRecipients` with regressions for numeric title, boolean message, oversized title, and oversized message; existing successful broadcast and notification ownership assertions remain.
- Commits: [68daeb1](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/68daeb1a060abbdede8b85c0e0c5c7a674a36a6a) (service validation), [ee32817](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/ee328179aa4b831a810617e0c6e621fddebc9559) (service tests), [28330c2](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/28330c2f24cddef370e2e4975b0ccb5944717bd1) (HTTP type validation), and [3b6930d](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/3b6930dc467233fab5283607d4f5c970b5ff2f8c) (HTTP regressions).
- Verified CI: [run 38616146533](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38616146533) completed successfully on `3b6930d`; all four jobs passed.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: audit issue submission and admin user-creation JSON field types, ensuring fields documented as strings are not silently coerced from numbers, booleans, arrays, or objects.

### 2026-10-14 — Issue, login, and admin user JSON type validation verified

- Updated the API to reject non-string values for issue fields (`title`, `description`, `category`, optional `location`), login `email`/`password`, and admin user fields (`name`, `email`, `password`, `role`, and optional profile fields). These inputs now return HTTP 400 instead of allowing Jackson's `asText()` coercion to turn numbers/booleans into strings.
- Added unit-style HTTP coverage in `ApiInputValidationTest` for numeric issue titles and numeric login emails, plus PostgreSQL HTTP coverage for a numeric admin-user name. Existing malformed JSON, content-type, and oversized-body checks remain.
- Commits: [661eee0](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/661eee0fa1948affb3c98a38e44c60f149ffb114) (validation), [503f854](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/503f854d450ac131347cfbfe9a98d0816d1b4241) (issue test), [9da41cf](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/9da41cf2a87c544566337edda4d15dc3fec779a4) (admin user test), [7998060](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/7998060d1aedc798cf8daa951c1acf34ba0397ae) (login validation), and [eb95954](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/eb959540604198bc1be2484a1e908192e69845bb) (login test).
- Verified CI: [run 38616603288](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38616603288) completed successfully on `eb95954`; Java verification, PostgreSQL integration tests, frontend build, and API container build all passed.
- Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment. No production database or deployment was touched.
- Next priority: inspect remaining API handlers for unhandled field-type coercion and service-layer invariants; preserve role/ownership behavior and add focused tests for any concrete defect.

### 2026-10-14 — Ticket status JSON type validation

- Extended the field-type audit to `POST /api/staff/tickets/{id}/status`. The handler now requires `status` to be a JSON string before normalizing or calling the DAO; numeric/boolean/object values return HTTP 400 rather than being coerced and potentially reported as an invalid state transition.
- Added an HTTP/PostgreSQL regression that submits numeric `status`, asserts HTTP 400, then continues with the valid `IN_PROGRESS` transition to prove the rejected request did not prevent the legitimate workflow.
- Commits: [6293334](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/6293334d8d4833c4bd25f06937aaf41054cc0b42) (validation) and [182888e](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/182888ede0f6e93d8995d1584f885886124114b4) (regression test).
- The workflow for the test commit was cancelled during rapid successive pushes; this is not a pass or failure. A fresh CI run on the current main head is required before marking this change verified.
- Next priority: inspect remaining API handlers and service-layer invariants for concrete type-validation gaps. No deployment or production database access was performed.

### 2026-10-14 — Consolidated API input-validation CI confirmation

- Follow-up CI [run 38617040775](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38617040775) completed successfully on `dc6c1c2`, including the ticket-status string validation and regression test. Java verification, PostgreSQL integration tests, frontend production build, and API container build all passed.
- The current validated API changes cover strict JSON types for login credentials, issue fields, emergency fields, admin-created user fields, ticket status, staff assignment IDs, booking resource IDs, and boolean staff availability; malformed/reversed booking times and oversized emergency inputs have explicit regression coverage.
- No deployment or production database access was performed. Local tests remain unrun because repository cloning is blocked by GitHub DNS resolution in this environment.
- Next priority: continue the API security/data-integrity audit for a concrete gap; preserve current role and ownership controls and require a regression test for any fix.

### 2026-10-14 — Production container now fails closed without token secret

- Security review found the Docker image did not set `CAMPUSOS_ENV=production`. Because `AccessTokenService` generates a random development-only secret when production mode is absent, a hosted container could silently start without the stable auth secret and invalidate tokens on restart or across replicas.
- Set `CAMPUSOS_ENV=production` in the runtime image and documented the behavior in the backend environment-variable table. The container now requires `CAMPUSOS_AUTH_SECRET` of at least 32 characters at startup.
- Added a CI smoke step that runs the built image without the secret and asserts it exits nonzero with the expected configuration error. Existing `AccessTokenServiceTest.productionRequiresConfiguredSecret` continues to cover the secret resolver.
- Commits: [ee366ed](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/ee366edd3824e97cfdd141ad4e09ef97b87972e0) (container production mode), [0ad3e4c](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/0ad3e4c571af3c6c6070eba589ede45478fcc144) (documentation), and [bbbf31c](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/bbbf31c728dcfea1afa4ac3900586c5f6ef7f959) (CI smoke test).
- Verified CI: [run 38617474025](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38617474025) completed successfully on `bbbf31c`; all four jobs passed, including the new fail-closed container smoke test.
- No deployment was performed. Local tests remain unrun because cloning fails at GitHub DNS resolution in this environment.
- Next priority: continue the security audit around browser token handling and frontend/API boundary hardening; do not deploy without the configured production secret and verified hosting settings.

### 2026-10-14 — README security posture corrected and CI verified

- Corrected stale README statements that said API requests were not protected by verified tokens and that database-backed integration tests were absent. The docs now reflect the signed bearer-token flow, current-role/ownership checks, and existing unit/PostgreSQL HTTP integration coverage.
- The security note now accurately calls out `sessionStorage` token exposure to same-origin JavaScript/XSS and lists the remaining release blockers: live Supabase verification, token revocation, production TLS/CORS verification, audit trail, distributed rate limiting, and a complete security review.
- Documentation commit: [d9b5ed1](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/d9b5ed1e5b9b384a2596e4d4ca57e8ba16242f3e).
- Verified CI: [run 38617920196](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38617920196) completed successfully on `d9b5ed1`; all four jobs passed.
- No deployment or live database access was performed. The repository tree scan found no tracked real `.env`, private-key, or `resources/config.properties` file; this was a current-tree check, not a full historical secret scan.
- Next priority: continue browser/API boundary hardening only where the change can be tested and avoids breaking the documented local-development workflow.

### 2026-10-14 — Production browser API transport guard verified

- Added `frontend/src/security.js` with `isApiUrlSecure`. In production builds, the client now blocks API requests to public `http://` origins before attaching/sending bearer tokens; HTTPS origins are accepted. HTTP loopback is allowed for local production-build previews, and local development remains unchanged.
- Added five Node built-in unit tests covering HTTPS, public HTTP rejection, loopback preview, development HTTP, and invalid URLs. `frontend/package.json` now exposes `npm test`, and CI runs it before the Vite production build.
- Commits: [0ee18c5](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/0ee18c53511a25ab6977a18d4891ace183d1ec3f) (helper), [2868006](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/2868006dd9a65995d1ba6c9a74513b4e86c9d918) (API guard), [b23f27e](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/b23f27ea51919af3e62bbe39efa59b18f40f3005) (unit tests), [d2e6071](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/d2e60711a99f341e5bf188a429d0308012ea6308) (test script), and [280d962](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/280d96215f1232a694faab3a18a2ee4d5879160d) (CI test step).
- Verified CI: [run 38618404805](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38618404805) completed successfully on `280d962`. All four jobs passed; the frontend security unit tests and production-container fail-closed smoke test both passed.
- No deployment was performed. The guard intentionally does not solve XSS or replace production TLS/CORS configuration.
- Next priority: run a final route/authorization and dependency/configuration review; only make further changes for a concrete defect with a verifiable regression test.

### 2026-10-14 — Frontend dependency audit added to CI

- Added `npm audit --audit-level=high` after `npm ci` in the frontend CI job. The verified run reported **0 vulnerabilities** (no advisories at any severity in the installed dependency tree) and passed the audit step.
- CI run [38618848266](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38618848266) completed successfully on `1bcb15d`; Java verification, frontend dependency audit + five security unit tests + production build, PostgreSQL integration tests, and container fail-closed smoke test all passed.
- The runner log emitted a maintenance warning that `actions/checkout@v4` and `actions/setup-node@v4` target Node.js 20 and are being forced onto Node.js 24. This did not fail CI, but upgrading the workflow actions to supported Node 24-native versions is the next maintenance task.
- No deployment or production database access was performed.

### 2026-10-14 — GitHub Actions Node runtime warning resolved

- Updated all four workflow checkout references from `actions/checkout@v4` to `@v7`, and the frontend setup action from `actions/setup-node@v4` to `@v7`. Verified their upstream `action.yml` files declare `runs.using: node24`, addressing the deprecation warning seen in CI logs.
- CI run [38619188597](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38619188597) passed on `485cdbed`: Java verification, PostgreSQL integration tests, frontend `npm audit` (0 vulnerabilities), five frontend security unit tests, Vite build, and the container fail-closed smoke test all passed.
- No deployment was performed.
- Next priority: perform a final code/configuration audit for concrete security gaps and check current dependency/secret posture; do not access production credentials or deploy.


### 2026-10-15 — Rate limiter cleanup hot-path hardening; current CI limitation

- Code review found that once the login rate limiter held at least 5,000 client buckets, `cleanupIfNeeded` scanned the entire concurrent map on every login request. At the 10,000-bucket cap, this made a bounded-memory defense impose repeated O(n) work on the authentication hot path.
- Changed `src/security/RateLimiter.java` to schedule a full stale-bucket sweep at most once per 60 seconds using an atomic compare-and-set gate. The existing 5,000-bucket threshold, 10,000-bucket cap, expiry rules, per-key synchronization, and fail-closed behavior for new keys at capacity remain intact.
- Added `RateLimiterTest.throttlesExpensiveBucketCleanup`, which crosses the cleanup threshold and asserts an immediate follow-up request does not reschedule another full scan.
- Commits: [975b6ca](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/975b6caa0dd72bbc9e062fb25fc2ba4f058f225c) (implementation) and [ea1114c](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/ea1114c8c1266bb205081a1fbf219c32ffdbff09) (regression test).
- Targeted verification performed locally against the current fetched source: standalone Java 21 compilation and harness passed rate-limit/reset behavior plus the cleanup-throttle regression. The five frontend URL-security unit tests also passed under Node 22.16.0 using the exact current source/test files.
- Full Maven suite was not run: Maven is not installed in this environment, and cloning the repository failed because `github.com` DNS resolution is unavailable. The last directly inspected full CI run is [38619188597](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38619188597) on `485cdbed`; all four jobs succeeded. It predates the later workflow SHA-pinning commits and the current rate-limiter change.
- Current push CI for the latest `main` head cannot be verified through the available connected GitHub interface: combined commit statuses are empty, and the workflow-run wrapper returns only pull-request-triggered runs. The GitHub Actions web page is also inaccessible from this environment. Do not infer success or failure from missing status data.
- No live Supabase project was accessed, no secrets were read, and no deployment was performed.
- Follow-up: the shared-IP lockout issue was addressed and regression coverage was added in the subsequent 2026-10-15 entry below. The new JUnit test and latest main-branch CI still require execution/verification.

### 2026-10-15 — Shared-IP login throttling regression and fix

- Fixed the login handler to clear the source-IP failure bucket only after AuthService.authenticate succeeds. Successful logins no longer accumulate toward a shared-IP lockout; failed credentials still consume the IP budget and are throttled after the configured five attempts. Account/email-level throttling in AuthService remains in place.
- Added a package-private AuthService injection point to ApiServer so login HTTP behavior can be tested without a live database; existing constructors keep their current production behavior.
- Added tests/api/LoginRateLimitTest.java: six successful logins from the same loopback IP must all return HTTP 200, while five invalid attempts return HTTP 401 and the sixth returns HTTP 429 with Retry-After: 900.
- Commits: [2acdb83](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/2acdb83782da715061ed454a668b5ab5ce17354f) (fix/test seam) and [49c4e8c](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/49c4e8c890e3759d10b49d4fbad76555e1ce76b5) (HTTP regression tests).
- Verification limitation: reviewed the diff and test logic, but did not run the new JUnit tests or full Maven suite because Maven is not installed in this environment. The GitHub Actions workflow triggers on pushes to main, but the available GitHub status/run lookup cannot expose push-triggered runs; no CI pass/fail is claimed for these commits.
- No live Supabase access, secret access, or deployment occurred.
- Next priority: verify the complete CI run for the current main head when Actions run visibility or a Maven-enabled checkout is available; then continue the route-by-route authorization review, focusing on untrusted proxy headers, role/ownership checks, and abuse controls.

### 2026-10-15 — Authorization review checkpoint; verification blocker remains

- Started the next route-by-route security review after the shared-IP throttle change. Inspected the HTTP handlers for issue submission, reporter ticket reads, ticket history/status, staff tickets, notifications, emergencies, resources, bookings, admin user management/assignment, and the corresponding ticket/user/booking DAO authorization checks.
- In the reviewed paths, found existing bearer-token/current-role checks, student ticket ownership checks, staff assignment checks, admin role gates, and booking cancellation ownership checks. This is a partial review, not a security sign-off; no new production code change was justified by the inspected segment alone.
- Checked the current main head after the login-throttle commits: the workflow file has a push trigger for main, but the connected combined-status query returns no entries and the connected workflow-run lookup only exposes pull-request-triggered runs. These empty results do not prove that CI passed or failed.
- Verification is blocked until either the Actions run for the current main head can be inspected or the repository can be built in an environment with Maven and dependency/network access. This environment has Java 21 but no Maven; the earlier repository clone failed at GitHub DNS resolution. The new HTTP regression test and full Maven suite therefore remain unexecuted here.
- No live Supabase access, secret access, or deployment occurred.
- Next action: obtain an inspectable Actions run for the current main commit or a Maven-enabled checkout, run `mvn --batch-mode --no-transfer-progress clean verify`, fix any failures, then continue the unfinished authorization review (including request-body limits, error handling, and deployment/proxy assumptions).


### 2026-10-15 — Current main CI independently verified; authorization audit resumes

- Remote `main` HEAD was confirmed as [a628736](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/a628736510ecf65e81fb516cc1999efacf8f018e) (`docs: record auth audit checkpoint and test blocker`), ahead of the previously known emergency regression commits. GitHub's compare endpoint confirms `9546869` is the merge base and `5d3ba96` is included in the 83 commits ahead of it; the emergency notification regression and its documentation are present in current `main`.
- Retrieved the actual push-triggered GitHub Actions run [38678705534](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38678705534) for exact HEAD `a628736510ecf65e81fb516cc1999efacf8f018e`. Run conclusion: **success**. All four jobs completed successfully: Java `mvn clean verify`, PostgreSQL workflow integration tests (including schema/least-privilege role checks), frontend dependency audit + security tests + production build, and API container build + fail-closed auth-secret smoke test.
- This resolves the previous *CI visibility* blocker for this exact commit. Local execution is still unavailable in this session: Java 21 exists, Maven is absent, and `git ls-remote` fails because this environment cannot resolve `github.com`. No local test result is claimed; CI is the verified test evidence.
- Open pull requests: none returned by GitHub. Open issues: none returned by repository issue search. Only `main` was returned by branch search.
- No production database, secrets, deployment, or hosting settings were accessed or changed.
- Next priority: continue the route-by-route trust-boundary audit, focusing on proxy-derived client identity, authentication/rate-limit concurrency, and request failure handling. Make a code change only when a concrete exploitable defect and a regression test can be established; run the CI checks for the exact resulting commit.


### 2026-10-15 — Confirmed CI completion and continued security review

- Rechecked the push-triggered workflow for exact main commit [685530a](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/685530a6b0e905e256bc819a965b0e2c0e7f865c). Run [38678947293](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38678947293) completed with conclusion **success**. All four jobs passed: Java compile/Maven verification, PostgreSQL workflow integration tests, frontend dependency audit/security tests/production build, and Java API container build including fail-closed auth-secret smoke test.
- Inspected current RateLimiter, AccessTokenService, login HTTP regression tests, API handlers, admin role gate, booking DAO/service, and PostgreSQL integration coverage. No confirmed defect from this read-only pass was patched; avoid speculative auth/rate-limit changes without a failing regression test.
- Login rate limiting remains source-peer-IP based and in-memory, with a 10,000-bucket cap and cleanup throttled to once per minute. This is not a distributed limit across replicas; proxy-aware client identity must not be added unless the trusted-proxy boundary is explicit. Account-level throttling remains in AuthService.
- Current CI is verified for 685530a; the repository was not locally cloned and Maven tests were not run locally in this environment. Remote CI is the test evidence.
- Next review target: inspect request-body parsing/Content-Type enforcement and error response paths for unhandled malformed inputs, then inspect database transaction/rollback edges in issue creation, emergency notifications, and booking cancellation. Make a code change only when the defect is concrete and a regression test can demonstrate it.
- No live database, credentials, deployment, or hosting settings were accessed or changed.


### 2026-10-15 — Pre-hosting hardening and administrator ticket export

- Verified CI run [38679082444](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38679082444) completed successfully for exact commit `9cf1c9f9a5d279fb36ffc9fd1b627b671e6951c4`; all four jobs passed.
- Fixed the frontend production API fallback: `localhost:8080` is now only the development default. A production build with missing `VITE_API_BASE_URL` fails closed in the browser instead of silently trying the visitor's localhost. Added a regression test for an empty production API origin.
- Added root `vercel.json` for Vite frontend build/output and SPA navigation. Hosting architecture remains split: Vercel hosts the frontend; the long-running Java API needs a Java/container-capable host; Supabase PostgreSQL needs a dedicated project and least-privilege JDBC role. No deployment was triggered or configured.
- Added administrator-only `GET /api/admin/reports/tickets.csv` and a Campus Overview download control. Report includes up to 10,000 most recent tickets, reporter identity, status, priority and assigned staff. CSV cells are quoted and spreadsheet formula prefixes are neutralized; unit tests cover quoting, line breaks, null cells and formula injection. Added a PostgreSQL-backed HTTP integration test for admin download and student denial.
- Updated README hosting setup, environment-variable boundaries, and route/feature status. Never put DB credentials or `CAMPUSOS_AUTH_SECRET` in a `VITE_*` variable. No keep-alive workflow was added; confirm Supabase plan/pause behavior and supported policy before considering any scheduled ping.
- CI for the code/config changes is triggered on pushes and prior runs may be cancelled by later pushes. Final verification must use the completed workflow run for the resulting exact `main` SHA, including the new Java tests, PostgreSQL HTTP integration test and frontend production build. Local Maven remains unavailable in this environment, so no local test pass is claimed.
- No Supabase project was connected or modified; no secrets, production data, deployment, or hosting settings were accessed or changed.
- Remaining release gates: complete route/role audit; verify schema and least-privilege role against a dedicated Supabase project; run full smoke test matrix against a deployed staging API/database after owner authorizes staging setup; configure trusted HTTPS API origin and CORS; decide on token revocation/distributed rate limiting; check that every visible role/navigation feature has an end-to-end test. Do not deploy until the owner explicitly says to host.

### 2026-10-15 — CSV download hardening and pre-hosting platform inventory

- Improved the administrator CSV download lifecycle in `frontend/src/App.jsx`: the browser object URL is now revoked after a short delay rather than synchronously immediately after triggering the download, reducing the risk of a browser cancelling the download before it consumes the blob.
- Verified exact code commit [596b94e](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/596b94ebf8a5003f26a97345a1ca9bc96c8201bc) with CI run [38680350481](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38680350481): all four jobs passed. The PostgreSQL-backed run executed all 38 JUnit test methods with zero failures/errors/skips, including the admin CSV HTTP integration test. The frontend dependency audit reported 0 vulnerabilities and the frontend security tests/build passed. The container build and fail-closed missing-auth-secret smoke check passed.
- Read-only platform inventory: the connected Vercel account lists only projects `prasad` and `magco`; no CAMPUSOS Vercel project was found. The connected Supabase account lists only `TRAVEL_CRM`, whose project status is `INACTIVE`. Do not use or modify that unrelated project for CAMPUSOS. No deployment, project creation, database query, secret read, or hosting configuration change was performed.
- Current pre-hosting release gates: create/identify a dedicated CAMPUSOS Supabase project and apply/review the schema and least-privilege role; create/identify the CAMPUSOS Vercel project; configure a separate Java/container API host with a stable `CAMPUSOS_AUTH_SECRET`, JDBC pooler URL with TLS, and exact `CAMPUSOS_CORS_ORIGINS`; then run live staging smoke tests before any production release. The repository has not been deployed by this workflow.

### 2026-10-15 — Pre-launch performance, mobile UX, and accessibility pass

- `src/api/ApiServer.java`: replaced the fixed executor's unbounded task queue with a bounded 256-request queue and `CallerRunsPolicy` back-pressure. Worker count remains capped at 16; this limits queued-request memory growth during bursts instead of promising unlimited throughput. Load testing is still required to tune the queue and worker count for the eventual host and database pool.
- `frontend/src/styles.css`: added visible keyboard focus, consistent disabled states, overflow protection for long user/ticket content, improved mobile navigation with readable labels, kept logout available on small screens, tightened small-screen panel spacing, and respected reduced-motion preferences. Existing visual theme was preserved rather than redesigned.
- Verified source commit [750516d](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/commit/750516de81ca0fc497b8bb2c7587228226700ade) using CI run [38680815949](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38680815949): all four jobs passed. PostgreSQL-backed tests ran 38 methods with 0 failures, 0 errors, and 0 skipped; least-privilege app-role checks passed; frontend audit found 0 vulnerabilities; Vite build passed; API container build and fail-closed auth-secret check passed.
- Frontend production build measurements from that CI run: JavaScript 255.86 kB minified / 76.61 kB gzip; CSS 10.78 kB minified / 3.15 kB gzip; Vite reported build time 881 ms. These are build artifact measurements, not real-user page-load measurements or a load-test result.
- Still outstanding before launch: live browser/device review, real-user/network performance metrics, API/database load and soak tests, real staging HTTP smoke tests, and a dedicated CAMPUSOS Supabase project/API host/Vercel project. No hosting or production database was changed.


### 2026-10-15 — Reporter ticket pagination and accurate dashboard counts

- Replaced the reporter-ticket query's unbounded result set with database-side pagination (20 rows per page, stable `created_at DESC, id DESC` ordering) and a validated `page` query parameter. The endpoint continues enforcing that a signed-in student/faculty member can only retrieve their own tickets.
- Added a compact PostgreSQL aggregate query for total, active, and resolved/closed ticket counts, so dashboard metrics remain accurate independently of the displayed page. The ticket view has Previous/Next controls and range/page information; overview shows the newest tickets.
- Kept the original one-argument DAO method as a bounded first-page compatibility wrapper. No schema change is required; the existing reporter/created-at index supports this access pattern, with `id` as a deterministic tie-breaker.
- Extended the PostgreSQL-backed HTTP workflow test to verify pagination metadata, bounded result size, summary counts, and rejection of page zero. CI must pass on the new commit before this task is considered complete.
- Browser/device review, representative-data query-plan inspection, and load testing remain staging gates. No deployment or database changes were performed.


### 2026-10-15 — Stable ordering for recent emergency feed

- Updated `EmergencyDAO.findRecent()` to order the bounded 50-item emergency feed by `created_at DESC, id DESC`. Emergencies created at the same timestamp now have deterministic ordering rather than database-dependent tie order.
- Updated the existing emergency timestamp index in both PostgreSQL/Supabase and MySQL schema bootstrap files to include the `id DESC` tie-breaker, keeping the index aligned with the query without adding a separate redundant index.
- Verification: source/schema review completed; GitHub Actions on this commit is the required build and integration verification. Local Maven execution is unavailable in this environment, so no local test pass is claimed.
- No live database was modified and no hosting/deployment was performed.
- Next priority: continue reviewing bounded list queries and transaction boundaries for a concrete correctness/performance issue; keep all live-environment work behind explicit owner authorization.


### 2026-10-15 — Deterministic ordering for resource, user, and staff-ticket lists

- Continued the bounded-list query audit and added primary-key tie-breakers to resource inventory (`name, id`), assignable staff (`role, name, id`), administrator user list (`role, name, id`), open-ticket IDs (`created_at, id`), and staff ticket queue (`priority rank, created_at, id`). Equal names/timestamps/priorities no longer rely on unspecified database row order.
- No schema/index changes were needed: this task is about deterministic ordering, not claiming a query-performance improvement. Existing result limits are unchanged.
- Verification: reviewed the exact source changes; CI for the resulting commit is required to validate compilation, PostgreSQL integration, frontend build, and container build. Maven is unavailable locally, so no local test pass is claimed.
- No live database or hosting/deployment changes were made.
- Next priority: inspect transaction boundaries and error paths for concrete data-integrity defects, then complete pre-hosting release-gate review without deploying absent explicit authorization.


### 2026-10-15 — Bound legacy unread-notification lookup

- The legacy `NotificationDAO.findUnreadMessages()` query loaded every unread message for a user and ordered oldest-first without a stable tie-breaker. Updated it to return only the newest 100 unread messages, ordered by `created_at DESC, id DESC`, consistent with the bounded inbox API and preventing unbounded result growth.
- Added a PostgreSQL integration regression test that inserts 105 unread messages and asserts the method returns exactly the newest 100 in deterministic order.
- No schema/index change was made: the existing notification indexes support user/read filtering and recent notification access; this focused change bounds application memory/result size.
- Verification: source and regression test are committed; GitHub Actions on the exact commit is the required validation. Local Maven execution is unavailable here, so no local test pass is claimed.
- No live database or hosting/deployment changes were made.
- Next priority: continue checking transactional rollback behavior and API body/error handling using concrete regression tests; keep live environment work behind explicit owner authorization.


### 2026-10-15 — Align MySQL bootstrap schema integrity constraints

- The data-integrity audit found a concrete mismatch: PostgreSQL's schema rejects negative resource quantities and booking intervals where `start_time >= end_time`, but the MySQL bootstrap schema did not. Service validation helps normal API requests, but database constraints are needed to protect direct DAO calls and future code paths.
- Added matching MySQL 8 CHECK constraints for non-negative resource quantity and strictly positive booking duration. Added a one-time MySQL migration for existing databases; operators must inspect and correct any pre-existing invalid rows before applying it. No live database was touched.
- Added a schema-contract regression test to ensure both bootstrap schemas enforce these invariants and the existing-MySQL migration documents/applies them.
- Verification is pending GitHub Actions for the exact commit; Maven is not available locally, so no local test pass is claimed.
- No hosting/deployment changes were made. Next: continue the transactional and API error-path audit after CI is green.


### 2026-10-15 — Make booking concurrency lock database-portable

- The transaction audit found that `BookingDAO.createIfAvailable()` used PostgreSQL-only `pg_advisory_xact_lock`, while the documented local configuration and MySQL bootstrap schema claim MySQL support. This caused resource bookings to fail on MySQL before reaching the capacity check.
- Replaced the advisory lock with a row-level `SELECT id FROM resources WHERE id = ? FOR UPDATE` lock inside the existing transaction. Both MySQL and PostgreSQL support this pattern; requests for the same resource serialize while capacity is checked and the booking is inserted. Missing resources are rejected while acquiring the lock.
- The existing PostgreSQL integration test exercises simultaneous capacity-one booking attempts and must remain green. The test suite does not currently include a live MySQL service, so MySQL runtime verification remains a follow-up rather than a claimed pass.
- No schema, live database, or hosting/deployment changes were made. CI on the exact commit is required; Maven is not available locally.
- Next priority: continue database-portability and transaction rollback review; keep live environment work behind explicit authorization.


### 2026-10-15 — CI caught least-privilege incompatibility in portable booking lock

- CI run [38683733044](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38683733044) failed on commit `e758f47`: Java verification, frontend tests/build, and container checks passed, but PostgreSQL workflow integration failed because `SELECT ... FOR UPDATE` requires UPDATE privilege on `resources`, intentionally not granted to the least-privilege app role.
- Corrective approach: retain PostgreSQL transaction-scoped advisory locking on PostgreSQL, where the role can acquire the lock without extra table privileges; use a resource-row `FOR UPDATE` lock on MySQL. This preserves same-resource booking serialization without broadening PostgreSQL permissions.
- Re-run the full CI workflow on the correction before considering the booking-lock task complete. MySQL runtime behavior remains unverified unless a MySQL service test is added.
- No live database, production secrets, or hosting/deployment settings were accessed or changed.


### 2026-10-15 — Make reporter ticket summary SQL portable

- The database portability audit found `TicketDAO.getReporterTicketSummary()` used PostgreSQL-only aggregate `FILTER (WHERE ...)`, which fails against the documented MySQL local-development database.
- Replaced the filtered aggregates with standard `SUM(CASE WHEN ... THEN 1 ELSE 0 END)` expressions for open and resolved counts.
- Extended the PostgreSQL workflow integration test to assert the reporter summary across closed, assigned, and in-progress tickets; this checks returned counts in CI. It does not substitute for a MySQL service test, which remains a portability verification gap.
- Updated this log before committing. No live database or hosting/deployment settings were changed.
- Next: continue the cross-database SQL audit, focusing on timestamp casts and dialect-specific constructs in booking capacity calculations.


### 2026-10-15 — Remove dialect-specific timestamp cast from booking capacity query

- The next portability pass found `BookingDAO.createIfAvailable()` seeded its capacity-check CTE with `CAST(? AS timestamp)`. PostgreSQL accepts this syntax, but MySQL's `CAST` target-type support does not reliably include `TIMESTAMP`, making the query a compatibility risk for the documented MySQL setup.
- Changed the seed to `SELECT ? AS point_time`; JDBC binds the parameter with `setTimestamp`, and the `UNION` with the schema's `start_time` supplies the common temporal type. Existing PostgreSQL workflow booking tests will verify the behavior in CI.
- This does not count as live MySQL runtime verification; the repository still needs a MySQL-backed integration job to establish that dialect end-to-end.
- No production database or hosting settings were changed. Wait for and inspect CI on this exact commit before proceeding.


### 2026-10-15 — Add real MySQL booking integration coverage to CI

- Added a dedicated MySQL 8.4 service job to GitHub Actions, initialized from the tracked `resources/campusos.sql` bootstrap schema and configured through the same JDBC environment variables used by the application.
- Added an environment-gated MySQL integration test for the actual `BookingDAO` path: generated IDs, booking creation, half-open adjacent intervals, overlap rejection, and two concurrent requests against a capacity-one resource. The test cleans up its own fixtures and is isolated from the PostgreSQL-only integration test.
- This is intended to close the biggest database portability gap. The change is not considered verified until the new MySQL job and all existing CI jobs finish successfully; any failures must be fixed and CI rerun.
- No live/production database or hosting settings were accessed or changed.
- Next: after CI is green, extend MySQL coverage to other critical DAO workflows and continue the release-gate/security audit.


### 2026-10-15 — Fix MySQL integration test compilation and await full CI

- The first CI run for the new MySQL job caught a Java test-compilation error: fixture IDs assigned during database setup were captured directly by lambdas and therefore were not effectively final. The production application code was not implicated by this failure.
- Fixed the test by copying the initialized fixture IDs into final local variables before using them in the overlap and concurrency lambdas.
- The first run's CI results are being treated as a genuine failure; the task is not complete until a new run on the correction passes all Java, frontend, container, PostgreSQL and MySQL jobs.


### 2026-10-15 — Correct second concurrent MySQL test lambda

- The rerun caught one remaining reference to the mutable setup IDs in the second concurrency lambda. Updated both worker submissions to use the final fixture IDs; CI compile output pinpointed the remaining references.
- Re-run the complete workflow on this exact fix. The MySQL integration task remains open until the MySQL service test itself passes, not merely compiles.


### 2026-10-15 — Isolate MySQL integration job from database-absence smoke test

- The MySQL booking integration test itself passed on MySQL 8.4: booking inserts, adjacent half-open intervals, overlap rejection, and simultaneous capacity-one requests all succeeded.
- The job still exited non-zero because running the entire Maven suite with MySQL connection variables made an unrelated API smoke test expect a database-unavailable health response (503) while the configured MySQL database correctly made it healthy (200).
- Narrowed the MySQL job to compile tests and execute only `MySqlBookingIntegrationTest`. The normal Java verification job continues to run the full unit/test suite without a configured database; the PostgreSQL job continues to run the full PostgreSQL-backed workflow suite.
- Re-run all jobs on this exact workflow adjustment. No application production code or live database was changed.


### 2026-10-15 — Extend MySQL integration coverage to issue/ticket lifecycle

- Expanded the MySQL 8.4 integration suite beyond bookings to exercise the actual issue submission transaction, automatic staff assignment, reporter/staff ticket views, portable reporter summary SQL, assignment notifications, ticket history, and status transitions through CLOSED.
- The test uses uniquely named fixtures and cleans up tickets, history, notifications, issues, and users. This supplements the booking interval/concurrency test; all MySQL runtime claims depend on the CI job result for the exact commit.
- No production database or hosting changes were made.


### 2026-10-15 — Verify MySQL and PostgreSQL CI, expand anonymous-route regression coverage

- GitHub Actions run [38685816831](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38685816831) completed successfully on commit `20afe17872651cb2741d0475eeefd0c66d6efb34`: all five jobs passed, including MySQL 8.4 booking plus issue/ticket lifecycle integration, PostgreSQL workflow integration, Java verification, frontend build/audit/tests, and container checks.
- Added a route-matrix regression test asserting anonymous GET and POST requests are rejected with HTTP 401 across protected ticket history, staff, notification, emergency, resource, booking, and admin endpoints. These checks run without requiring a database, so they verify authentication is enforced before protected data access.
- Verification for the new route matrix is pending CI on the exact commit. Production database and deployment remain untouched.


### 2026-10-15 — Verify cross-database integration and anonymous route coverage

- GitHub Actions run [38686007919](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38686007919) completed successfully on commit `b435c4d9cbfa790d40e4e0aba6777bb4b2bdcd08`: all five jobs passed, including the new MySQL 8.4 integration tests and anonymous protected-route regression test.
- MySQL integration now covers booking capacity and concurrency plus issue submission, technician assignment, ticket summary counts, notifications, ticket history and the full status lifecycle. PostgreSQL 17 workflow integration remains green.
- Updated README feature-status claims to distinguish CI-backed MySQL/PostgreSQL coverage from still-pending live verification against the actual hosted Supabase project.
- No live Supabase database, production secrets, or hosting settings were accessed or changed.
- Next priority: continue the release-gate audit, especially token revocation/session lifecycle and hosted deployment prerequisites; do not deploy without explicit authorization.


### 2026-10-15 — Implement persistent bearer-token revocation and logout

- Addressed the documented session-lifecycle gap with a persistent token revocation store. The database stores only a SHA-256 fingerprint of a bearer token and its expiry, never the plaintext token; expired revocation rows are cleaned up when a logout is processed.
- Added `POST /api/auth/logout`; authenticated requests check the revocation store, and the frontend attempts server-side revocation before always clearing its local session state.
- Added token-revocation tables to the MySQL and Supabase bootstrap schemas, one-time migrations for existing databases, and least-privilege PostgreSQL app-role grants. Added schema-contract, MySQL DAO, and PostgreSQL HTTP logout regression coverage.
- This is a multi-file security/schema change and is not considered complete until all CI jobs pass on the exact commit. Existing deployed databases would need the appropriate migration before enabling this code; no live database or hosting configuration was modified.


### 2026-10-15 — Fix revocation regression test isolation and least-privilege cleanup

- CI caught two test harness issues in the first token-revocation run: two fake-DAO HTTP tests had not been switched to the no-op revocation adapter, so they attempted a real DB lookup; the new PostgreSQL logout test attempted fixture cleanup through the deliberately least-privilege app role, which correctly cannot delete users.
- Updated all fake-DAO tests to inject the no-op revocation store and changed integration-fixture cleanup to use the dedicated CI admin connection. Application runtime authorization and least-privilege grants remain unchanged.
- The MySQL token-revocation DAO test already passed on MySQL 8.4. The full workflow must pass on this correction before considering logout/revocation verified.


### 2026-10-15 — Verify persistent logout revocation and document migration/release requirements

- GitHub Actions run [38686864208](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38686864208) completed successfully on commit `57cfecc6c96de1c14e6ce64f6a0ad261e2a3c766`: all five jobs passed. The MySQL job verified booking, issue/ticket lifecycle, and persistent token-revocation SQL; PostgreSQL HTTP integration verified logout invalidates the token immediately; Java, frontend, and container jobs passed.
- Updated README to document the logout endpoint, persistent revocation behavior, the migration/grant requirement for existing databases, and the remaining security and hosted-project release blockers.
- The change is verified against ephemeral CI databases only. It does not establish compatibility with a real Supabase project, and no live database, production secret, or hosting project was accessed.
- Next blocker: live staging verification requires an owner-approved dedicated CAMPUSOS Supabase project and API hosting configuration. Do not use unrelated projects or deploy without explicit authorization.


### 2026-10-15 — Add 1,000-concurrent-request HTTP load smoke test

- Added a dedicated CI job that sends 1,000 simultaneous asynchronous requests to `GET /api/health`, verifies every response is HTTP 200 with the expected body, and records total duration plus p50/p95/max request latency.
- The test is opt-in and isolated from normal unit tests; CI gives it a five-minute job limit and asserts the run completes within 30 seconds with p95 below 20 seconds.
- This is an initial HTTP concurrency smoke test, **not** proof that 1,000 authenticated, database-backed users can be supported. The endpoint intentionally avoids DB load so it can isolate HTTP server worker/queue behavior; database throughput, login hashing, revocation/role queries, connection-pool saturation, realistic user journeys and production load still require separate tests.
- Existing HTTP server worker/queue sizes and Hikari pool limits remain bounded. Do not increase pool size based only on user count; size it against the actual Supabase connection allowance and number of API instances.
- Wait for the dedicated load job and all existing CI jobs on this exact commit. Fix regressions and rerun CI until green before proceeding.


### 2026-10-15 — Add 1,000 concurrent authenticated database-backed requests

- Added an opt-in PostgreSQL integration load test using 1,000 distinct ephemeral student accounts and 1,000 simultaneous requests to the real authenticated reporter-ticket endpoint; every request has its own user ID and signed token.
- Each request passes signed-token verification, persistent revocation lookup, current-role lookup, the bounded ticket-page query and the reporter-summary query through the configured Hikari pool. The test asserts every request returns HTTP 200 and records p50/p95/max latency plus total duration.
- The PostgreSQL CI job enables this test only after its schema and least-privilege application role are configured. Test users are deleted through the dedicated CI admin connection; no live database is touched.
- Acceptance limits are intentionally broad CI smoke thresholds (45 seconds total, p95 below 30 seconds), not a claim that production is certified for 1,000 users. This CI environment has a local ephemeral PostgreSQL service and a pool size of five; real capacity still depends on the chosen Supabase tier, API instance resources, network latency, actual user mix, write contention and sustained-duration load.
- Wait for all CI jobs, inspect the load-test latency output and fix any failures before calling this task verified.


### 2026-10-15 — Strengthen the database load test to represent 1,000 distinct users

- Tightened the load test to create 1,000 distinct ephemeral student accounts and issue 1,000 simultaneous authenticated reporter-ticket requests, one per account, rather than reusing 100 accounts ten times.
- This is a more representative concurrent-user smoke test for the authentication and database path. The latest preceding 100-user iteration passed all six CI jobs; its PostgreSQL test reported 1,000 concurrent DB-backed requests in 1,634 ms total, p50 445 ms, p95 1,178 ms, max 1,223 ms, using a five-connection pool. These numbers are from ephemeral GitHub-hosted CI and must not be treated as a production capacity guarantee.
- The strengthened 1,000-distinct-user version is pending verification on the next commit; report its measured output only after CI completes.


### 2026-10-15 — Verify 1,000 distinct concurrent database-backed users

- GitHub Actions run [38705145092](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38705145092) completed successfully on commit `b3fc78d1428e304afd7f40deb08864f2c05c486c`; all six jobs passed: Java verification, frontend audit/tests/build, API container build, MySQL integration, PostgreSQL integration, and HTTP load smoke.
- The strengthened PostgreSQL load test created 1,000 distinct ephemeral student accounts and sent 1,000 simultaneous authenticated GET requests through the actual reporter-ticket endpoint. Every request returned HTTP 200. With the CI Hikari pool set to five connections, measured load window was 1,628 ms total, p50 386 ms, p95 1,136 ms, max 1,175 ms.
- The separate dependency-free HTTP test also passed 1,000 concurrent liveness requests. These are positive baseline smoke-test results for short bursts, not proof of production capacity, sustained load, concurrent login/password hashing, write-heavy workloads, or a real Supabase connection limit.
- No live database or hosting resource was used. Next: stress-test concurrent resource booking invariants beyond two simultaneous callers, then add broader authenticated workflow/soak checks before staging.


### 2026-10-15 — Strengthen booking race tests to 25 simultaneous contenders per database

- Increased the capacity-one race tests from two callers to 25 synchronized callers in both the MySQL 8.4 and PostgreSQL 17 integration suites.
- Each test now requires exactly one successful reservation and 24 explicit capacity-conflict responses; unexpected SQL errors and timeouts fail the test. The PostgreSQL test varies the user identity across student/admin/technician fixtures; the MySQL test uses a shared fixture user.
- This stresses the resource row/advisory-lock serialization with more waiting workers than the configured five-connection CI pool. The update is pending CI verification; no production database was touched.


### 2026-10-15 — Fix compile error in expanded concurrent booking test

- The first CI run after increasing the booking contention test to 25 callers failed compilation because the PostgreSQL integration test used `ArrayList` without importing it. All dependent Java/container/load jobs failed for that same test-compilation error; the frontend job passed.
- Added the missing import. This is a test harness correction, not a production application change. Re-run all six CI jobs and inspect MySQL and PostgreSQL booking stress results before marking this task complete.


### 2026-10-15 — Verify 25-way booking contention and refreshed 1,000-user load test

- GitHub Actions run [38705946833](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38705946833) completed successfully on commit `e79c7b33b2e3b58ca70c3df7441e413a45b0ee5e); all six jobs passed.
- MySQL 8.4 and PostgreSQL 17 integration suites passed with 25 synchronized simultaneous attempts against a capacity-one resource: exactly one booking succeeds and the other 24 receive the expected capacity conflict.
- The 1,000-distinct-user PostgreSQL load test also passed again: 1,000 simultaneous authenticated DB-backed requests completed in 1,720 ms total, p50 346 ms, p95 1,013 ms, max 1,090 ms with a five-connection pool.
- These results establish useful CI regression coverage for short bursts and booking race invariants, but they are not a production capacity certification. Sustained load, realistic frontend journeys, concurrent login/password hashing, write-heavy load, actual Supabase tier limits, and failover behavior remain untested.
- Next: audit notification/emergency fan-out and bounded work for large campus populations; then add longer mixed read/write load checks before staging.


### 2026-10-15 — Add 1,000-recipient emergency broadcast integration test

- Added a cross-database integration test that creates 1,000 temporary campus accounts, creates an emergency through the real `EmergencyService`, and verifies the set-based SQL fan-out produces exactly one notification for every user except the creator.
- Enabled the test in both the MySQL 8.4 integration job and the PostgreSQL 17 least-privilege application-role job. The test removes its own notification rows, emergency record and temporary accounts through the CI admin connection.
- This specifically covers the important campus-wide emergency flow at the requested population size; it is a short CI transaction test, not a guarantee of push-delivery latency or sustained production throughput.
- Wait for all CI jobs to finish on this exact change; fix and rerun any failures before moving on.


### 2026-10-15 — Verify 1,000-recipient emergency notification fan-out

- GitHub Actions run [38706464707](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38706464707) completed successfully on commit `7a0487bb43cc8320122c917a25b7498f821110af`; all six jobs passed.
- The new `EmergencyBroadcastScaleTest` passed against both MySQL 8.4 and PostgreSQL 17. It creates 1,000 temporary accounts, issues an emergency through the real service layer, and asserts the notification count equals every database user except the creator; cleanup removes the test's fan-out notifications and fixtures.
- The 1,000-distinct-user authenticated read test also passed: 1,000 concurrent requests completed in 1,498 ms total, p50 329 ms, p95 1,092 ms, max 1,141 ms with a five-connection pool.
- The earlier 25-contender booking stress test remains green for both SQL engines. These are short CI tests, not production sustained-load certification.
- Next priority: exercise mixed concurrent writes (issue submission, assignment, ticket history and notifications), then run a longer mixed read/write test and document staging gates.


### 2026-10-15 — Enable cross-database mixed concurrent-write verification

- Enabled `ApiWriteLoadTest` in both the MySQL 8.4 targeted integration job and the PostgreSQL 17 least-privilege full integration job.
- The test sends 100 concurrent authenticated issue submissions through `POST /api/issues`, then verifies every issue, assigned ticket, ticket-history record, technician notification and reporter notification was persisted.
- The test compiles and is skipped in jobs without `CAMPUSOS_WRITE_LOAD_TEST_ENABLED=true`; its first actual database-backed run is pending. Do not treat compile success alone as validation.


### 2026-10-15 — Verify MySQL concurrent issue write path; enable equivalent PostgreSQL coverage

- GitHub Actions run [38707339299](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38707339299) completed successfully on commit `fb4df073ec53e939b200e3d0a27cb910f17a18f8). The MySQL write-load test passed: 100 concurrent authenticated issue submissions completed in 1,054 ms, and all issue/ticket/assignment/history/notification assertions passed.
- The first workflow edit enabled the write-load flag only in the MySQL job; the PostgreSQL suite compiled the test but skipped it. Added the missing PostgreSQL job flag so the same 100-request workflow is tested using the least-privilege app role as well.
- PostgreSQL runtime verification is pending the next full CI run; do not treat the earlier run as cross-database write-load validation.


### 2026-10-15 — Prevent shared-IP login lockouts for large campus populations

- The 1,000-user review found the API-level login limiter keyed only by remote IP. Behind a campus NAT or reverse proxy, five failed attempts from one account could therefore block unrelated students sharing the same apparent IP.
- Changed the outer limiter key to the normalized account email plus source address, while preserving the existing account-level limiter in `AuthService`. A regression test now proves repeated failures for one account are still throttled while a different account from the same apparent IP can sign in.
- This is important for multi-user campuses and reverse-proxy hosting. CI must verify the new regression and all six existing jobs before the fix is considered complete.


### 2026-10-15 — Correct escaping in account-isolation rate-limit regression test

- The first CI run of the shared-IP login fix found an over-escaped JSON literal in the new test helper, causing Java test-compilation errors; application code compiled in the prior test version, but the complete pipeline was red.
- Corrected the test JSON string to emit valid `email` and `password` fields. Re-run the full workflow and verify the new regression itself passes, not just compilation.


### 2026-10-15 — Finalize JSON literal escaping for login test

- The first correction still left an extra backslash in the generated Java source. Replaced the helper line with a raw literal and checked the exact file content before committing.
- CI remains the source of truth for this fix; do not mark the shared-IP regression complete until the full run is green.


### 2026-10-15 — Extend 1,000-user read load from one burst to five rounds

- Strengthened `ApiDatabaseLoadTest` to run five rounds of 1,000 simultaneous authenticated requests, for 5,000 database-backed requests total across 1,000 distinct users. Each round checks every HTTP status and response body; the test reports round duration and aggregate p50/p95/max latency.
- The previous single-round CI result was 997 ms total, p50 351 ms, p95 622 ms, max 723 ms with a five-connection PostgreSQL pool (run [38708218745](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38708218745)). The five-round variant is pending verification and has a 90-second total budget with p95 below 30 seconds.
- Cross-database write-path tests also passed in that run: 100 simultaneous issue submissions completed in 1,244 ms on MySQL and 233 ms on PostgreSQL, with issue, assigned ticket, history and notification assertions.
- The shared-IP login-throttle regression passed (3 rate-limit tests, no failures) on the latest full CI run. Do not treat a new code change as complete until the strengthened 5,000-request test and all CI jobs pass.


### 2026-10-15 — Add 100-way real password-verification/login load test

- Added an opt-in integration test that creates 100 temporary student accounts and submits 100 concurrent successful `POST /api/auth/login` requests from the same apparent source IP. This exercises real PBKDF2 verification, user lookup, token creation, and the account-plus-source-address limiter rather than bypassing authentication.
- Enabled the test in both MySQL 8.4 and PostgreSQL 17 integration jobs; it asserts every response is HTTP 200 with a token and reports p50/p95/max latency. The test removes temporary users through the CI admin connection.
- The preceding five-round read-load test passed on PostgreSQL: 5,000 authenticated requests across 1,000 distinct users completed in 9,315 ms total, p50 255 ms, p95 2,086 ms, max 2,188 ms, using a five-connection pool (run [38708669488](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38708669488)).
- This new login stress test is pending CI verification. Password hashing is CPU-intensive; do not extrapolate from 100 simultaneous logins to 1,000 simultaneous login attempts or claim production capacity without a dedicated staging load test.


### 2026-10-15 — Verify five-round 1,000-user reads and 100-way concurrent logins

- GitHub Actions run [38709005581](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38709005581) completed successfully on commit `af22a08c395ac29331225915141eff77643b0f83`; all six jobs passed, including frontend audit/build, Java verification, container build, MySQL, PostgreSQL, and the HTTP load smoke job.
- The PostgreSQL test sent 5,000 authenticated DB-backed reads in five rounds of 1,000 concurrent requests across 1,000 distinct users. Total duration was 8,673 ms; p50 351 ms, p95 1,310 ms, max 2,088 ms; pool size five.
- The new real login load test passed on both databases with 100 distinct accounts logging in concurrently from the same apparent IP: MySQL 1,764 ms total (p50 1,096 ms, p95 1,633 ms, max 1,758 ms); PostgreSQL 1,317 ms total (p50 706 ms, p95 1,246 ms, max 1,275 ms). Every login returned HTTP 200 and a token.
- The cross-database 100-issue write test also passed (MySQL 1,244 ms; PostgreSQL 233 ms), along with 25-way capacity-one booking races and 1,000-recipient emergency broadcasts. These are CI smoke tests on ephemeral databases; production traffic shape, database tier limits, sustained uptime, and failover still require staging load tests.
- Next audit target: admin user management currently uses a bounded first-page query; verify the frontend can navigate beyond 200 users rather than silently hiding users in a 1,000-person campus.


### 2026-10-15 — Fix admin user directory truncation for campuses above 200 accounts

- The scale audit found that `UserDAO.findUsers()` silently returned only the first 200 users and the React admin directory had no pagination controls. This would hide accounts in a campus with 1,000+ users.
- Replaced the fixed first-200 query with deterministic page/limit SQL, added a total-user count and page metadata to `GET /api/admin/users?page=1&pageSize=50`, and added Previous/Next/Refresh controls plus total account/page indicators to the admin UI. API page size is capped at 100.
- Added a cross-database integration test plan to verify every account is reachable exactly once across pages and malformed/out-of-range page inputs are rejected. The test and full CI verification are pending.


### 2026-10-15 — Raise admin directory pagination test to 1,000 campus accounts

- The first pagination regression passed on both MySQL and PostgreSQL, but its fixture created only 121 accounts. That proved multi-page behavior without exercising the requested 1,000-user campus size.
- Increased the dedicated fixture to 1,000 students plus one admin. The test checks every database user is returned exactly once across all pages, each response is capped at 50 rows, all 1,001 fixture accounts are reachable, and invalid page/page-size values are rejected.
- The previous six-job CI run [38709719029](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38709719029) passed, including 5,000 authenticated database-backed reads across 1,000 distinct users (five rounds of 1,000; 9,027 ms total, p50 298 ms, p95 1,277 ms, max 2,308 ms, PostgreSQL pool size 5), concurrent login/write tests, emergency fan-out, booking races, frontend audit/build and container checks.
- The stronger 1,001-account pagination fixture is pending the next full CI run. These tests provide useful burst-load and data-visibility coverage, not a production capacity certification.


### 2026-10-15 — Paginate staff/admin ticket queues for large campuses

- The scale audit found the staff ticket DAO returned only the first 100 tickets with no UI navigation, silently hiding older/lower-priority work once the queue exceeded 100 rows.
- Added deterministic page/limit SQL and total counts for both administrator-wide and staff-assigned queues, API validation for `page`/`pageSize`, bounded response metadata, and Previous/Next controls in the work-queue UI.
- Added a cross-database HTTP integration test that creates 125 real tickets, verifies every ticket is reachable exactly once across pages, checks invalid paging inputs, and ensures results exceed the previous 100-ticket cap. CI enables it against MySQL 8.4 and PostgreSQL 17.
- This change is pending full CI verification; do not claim the staff queue scale issue is fixed until both SQL engines and frontend build pass.


### 2026-10-15 — Enable staff queue pagination regression on both database engines

- The first staff-ticket pagination commit passed all six CI jobs, and MySQL executed the new 125-ticket API test successfully. Review of the workflow revealed the opt-in environment flag had only been added to the MySQL job, so PostgreSQL compiled but skipped this specific integration test.
- Enabled `StaffTicketPaginationTest` in both MySQL 8.4 and PostgreSQL 17 jobs and removed a no-op loop from the test setup. The next CI run must show the test executing (not skipped) on both engines before cross-database pagination is considered verified.
- Previous run: [38710401124](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38710401124), all six jobs green; MySQL staff pagination test passed with 125 fixtures beyond the former 100-row limit.


### 2026-10-15 — Correct CI environment flag placement for staff pagination test

- The first attempt to enable staff-ticket pagination on both database engines accidentally duplicated the YAML key in the MySQL job instead of adding it to PostgreSQL. GitHub rejected the workflow before starting any jobs (run [38710634714](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38710634714)); no tests ran on that commit.
- Removed the duplicate key and added exactly one opt-in flag to each database job. The corrected workflow must trigger and execute the new regression on both MySQL and PostgreSQL before this task is complete.


### 2026-10-15 — Increase concurrent issue-write test to 1,000 users

- The existing write-load test verified 100 simultaneous authenticated issue submissions and asserted the matching issue, ticket, assignment, ticket-history, technician-notification and reporter-notification records.
- Increased the fixture to 1,000 distinct student accounts and 1,000 simultaneous `POST /api/issues` requests. Every response must be HTTP 201, and the database must contain exactly 1,000 matching issues, assigned tickets, history rows, and both classes of notifications. Test timeout is 90 seconds to detect pathological saturation while tolerating CI variability.
- The test runs against both MySQL 8.4 and PostgreSQL 17 using the configured five-connection CI pool. The full workflow result is pending; if it fails, inspect whether the cause is an application correctness issue, queue/pool saturation, or only an unrealistic CI threshold before adjusting.
- Previous verified CI run [38710767750](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38710767750) passed all six jobs, including the 1,001-account admin pagination test and 125-ticket staff queue pagination test on both database engines.


### 2026-10-15 — Fix HTTP accept/worker saturation exposed by 1,000 concurrent writes

- The 1,000-request write stress test failed on both PostgreSQL and MySQL with client-side connection resets before receiving HTTP response headers. The independent 1,000-request HTTP smoke test and database-backed read stress test passed, pointing to request burst handling rather than a compilation or database syntax issue.
- Root cause found in server configuration: the JDK HTTP server used backlog `0` (OS default) and as few as four worker threads on small CI runners, with a queue of only 256 tasks. A simultaneous 1,000-client arrival could overrun the accept/request dispatch capacity.
- Increased the accept backlog to 2,048, raised the default worker pool to 16–64 based on available processors, expanded the bounded request queue to 2,048, and added validated environment overrides (`CAMPUSOS_HTTP_WORKER_THREADS` 4–128 and `CAMPUSOS_HTTP_QUEUE_CAPACITY` 64–10,000). The queue remains bounded and `CallerRunsPolicy` still applies back-pressure if the system exceeds configured capacity.
- Verified by GitHub Actions run [38721939842](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38721939842), all six jobs green. The 1,000-write burst passed on MySQL in 2,228 ms and PostgreSQL in 1,486 ms; all issue, ticket, assignment, history, and notification count assertions passed. The PostgreSQL 5,000-request read stress test passed in 3,131 ms (p50 306 ms, p95 495 ms, max 572 ms) with a five-connection pool. The HTTP liveness burst, frontend audit/build, Java verification, and container build also passed.
- These tests confirm the tested CI burst workloads on ephemeral services, not a production guarantee. Live Supabase networking, host CPU/memory limits, connection limits, sustained load, failure recovery, and multi-instance rate limiting still need staging verification.


### 2026-10-15 — Record verified 1,000-user burst handling and operating controls

- Added the HTTP worker-thread and bounded-queue environment variables to the README, with allowed ranges and the reminder to size them together with the JDBC pool and database connection limits.
- Updated CI documentation to reflect the actual 1,000 simultaneous issue-write workload, rather than the former 100-request test, and recorded the current verified results.
- Verification source: [GitHub Actions run 38721939842](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/38721939842), completed successfully on `b16c829d3fce7e0d25d6140e2e40349497dcd786`; all six jobs passed. MySQL write burst: 1,000 requests in 2,228 ms. PostgreSQL write burst: 1,000 requests in 1,486 ms. PostgreSQL read workload: 5,000 requests over five 1,000-concurrent rounds in 3,131 ms (p50 306 ms, p95 495 ms, max 572 ms; pool size 5). All 1,000 issue-write integrity assertions passed on both engines.
- Capacity boundary: these are burst smoke tests on ephemeral GitHub-hosted runners and CI databases. They do not prove sustained 1,000-user production capacity, real Supabase connectivity, a specific response-time SLO, or resilience to provider-level throttling/outages. Staging load tests and host/database sizing remain release gates.
