# CAMPUSOS Deployment Readiness

Last reviewed: 2026-10-28  
Repository: `VISSHAL-ANAND/JAVA_PROJRCT`  
Latest inspected baseline commit: `2e64c2d6594f983d35ade91a8d787fc7675201cd`  
Latest CI: [run 40668560841](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/40668560841) — **7/7 jobs passed**

## Decision

**Repository build/deployment packaging is ready for hosting setup; no deployment is live yet.** Production approval is still blocked on an approved dedicated host/database, environment secrets, and live end-to-end verification. Per the owner’s current instruction, do not start any additional load testing during this deployment phase.

## Evidence from the actual production Docker image in CI

| Scenario | Result | Status |
| --- | --- | --- |
| 1,000 concurrent authenticated protected-route requests | 1,000/1,000 success; p95 920 ms; max 984 ms | Passes CI burst gate |
| 1,000 concurrent database-backed readiness requests | 1,000/1,000 success; p95 400 ms | Passes CI burst gate |
| 1,000 simultaneous database-backed successful logins | 1,000/1,000 success; p95 12.441 s; max 12.995 s | Correctness passes; proposed staging p95 <10 s target not met |
| MySQL 8.4 and PostgreSQL 17 integration/load suites | Booking contention, 1,000 issue writes, 1,000-user emergency fan-out, token revocation, pagination, login limiting, 5,000 authenticated reads, and five-round mixed workloads passed | Passes CI |
| Frontend audit/tests/build, Java verification, container build/runtime | All passed in latest CI | Passes CI |

CI uses disposable databases and shared GitHub runners. The measurements are useful regression evidence, not a promise that an arbitrary production plan can handle 1,000 simultaneous users.

## Required before production

- [ ] Create an isolated staging frontend, Java API service, and managed PostgreSQL database owned/approved for CAMPUSOS.
- [ ] Apply the correct bootstrap schema to a new database, or review/apply only the matching migrations to an existing staging database.
- [ ] Create a least-privilege app role; verify permissions and connection limits.
- [ ] Configure a stable staging-only `CAMPUSOS_AUTH_SECRET`, JDBC URL/credentials, pool size, exact frontend CORS origin, and frontend `VITE_API_BASE_URL`. Never put backend secrets in `VITE_*` variables.
- [ ] Verify HTTPS, health/readiness, login/logout, issue creation, technician assignment/status/history, notifications, emergency broadcast, booking/cancellation, and admin workflows end-to-end.
- [ ] (Deferred by owner) Do not run load tests during the current deployment task. Revisit only when explicitly requested.
- [ ] Capture CPU/memory, HTTP queue/worker metrics, JDBC pool waits, database connections/locks, error rate, and p95 latency. Acceptance target: <1% failed requests, API p95 <2 s, login p95 <10 s. Investigate any miss; do not weaken the target merely to get a green run.
- [ ] Complete security review, including session-token storage/XSS risk, TLS/CORS, password-hash policy, secret rotation, audit logging, abuse/rate limits, and error/log redaction.
- [ ] Verify backups and restore, monitoring/alerts, graceful shutdown/termination grace period, and rollback procedure.

## Hosting architecture

- **Frontend:** Vercel static/SPA build from the repository root using `vercel.json`.
- **Backend:** a separate Java/container-capable web service using the root `Dockerfile`; Vercel's frontend deployment does not host the long-running Java HTTP server.
- **Database:** dedicated managed PostgreSQL/Supabase project, separate from any unrelated application database.

Repository inspection confirms the current `main` commit is `2e64c2d6594f983d35ade91a8d787fc7675201cd`; CI run [40668560841](https://github.com/VISSHAL-ANAND/JAVA_PROJRCT/actions/runs/40668560841) is green across all seven jobs. Vercel integration currently exposes three existing projects (`locus_cafe`, `prasad`, `magco`) but no CAMPUSOS project; none will be repurposed. The API already reads `PORT`, `CAMPUSOS_ENV`, `CAMPUSOS_AUTH_SECRET`, `CAMPUSOS_DB_URL`, `CAMPUSOS_DB_USERNAME`, `CAMPUSOS_DB_PASSWORD`, `CAMPUSOS_DB_POOL_SIZE`, and `CAMPUSOS_CORS_ORIGINS` from environment variables. `vercel.json` already builds `frontend/` and applies SPA fallback. The frontend requires `VITE_API_BASE_URL` to point to the deployed HTTPS API; leaving it unset in production causes requests to use the frontend origin and fail. `/api/health` and `/api/health/ready` exist. No live infrastructure or database was created or modified.
