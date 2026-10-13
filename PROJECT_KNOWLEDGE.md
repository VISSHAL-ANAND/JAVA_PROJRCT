# CAMPUSOS — Project Knowledge & Engineering Log

> **Purpose:** This is the persistent handover document for CAMPUSOS. Update it after every meaningful investigation, code change, test, bug fix, design decision, and deployment change. It records what is known, what is verified, what remains incomplete, and the next safe step.
>
> **Last reviewed:** 2026-10-13  
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
- CAMPUSOS_AUTH_SECRET must be configured to a strong stable value (32+ characters) for hosted use. If absent, a random per-process key is generated for local development, so tokens stop validating after restart. Current stateless tokens cannot be individually revoked before expiry; full route-by-route role coverage and security tests remain required.

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
- **Deployment architecture must be resolved before implementation:** Vercel is a natural target for the Vite frontend, but this repository's long-running Java `HttpServer`/TCP server is not a Vercel-native runtime. Supabase uses PostgreSQL, while the current DAO/schema and JDBC driver target MySQL. Do not assume these can be connected by only setting environment variables. Compare (A) Vercel frontend + Java API on a Java-capable host + retain MySQL, versus (B) Vercel frontend + Java API on a Java-capable host + migrate DAO/schema to Supabase PostgreSQL, versus (C) rewrite the API as Vercel serverless functions. Preserve the Java stack unless a conscious decision selects another option.
- No deployment or hosting changes are authorized until the owner explicitly says to host.
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

<!-- APPEND NEW DATED ENTRIES BELOW; keep older entries intact. -->

## 13. Next actions — ordered backlog

### Phase 0 — Repository safety and reproducibility
- [x] Add/verify `.gitignore` for Maven output, Node dependencies/build output, IDE files, and logs.
- [x] Add a safe config example and environment-based database credential overrides; document them in README.
- [ ] Check main-branch tracked files and history for generated artifacts/secrets.
- [x] Inspect initial GitHub Actions build steps: Java `mvn clean verify` and frontend `npm ci` + `npm run build` both succeeded on the first workflow run; add actual API/database/security smoke tests next.
- [x] Remove accidentally tracked `frontend/node_modules`; verify the main tree no longer includes it. Keep checking future diffs because `.gitignore` alone does not untrack existing files.
- [ ] Scan tracked files and Git history for generated artifacts/secrets; rotate any exposed real credential.
- [x] Replace raw exception text in API responses with safe messages and log server-side failures (initial pass; malformed JSON/body-size handling still needs work).
- [ ] Review the `side` diff separately; do not merge its generated `target/` files or machine-specific batch paths.

### Phase 1 — API security foundation (highest priority)
- [x] Choose and document a signed bearer-token strategy with expiry; stable hosted secret required. Individual token revocation is not implemented.
- [x] Implement token-verified identity for issue submission and reporter ticket reads; role checks for issue creation. Staff/admin route authorization remains to do.
- [x] Derive issue reporter identity from token and verify ticket route ID matches authenticated user.
- [ ] Ensure users can only read/update tickets they are allowed to access.
- [x] Add JSON body-size/content-type/malformed-body checks for current routes and safe API errors; further validation and route coverage remain.
- [x] CI run 38428705148 passed Java verify, frontend production build, and 9 unit/smoke tests (0 failures).
- [ ] Add wrong-role, wrong-owner, invalid-input, and database-backed success tests.

### Phase 2 — Test foundation and API quality
- [x] Introduce JUnit 5 and the Maven Surefire plugin; add initial unit tests for priority classification (latest CI run pending verification).
- [ ] Add database-backed integration tests for login, issue creation, ticket ownership/assignment, status history, notifications, emergency broadcast, and concurrent bookings. Anonymous and wrong-role API smoke tests now pass.
- [ ] Define consistent JSON response/error contracts.
- [x] Add liveness/readiness endpoints and HTTP API executor shutdown hook; structured request IDs/metrics remain.
- [x] Move frontend API base URL to `VITE_API_BASE_URL`; document the local example and hosting configuration. (Java API CORS remains fixed to localhost and must be made environment-configurable before hosting.)

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
- [ ] Decide deployment architecture: Vercel frontend + Java API on Java-capable host, and choose MySQL vs Supabase PostgreSQL migration.
- [ ] Review the user's existing Supabase keep-alive GitHub Actions YAML before adopting it; do not assume scheduled pings prevent pausing or replace monitoring.
- [x] Add initial CI build checks for Java and frontend (first run still needs review).
- [ ] Add automated API smoke tests, dependency scanning, backups, restore test, monitoring, and rollback plan.
- [ ] Verify HTTPS, secrets management, migrations, least-privilege DB account, and production CORS.
- [ ] Run end-to-end acceptance tests and document release procedure.

**Immediate next task:** Confirm the CI run containing JUnit tests passes. Then agree on the Vercel/Supabase/Java hosting architecture before database migration and auth changes. Recommended target: Vercel for React frontend, a Java-capable host for the existing Java API, and Supabase PostgreSQL after a deliberate DAO/schema migration. Do not host until explicitly authorized.
